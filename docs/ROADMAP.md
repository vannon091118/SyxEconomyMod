# SyxEconomyMod — Development Roadmap & Master TODO

> **Version:** v0.1.4 | **Stand:** 2026-07-24 | **Spiel:** V71.44

---

## ✅ Abgeschlossen (Phase 0–4.7)

### Phase 4: Adapter-Architektur & Crash-Härtung
- 5 Interfaces + 12 Adapter-Implementierungen. 0 direkte Reflection-Stellen im Core (via Adapter).
- TreasuryCrisis: 5 Tier-Stufen + Hard-Floor. Hysterese.
- DiagnosticExporter: 3 CSV/Tag. Rebalance-Dashboard (Python).
- Stage-gated Wallets. Build-Gate-System. MethodHandle-Adapter.

### Phase 4.5: Stabilisierung (teilweise abgeschlossen)
- `foodAffordabilityGateEnabled = true` (Cheat-Loop gestoppt)
- `IdentityMapRegistry` (save/load-Datenverlust jetzt laut statt still)
- `printStackTrace()`: alle 124 Sites entfernt
- `catch(Throwable)`: 27→2 Sites tightened

### Phase 4.7: Session-Ergebnisse (2026-07-24)

| Fix | Datei | Beschreibung |
|-----|-------|-------------|
| Carpenter Cold-Start | `FirmLedger.java` | `hill!=null` Guard + `minimumWorkersPerWorkplace` statt 0 |
| mean_wage-Runaway | `FirmLedger.java` | `observedSlope` auf `wageMax=1000` gecapped (4 Pfade) |
| Re-Entry-Crash | `EconomySim.java` | Guard idempotent + `lastUpdateTick=-1` in `load()` |
| God-Class-Split | 3 neue Dateien | `RoomOperatingModeController`, `PropertyMarketController`, `CrisisDispatch` |
| IdentityHashMap Phase 1 | `FirmLedger.java`, `StateWageMarket.java` | 3 Maps: `RoomBlueprintImp`→`String` (stabiler `blueprint.key`) |
| EconomySim LOC | 1.553→1.439 | −114 Zeilen |

---

## 🚧 Offen — Priorisierte TODO-Liste

### 🔴 P0 — Blocker vor Phase 5

| ID | Task | Aufwand | Dateien |
|----|------|---------|---------|
| **T-001** | IdentityHashMap → Long-Key-Migration | 1.5d | Phase 1: 3/16 done (RoomBlueprintImp→String). Phase 2 (Induvidual→Humanoid) deferred. Phase 3 (RoomInstance) offen. |
| **T-002** | Oddjob-Clamp: harte Grenze im Setter | 0.5d | `EconConfig.java`, `EconomyWindow.java` |
| **T-003** | EngineSeams-Migration: 36→34 Direkt-Calls | 0.25d done | 2 deprecated AI-Plan-Calls auf ISyxAI migriert. Verbleibende 34 sind stabile Vanilla-Wrapper — EngineSeams IST der Adapter-Layer. |
| **T-004** | catch(Throwable)-Tightening: 2 Sites | 0.5d | 2 Sites in core/ |

### 🟠 P1 — Broken Features

| ID | Task | Aufwand | Dateien |
|----|------|---------|---------|
| **T-005** | FlowMeter-Coverage: Farms/Pastures sampeln | 0.5d | `FlowMeter.java` |
| **T-006** | Vermögensklassen-Drift: Klassifikation syncen | 0.5d | `WealthStats.java`, `Wallets.java` |
| **T-007** | Hungersignal→Bevölkerungskonsequenz | 0.5d | `BrokeFoodPlan.java`, `MeticImmigration.java` |

### 🟡 P2 — Papercuts & Diagnostics

| ID | Task | Aufwand | Dateien |
|----|------|---------|---------|
| **T-008** | AccessAutomation-Spam: Statusmeldung rate-limit | 0.25d | `AccessAutomation.java` |
| **T-009** | Advisor-Widerspruch: Defizit/Stabil syncen | 0.25d | `EconomyWindow.java` |
| **T-010** | Carpenter targetWage=0 in FlowPrices | 0.25d | `FlowPrices.java` |

---

## Phase 5: Tests & Modularisierung (0/3)

- 🔧 JUnit für `ExchangeKernel` (Wealth-Konservierung)
- 🔧 JUnit für `FlowPrices` Elastizität
- 🔧 `EconomySim` weitere Module extrahieren (Ziel: <1000 LOC)

## Phase 5a: Per-Citizen Training-EXP (0/8 — Plan existiert)

- Plan: [`docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md`](superpowers/plans/2026-07-23-per-citizen-training-exp.md)
- 11 Tasks, 8 Klassen, 3 Tests. Aufwand: 2–3 Sessions.

## Phase 5e: Player-Agency (teilweise abgeschlossen)

| Task | Status |
|------|--------|
| RoomOperatingModeController | ✅ Extrahiert |
| PropertyMarketController | ✅ Extrahiert |
| CrisisDispatch | ✅ Extrahiert |
| Oddjob-Wage-Cap (≤75% von defaultWage) | ❌ (T-002) |
| Pause-vs-Operating-Cost-Choice-UI | ❌ |
| State-Wage-Regulation-Gate | ❌ |

## Phase 6: Advisor-Visualisierungs-Konzept (aspirational)

Datenquellen alle im Code vorhanden. Rendering fehlt. 5 Panels: Status-Gauges, Preis-Sparklines, Kausal-Warnketten, Makro-Trends, Steuerung.

---

## Technische Kennzahlen (2026-07-24)

| Metrik | Wert |
|--------|------|
| Java-Dateien (core/) | 96 |
| EconomySim LOC | 1.442 (−111 von 1.553) |
| Neue Extraktionen | 3 (RoomOperatingModeController, PropertyMarketController, CrisisDispatch) |
| IdentityHashMap in core/ | 10 (3 migriert auf HashMap<String>, 1 Phase-3-ready, 6 Phase-2-pending) |
| catch(Throwable) in core/ | 2 |
| EngineSeams-Direkt-Calls | 34 (in 15 Dateien — 2 deprecated migriert auf ISyxAI, 34 stabile Wrapper) |
| Treasury-Tiers | 5 (+ Hard Floor) |
| printStackTrace() | 0 |

---

## Definition of Done

- `mvn compile` → BUILD SUCCESS
- Keine stummen Catch-Blöcke
- Keine `printStackTrace()` im Core
- IdentityHashMap auf Long-Keys migriert (Phase-5-Gate)
- EngineSeams-Direkt-Calls = 0 (Phase-5-Gate)
