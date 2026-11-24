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
from decimal import ROUND_HALF_UP, Decimal
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

ALLE_RHYTHMEN = {"VIERTELJAEHRLICH", "HALBJAEHRLICH", "JAEHRLICH", "ZWEIJAEHRLICH", "DREIJAEHRLICH"}

class Abbruch(Exception):
    """Etwas stimmt nicht; das Skript endet mit dieser Meldung."""


@dataclass
class Fall:
    """Ein Vertrag und was das Backend dafür berechnen muss."""

    anbieter: str
    art: str
    kategorie: str = "SONSTIGES"
    zahlweise: str = "MONATLICH"
    kuendbar: bool = True
    felder: dict = field(default_factory=dict)
    dringend: bool = False
    # Ohne Angabe AKTIV bzw. bei GEKUENDIGT, BEENDET und STORNIERT wie art.
    status: str | None = None
    # Erwartetes berechnet.kostenImJahr; None prüft es nicht.
    kosten_im_jahr: Decimal | None = None
    # Erwartetes berechnet.kuendigung.zum; None prüft es nicht.
    zum: date | None = None
    # Erwartetes berechnet.kuendigung.kuendigenBis; None prüft es nicht.
    kuendigen_bis: date | None = None
    # Erwartete Art von berechnet.naechstePreisaenderung bei einer Preissteigerung; None: keine Änderung.
    naechste_art: str | None = "PROGNOSE"
    # Erwartete Termine der Prognose (sonst drei beliebige) bzw. ihre Beträge; None prüft sie nicht.
    prognose_ab: list | None = None
    prognose_betraege: list | None = None
    # Erwartete art der Einträge in preise; ohne Angabe genau ein AKTUELL (nur bei Fällen ohne eigene Preisliste).
    preis_arten: list | None = None
    # Erwarteter betrag (aktueller Preis); None prüft ihn nicht.
    aktueller_preis: Decimal | None = None
    # So viele Einträge in preise hat das Backend beim Speichern aus der Preissteigerung angelegt (automatisch).
    automatische: int = 0
    # Nach dem Anlegen wieder aufnehmen (#166): Felder von VertragWiederaufnahme, mindestens datum; die übrigen wie
    # beim Anlegen. None: nicht wieder aufnehmen.
    wiederaufnahme: dict | None = None
    # So viele Unterbrechungen hat der Vertrag danach.
    unterbrechungen: int = 0

    @property
    def erwarteter_status(self):
        if self.status:
            return self.status
        if self.art == "WIEDER_AB":
            return "UNTERBROCHEN"
        return self.art if self.art in ("GEKUENDIGT", "BEENDET", "STORNIERT") else "AKTIV"


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


def komponenten_zuruecksetzen(sitzung, konto):
    """Setzt die Komponenten auf den Sollstand, falls sie jemand geändert hat (z. B. in den Einstellungen).

    Gibt zurück, ob sie abwichen.
    """
    status, antwort = sitzung.anfrage("GET", "/api/nutzer/ich/komponenten")
    if status == 404:
        raise Abbruch(f"{konto.email}: Das Konto gibt es in Keycloak, aber nicht im Backend. Meist wurde die lokale "
                      "Datenbank zurückgesetzt, Keycloak aber nicht. Konto in Keycloak löschen und das Skript neu "
                      "starten.")
    erwarte(status, 200, f"{konto.email}: Komponenten", antwort)
    if set(antwort["komponenten"]) == konto.komponenten:
        return False
    status, antwort = sitzung.anfrage("PUT", "/api/nutzer/ich/komponenten", {
        "komponenten": sorted(konto.komponenten),
        "version": antwort["version"],
    })
    erwarte(status, 200, f"{konto.email}: Komponenten zurücksetzen", antwort)
    return True


def vertraege_loeschen(sitzung, konto):
    status, vertraege = sitzung.anfrage("GET", "/api/vertraege")
    erwarte(status, 200, f"{konto.email}: Verträge auflisten", vertraege)
    for vertrag in vertraege:
        pfad = f"/api/vertraege/{vertrag['id']}?version={vertrag['version']}"
        status, antwort = sitzung.anfrage("DELETE", pfad)
        erwarte(status, 204, f"{konto.email}: Vertrag löschen", antwort)


def vor_einem_jahr(tag):
    """Derselbe Tag im Vorjahr; der 29.02. wird zum 28.02."""
    return tag.replace(year=tag.year - 1, day=min(tag.day, calendar.monthrange(tag.year - 1, tag.month)[1]))


def standardbeginn(heute):
    """Ohne Angabe seit einem Jahr im Raster von heute + 9 Tagen: Abbuchung dann, Ende einen Tag vorher."""
    return vor_einem_jahr(heute + timedelta(days=9))


def anfrage_zu(fall, kategorie_ids, heute):
    daten = {
        "anbieter": fall.anbieter,
        "bezeichnung": None,
        "kategorieId": kategorie_ids[fall.kategorie],
        "vertragsnummer": None,
        "zahlweise": fall.zahlweise,
        "zahlungsweg": None,
        "anteilProzent": None,
        "einmalkosten": [],
        "preissteigerung": None,
        "preisgarantieBis": None,
        "erinnerungSonderkuendigung": True,
        "kuendbar": fall.kuendbar,
        "beginn": standardbeginn(heute),
        "laufzeitende": None,
        "verlaengerung": None,
        "kuendigungsfristMonate": None,
        "gekuendigtZum": None,
        "notiz": None,
    } | fall.felder
    # Ohne eigene Preisliste ein Preis ab Vertragsbeginn.
    daten.setdefault("preise", [{"gueltigAb": daten["beginn"].isoformat(),
                                 "betrag": {"wert": 9.99, "waehrung": "EUR"}}])
    return {schluessel: wert.isoformat() if isinstance(wert, date) else wert for schluessel, wert in daten.items()}


def vertragsende(daten):
    """Wie das Backend: gekuendigtZum bzw. Laufzeitende eines kündbaren Vertrags ohne Verlängerung, das frühere."""
    enden = [daten["gekuendigtZum"]]
    if daten["kuendbar"] and daten["verlaengerung"] == "KEINE":
        enden.append(daten["laufzeitende"])
    enden = [ende for ende in enden if ende is not None]
    return min(enden) if enden else None


def pruefe_berechnung(fall, anfrage, antwort):
    """Vergleicht mit der Erwartung des Falls; gibt zurück, was die Tabelle am Ende zeigt."""
    berechnet = antwort["berechnet"]
    art = berechnet["kuendigung"]["art"]
    status = berechnet["status"]
    fehler = []
    if art != fall.art:
        fehler.append(f"art {art} statt {fall.art}")
    if status != fall.erwarteter_status:
        fehler.append(f"status {status} statt {fall.erwarteter_status}")
    if berechnet["kuendigung"]["dringend"] != fall.dringend:
        fehler.append(f"dringend {berechnet['kuendigung']['dringend']} statt {fall.dringend}")
    if fall.zum is not None and berechnet["kuendigung"]["zum"] != fall.zum.isoformat():
        fehler.append(f"zum {berechnet['kuendigung']['zum']} statt {fall.zum}")
    if fall.kuendigen_bis is not None and berechnet["kuendigung"]["kuendigenBis"] != fall.kuendigen_bis.isoformat():
        fehler.append(f"kuendigenBis {berechnet['kuendigung']['kuendigenBis']} statt {fall.kuendigen_bis}")
    preis_arten = [preis["art"] for preis in antwort["preise"]]
    if preis_arten != (fall.preis_arten or ["AKTUELL"]):
        fehler.append(f"Preiszeilen {preis_arten} statt {fall.preis_arten or ['AKTUELL']}")
    automatische = sum(1 for preis in antwort["preise"] if preis["automatisch"])
    if automatische != fall.automatische:
        fehler.append(f"{automatische} automatische Preise statt {fall.automatische}")
    if fall.aktueller_preis is not None and Decimal(str(antwort["betrag"]["wert"])) != fall.aktueller_preis:
        fehler.append(f"betrag {antwort['betrag']['wert']} statt {fall.aktueller_preis}")
    if berechnet["vertragsende"] != vertragsende(anfrage):
        fehler.append(f"vertragsende {berechnet['vertragsende']} statt {vertragsende(anfrage)}")
    wieder_aufnehmbar = fall.erwarteter_status in ("GEKUENDIGT", "BEENDET") and vertragsende(anfrage) is not None
    if (berechnet["wiederaufnahme"] is not None) != wieder_aufnehmbar:
        fehler.append(f"wiederaufnahme {berechnet['wiederaufnahme']} bei Status {status}")
    if len(antwort["unterbrechungen"]) != fall.unterbrechungen:
        fehler.append(f"{len(antwort['unterbrechungen'])} Unterbrechungen statt {fall.unterbrechungen}")
    if fall.kosten_im_jahr is not None and Decimal(str(berechnet["kostenImJahr"]["wert"])) != fall.kosten_im_jahr:
        fehler.append(f"kostenImJahr {berechnet['kostenImJahr']['wert']} statt {fall.kosten_im_jahr}")
    einmalkosten = sum((Decimal(str(eintrag["betrag"]["wert"])) for eintrag in fall.felder.get("einmalkosten", [])),
                       Decimal(0))
    if Decimal(str(berechnet["einmalkosten"]["wert"])) != einmalkosten:
        fehler.append(f"einmalkosten {berechnet['einmalkosten']['wert']} statt {einmalkosten}")
    prognose = berechnet["prognose"]
    if fall.felder.get("preissteigerung"):
        # Am Vertragsende hört die Prognose auf, dann auch weniger als drei.
        anzahl = len(fall.prognose_ab) if fall.prognose_ab is not None else 3
        naechste = berechnet["naechstePreisaenderung"]
        if len(prognose) != anzahl or (naechste["art"] if naechste else None) != fall.naechste_art:
            fehler.append(f"{len(prognose)} Prognosepreise statt {anzahl}, nächste Preisänderung {naechste} "
                          f"statt {fall.naechste_art}")
        ab = [date.fromisoformat(preis["ab"]) for preis in prognose]
        if fall.prognose_ab is not None and ab != fall.prognose_ab:
            fehler.append(f"Prognose ab {[str(tag) for tag in ab]} statt {[str(tag) for tag in fall.prognose_ab]}")
        betraege = [Decimal(str(preis["betrag"]["wert"])) for preis in prognose]
        if fall.prognose_betraege is not None and betraege != fall.prognose_betraege:
            fehler.append(f"Prognosebeträge {betraege} statt {fall.prognose_betraege}")
    elif prognose:
        fehler.append(f"{len(prognose)} Prognosepreise ohne Preissteigerung")
    if (berechnet["eigenerAnteil"] is not None) != geteilt(fall):
        fehler.append(f"eigenerAnteil {berechnet['eigenerAnteil']} bei anteilProzent {fall.felder.get('anteilProzent')}")
    if fehler:
        raise Abbruch(f"Fall „{fall.anbieter[:60]}“: {', '.join(fehler)}")
    return art, status


def vertraege_anlegen(sitzung, konto, heute):
    status, kategorien = sitzung.anfrage("GET", "/api/vertraege/kategorien")
    erwarte(status, 200, f"{konto.email}: Kategorien", kategorien)
    kategorie_ids = {kategorie["schluessel"]: kategorie["id"] for kategorie in kategorien if kategorie["schluessel"]}
    zeilen = []
    for fall in konto.faelle:
        anfrage = anfrage_zu(fall, kategorie_ids, heute)
        status, antwort = sitzung.anfrage("POST", "/api/vertraege", anfrage)
        erwarte(status, 201, f"Fall „{fall.anbieter[:60]}“: Anlegen", antwort)
        if fall.wiederaufnahme:
            anfrage, antwort = wieder_aufnehmen(sitzung, fall, anfrage, antwort)
        art, vertragsstatus = pruefe_berechnung(fall, anfrage, antwort)
        zeilen.append((fall.anbieter[:50], art, "ja" if fall.dringend else "", vertragsstatus))
    return zeilen


def wieder_aufnehmen(sitzung, fall, anfrage, antwort):
    """Nimmt den angelegten Vertrag wieder auf; gibt die Angaben danach (wie eine Anfrage) und die Antwort zurück."""
    daten = {"version": antwort["version"], "laufzeitende": None, "verlaengerung": anfrage["verlaengerung"],
             "kuendigungsfristMonate": anfrage["kuendigungsfristMonate"], "betrag": None} | fall.wiederaufnahme
    daten = {schluessel: wert.isoformat() if isinstance(wert, date) else wert for schluessel, wert in daten.items()}
    status, antwort = sitzung.anfrage("POST", f"/api/vertraege/{antwort['id']}/wiederaufnahme", daten)
    erwarte(status, 200, f"Fall „{fall.anbieter[:60]}“: Wieder aufnehmen", antwort)
    nachher = anfrage | {"gekuendigtZum": None, "laufzeitende": daten["laufzeitende"]}
    if anfrage["kuendbar"]:
        nachher |= {"verlaengerung": daten["verlaengerung"], "kuendigungsfristMonate": daten["kuendigungsfristMonate"]}
    return nachher, antwort


def geteilt(fall):
    """Mit Anteil unter 100 %: Dann gibt es berechnet.eigenerAnteil."""
    anteil = fall.felder.get("anteilProzent")
    return anteil is not None and anteil < 100


def einmalkosten_im_jahr(faelle, jahr):
    """Wie das Backend: je Vertrag der Anteil ungerundet, erst die Summe gerundet; None ohne Einträge im Jahr."""
    summe, vorhanden = Decimal(0), False
    for fall in faelle:
        anteil = Decimal(str(fall.felder.get("anteilProzent") or 100)) / 100
        for eintrag in fall.felder.get("einmalkosten", []):
            if date.fromisoformat(eintrag["faelligAm"]).year == jahr:
                summe += Decimal(str(eintrag["betrag"]["wert"])) * anteil
                vorhanden = True
    return summe.quantize(Decimal("0.01"), ROUND_HALF_UP) if vorhanden else None


def pruefe_uebersicht(sitzung, konto, heute):
    status, antwort = sitzung.anfrage("GET", "/api/vertraege/uebersicht")
    erwarte(status, 200, f"{konto.email}: Übersicht", antwort)
    erwartet = einmalkosten_im_jahr(konto.faelle, heute.year)
    einmal = [Decimal(str(betrag["wert"])) for betrag in antwort["einmalkostenImJahr"]]
    if einmal != ([erwartet] if erwartet is not None else []):
        raise Abbruch(f"{konto.email}: einmalkostenImJahr {einmal} statt {erwartet}")
    # Die Testfälle mit Anteil laufen alle noch.
    hat_anteile = any(geteilt(fall) for fall in konto.faelle)
    if antwort["hatAnteile"] != hat_anteile:
        raise Abbruch(f"{konto.email}: hatAnteile {antwort['hatAnteile']} statt {hat_anteile}")
    hat_nicht_begonnene = any(fall.erwarteter_status == "NICHT_BEGONNEN" for fall in konto.faelle)
    if antwort["hatNichtBegonnene"] != hat_nicht_begonnene:
        raise Abbruch(f"{konto.email}: hatNichtBegonnene {antwort['hatNichtBegonnene']} statt {hat_nicht_begonnene}")
    fristen = len(antwort["naechsteKuendigungsfristen"])
    if fristen != konto.fristen_in_uebersicht:
        raise Abbruch(f"{konto.email}: {fristen} Fristen in der Übersicht statt {konto.fristen_in_uebersicht}")


def einrichten(url, konto, heute):
    sitzung = Sitzung(url)
    zustand = anmelden(sitzung, konto)
    # Vor den Verträgen: Ohne VERTRAGSUEBERSICHT ließen sie sich nicht ersetzen.
    if komponenten_zuruecksetzen(sitzung, konto):
        zustand += ", Komponenten zurückgesetzt"
    zeilen = []
    if konto.fristen_in_uebersicht is None:
        status, antwort = sitzung.anfrage("GET", "/api/vertraege")
        erwarte(status, 403, f"{konto.email}: Vertragsbereich ohne Komponente", antwort)
    else:
        vertraege_loeschen(sitzung, konto)
        zeilen = vertraege_anlegen(sitzung, konto, heute)
        pruefe_uebersicht(sitzung, konto, heute)
    status, antwort = sitzung.anfrage("POST", "/api/abmeldung")
    erwarte(status, 204, f"{konto.email}: Abmeldung", antwort)
    return zustand, zeilen


def plus_monate(tag, monate):
    """Wie LocalDate.plusMonths: Gibt es den Tag im Zielmonat nicht, der Monatsletzte."""
    jahr, monat = divmod(tag.month - 1 + monate, 12)
    jahr += tag.year
    return tag.replace(year=jahr, month=monat + 1, day=min(tag.day, calendar.monthrange(jahr, monat + 1)[1]))


def termine(anker, monate, nach):
    """Die drei Termine der Prognose: vom Anker aus im Rhythmus, nur die nach dem Tag „nach“ (Garantie, letzter Preis
    oder heute; ein Termin bis heute ist fällig und wird beim Speichern als Preis angelegt)."""
    k = 0
    while plus_monate(anker, k * monate) <= nach:
        k += 1
    return [plus_monate(anker, (k + i) * monate) for i in range(3)]


def monatsletzter(tag):
    return tag.replace(day=calendar.monthrange(tag.year, tag.month)[1])


def ende_nach(beginn, monate):
    """Wie das Backend: letzter Tag einer Laufzeit ab Beginn, der Tag vor dem Beginn-Tag, sonst der Monatsletzte."""
    tag = plus_monate(beginn, monate)
    return tag if tag.day < beginn.day else tag - timedelta(days=1)


def kuendigen_bis(ende, frist_monate):
    """Wie das Backend: Ende minus Frist; liegt das Ende auf einem Monatsende, auch „kündigen bis“."""
    tag = plus_monate(ende, -frist_monate)
    return monatsletzter(tag) if ende == monatsletzter(ende) else tag


def monatsende_am_30(ab):
    """Der erste 30. ab „ab“, der ein Monatsende ist und auf einen Monat mit 31 Tagen folgt (30.04., 30.06., 30.09.,
    30.11.); „kündigen bis“ muss dann auf den 31. fallen, nicht auf den 30."""
    tag = monatsletzter(ab)
    while not (tag.day == 30 and tag.month in (4, 6, 9, 11)):
        tag = monatsletzter(tag + timedelta(days=1))
    return tag


def ende_bei_beginn_am_31(heute):
    """Das nächste Vertragsende ab heute bei Beginn am 31. und monatlicher Verlängerung: der Tag vor dem 31., also der
    30., im Februar der Monatsletzte (§ 188 Abs. 3 BGB)."""
    jahr, monat = heute.year, heute.month
    while True:
        termin = date(jahr, monat, min(30, calendar.monthrange(jahr, monat)[1]))
        if termin >= heute:
            return termin
        jahr, monat = (jahr + 1, 1) if monat == 12 else (jahr, monat + 1)


def faelle_alles(heute):
    """Jeder Fall aus #77. Abstände mit Reserve zu den 45 Tagen, damit kurze Monate nichts kippen."""

    def tage(anzahl):
        return heute + timedelta(days=anzahl)

    # Fünf Jahre und zehn Tage zurück: fünf vergangene jährliche Erhöhungen, die letzte vor zehn Tagen.
    seit_fuenf_jahren = plus_monate(heute, -60) - timedelta(days=10)

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
        # Endet der Vertrag am Monatsende, ist auch „kündigen bis“ eins: 30.11. mit einem Monat → 31.10.
        Fall("Kündigen bis – Laufzeitende am Monatsende", "KUENDIGEN_BIS", zum=monatsende_am_30(tage(80)),
             kuendigen_bis=kuendigen_bis(monatsende_am_30(tage(80)), 1),
             felder={"laufzeitende": monatsende_am_30(tage(80)), "verlaengerung": "ZWOELF_MONATE",
                     "kuendigungsfristMonate": 1}),
        # Ohne Laufzeitende zählen die Verlängerungen vom Beginn aus; die erste Laufzeit ist schon vorbei.
        Fall("Kündigen bis – ohne Laufzeitende, 12 Monate vom Beginn", "KUENDIGEN_BIS",
             zum=ende_nach(tage(-500), 24), kuendigen_bis=kuendigen_bis(ende_nach(tage(-500), 24), 1),
             felder={"beginn": tage(-500), "verlaengerung": "ZWOELF_MONATE", "kuendigungsfristMonate": 1}),
        # Monatliche Verlängerung erst nach dem Laufzeitende: bis dahin KUENDIGEN_BIS.
        Fall("Kündigen bis – monatlich, Laufzeitende kommt noch", "KUENDIGEN_BIS", zum=tage(120),
             felder={"laufzeitende": tage(120), "verlaengerung": "MONATLICH", "kuendigungsfristMonate": 1}),
        Fall("Kündigen bis – halbjährlich", "KUENDIGEN_BIS", zahlweise="HALBJAEHRLICH",
             felder={"laufzeitende": tage(180), "verlaengerung": "ZAHLUNGSINTERVALL"}),
        Fall("Kündigen bis – jährlich", "KUENDIGEN_BIS", zahlweise="JAEHRLICH",
             felder={"laufzeitende": tage(300), "verlaengerung": "ZAHLUNGSINTERVALL"}),
        # Kündigungsart MONATLICH_KUENDBAR
        Fall("Monatlich kündbar – ohne Laufzeitende", "MONATLICH_KUENDBAR",
             felder={"verlaengerung": "MONATLICH", "kuendigungsfristMonate": 1, "beginn": tage(-400)}),
        Fall("Monatlich kündbar – Laufzeitende vorbei", "MONATLICH_KUENDBAR",
             felder={"laufzeitende": tage(-30), "verlaengerung": "ZAHLUNGSINTERVALL"}),
        # Vertragsenden vom Beginn aus: 30., im Februar der Monatsletzte, nicht abgerutscht.
        Fall("Monatlich kündbar – Beginn am 31.", "MONATLICH_KUENDBAR", zum=ende_bei_beginn_am_31(heute),
             felder={"beginn": date(heute.year - 1, 1, 31), "verlaengerung": "MONATLICH", "kuendigungsfristMonate": 0}),
        # Kündigungsart ENDET
        Fall("Endet – in 100 Tagen", "ENDET", felder={"laufzeitende": tage(100), "verlaengerung": "KEINE"}),
        Fall("Endet – in 5 Tagen", "ENDET", felder={"laufzeitende": tage(5), "verlaengerung": "KEINE"}),
        # Kündigungsart KEINE_LAUFZEIT
        Fall("Keine Laufzeit – optionale Felder alle leer", "KEINE_LAUFZEIT"),
        Fall("Keine Laufzeit – Laufzeitende ohne Verlängerung, Frist abgelaufen", "KEINE_LAUFZEIT",
             felder={"laufzeitende": tage(10), "kuendigungsfristMonate": 3}),
        Fall("Keine Laufzeit – Beginn am 31. (Abbuchung am 31. bzw. Monatsletzten)", "KEINE_LAUFZEIT",
             felder={"beginn": date(heute.year - 1, 1, 31)}),
        # Beginn im laufenden Jahr: Abbuchungen erst ab heute, je am heutigen Tag des Monats.
        Fall("Kosten im Jahr – Beginn heute", "KEINE_LAUFZEIT", felder={"beginn": heute},
             kosten_im_jahr=Decimal("9.99") * (13 - heute.month)),
        # Kündigungsart NICHT_KUENDBAR
        Fall("Nicht kündbar", "NICHT_KUENDBAR", kuendbar=False, felder={"beginn": tage(-1000)}),
        # Kündigungsart GEKUENDIGT
        Fall("Gekündigt – optionale Felder alle gefüllt", "GEKUENDIGT", kategorie="VERSICHERUNG",
             felder={"bezeichnung": "Alle Felder", "vertragsnummer": "TEST-4711", "zahlungsweg": "LASTSCHRIFT",
                     "beginn": tage(-200), "laufzeitende": tage(165), "verlaengerung": "ZWOELF_MONATE",
                     "kuendigungsfristMonate": 3, "gekuendigtZum": tage(165), "notiz": "Alle optionalen Felder gesetzt."}),
        Fall("Gekündigt – in 5 Tagen", "GEKUENDIGT", felder={"gekuendigtZum": tage(5)}),
        # Kündigungsart BEENDET
        Fall("Beendet – Kündigungsdatum vorbei", "BEENDET", felder={"gekuendigtZum": tage(-1)}),
        Fall("Beendet – Laufzeitende vorbei, Verlängerung KEINE", "BEENDET",
             felder={"laufzeitende": tage(-1), "verlaengerung": "KEINE"}),
        Fall("Beendet – nicht kündbar, wieder aufnehmbar", "BEENDET", kuendbar=False, kategorie="GEBUEHREN_BEITRAEGE",
             zahlweise="VIERTELJAEHRLICH", felder={"beginn": tage(-1500), "gekuendigtZum": tage(-200)}),
        # Wieder aufgenommen (#166): Die Zeit dazwischen ist eine Unterbrechung ohne Abbuchungen.
        Fall("Wiederaufnahme – über den Sommer gekündigt", "MONATLICH_KUENDBAR", unterbrechungen=1,
             felder={"beginn": tage(-400), "verlaengerung": "ZAHLUNGSINTERVALL", "kuendigungsfristMonate": 0,
                     "gekuendigtZum": tage(-120)},
             wiederaufnahme={"datum": tage(-30)}),
        Fall("Wiederaufnahme – nahtlos, ohne Unterbrechung", "MONATLICH_KUENDBAR",
             felder={"beginn": tage(-400), "verlaengerung": "ZAHLUNGSINTERVALL", "kuendigungsfristMonate": 0,
                     "gekuendigtZum": tage(-60)},
             wiederaufnahme={"datum": tage(-59)}),
        Fall("Wiederaufnahme – unterbrochen, wieder ab in 30 Tagen", "WIEDER_AB", zum=tage(30), unterbrechungen=1,
             preis_arten=["AKTUELL", "ANGEKUENDIGT"],
             felder={"beginn": tage(-400), "verlaengerung": "ZAHLUNGSINTERVALL", "kuendigungsfristMonate": 0,
                     "gekuendigtZum": tage(-30)},
             wiederaufnahme={"datum": tage(30), "betrag": {"wert": 11.99, "waehrung": "EUR"}}),
        Fall("Wiederaufnahme – nicht kündbar, nach einer Trennung", "NICHT_KUENDBAR", kuendbar=False,
             kategorie="GEBUEHREN_BEITRAEGE", zahlweise="VIERTELJAEHRLICH", unterbrechungen=1,
             felder={"beginn": tage(-2000), "gekuendigtZum": tage(-600)},
             wiederaufnahme={"datum": tage(-60)}),
        # Noch nicht begonnen: zählt nicht pro Monat, lässt sich aber schon kündigen.
        Fall("Noch nicht begonnen – Beginn in 20 Tagen", "KUENDIGEN_BIS", status="NICHT_BEGONNEN",
             felder={"beginn": tage(20), "verlaengerung": "ZWOELF_MONATE", "kuendigungsfristMonate": 1}),
        Fall("Noch nicht begonnen – schon gekündigt", "GEKUENDIGT", status="NICHT_BEGONNEN",
             felder={"beginn": tage(20), "gekuendigtZum": tage(120)}),
        # Storniert: vor dem Beginn gekündigt, läuft nie; die Gebühr fällt trotzdem an.
        # Gekündigt genau zum Beginn: läuft einen Tag, also nicht storniert.
        Fall("Gekündigt zum Beginn", "GEKUENDIGT", status="NICHT_BEGONNEN",
             felder={"beginn": tage(25), "gekuendigtZum": tage(25)}),
        Fall("Storniert – vor dem Beginn gekündigt", "STORNIERT",
             felder={"beginn": tage(30), "gekuendigtZum": tage(29), "einmalkosten": [
                 {"bezeichnung": "Stornogebühr", "betrag": {"wert": 15.00, "waehrung": "EUR"},
                  "faelligAm": heute.isoformat()}]}),
        # Preisliste: früherer Preis, aktueller und angekündigte Erhöhung.
        Fall("Preise – Erhöhung angekündigt", "KEINE_LAUFZEIT", kategorie="STREAMING_ABOS",
             preis_arten=["FRUEHER", "AKTUELL", "ANGEKUENDIGT"], aktueller_preis=Decimal("9.99"),
             felder={"beginn": tage(-400), "preise": [
                 {"gueltigAb": tage(-400).isoformat(), "betrag": {"wert": 7.99, "waehrung": "EUR"}},
                 {"gueltigAb": tage(-100).isoformat(), "betrag": {"wert": 9.99, "waehrung": "EUR"}},
                 {"gueltigAb": tage(30).isoformat(), "betrag": {"wert": 11.99, "waehrung": "EUR"}}]}),
        # Eine Erhöhung nach dem Vertragsende gilt nie: NACH_ENDE, keine nächste Preisänderung.
        Fall("Preise – Erhöhung nach Vertragsende", "GEKUENDIGT", preis_arten=["AKTUELL", "NACH_ENDE"],
             felder={"gekuendigtZum": tage(60), "preise": [
                 {"gueltigAb": standardbeginn(heute).isoformat(), "betrag": {"wert": 9.99, "waehrung": "EUR"}},
                 {"gueltigAb": tage(90).isoformat(), "betrag": {"wert": 11.99, "waehrung": "EUR"}}]}),
        # Beendet: aktuell ist der Preis am letzten Tag, die Erhöhung danach wurde nie gezahlt.
        Fall("Preise – beendet nach Preiserhöhung", "BEENDET", preis_arten=["FRUEHER", "AKTUELL", "NACH_ENDE"],
             aktueller_preis=Decimal("9.99"),
             felder={"beginn": tage(-400), "gekuendigtZum": tage(-30), "preise": [
                 {"gueltigAb": tage(-400).isoformat(), "betrag": {"wert": 7.99, "waehrung": "EUR"}},
                 {"gueltigAb": tage(-100).isoformat(), "betrag": {"wert": 9.99, "waehrung": "EUR"}},
                 {"gueltigAb": tage(-10).isoformat(), "betrag": {"wert": 11.99, "waehrung": "EUR"}}]}),
        # Geteilte Verträge: Übersichten zählen nur den Anteil; 100 % zählt wie ohne Anteil.
        Fall("Geteilt – 50 %", "KEINE_LAUFZEIT", kategorie="STREAMING_ABOS", felder={"anteilProzent": 50}),
        Fall("Geteilt – 33,3 %", "KEINE_LAUFZEIT", kategorie="MOBILFUNK_INTERNET", zahlweise="VIERTELJAEHRLICH",
             felder={"anteilProzent": 33.3}),
        Fall("Geteilt – 100 %", "KEINE_LAUFZEIT", felder={"anteilProzent": 100}),
        # Preissteigerung: Prognose der nächsten drei Preise, auch mit Garantie und vergangenem Anker.
        Fall("Preissteigerung – 2,5 % jährlich", "KEINE_LAUFZEIT", kategorie="VERSICHERUNG", zahlweise="JAEHRLICH",
             felder={"preissteigerung": {"art": "PROZENT", "prozent": 2.5, "betrag": None, "rhythmus": "JAEHRLICH",
                                         "naechsteAm": tage(60).isoformat()}}),
        Fall("Preissteigerung – 1 € vierteljährlich mit Preisgarantie", "KEINE_LAUFZEIT", kategorie="STREAMING_ABOS",
             prognose_ab=termine(tage(20), 3, nach=tage(100)),
             felder={"preisgarantieBis": tage(100),
                     "preissteigerung": {"art": "BETRAG", "prozent": None, "betrag": {"wert": 1.00, "waehrung": "EUR"},
                                         "rhythmus": "VIERTELJAEHRLICH", "naechsteAm": tage(20).isoformat()}}),
        Fall("Preissteigerung – nächste Erhöhung vergangen, ohne Erinnerung", "KEINE_LAUFZEIT",
             felder={"erinnerungSonderkuendigung": False,
                     "preissteigerung": {"art": "PROZENT", "prozent": 3, "betrag": None, "rhythmus": "ZWEIJAEHRLICH",
                                         "naechsteAm": tage(-400).isoformat()}}),
        # Alle Rhythmen; die Termine zählen vom Anker aus.
        Fall("Preissteigerung – 2 % halbjährlich", "KEINE_LAUFZEIT", prognose_ab=termine(tage(50), 6, nach=tage(0)),
             felder={"preissteigerung": {"art": "PROZENT", "prozent": 2, "betrag": None, "rhythmus": "HALBJAEHRLICH",
                                         "naechsteAm": tage(50).isoformat()}}),
        Fall("Preissteigerung – 5 € dreijährlich", "KEINE_LAUFZEIT", zahlweise="JAEHRLICH",
             prognose_ab=termine(tage(200), 36, nach=tage(0)),
             felder={"preissteigerung": {"art": "BETRAG", "prozent": None, "betrag": {"wert": 5.00, "waehrung": "EUR"},
                                         "rhythmus": "DREIJAEHRLICH", "naechsteAm": tage(200).isoformat()}}),
        # Vergangene Erhöhungen (#159): Beim Speichern legt das Backend die fälligen Stufen als automatische Preise an
        # (je Schritt gerundet), die Prognose rechnet auf der letzten weiter.
        Fall("Preissteigerung – seit fünf Jahren, vergangene Erhöhungen", "KEINE_LAUFZEIT", kategorie="VERSICHERUNG",
             preis_arten=["FRUEHER"] * 5 + ["AKTUELL"], automatische=5, aktueller_preis=Decimal("110.40"),
             prognose_ab=termine(plus_monate(seit_fuenf_jahren, 12), 12, nach=heute),
             prognose_betraege=[Decimal("112.61"), Decimal("114.86"), Decimal("117.16")],
             felder={"beginn": seit_fuenf_jahren, "preise": [
                 {"gueltigAb": seit_fuenf_jahren.isoformat(), "betrag": {"wert": 100.00, "waehrung": "EUR"}}],
                     "preissteigerung": {"art": "PROZENT", "prozent": 2, "betrag": None, "rhythmus": "JAEHRLICH",
                                         "naechsteAm": plus_monate(seit_fuenf_jahren, 12).isoformat()}}),
        # Angekündigter Preis und Prognose: Die Erhöhung kommt zuerst, der Termin der Regel davor wird übersprungen,
        # die Prognose rechnet vom angekündigten Preis aus weiter (je Schritt auf Cent gerundet).
        Fall("Preissteigerung – mit angekündigter Erhöhung", "KEINE_LAUFZEIT", kategorie="STREAMING_ABOS",
             naechste_art="ANGEKUENDIGT", prognose_ab=termine(tage(10), 12, nach=tage(30)),
             preis_arten=["AKTUELL", "ANGEKUENDIGT"],
             prognose_betraege=[Decimal("13.19"), Decimal("14.51"), Decimal("15.96")],
             felder={"beginn": tage(-400), "preise": [
                 {"gueltigAb": tage(-400).isoformat(), "betrag": {"wert": 9.99, "waehrung": "EUR"}},
                 {"gueltigAb": tage(30).isoformat(), "betrag": {"wert": 11.99, "waehrung": "EUR"}}],
                     "preissteigerung": {"art": "PROZENT", "prozent": 10, "betrag": None, "rhythmus": "JAEHRLICH",
                                         "naechsteAm": tage(10).isoformat()}}),
        # Preisgarantie: abgelaufen ändert nichts; laufend verschiebt sie die Prognose, auch bei Prozent.
        Fall("Preisgarantie – abgelaufen", "KEINE_LAUFZEIT", prognose_ab=termine(tage(40), 12, nach=tage(0)),
             felder={"preisgarantieBis": tage(-30),
                     "preissteigerung": {"art": "PROZENT", "prozent": 2, "betrag": None, "rhythmus": "JAEHRLICH",
                                         "naechsteAm": tage(40).isoformat()}}),
        Fall("Preisgarantie – Steigerung in Prozent", "KEINE_LAUFZEIT", kategorie="VERSICHERUNG",
             prognose_ab=termine(tage(20), 3, nach=tage(200)),
             felder={"preisgarantieBis": tage(200),
                     "preissteigerung": {"art": "PROZENT", "prozent": 3, "betrag": None, "rhythmus": "VIERTELJAEHRLICH",
                                         "naechsteAm": tage(20).isoformat()}}),
        # Mit Vertragsende: Die Prognose hört am letzten Tag auf; beendet oder storniert gibt es keine.
        Fall("Preissteigerung – gekündigt, Prognose bis zum Ende", "GEKUENDIGT", prognose_ab=[tage(20)],
             felder={"gekuendigtZum": tage(100),
                     "preissteigerung": {"art": "PROZENT", "prozent": 2, "betrag": None, "rhythmus": "VIERTELJAEHRLICH",
                                         "naechsteAm": tage(20).isoformat()}}),
        Fall("Preissteigerung – beendet", "BEENDET", prognose_ab=[], naechste_art=None,
             felder={"gekuendigtZum": tage(-1),
                     "preissteigerung": {"art": "PROZENT", "prozent": 2, "betrag": None, "rhythmus": "VIERTELJAEHRLICH",
                                         "naechsteAm": tage(20).isoformat()}}),
        Fall("Preissteigerung – storniert", "STORNIERT", prognose_ab=[], naechste_art=None,
             felder={"beginn": tage(30), "gekuendigtZum": tage(29),
                     "preissteigerung": {"art": "PROZENT", "prozent": 2, "betrag": None, "rhythmus": "JAEHRLICH",
                                         "naechsteAm": tage(10).isoformat()}}),
        # Geteilt: Die Prognose zeigt die vollen Beträge, nur eigenerAnteil zählt den Anteil.
        Fall("Preissteigerung – geteilt 50 %", "KEINE_LAUFZEIT", kategorie="MOBILFUNK_INTERNET",
             prognose_betraege=[Decimal("10.49"), Decimal("11.01"), Decimal("11.56")],
             felder={"anteilProzent": 50,
                     "preissteigerung": {"art": "PROZENT", "prozent": 5, "betrag": None, "rhythmus": "JAEHRLICH",
                                         "naechsteAm": tage(60).isoformat()}}),
        # Einmalkosten: im Jahr und im Vorjahr; in der Übersicht nur die im Jahr, beim geteilten nur der Anteil.
        Fall("Einmalkosten – im Jahr und im Vorjahr", "KEINE_LAUFZEIT", kategorie="MOBILFUNK_INTERNET",
             felder={"einmalkosten": [
                 {"bezeichnung": "Anschlussgebühr", "betrag": {"wert": 49.00, "waehrung": "EUR"},
                  "faelligAm": date(heute.year - 1, 6, 1).isoformat()},
                 {"bezeichnung": "Router", "betrag": {"wert": 99.99, "waehrung": "EUR"},
                  "faelligAm": date(heute.year, 6, 1).isoformat()}]}),
        Fall("Einmalkosten – im nächsten Jahr", "KEINE_LAUFZEIT",
             felder={"einmalkosten": [
                 {"bezeichnung": "Wechselprämie zurück", "betrag": {"wert": 25.00, "waehrung": "EUR"},
                  "faelligAm": date(heute.year + 1, 3, 1).isoformat()}]}),
        Fall("Einmalkosten – geteilt 50 %", "KEINE_LAUFZEIT", kategorie="STREAMING_ABOS",
             felder={"anteilProzent": 50, "einmalkosten": [
                 {"bezeichnung": "Aufnahmegebühr", "betrag": {"wert": 10.01, "waehrung": "EUR"},
                  "faelligAm": date(heute.year, 2, 1).isoformat()}]}),
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
    rhythmen = {fall.felder["preissteigerung"]["rhythmus"] for fall in faelle if fall.felder.get("preissteigerung")}
    fehlend = {
        "Rhythmen der Preissteigerung": ALLE_RHYTHMEN - rhythmen,
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
    for anbieter, art, dringend, status in zeilen:
        print(f"  {anbieter:<50}  {art:<18}  {dringend:<2}  {status}")


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
