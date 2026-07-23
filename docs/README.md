# SyxEconomyMod — Documentation Index

> **Stand:** 2026-07-23 (Truth-Audit-Pass) | **Spiel:** Songs of Syx V71.44 | **Mod-Version:** v0.1.0
>
> Diese Seite ist der Einstiegspunkt für alle Dokumentation. Sie wurde im Rahmen der
> Truth-Konsolidierung erstellt (siehe [docs/reports/TRUTH_REPORT.md](reports/TRUTH_REPORT.md)).

---

## Single Source of Truth

| Dokument | Pfad | Rolle |
|----------|------|-------|
| **Changelog (Root)** | [CHANGELOG.md](../CHANGELOG.md) | ✓ **KANONISCH** — vollständige Release-Historie (10 Einträge) |
| **Implementation Plan** | [docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md](superpowers/plans/2026-07-23-per-citizen-training-exp.md) | ✓ **KANONISCH** — Phase-5-Plan (Training EXP + Needs Bridge) |
| **README** | [README.md](../README.md) | ✓ Mod-Übersicht |
| **pom.xml** | [pom.xml](../pom.xml) | ✓ Versions- und Build-Metadaten |

**Beachte:** Es existieren drei redundante Dokumente, die ersetzt wurden:
- ~~`docs/CHANGELOG.md`~~ — ersetzt durch Redirect-Notice auf [CHANGELOG.md](../CHANGELOG.md)
- ~~`IMPLEMENTATION_PLAN.md`~~ — verschoben nach `docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md`

---

## Architektur & Design

| Dokument | Pfad | Status |
|----------|------|--------|
| Architecture | [docs/ARCHITECTURE.md](ARCHITECTURE.md) | ✓ Schichtenmodell, Orchestrierung |
| API Reference | [docs/API_REFERENCE.md](API_REFERENCE.md) | ✓ Vanilla- & Mod-APIs (872 Zeilen) |
| Glossary | [docs/GLOSSARY.md](GLOSSARY.md) | ✓ Klassen-Glossar (**112 Einträge**, 4 Kategorien) |
| Icon Inventory | [docs/ICON_INVENTORY.md](ICON_INVENTORY.md) | ✓ Icon-Mapping |

## Phase-Pläne (Archivierungs-Wert)

| Dokument | Pfad | Status |
|----------|------|--------|
| Phase-4-Adapter-Plan | [docs/PHASE4_ADAPTER_PLAN.md](PHASE4_ADAPTER_PLAN.md) | ✅ ABGESCHLOSSEN |
| Roadmap | [docs/ROADMAP.md](ROADMAP.md) | ✓ — Truth-korrigiert |

## Session-Berichte (2026-07-23)

| Dokument | Pfad | Status |
|----------|------|--------|
| Session Summary | [docs/SESSION_SUMMARY_2026-07-23.md](SESSION_SUMMARY_2026-07-23.md) | ✓ — Tier-Count korrigiert (6→5) |

## Audit-Reports (2026-07-23)

| Dokument | Pfad | Status |
|----------|------|--------|
| Truth-Report | [docs/reports/TRUTH_REPORT.md](reports/TRUTH_REPORT.md) | ✓ Dieser Konsolidierungs-Pass |
| Coverage Audit Final | [docs/reports/COVERAGE_AUDIT_FINAL_2026-07-23.md](reports/COVERAGE_AUDIT_FINAL_2026-07-23.md) | ✓ |
| Fullscan Cleanup | [docs/reports/FULLSCAN_CLEANUP_2026-07-23.md](reports/FULLSCAN_CLEANUP_2026-07-23.md) | ✓ |
| GUI-vs-Mod-Gap-Analyse | [docs/reports/GUI_VS_MOD_GAP_ANALYSIS.md](reports/GUI_VS_MOD_GAP_ANALYSIS.md) | ✓ |
| Vanilla-Access-Verifikation | [docs/reports/VANILLA_ACCESS_VERIFICATION.md](reports/VANILLA_ACCESS_VERIFICATION.md) | ✓ |

## Historische Snapshots 🗄

Alle historischen, eingefrorenen Snapshots sind seit dem 2026-07-23 in
**[docs/archive/](archive/)** verschoben. Details + Inhaltsverzeichnis im
[docs/archive/README.md](archive/README.md) Banner.

Diese Trennung ist **bewusst** — wer in `docs/archive/` schaut, geht in die Geschichte.
Wer in `docs/` selbst liest, sieht nur noch aktive Wahrheit.

## Tools-Verzeichnis

| Skript / Notebook | Pfad | Zweck |
|-------------------|------|-------|
| Build-Gate | `tools/build-gate.sh` | Pre-Compile-Code-Audit + Adapter-Signaturen |
| Code-Audit | `tools/code-audit.sh` | Leere catch-Blöcke + catch(Throwable)-Sites |
| Version-Consistency | `tools/verify-version-consistency.sh` | pom.xml ↔ CHANGELOG.md ↔ git tags |
| Rebalance-Plots (Python) | `tools/rebalance_plots.py` | 5 Plots für CSV-Analyse |
| Rebalance-Dashboard (Jupyter) | `tools/rebalance_dashboard.ipynb` | Visualisierungs-Notebook |

---

## Truth-Status der Mod-Aussagen

| Aussage | Quelle | Code-Wahrheit |
|---------|--------|--------------|
| Adapter-Dateien = 17 | überall | ✓ |
| Source-Java-Dateien = 108 | CHANGELOG, ROADMAP | ✗ **112** |
| TreasuryCrisis-Stufen = 3 | CHANGELOG, README | ✗ **5 Stufen + Hard Floor** |
| TreasuryCrisis-Stufen = 6 | ROADMAP, SESSION_SUMMARY-Header | ✗ **5 Stufen + Hard Floor** |
| CSV-Macro-Spalten = 31 | CHANGELOG (Root) | ✗ **32** |
| CSV-Macro-Spalten = 32 | README, SESSION_SUMMARY | ✓ |
| Test-Dateien = 0/3 (Phase 5) | ROADMAP | ✗ **1/3** (TreasuryCrisisTest.java) |
| Phase-5-Klassen implementiert | IMPLEMENTATION_PLAN.md | ✗ **0 von 8** (Plan, nicht implementiert) |
| pom.xml-Version = 1.7.3 | SESSION_SUMMARY-Vorher | ✗ **0.1.0** |

Details: [docs/reports/TRUTH_REPORT.md](reports/TRUTH_REPORT.md).
