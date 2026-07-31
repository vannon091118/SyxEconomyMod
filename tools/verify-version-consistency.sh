#!/usr/bin/env bash
# SyxEconomyMod — Version-Changelog Consistency Gate (DEPRECATED)
# ==============================================================
# Sprint v0.13.118+Governance-Diät: alle Logik nach verify-doc-sync.sh
# konsolidiert (Single-Source-of-Truth). Dieser Skript bleibt als
# Thin-Wrapper für Backward-Compat mit install-hooks.sh bestehen —
# install-hooks.sh komponiert den Pre-Commit-Hook mit Verweis auf
# dieses Skript-Namen. Bei Migration auf verify-doc-sync.sh kann
# install-hooks.sh aktualisiert werden um diesen Wrapper zu skippen.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "${SCRIPT_DIR}/verify-doc-sync.sh"
