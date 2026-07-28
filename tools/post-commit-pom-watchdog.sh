#!/usr/bin/env bash
# SyxEconomyMod — Post-Commit-Pom-Watchdog
# =========================================
# Wird nach jedem Commit aufgerufen. Prueft, ob pom.xml ohne gleichzeitige
# CHANGELOG.md-Aenderung modifiziert wurde. Wenn ja: WARNUNG (kein Hard-Block,
# damit Notfall-Hotfixes moeglich bleiben).
#
# Installation:
#   cp tools/post-commit-pom-watchdog.sh .git/hooks/post-commit
#   chmod +x .git/hooks/post-commit
#
# Oder via tools/install-hooks.sh (das einen kombinierten Hook installiert).
#
# Exit-Code:
#   0 - immer (Watchdog ist non-blocking)

set -e

# Nur laufen wenn wir in einem Git-Repo sind
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

# Wenn kein voriger Commit existiert (Initial-Commit), nichts zu pruefen
git rev-parse HEAD~1 >/dev/null 2>&1 || exit 0

YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

CHANGED=$(git diff --name-only HEAD~1 HEAD 2>/dev/null || true)

# Wenn pom.xml gendert wurde, aber CHANGELOG.md nicht -> WARNUNG
if echo "$CHANGED" | grep -qx 'pom.xml'; then
    if ! echo "$CHANGED" | grep -qx 'CHANGELOG.md'; then
        echo ""
        echo -e "${YELLOW}=============================================================="
        echo -e "  WATCHDOG: pom.xml wurde geaendert, CHANGELOG.md aber nicht."
        echo -e "==============================================================${NC}"
        echo ""
        echo "  Wenn das ein Versions-Bump war:"
        echo "    bash tools/bump-version.sh [patch|minor|major] -m '...'"
        echo ""
        echo "  Wenn das ein NOTFALL-Fix war (z. B. dep-version):"
        echo "    git commit --amend  # CHANGELOG.md manuell hinzufuegen, dann amend"
        echo "    ODER: git tag <version> manuell setzen"
        echo ""
        echo "  Wenn das ein FALSE-POSITIVE war (z. B. test-fixture-Update):"
        echo "    Diese Warnung kann ignoriert werden. Hook non-blocking."
        echo ""
        exit 0  # Non-blocking
    fi
fi

# Empfohlen: nach Bump-Commits den Auto-Formatter ausfuehren
# (derzeit kein Auto-Formatter aktiv, hier als Hook-Stub)
exit 0