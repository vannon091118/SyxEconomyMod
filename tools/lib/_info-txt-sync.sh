#!/usr/bin/env bash
# SyxEconomyMod — _Info.txt Sync Library
# =========================================
# Stellt Funktionen bereit, die _Info.txt ↔ pom.xml-Konsistenz pruefen.
# Wird gesourced von verify-version-consistency.sh und bump-version.sh.
#
# Funktionen:
#   sync_info_txt_template_report MODE POM
#       MODE: "strict" (rot, mit Fix-Hint, returns 1) | "warn" (rot, ohne Hard-Fail, returns 1)
#       POM:  Pfad zu pom.xml (default: pom.xml)
#       Returns 0 wenn konsistent, 1 wenn Drift.
#   sync_info_txt_deployed_report EXPECTED_VERSION
#       EXPECTED_VERSION: z. B. "0.1.0"
#       Returns 0 wenn aktuell oder Datei fehlt, 1 wenn stale (yellow).
#
# Pflichtplatzhalter-Syntax in _Info.txt: ${name}
# Property-Syntax in pom.xml:             <name>value</name>
#
# Vertrag:
#   - sync_*_report druckt Status auf stdout und returnt 0 (ok/fehlt) oder 1 (drift/stale).
#   - Caller, die nur warnen wollen, muessen mit '|| true' maskieren, weil
#     viele Caller 'set -e' aktiv haben — sonst wird der Warn-Intent zum Hard-Fail.
#   - Caller, die Hard-Fail wollen, nutzen 'if ! sync_*_report ...; then exit 1; fi'.

# Konvention: Diese Bibliothek darf kein 'set -e'/'set -u' setzen,
# weil sie in Callern mit unterschiedlichen Shell-Modi gesourced wird.

INFO_TXT_TEMPLATE_FILENAME="_Info.txt"
INFO_TXT_DEPLOYED_PATH="target/out/SyxEconomyMod/_Info.txt"

# ── Internal: Property-Namen aus _Info.txt extrahieren ────────────────
# Eine Property pro Zeile, sortiert, dedupliziert.
# Returns 0 auch wenn Datei fehlt oder keine Treffer.
_extract_placeholders() {
    if [[ ! -f "$INFO_TXT_TEMPLATE_FILENAME" ]]; then
        return 0
    fi
    grep -oE '\$\{[a-zA-Z0-9._-]+\}' "$INFO_TXT_TEMPLATE_FILENAME" \
        | sed 's|[${}]||g' | sort -u
}

# ── Internal: Escape regex-Metazeichen (fuer use in grep -E) ─────────
# Nur '.' braucht Escape — Maven-Property-Namen enthalten nur
# [a-zA-Z0-9._-] (siehe Maven POM-Spec), alle anderen Zeichen sind
# in grep -E als literal oder unkritisch.
_escape_dot() {
    printf '%s' "$1" | sed 's/\./\\./g'
}

# ── Internal: Pruefe ob Property-Name in pom.xml existiert ───────────
# Anchored match: <name>value</name> — vermeidet false positives auf
# <nameOther> oder <name.suffix>.
#   $1 = name (z. B. "mod.version")
#   $2 = Pfad zu pom.xml (default: pom.xml)
# Returns: 0 wenn vorhanden, 1 wenn nicht, 2 wenn Datei fehlt.
_property_in_pom() {
    local name="$1"
    local pom="${2:-pom.xml}"
    if [[ ! -f "$pom" ]]; then
        return 2
    fi
    local esc
    esc=$(_escape_dot "$name")
    grep -qE "<${esc}>[^<]*</${esc}>" "$pom"
}

# ── Public: Template ↔ pom.xml-Properties Konsistenz-Report ───────────
# Argumente:
#   $1 = "strict" oder "warn" (default "warn")
#   $2 = Pfad zu pom.xml (default "pom.xml")
# Gibt formatierten Report auf stdout aus.
# Return: 0 wenn konsistent, 1 wenn Drift.
sync_info_txt_template_report() {
    local mode="${1:-warn}"
    local pom="${2:-pom.xml}"

    if [[ ! -f "$INFO_TXT_TEMPLATE_FILENAME" ]]; then
        return 0
    fi

    local placeholders
    placeholders=$(_extract_placeholders || true)
    local count
    count=$(printf '%s\n' "$placeholders" | grep -c . || true)

    local missing=()
    while IFS= read -r name; do
        [[ -z "$name" ]] && continue
        if ! _property_in_pom "$name" "$pom"; then
            missing+=("$name")
        fi
    done <<< "$placeholders"

    if [[ ${#missing[@]} -eq 0 ]]; then
        if [[ "$mode" == "strict" ]]; then
            printf '\033[0;32m✓ _Info.txt Platzhalter konsistent (%d referenziert)\033[0m\n' "$count"
        else
            printf '  - \033[0;32mOK\033[0m _Info.txt Template referenziert %d gueltige Properties\n' "$count"
        fi
        return 0
    fi

    # Drift detected
    if [[ "$mode" == "strict" ]]; then
        printf '\033[0;31m✗ _Info.txt referenziert Platzhalter ohne pom.xml-Property:\033[0m\n'
        for p in "${missing[@]}"; do
            printf '    ${%s}\n' "$p"
        done
        printf '\033[0;31m    Fix: <name> in pom.xml ergaenzen ODER Platzhalter aus _Info.txt entfernen.\033[0m\n'
    else
        printf '  \033[0;31m✗ _Info.txt referenziert fehlende pom.xml-Properties:\033[0m\n'
        for p in "${missing[@]}"; do
            printf '      ${%s}\n' "$p"
        done
        printf '  \033[0;31m  Vor dem naechsten Build beheben (sonst bricht Maven-Filter).\033[0m\n'
    fi
    return 1
}

# ── Public: Deployed _Info.txt Freshness-Report ──────────────────────
# Argument:
#   $1 = expected version (z. B. "0.1.0")
# Gibt formatierten Report auf stdout aus.
# Return: 0 wenn aktuell oder Datei fehlt, 1 wenn stale.
sync_info_txt_deployed_report() {
    local expected_version="$1"

    if [[ ! -f "$INFO_TXT_DEPLOYED_PATH" ]]; then
        printf '  - target/out/SyxEconomyMod/_Info.txt fehlt (kein vorheriger Build)\n'
        printf '    Empfohlen: mvn package -DskipTests\n'
        return 0
    fi

    local deployed
    deployed=$(grep -oE 'VERSION: "[^"]+"' "$INFO_TXT_DEPLOYED_PATH" | head -1 | sed 's/.*"\([^"]*\)".*/\1/' || true)

    if [[ -z "$deployed" ]]; then
        return 0
    fi

    if [[ "$deployed" != "$expected_version" ]]; then
        printf '  \033[1;33m⚠ Deployed _Info.txt stale: VERSION="%s" vs pom.xml="%s"\033[0m\n' "$deployed" "$expected_version"
        printf '    Fix: mvn package -DskipTests (regeneriert target/out/SyxEconomyMod/_Info.txt)\n'
        return 1
    fi

    printf '  - \033[0;32mOK\033[0m Deployed _Info.txt aktuell (VERSION: "%s")\n' "$expected_version"
    return 0
}
