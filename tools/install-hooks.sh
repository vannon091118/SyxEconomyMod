#!/usr/bin/env bash
# ==============================================================================
# SyxEconomyMod — Git Hooks Installer (Operator Handbuch)
# Version: Sprint v0.13.121+ConceptGlossary
# ==============================================================================
# ZWECK & IDEMPOTENZ:
#   Installiert die kombinierten CI/CD-Pre-Commit-Gates im lokalen .git/ Ordner.
#   Das Skript ist strikt IDEMPOTENT: Ein wiederholter Aufruf ueberschreibt
#   bestehende Hooks sicher mit der aktuellen Version (siehe remove_hook())
#   und richtet keinen Schaden an, sofern die Hook-Datei bereits aus diesem
#   Skript installiert wurde; fremd-erzeugte Hooks bleiben unangetastet.
#
# HISTORIE (Migration via Thin-Wrapper-Deprecation-Pattern):
#   VOR v0.13.118+Governance-Diät: Es gab unzaehlige Einzelskripte:
#     - verify-version-consistency.sh (Versions-Checks)
#     - docs-truth-consistency.sh (Doku-Drift)
#     - verify-audit-claims.sh ([PM-OK]/[HYP]-Tag-Validation)
#     - post-commit-pom-watchdog.sh (Maven-Property-Watch)
#     - truth-stamp.sh + truth-stamp.py (Post-Commit Truth-Stamp)
#     - post-commit-session-handover.sh (Auto-Handover-Generation)
#   DIESE WURDEN via Thin-Wrapper-Deprecation-Pattern zuerst zu hohlen Echos
#   degradiert und im Sprint v0.13.118+ endgueltig geloescht.
#   AB v0.13.121+: Alles laeuft zentral ueber EINEN Pflicht-Hook.
#
# INSTALLIERTE HOOK-DATEIEN:
#   1. .git/hooks/pre-commit  (BLOCKIEREND):
#      Ruft `bash tools/gate.sh precommit` auf und
#        - Phase-4.7-Shield (IdentityHashMap/catch(Throwable-Drift)
#        - Doku-Sync (Stam-Doku-Marker vs pom.xml)
#        - God-Class-Guard (Hard-Block bei Drift)
#      Bei Fail: Git-Commit wird abgebrochen. Developer sieht rote Meldung
#                und kann den Drift fixen ODER explizit GATE_SKIP=true setzen.
#
#   2. .git/hooks/post-commit  (NON-BLOCKING):
#      Ruft `bash tools/post-commit-shield.sh` auf und
#        - Hard-Block-Drift nach dem Commit
#        - mod.homepage/mod.credits-Sync-Probe (soft-WARN)
#      Bei Fail: nur Log-Eintrag im Working-Tree, KEIN Rollback.
#
# VERWENDUNG:
#   bash tools/install-hooks.sh           # Default: installiert beide Hooks
#   bash tools/install-hooks.sh --remove  # entfernt beide Hooks (idempotent)
#   bash tools/install-hooks.sh --help    # zeigt diesen Header an

set -eo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

HOOK_FILE=".git/hooks/pre-commit"
POST_COMMIT_FILE=".git/hooks/post-commit"
PHASE47_GATE="tools/phase47-shield.sh"
SHIELD_POST="tools/post-commit-shield.sh"
UNIFIED_GATE="tools/gate.sh"

remove_hook() {
    if [ -f "$HOOK_FILE" ]; then
        if grep -q 'gate.sh precommit\|docs-truth-consistency.sh\|verify-version-consistency.sh' "$HOOK_FILE"; then
            rm "$HOOK_FILE"
            echo -e "${GREEN}Hook entfernt: $HOOK_FILE${NC}"
        else
            echo -e "${YELLOW}$HOOK_FILE existiert, wurde aber nicht von diesem Install-Skript erstellt.${NC}"
            echo -e "${YELLOW}Manuell pruefen/loeschen.${NC}"
        fi
    else
        echo -e "${YELLOW}Kein Hook vorhanden.${NC}"
    fi
    exit 0
}

if [ "${1:-}" = "--remove" ]; then
    remove_hook
    if [ -f "$POST_COMMIT_FILE" ]; then
        if grep -q 'post-commit-shield\|gate.sh' "$POST_COMMIT_FILE"; then
            rm "$POST_COMMIT_FILE"
            echo -e "${GREEN}Hook entfernt: $POST_COMMIT_FILE${NC}"
        fi
    fi
    exit 0
fi

# Sprint v0.13.108+Doku-Slim Preflight: nur phase47 + gate.sh erforderlich.
# 4 Wrapper-Skripte (verify-version / docs-truth / verify-audit / truth-stamp +
# post-commit-watchdog + handover) sind in GATE-11 obsolet geworden.
if [ ! -x "$PHASE47_GATE" ]; then
    echo -e "${RED}FEHLER: $PHASE47_GATE fehlt oder nicht ausfuehrbar.${NC}" >&2
    echo "  Sprint v0.13.108+Doku-Slim: nur phase47-shield + gate.sh erforderlich." >&2
    exit 2
fi
if [ ! -x "$UNIFIED_GATE" ]; then
    echo -e "${RED}FEHLER: $UNIFIED_GATE fehlt oder nicht ausfuehrbar.${NC}" >&2
    echo "  Sprint v0.13.108+Doku-Slim: tools/gate.sh ist Pflicht-Hook-Backend." >&2
    exit 2
fi

# Optional Post-Commit-Shield: warn-only wenn fehlend
if [ ! -f "$SHIELD_POST" ]; then
    echo -e "${YELLOW}Hinweis: $SHIELD_POST fehlt - Post-Commit-Shield nicht installiert.${NC}" >&2
    SKIP_SHIELD_POST=1
fi

if ! command -v git >/dev/null 2>&1; then
    echo -e "${RED}FEHLER: 'git' nicht im PATH.${NC}" >&2
    exit 2
fi

# .git-Verzeichnis anlegen falls noetig
if [ ! -d .git ]; then
    echo -e "${YELLOW}Hinweis: kein .git/ gefunden — fuehre 'git init' durch.${NC}"
    if [ "${SKIP_GIT_INIT:-0}" -ne 1 ]; then
        git init -q
        echo -e "${GREEN}git init ausgefuehrt.${NC}"
    else
        echo -e "${RED}Abbruch: SKIP_GIT_INIT=1, aber kein .git/.${NC}" >&2
        exit 2
    fi
fi

mkdir -p .git/hooks

# Sprint v0.13.108+Doku-Slim: Hook-Template ruft 1 Universal-Gate.
cat > "$HOOK_FILE" << 'EOF'
#!/usr/bin/env bash
# SyxEconomyMod — Pre-Commit Gate (auto-generated by tools/install-hooks.sh)
# =============================================================================
# Sprint v0.13.108+Doku-Slim: 1 Aufruf von tools/gate.sh precommit kombiniert:
#   1) Phase-4.7-Shield: IdentityHashMap/EngineSeams/catch(Throwable)/printStackTrace
#   2) Doku-Sync: pom.xml <version> ↔ Doku Anchor (README/CHANGELOG/ROADMAP/GLOSSARY)
#   3) God-Class-Guard: Hard-Block gegen neue God-Files
# Alle muessen PASS liefern, sonst wird der Commit abgebrochen.

set -e

echo ""
echo ">>> Pre-Commit Gate (1 unified check via tools/gate.sh precommit)"
bash tools/gate.sh precommit
EOF

chmod +x "$HOOK_FILE"

echo -e "${GREEN}Installiert: $HOOK_FILE (1 unified gate via tools/gate.sh precommit)${NC}"

# Post-Commit-Shield installieren (non-blocking). Alte Hook-Systeme (Watcher,
# Truth-Stamp, Session-Handover) sind in GATE-11 geloescht — keine
# Re-Integration noetig.
if [ ! -f "$POST_COMMIT_FILE" ] && [ -z "${SKIP_SHIELD_POST:-}" ]; then
    cat > "$POST_COMMIT_FILE" <<'HOOK_EOF'
#!/usr/bin/env bash
# SyxEconomyMod — Post-Commit Shield Hook (auto-generated by tools/install-hooks.sh)
# ==================================================================================
# Sprint v0.13.108+Doku-Slim: Non-blocking Phase-4.7 Regression Detector.
HOOK_EOF
    echo "bash tools/post-commit-shield.sh" >> "$POST_COMMIT_FILE"
    chmod +x "$POST_COMMIT_FILE"
    echo -e "${GREEN}Installiert: $POST_COMMIT_FILE (Phase-4.7 Regression Detector)${NC}"
fi

echo ""
echo "Kombiniertes Pre-Commit-Gate: tools/gate.sh precommit"
echo "  Phase-4.7-Shield: blockt IdentityHashMap/EngineSeams/catch(Throwable)/printStackTrace-Regression"
echo "  Doku-Sync: pom.xml ↔ Doku-Anker (README/CHANGELOG/ROADMAP/GLOSSARY)"
echo "  God-Class-Guard: Hard-Block gegen neue God-Klassen"
echo "Post-Commit-Hook: Phase-4.7 Shield-Regression-Detector (non-blocking)"
echo "Bypass fuer einzelnen Commit: git commit --no-verify"
echo "Bypass dauerhaft: Hook loeschen via:"
echo "  bash tools/install-hooks.sh --remove"
