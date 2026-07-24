# Fullscan Cleanup — SyxEconomyMod v1.7.2

> **Datum:** 2026-07-23 | **Build:** SUCCESS | **Fokus:** Vorgaben zu wörtlich genommen, Halluzinationen, Typos, Benennungen, Doku-Drift

---

## 1. Dateibaum-Diskrepanz (Cached vs. Real)

Der gecachte Dateibaum vom Session-Start war veraltet. Die tatsächliche Disk-Struktur wurde bereits in einer früheren Session bereinigt:

| Gecachter Pfad | Tatsächlicher Zustand |
|---|---|
| `docs/Code Gegenprüfung gegen.md` | ❌ Existiert nicht mehr (bereits gelöscht) |
| `docs/VANILLA_VERIFICATION.md` | ❌ In `docs/API_REFERENCE.md` absorbiert |
| `docs/ECONOMY_API_REFERENCE.md` | ❌ In `docs/API_REFERENCE.md` absorbiert |
| `docs/ADVISOR_VISUALIZATION_CONCEPT.md` | ❌ Existiert nicht mehr |
| `docs/reports/core_semantic_diff.md` | → `docs/archive/HISTORICAL_SEMANTIC_DIFF.md` (109KB, archiviert 2026-07-23) |
| `docs/reports/FULLSCAN_VANILLA_REPORT.md` | → `docs/archive/HISTORICAL_FULLSCAN.md` (archiviert 2026-07-23) |
| `docs/reports/original_README.md` | → `docs/archive/HISTORICAL_ORIGINAL_README.md` (archiviert 2026-07-23) |
| `docs/reports/bug_report_and_vanilla_analysis.md` | → Bereits gelöscht/integriert |
| `docs/04_roadmap.md` | → `docs/ROADMAP.md` (umbenannt) |

---

## 2. Version-Header-Fixes (5 Docs)

Alle auf v1.7.2 aktualisiert:

| Datei | Vorher | Nachher |
|---|---|---|
| `ARCHITECTURE.md` | `Version 1.7.1`, `Stand v1.7.0` | `Version 1.7.2` |
| `API_REFERENCE.md` | `v1.7.0` | `v1.7.2` |
| `ICON_INVENTORY.md` | `v1.7.0` | `v1.7.2` |
| `ROADMAP.md` | `v1.7.1` | `v1.7.2` |
| `PHASE4_ADAPTER_PLAN.md` | `v1.7.0` | `v1.7.2` |

---

## 3. Halluzinierte Referenzen

| Fund | Datei:Zeile | Fix |
|---|---|---|
| `PROGRESSION_DESIGN.md` — existiert nicht | `EconIndicators.java:42` | → `COVERAGE_AUDIT_FINAL_2026-07-23.md` |

Keine weiteren halluzinierten Doc-Referenzen gefunden. Keine halluzinierten API-Namen (FACTONS mit O, CIVIC ohne S, HUMANOID-Typo) im Code.

---

## 4. Code-Kommentare: Deutsch → Englisch

`EconIndicators.java` vollständig von Deutsch auf Englisch migriert:

| Vorher (Deutsch) | Nachher (Englisch) |
|---|---|
| `Ring-Buffer von 3-5 EconSnapshots plus Differenzbildung` | `Ring buffer of up to 6 EconSnapshots with trend detection` |
| `Trend-Flags (berechnet aus Differenzen)` | `Trend flags (computed from snapshot diffs)` |
| `Schwellwerte (aus PROGRESSION_DESIGN.md)` | `Thresholds (from COVERAGE_AUDIT_FINAL_2026-07-23.md)` |
| `Neuen Snapshot einfuegen` | `Insert new snapshot` |
| `Gini steigend UND ueber Schwellwert` | `Gini rising AND above threshold` |
| `Lohn gesunken` | `Wage decreased` |
| `Staatskasse schrumpft` | `Treasury shrinking` |
| `Sichtbarkeit: Trendumschlag wird in der Chronik gemeldet` | `Visibility: trend reversal is logged to the chronicle` |
| `Echte Konsequenz: anhaltend sinkende Einnahmen...` | `Real consequence: persistent revenue decline...` |

---

## 5. Systematische Deutsch/Englisch-Mischung (Dokumentiert, nicht behoben)

**214+ deutsche Kommentare** in 20+ Java-Dateien identifiziert. Betroffene Dateien (Top 10):

| Datei | Deutsche Kommentare |
|---|---|
| `EconomyWindow.java` | 40+ |
| `EconConfig.java` | 20+ |
| `EconomySim.java` | 15+ |
| `EconProgression.java` | 15+ |
| `Wallets.java` | 5+ |
| `PropertyLedger.java` | 5+ |
| `PovertyPressure.java` | 3 |
| `OddjobAutomation.java` | 1 |
| `WarehouseAutomation.java` | 3 |
| `StateWageMarket.java` | 1 |

**Bewertung:** Dies ist ein systematisches Muster. Der Agent hat die deutschen User-Instruktionen direkt als Code-Kommentare übernommen ("Vorgaben zu wörtlich genommen"). Die EventLog-Runtime-Strings sind ebenfalls auf Deutsch — dies ist jedoch konsistent mit den EconTexts-UI-Strings und dem deutschsprachigen User. Komplett-Migration aller Kommentare auf Englisch würde ~200+ Zeilen in 20+ Dateien betreffen und ist ein separates Projekt.

---

## 6. Naming-Inkonsistenzen (Dokumentiert, nicht behoben)

| Muster | Beispiele | Bewertung |
|---|---|---|
| `Econ` vs `Economic` Prefix | `EconConfig`, `EconIndicators`, aber `EconomicRoles`, `EconomySim` | Niedrig — etablierte Konvention |
| Controller/Kernel/Automation | `ConstructionHoardController`, `AuditKernel`, `FurnishingAutomation` | Mittel — unklare Unterscheidung |
| Food-Rollback-Redundanz | `FoodRollback` + `FoodRollbackKernel` vs `FoodPlanController` + `FoodTransactionPlan` | Mittel — potenzielles Refactoring |
| `InflationOff` | Klingt wie Toggle, nicht wie Entity | Niedrig — klar benannt |
| `MainScript`/`InstanceScript` | Generische Vanilla-Namen | Niedrig — von Engine vorgegeben |

---

## 7. Positive Befunde

- ✅ Keine halluzinierten API-Namen (FACTONS, CIVIC, HUMANOID)
- ✅ Alle 6 AI-Plan-Klassennamen V71.44-verifiziert
- ✅ `_Info.txt` Maven-Filtering funktioniert korrekt (deployed Version zeigt 1.7.2)
- ✅ Keine toten/verwaisten Dateien mehr im docs/-Baum
- ✅ `docs/archive/HISTORICAL_*` sauber getrennt von aktiven Docs (Konsolidierungs-Schritt 2 abgeschlossen 2026-07-23)
- ✅ `API_REFERENCE.md` (31KB) enthält jetzt alle verifizierten Vanilla-APIs an einem Ort

---

## 8. Geänderte Dateien

| Datei | Änderung |
|---|---|
| `docs/ARCHITECTURE.md` | Version 1.7.1→1.7.2, Stand v1.7.0→v1.7.2 |
| `docs/API_REFERENCE.md` | Version v1.7.0→v1.7.2 |
| `docs/ICON_INVENTORY.md` | Version v1.7.0→v1.7.2 |
| `docs/ROADMAP.md` | Version v1.7.1→v1.7.2 |
| `docs/PHASE4_ADAPTER_PLAN.md` | Version v1.7.0→v1.7.2 |
| `src/.../EconIndicators.java` | Deutsch→Englisch Kommentare, halluzinierte Doc-Ref gefixt |

---

*Report erstellt: 2026-07-23 | Build: SUCCESS | 6 Dateien geändert | Keine Regressionen*
