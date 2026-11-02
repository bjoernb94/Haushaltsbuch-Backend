#!/usr/bin/env python3
"""Legt lokal Testkonten mit Verträgen an, die alle fachlichen Zustände zeigen.

Arbeitet nur über die öffentliche API, die Daten laufen also durch dieselben Prüfungen wie im
Betrieb. Beliebig oft ausführbar: Vorhandene Konten bleiben, ihre Verträge werden ersetzt. Die Daten
hängen vom heutigen Tag ab; erneutes Ausführen bringt den Stand wieder in Ordnung.

Aufruf und Konten: docs/entwicklung.md, Abschnitt „Testkonten“.
"""

import argparse
import calendar
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from http.cookies import SimpleCookie
from zoneinfo import ZoneInfo

# Nur für die lokale Umgebung; nirgends sonst verwenden.
PASSWORT = "Testkonto-1!"

LOKALE_HOSTS = {"localhost", "127.0.0.1", "::1"}

STANDARD_URL = "http://localhost:8080"

ALLE_KOMPONENTEN = {"FINANZUEBERSICHT", "VERTRAGSUEBERSICHT", "VERSORGUNGSVERTRAEGE", "PORTFOLIO"}

ALLE_ZAHLWEISEN = {"MONATLICH", "VIERTELJAEHRLICH", "HALBJAEHRLICH", "JAEHRLICH"}

ALLE_ZAHLUNGSWEGE = {"LASTSCHRIFT", "KREDITKARTE", "PAYPAL", "UEBERWEISUNG", "DAUERAUFTRAG"}

ALLE_KATEGORIEN = {"STREAMING_ABOS", "SOFTWARE_APPS", "VERSICHERUNG", "MOBILFUNK_INTERNET", "MITGLIEDSCHAFTEN",
                   "GEBUEHREN_BEITRAEGE", "SONSTIGES"}

# Für erwartet_naechste: Es gibt eine nächste Abbuchung, das genaue Datum ist egal.
VORHANDEN = object()


class Abbruch(Exception):
    """Etwas stimmt nicht; das Skript endet mit dieser Meldung."""


@dataclass
class Fall:
    """Ein Vertrag und was das Backend dafür berechnen muss."""

    anbieter: str
    art: str
    kategorie: str = "SONSTIGES"
    zahlweise: str = "MONATLICH"
    abbuchung_ab: date | None = None
    kuendbar: bool = True
    felder: dict = field(default_factory=dict)
    dringend: bool = False
    erwartet_naechste: object = VORHANDEN


@dataclass
class Konto:
    name: str
    komponenten: set
    faelle: list
    # Anzahl in vertraege/uebersicht; None, wenn das Konto den Vertragsbereich nicht gebucht hat.
    fristen_in_uebersicht: int | None

    @property
    def email(self):
        return f"testkonto.{self.name}@example.org"


class Sitzung:
    """Ein Nutzer: eigene Cookies für Session und CSRF-Token, wie ein eigener Browser."""

    def __init__(self, url):
        self.url = url
        self.cookies = {}

    def anfrage(self, methode, pfad, daten=None):
        """Liefert Status und JSON der Antwort; Fehlerstatus sind hier kein Fehler."""
        if methode != "GET" and "XSRF-TOKEN" not in self.cookies:
            # Nach der Anmeldung ist das Token neu und kommt erst mit der nächsten Antwort.
            self.anfrage("GET", "/actuator/health")
        kopf = {"Accept": "application/json, application/problem+json"}
        if self.cookies:
            kopf["Cookie"] = "; ".join(f"{name}={wert}" for name, wert in self.cookies.items())
        if methode != "GET":
            kopf["X-XSRF-TOKEN"] = self.cookies.get("XSRF-TOKEN", "")
        inhalt = None
        if daten is not None:
            inhalt = json.dumps(daten).encode()
            kopf["Content-Type"] = "application/json"
        anfrage = urllib.request.Request(self.url + pfad, data=inhalt, headers=kopf, method=methode)
        try:
            with urllib.request.urlopen(anfrage, timeout=30) as antwort:
                return self._auswerten(antwort)
        except urllib.error.HTTPError as fehler:
            return self._auswerten(fehler)
        except urllib.error.URLError as fehler:
            raise Abbruch(f"Backend unter {self.url} nicht erreichbar: {fehler.reason}") from fehler

    def _auswerten(self, antwort):
        for zeile in antwort.headers.get_all("Set-Cookie") or []:
            for name, wert in SimpleCookie(zeile).items():
                if wert["max-age"] == "0" or not wert.value:
                    self.cookies.pop(name, None)
                else:
                    self.cookies[name] = wert.value
        text = antwort.read().decode()
        try:
            return antwort.status, json.loads(text) if text else None
        except json.JSONDecodeError:
            return antwort.status, text


def pruefe_lokale_umgebung(url):
    """Die Passwörter stehen hier im Klartext: Gegen etwas anderes als die lokale Umgebung läuft nichts."""
    host = urllib.parse.urlparse(url).hostname
    if host not in LOKALE_HOSTS:
        raise Abbruch(f"Nur für die lokale Umgebung, nicht für {url}.")
    # Die OpenAPI-Beschreibung gibt es nur im dev-Profil; fängt z. B. einen Tunnel auf prod ab.
    status, _ = Sitzung(url).anfrage("GET", "/v3/api-docs")
    if status != 200:
        raise Abbruch(f"{url} läuft nicht im dev-Profil (/v3/api-docs antwortet mit {status}).")


def erwarte(status, erwartet, was, antwort):
    if status != erwartet:
        raise Abbruch(f"{was}: Status {status} statt {erwartet}: {antwort}")


def anmelden(sitzung, konto):
    """Registriert das Konto oder meldet es an, wenn es das schon gibt."""
    status, antwort = sitzung.anfrage("POST", "/api/registrierung", {
        "email": konto.email,
        "passwort": PASSWORT,
        "vorname": "Testkonto",
        "name": konto.name,
        "komponenten": sorted(konto.komponenten),
    })
    if status == 201 and sitzung.anfrage("GET", "/api/nutzer/ich/komponenten")[0] == 200:
        return "angelegt"
    if status not in (201, 409):
        raise Abbruch(f"{konto.email}: Registrierung ergab Status {status}: {antwort}")
    status, antwort = sitzung.anfrage("POST", "/api/anmeldung", {"email": konto.email, "passwort": PASSWORT})
    if status == 401:
        raise Abbruch(f"{konto.email}: Das Konto gibt es, aber das Passwort stimmt nicht mehr. Konto in Keycloak "
                      "löschen oder Passwort zurücksetzen.")
    erwarte(status, 204, f"{konto.email}: Anmeldung", antwort)
    return "vorhanden"


def pruefe_komponenten(sitzung, konto):
    status, antwort = sitzung.anfrage("GET", "/api/nutzer/ich/komponenten")
    erwarte(status, 200, f"{konto.email}: Komponenten", antwort)
    if set(antwort) != konto.komponenten:
        raise Abbruch(f"{konto.email}: Komponenten {sorted(antwort)} statt {sorted(konto.komponenten)}. Leer heißt oft: "
                      "Die lokale Datenbank wurde zurückgesetzt, das Konto in Keycloak aber nicht. Konto in Keycloak "
                      "löschen und das Skript neu starten.")


def vertraege_loeschen(sitzung, konto):
    status, vertraege = sitzung.anfrage("GET", "/api/vertraege")
    erwarte(status, 200, f"{konto.email}: Verträge auflisten", vertraege)
    for vertrag in vertraege:
        pfad = f"/api/vertraege/{vertrag['id']}?version={vertrag['version']}"
        status, antwort = sitzung.anfrage("DELETE", pfad)
        erwarte(status, 204, f"{konto.email}: Vertrag löschen", antwort)


def anfrage_zu(fall, kategorie_ids, heute):
    daten = {
        "anbieter": fall.anbieter,
        "bezeichnung": None,
        "kategorieId": kategorie_ids[fall.kategorie],
        "vertragsnummer": None,
        "betrag": {"wert": 9.99, "waehrung": "EUR"},
        "zahlweise": fall.zahlweise,
        "abbuchungAb": fall.abbuchung_ab or heute + timedelta(days=9),
        "zahlungsweg": None,
        "kuendbar": fall.kuendbar,
        "beginn": None,
        "laufzeitende": None,
        "verlaengerung": None,
        "kuendigungsfristMonate": None,
        "gekuendigtZum": None,
        "notiz": None,
    } | fall.felder
    return {schluessel: wert.isoformat() if isinstance(wert, date) else wert for schluessel, wert in daten.items()}


def pruefe_berechnung(fall, antwort):
    """Vergleicht mit der Erwartung des Falls; gibt zurück, was die Tabelle am Ende zeigt."""
    berechnet = antwort["berechnet"]
    art = berechnet["kuendigung"]["art"]
    naechste = berechnet["naechsteAbbuchung"]
    fehler = []
    if art != fall.art:
        fehler.append(f"art {art} statt {fall.art}")
    if berechnet["kuendigung"]["dringend"] != fall.dringend:
        fehler.append(f"dringend {berechnet['kuendigung']['dringend']} statt {fall.dringend}")
    if fall.erwartet_naechste is VORHANDEN:
        if naechste is None:
            fehler.append("keine nächste Abbuchung, erwartet war eine")
    elif fall.erwartet_naechste is None:
        if naechste is not None:
            fehler.append(f"nächste Abbuchung {naechste}, erwartet war keine")
    elif naechste != fall.erwartet_naechste.isoformat():
        fehler.append(f"nächste Abbuchung {naechste} statt {fall.erwartet_naechste}")
    if fehler:
        raise Abbruch(f"Fall „{fall.anbieter[:60]}“: {', '.join(fehler)}")
    return art, naechste


def vertraege_anlegen(sitzung, konto, heute):
    status, kategorien = sitzung.anfrage("GET", "/api/vertraege/kategorien")
    erwarte(status, 200, f"{konto.email}: Kategorien", kategorien)
    kategorie_ids = {kategorie["schluessel"]: kategorie["id"] for kategorie in kategorien if kategorie["schluessel"]}
    zeilen = []
    for fall in konto.faelle:
        status, antwort = sitzung.anfrage("POST", "/api/vertraege", anfrage_zu(fall, kategorie_ids, heute))
        erwarte(status, 201, f"Fall „{fall.anbieter[:60]}“: Anlegen", antwort)
        art, naechste = pruefe_berechnung(fall, antwort)
        zeilen.append((fall.anbieter[:50], art, "ja" if fall.dringend else "", naechste or "keine"))
    return zeilen


def pruefe_uebersicht(sitzung, konto):
    status, antwort = sitzung.anfrage("GET", "/api/vertraege/uebersicht")
    erwarte(status, 200, f"{konto.email}: Übersicht", antwort)
    fristen = len(antwort["naechsteKuendigungsfristen"])
    if fristen != konto.fristen_in_uebersicht:
        raise Abbruch(f"{konto.email}: {fristen} Fristen in der Übersicht statt {konto.fristen_in_uebersicht}")


def einrichten(url, konto, heute):
    sitzung = Sitzung(url)
    zustand = anmelden(sitzung, konto)
    pruefe_komponenten(sitzung, konto)
    zeilen = []
    if konto.fristen_in_uebersicht is None:
        status, antwort = sitzung.anfrage("GET", "/api/vertraege")
        erwarte(status, 403, f"{konto.email}: Vertragsbereich ohne Komponente", antwort)
    else:
        vertraege_loeschen(sitzung, konto)
        zeilen = vertraege_anlegen(sitzung, konto, heute)
        pruefe_uebersicht(sitzung, konto)
    status, antwort = sitzung.anfrage("POST", "/api/abmeldung")
    erwarte(status, 204, f"{konto.email}: Abmeldung", antwort)
    return zustand, zeilen


def abbuchung_vom_monatsende(heute):
    """Die nächste Abbuchung bei monatlichem Anker 31.01.: der 31. oder der letzte Tag eines kürzeren Monats."""
    jahr, monat = heute.year, heute.month
    while True:
        termin = date(jahr, monat, min(31, calendar.monthrange(jahr, monat)[1]))
        if termin >= heute:
            return termin
        jahr, monat = (jahr + 1, 1) if monat == 12 else (jahr, monat + 1)


def faelle_alles(heute):
    """Jeder Fall aus #77. Abstände mit Reserve zu den 45 Tagen, damit kurze Monate nichts kippen."""

    def tage(anzahl):
        return heute + timedelta(days=anzahl)

    return [
        # Kündigungsart KUENDIGEN_BIS; mehr als fünf, damit die Übersicht kürzt.
        Fall("Kündigen bis – dringend", "KUENDIGEN_BIS", dringend=True, kategorie="VERSICHERUNG",
             felder={"laufzeitende": tage(60), "verlaengerung": "ZWOELF_MONATE", "kuendigungsfristMonate": 1,
                     "zahlungsweg": "LASTSCHRIFT"}),
        Fall("Kündigen bis – nicht dringend", "KUENDIGEN_BIS", kategorie="MOBILFUNK_INTERNET",
             felder={"laufzeitende": tage(200), "verlaengerung": "ZWOELF_MONATE", "kuendigungsfristMonate": 1,
                     "zahlungsweg": "KREDITKARTE"}),
        Fall("Kündigen bis – Frist 0 Monate", "KUENDIGEN_BIS", kategorie="MITGLIEDSCHAFTEN",
             felder={"laufzeitende": tage(100), "verlaengerung": "ZWOELF_MONATE", "kuendigungsfristMonate": 0,
                     "zahlungsweg": "PAYPAL"}),
        Fall("Kündigen bis – Frist 3 Monate", "KUENDIGEN_BIS", kategorie="GEBUEHREN_BEITRAEGE",
             felder={"laufzeitende": tage(150), "verlaengerung": "ZWOELF_MONATE", "kuendigungsfristMonate": 3,
                     "zahlungsweg": "UEBERWEISUNG"}),
        Fall("Kündigen bis – ohne Frist", "KUENDIGEN_BIS", kategorie="SOFTWARE_APPS",
             felder={"laufzeitende": tage(120), "verlaengerung": "ZWOELF_MONATE", "zahlungsweg": "DAUERAUFTRAG"}),
        Fall("Kündigen bis – ohne Angabe zur Verlängerung", "KUENDIGEN_BIS", kategorie="STREAMING_ABOS",
             felder={"laufzeitende": tage(90), "kuendigungsfristMonate": 1}),
        Fall("Kündigen bis – vierteljährlich", "KUENDIGEN_BIS", zahlweise="VIERTELJAEHRLICH", dringend=True,
             felder={"laufzeitende": tage(40), "verlaengerung": "ZAHLUNGSINTERVALL", "kuendigungsfristMonate": 1,
                     "bezeichnung": "Mit Bezeichnung"}),
        Fall("Kündigen bis – halbjährlich", "KUENDIGEN_BIS", zahlweise="HALBJAEHRLICH",
             felder={"laufzeitende": tage(180), "verlaengerung": "ZAHLUNGSINTERVALL"}),
        Fall("Kündigen bis – jährlich", "KUENDIGEN_BIS", zahlweise="JAEHRLICH",
             felder={"laufzeitende": tage(300), "verlaengerung": "ZAHLUNGSINTERVALL"}),
        # Kündigungsart MONATLICH_KUENDBAR
        Fall("Monatlich kündbar – ohne Laufzeitende", "MONATLICH_KUENDBAR",
             felder={"verlaengerung": "MONATLICH", "kuendigungsfristMonate": 1, "beginn": tage(-400)}),
        Fall("Monatlich kündbar – Laufzeitende vorbei", "MONATLICH_KUENDBAR",
             felder={"laufzeitende": tage(-30), "verlaengerung": "ZAHLUNGSINTERVALL"}),
        # Kündigungsart ENDET
        Fall("Endet – Abbuchung davor", "ENDET", felder={"laufzeitende": tage(100), "verlaengerung": "KEINE"}),
        Fall("Endet – Abbuchung genau am Vertragsende", "ENDET", erwartet_naechste=tage(9),
             felder={"laufzeitende": tage(9), "verlaengerung": "KEINE"}),
        Fall("Endet – keine Abbuchung mehr", "ENDET", erwartet_naechste=None,
             felder={"laufzeitende": tage(5), "verlaengerung": "KEINE"}),
        # Kündigungsart KEINE_LAUFZEIT
        Fall("Keine Laufzeit – optionale Felder alle leer", "KEINE_LAUFZEIT"),
        Fall("Keine Laufzeit – Laufzeitende ohne Verlängerung, Frist abgelaufen", "KEINE_LAUFZEIT",
             felder={"laufzeitende": tage(10), "kuendigungsfristMonate": 3}),
        Fall("Keine Laufzeit – Monatsende als Anker (31.01.)", "KEINE_LAUFZEIT", abbuchung_ab=date(heute.year - 1, 1, 31),
             erwartet_naechste=abbuchung_vom_monatsende(heute)),
        # Kündigungsart NICHT_KUENDBAR
        Fall("Nicht kündbar", "NICHT_KUENDBAR", kuendbar=False, felder={"beginn": tage(-1000)}),
        # Kündigungsart GEKUENDIGT
        Fall("Gekündigt – optionale Felder alle gefüllt", "GEKUENDIGT", kategorie="VERSICHERUNG",
             felder={"bezeichnung": "Alle Felder", "vertragsnummer": "TEST-4711", "zahlungsweg": "LASTSCHRIFT",
                     "beginn": tage(-200), "laufzeitende": tage(165), "verlaengerung": "ZWOELF_MONATE",
                     "kuendigungsfristMonate": 3, "gekuendigtZum": tage(165), "notiz": "Alle optionalen Felder gesetzt."}),
        Fall("Gekündigt – keine Abbuchung mehr", "GEKUENDIGT", erwartet_naechste=None,
             felder={"gekuendigtZum": tage(5)}),
        # Kündigungsart BEENDET
        Fall("Beendet – Kündigungsdatum vorbei", "BEENDET", erwartet_naechste=None, felder={"gekuendigtZum": tage(-1)}),
        Fall("Beendet – Laufzeitende vorbei, Verlängerung KEINE", "BEENDET", erwartet_naechste=None,
             felder={"laufzeitende": tage(-1), "verlaengerung": "KEINE"}),
        # Felder: Höchstlängen wie in VertragAnfrage.
        Fall("Sehr lange Namen – " + "Langer Anbietername " * 9, "KEINE_LAUFZEIT",
             felder={"bezeichnung": "Lange Bezeichnung " * 11, "vertragsnummer": "1234567890" * 10,
                     "notiz": "Lange Notiz. " * 76}),
    ]


def faelle_ohne_fristen(heute):
    """Nichts, das in der Übersicht oder der Liste eine Frist zeigt, also auch kein MONATLICH_KUENDBAR."""
    return [fall for fall in faelle_alles(heute) if fall.art not in ("KUENDIGEN_BIS", "MONATLICH_KUENDBAR")]


def konten(heute):
    return [
        Konto("alles", ALLE_KOMPONENTEN, faelle_alles(heute), 5),
        Konto("leer", {"VERTRAGSUEBERSICHT"}, [], 0),
        Konto("ohne-fristen", {"VERTRAGSUEBERSICHT"}, faelle_ohne_fristen(heute), 0),
        Konto("ohne-vertragsbereich", {"PORTFOLIO"}, [], None),
    ]


def pruefe_abdeckung(faelle):
    """Hält fest, dass das Konto „alles“ jede geforderte Feldvariante enthält."""
    frist = {fall.felder.get("kuendigungsfristMonate") for fall in faelle}
    fehlend = {
        "Zahlweisen": ALLE_ZAHLWEISEN - {fall.zahlweise for fall in faelle},
        "Kategorien": ALLE_KATEGORIEN - {fall.kategorie for fall in faelle},
        "Zahlungswege": ALLE_ZAHLUNGSWEGE - {fall.felder.get("zahlungsweg") for fall in faelle},
        "Fristen": {None, 0, 1, 3} - frist,
    }
    for was, werte in fehlend.items():
        if werte:
            raise Abbruch(f"Im Konto „alles“ fehlen {was}: {sorted(map(str, werte))}")
    if sum(fall.art == "KUENDIGEN_BIS" for fall in faelle) <= 5:
        raise Abbruch("Im Konto „alles“ müssen mehr als fünf Fristen anstehen.")


def ausgeben(konto, zustand, zeilen):
    print(f"\n{konto.email} ({zustand}; {', '.join(sorted(konto.komponenten))})")
    for anbieter, art, dringend, naechste in zeilen:
        print(f"  {anbieter:<50}  {art:<18}  {dringend:<2}  {naechste}")


def main():
    parser = argparse.ArgumentParser(description="Legt lokal Testkonten mit Testdaten an.")
    parser.add_argument("--url", default=STANDARD_URL, help=f"Adresse des lokalen Backends (Standard: {STANDARD_URL})")
    url = parser.parse_args().url.rstrip("/")
    # Wie die Clock-Bean des Backends, sonst kippen Fälle rund um Mitternacht.
    heute = datetime.now(ZoneInfo("Europe/Berlin")).date()
    try:
        pruefe_lokale_umgebung(url)
        alle = konten(heute)
        pruefe_abdeckung(alle[0].faelle)
        for konto in alle:
            ausgeben(konto, *einrichten(url, konto, heute))
    except Abbruch as abbruch:
        print(f"Abbruch: {abbruch}", file=sys.stderr)
        return 1
    print(f"\nFertig. Passwort aller Konten: {PASSWORT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
