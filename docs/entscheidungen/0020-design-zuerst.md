# 0020 Design zuerst: Der Canvas ist maßgeblich

**Status:** angenommen, 2026-11-17 ([#127](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/127); umgesetzt mit [Frontend #112](https://github.com/bjoernb94/Haushaltsbuch-Frontend/issues/112))

## Kontext

Das Design der SPA entsteht im Claude-Design-Canvas „Haushaltsbuch“, die Implementierung übernimmt es von
Hand. Bisher war nicht festgelegt, was gilt, wenn beide auseinanderlaufen. So stehen Farben als feste
Hex-Werte in den Artboards, `styles.scss` hat eigene Tokens, und der Canvas ist in sich nicht einheitlich
(zwei Werte für Trenner, zwei für Hintergründe).

## Entscheidung

Der Canvas ist maßgeblich. Seiten und Verhalten entstehen zuerst im Canvas, die Implementierung folgt daraus.

- Das Design System im Canvas hält Farben und Bausteine. `styles.scss` übernimmt sie mit denselben Namen
  (`--app-*`).
- Weicht die Implementierung bewusst ab (Technik, Barrierefreiheit, Berechnungen nur im Backend nach ADR
  [0010](0010-berechnungen-nur-im-backend.md)), wird erst das Artboard angepasst oder die Abweichung am Issue
  festgehalten.

## Folgen

- Es gibt eine Stelle, an der man nachsehen kann, wie eine Seite aussehen und sich verhalten soll.
- Kein Generator vom Design zum Code: Der Abgleich ist Handarbeit und steht deshalb in der PR-Vorlage des
  Frontends.
- Eine neue Farbe oder ein neuer Baustein kommt zuerst ins Design System, dann in den Code.
- Rechnet ein Prototyp im Canvas zur Anschauung, ist das nicht maßgeblich; es gilt ADR
  [0010](0010-berechnungen-nur-im-backend.md).
