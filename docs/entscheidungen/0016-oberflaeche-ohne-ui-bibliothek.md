# 0016 Eigene Oberfläche ohne UI-Bibliothek

**Status:** angenommen, 2026-11-02, nachgetragen mit [#127](https://github.com/bjoernb94/Haushaltsbuch-Backend/issues/127) (umgesetzt mit
[Frontend #4](https://github.com/bjoernb94/Haushaltsbuch-Frontend/issues/4))

## Kontext

Das Grundgerüst der SPA nutzte Angular Material. Das Design im Canvas „Haushaltsbuch“ folgt aber nicht
Material Design: eigene Farben, Abstände, Formularfelder und Navigation. Material hätte man an fast jeder
Stelle überschreiben müssen, mit Stylesheets und Abhängigkeiten, die das Bundle vergrößern und bei jedem
Update brechen können.

## Entscheidung

Die SPA nutzt keine UI-Bibliothek. Angular Material wurde mit dem Redesign entfernt.

- Markup und Styles entstehen je Komponente nach dem Design.
- Icons sind Inline-SVG, keine Icon-Schrift.
- Die Schrift Manrope kommt lokal aus `@fontsource-variable/manrope`, nicht von Dritten.
- Formularfelder und Buttons sind SCSS-Mixins (`shared/formular/_formular.scss`), Farben CSS-Variablen
  `--app-*` in `styles.scss`.

## Folgen

- Die Oberfläche entspricht dem Design ohne Umwege, und die SPA stellt keine Anfragen an Dritte.
- Barrierefreiheit, Tastaturbedienung und Fokus müssen wir selbst sicherstellen; angular-eslint prüft die
  Templates auf Barrierefreiheit, Dialoge nutzen das native `dialog`.
- Gemeinsame Bausteine entstehen von Hand und gehören nach `shared/`, sobald sie mehr als ein Bereich
  braucht.
