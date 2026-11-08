#!/usr/bin/env bash
# Packt alles für den Betrieb per Compose in target/haushaltsbuch-<version>.tar.gz: compose.yaml,
# .env.beispiel, Realm-Import und das Init-Skript der Datenbank. Der Release-Workflow hängt das Archiv
# ans Release; zum Ausprobieren liegt der Ordner daneben in target/haushaltsbuch-<version>/.
set -euo pipefail

version=${1:?Version angeben, z. B. 0.1.0}
cd "$(dirname "$0")/.."
ziel="target/haushaltsbuch-$version"

rm -rf "$ziel" "$ziel.tar.gz"
mkdir -p "$ziel/keycloak/import" "$ziel/postgres/init"
cp betrieb/compose.yaml betrieb/.env.beispiel "$ziel/"
cp docker/keycloak/import/*.json "$ziel/keycloak/import/"
cp docker/postgres/init/* "$ziel/postgres/init/"
tar -czf "$ziel.tar.gz" -C target "haushaltsbuch-$version"
echo "$ziel.tar.gz"
