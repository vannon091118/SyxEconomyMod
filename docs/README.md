# SyxEconomyMod — Documentation Index

> **Stand:** 2026-07-24 | **Spiel:** Songs of Syx V71.44 | **Mod-Version:** v0.1.4

---

## Single Source of Truth

| Dokument | Pfad | Rolle |
|----------|------|-------|
| **Changelog** | [CHANGELOG.md](../CHANGELOG.md) | ✓ **KANONISCH** — vollständige Release-Historie |
| **README** | [README.md](../README.md) | ✓ Mod-Übersicht, Quick Start, Dev-Tooling |
| **Roadmap & TODO** | [ROADMAP.md](ROADMAP.md) | ✓ **KANONISCH** — priorisierte TODO-Liste, Phase-Status |
| **Backlog** | [BACKLOG.md](BACKLOG.md) | ✓ Live-Test-Funde, Papercuts, Bugs |
| **pom.xml** | [pom.xml](../pom.xml) | ✓ Versions- und Build-Metadaten |

## Architektur & Design

| Dokument | Pfad | Status |
|----------|------|--------|
| Architecture | [ARCHITECTURE.md](ARCHITECTURE.md) | ✓ Schichtenmodell, Orchestrierung, neue Extraktionen |
| API Reference | [API_REFERENCE.md](API_REFERENCE.md) | ✓ Vanilla- & Mod-APIs |
| Glossary | [GLOSSARY.md](GLOSSARY.md) | ✓ Klassen-Glossar |
| Icon Inventory | [ICON_INVENTORY.md](ICON_INVENTORY.md) | ✓ Icon-Mapping |
| Balance Levers | [BALANCE_LEVERS.md](BALANCE_LEVERS.md) | ✓ Alle tunable Konstanten |
| Persistence Options | [PERSISTENCE_OPTIONS.md](PERSISTENCE_OPTIONS.md) | ✓ H2/SQLite-Persistenz-Design |

## Pläne & Audits

| Dokument | Pfad | Status |
|----------|------|--------|
| Phase-4.7 Stabilization Plan | [superpowers/plans/2026-07-24-phase47-stabilization.md](superpowers/plans/2026-07-24-phase47-stabilization.md) | ✓ In Arbeit |
| 3-Fenster UI-Refactor Plan | [superpowers/plans/2026-07-24-3-window-ux-refactor.md](superpowers/plans/2026-07-24-3-window-ux-refactor.md) | ✓ Aktiv — Single Source of Truth für UI |
| Per-Citizen Training-EXP Plan | [superpowers/plans/2026-07-23-per-citizen-training-exp.md](superpowers/plans/2026-07-23-per-citizen-training-exp.md) | ✓ Phase 5a |
| Phase-4-Adapter-Plan | [PHASE4_ADAPTER_PLAN.md](PHASE4_ADAPTER_PLAN.md) | ✅ Abgeschlossen |
| Master Audit Phase 5 | [MASTER_AUDIT_PHASE5.md](MASTER_AUDIT_PHASE5.md) | 📋 Audit-Referenz |
| Gap Analysis Phase 5 | [GAP_ANALYSIS_PHASE5.md](GAP_ANALYSIS_PHASE5.md) | 📋 Lücken-Analyse |

## Historische Snapshots 🗄

Alle eingefrorenen Snapshots und historischen Reports sind in
**[docs/archive/](archive/)**. Wer in `archive/` schaut, geht in die Geschichte.
Wer in `docs/` liest, sieht nur aktive Wahrheit.

## Live-Notes-Funnel

Playtest-Notizen: `docs/live-notes/YYYY-MM-DD-title.md` → `./tools/consolidate-live-notes.sh` → BACKLOG.md.

---

## Truth-Status (2026-07-24)

| Aussage | Code-Wahrheit |
|---------|--------------|
| EconomySim LOC | **1.442** (von 1.553 reduziert) |
| Neue Extraktions-Klassen | **3** (RoomOperatingModeController, PropertyMarketController, CrisisDispatch) |
| catch(Throwable) in core/ | **0** |
| printStackTrace() in core/ | **0** |
| IdentityHashMap in core/ | **3** Dateien |
| EngineSeams-Direkt-Calls | **31** |
| TreasuryCrisis-Stufen | **5 + Hard Floor** |
