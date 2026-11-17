# 0017 Standalone, Signals, `OnPush` und zoneless

**Status:** angenommen, 2026-10-29, nachgetragen mit [#127](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/127)

## Kontext

Angular erlaubt zwei Arten, eine Anwendung zu bauen: mit NgModules, zone.js und Change Detection über den
ganzen Baum, oder mit Standalone-Komponenten, Signals und gezielter Aktualisierung. Die SPA ist neu, es gibt
keinen alten Code, der den älteren Weg nahelegt.

## Entscheidung

Die SPA nutzt den neuen Weg durchgehend:

- Standalone-Komponenten, keine NgModules.
- Zustand in Signals (`signal`, `computed`, `input`, `output`), RxJS nur für HTTP und Ereignisströme.
- Jede Komponente mit `ChangeDetectionStrategy.OnPush`.
- Zoneless, ohne zone.js.
- `strict` und `strictTemplates` an.

## Folgen

- Kleineres Bundle, Aktualisierung nur, wo sich ein Signal ändert; Fehler im Template fallen beim Build auf.
- Code, der sich auf zone.js verlässt (z.&nbsp;B. `setTimeout` ohne Signal), aktualisiert die Ansicht
  nicht. Zustand gehört deshalb immer in ein Signal.
- Unit-Tests laufen ebenfalls zoneless und warten mit `fixture.whenStable()` statt `fakeAsync`.
