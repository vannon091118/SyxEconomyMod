# SyxEconomyMod — Entwicklung & Roadmap

> **Version:** v0.13.5 | **Spiel:** Songs of Syx V71.44 | **Stand:** 2026-07-25
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` validiert dies vor jedem `mvn compile`.

---

## Aktive P0/P1-Blocker (aus [`docs/BACKLOG.md`](docs/BACKLOG.md))

Severity: 🔴 P0 = Crash/Datenverlust · 🟠 P1 = broken feature · 🟡 P2 = papercut · 🟢 P3 = nice-to-have

### 🔴 Aktuelle keine P0-Blocker offen

### 🟠 P1 — Broken Features (5 offen)

| ID | Task | Datei | Aufwand |
|---|---|---|---|
| **B-001** | FlowMeter: sampelt `SETT.ROOMS().industries.all` only — FARM_GRAIN/FARM_FRUIT/FARM_COTTON/WORKSHOP_POTTERY nie erfasst, `profit_per_day=0.00` | `FlowMeter.java` | ~20 LoC Fix + Sample-Range erweitern |
| **B-004** | Vermögensklassen-Drift: Verteilung zeigt 30 Mittelstand + 24 Elite, Bürger-Klassen zeigt 28 Arm + 23 Mittelstand — 3 Bürger fehlen, 24 Elite unsichtbar | `WealthStats.java`, `Wallets.java` | Klassifikations-Pipeline angleichen |
| **B-005** | Oddjob-Clamp Placebo: `setPay()` loggt nur Warnung, erzwingt keinen Cap. Slider+Save schreiben ungeclampt | `EconConfig.java` (Setter), `OddjobMarket.java` | harte Grenze im Setter + Save-Validation |
| **B-006** | IdentityHashMap-Migration Phase 2/3 — Phase 1 (RoomBlueprintImp→String) fertig. Phase 2: Induvidual→Humanoid `id()`-Key. Phase 3: RoomInstance→Composite-Long-Key | `*HashMap`-Sites in `core/` | mehrere Sessions |
| **B-009** | Hungersignal ohne Bevölkerungskonsequenz. Save mit 20 Tagen `starving_signal=1, food_days=0` zeigt wachsende Population — keine Kopplung an Immigration/Reproduktion | `BrokeFoodPlan.java`, `MeticImmigration.java`, `Roster.java` | Hook an Demographie-Update |

### 🟡 P2 — Papercuts (3 offen)

| ID | Task | Datei |
|---|---|---|
| **B-002** | AccessAutomation-Spam: 14 Status-Meldungen/Tick ins Spieler-Chronik | `AccessAutomation.java` |
| **B-008** | EngineSeams-Direkt-Calls reduzieren — Legacy-Fassade, Adapter-Injection statt direktem Engines-Zugriff | diverse `core/`-Sites |
| **B-010** | Carpenter `targetWage=0` in `FlowPrices` — kein Wage-Signal für Carpenter-Beruf | `FlowPrices.java` |

### ✅ Behoben in aktueller Session

| ID | Fix | In Version |
|---|---|---|
| **B-003** | Advisor-Widerspruch "Defizit"+"Staatskasse stabil" | v0.13.0 |
| **B-007** | `catch(Throwable)`-Sites 27→0 | v0.1.0 |

---

## Phase A: Bypass-SDK — ✅ DONE (v0.13.4)

- `adapter/seam/BypassGate.java` — Zentraler Entry-Point (MethodHandles.Lookup, typisierte Factories)
- `adapter/seam/FieldAccessor.java` — IntField, DoubleField, FloatField, RefField<T> mit getStatic/setStatic
- `adapter/seam/MethodAccessor.java` — VoidMethod, BooleanMethod
- `adapter/seam/ClassResolver.java` — Class.forName mit Game-ClassLoader
- Interfaces (ISyx*) bleiben als stabiler Kontrakt erhalten
- Phase B–F: Thin-Adapter-Migration, Fallback-Löschung, Config-Cleanup

## Phase-5-Pläne (in [`docs/superpowers/plans/`](docs/superpowers/plans/))

| Plan | Status | Aufwand |
|---|---|---|
| [2026-07-23-per-citizen-training-exp.md](docs/superpowers/plans/2026-07-23-per-citizen-training-exp.md) | 0/8 implementiert (Phase 5a) | 2–3 Sessions |
| [2026-07-24-3-window-ux-refactor.md](docs/superpowers/plans/2026-07-24-3-window-ux-refactor.md) | Done in v0.13.x | — |
| [2026-07-24-phase47-stabilization.md](docs/superpowers/plans/2026-07-24-phase47-stabilization.md) | Done in v0.1.4 | — |
| [2026-07-24-redundanz-modularisierung.md](docs/superpowers/plans/2026-07-24-redundanz-modularisierung.md) | Done | — |

---

## Abgeschlossen (v0.0.1 → v0.13.2)

| Bereich | Status |
|---|---|
| **Adapter-Layer** (Phase 4) | ✅ 5 Interfaces + 12 Implementierungen, 0 direkte Reflection-Stellen im Core |
| **TreasuryCrisis** (5-stufig + Hard-Floor) | ✅ |
| **DiagnosticExporter** (3 CSV/Tag + Python-Dashboard) | ✅ |
| **IdentityHashMap Phase 1** (RoomBlueprintImp→String) | ✅ 3 Maps migriert |
| **Cold-Start-Bug** (Carpenter 0-Output) | ✅ `hill!=null`-Guard + `minimumWorkersPerWorkplace` |
| **mean_wage-Runaway** | ✅ `Math.min(slope, wageMax=1000)` auf 4 Pfaden |
| **Re-Entry-Crash** (`lastUpdateTick==ticks`) | ✅ idempotenter Guard + Reset in `load()` |
| **God-Class-Split EconomySim** | ✅ RoomOperatingModeController, PropertyMarketController, CrisisDispatch extrahiert |
| **Bug-Loop Cheat** (`foodAffordabilityGateEnabled=true`) | ✅ |
| **Stage-gated Wallets** (200/500/2000/5000 D) | ✅ |
| **5-Stufen-System** (SUBSISTENZ→IMPERIUM) | ✅ |
| **Gini→Loyalty** Booster | ✅ |
| **5 UI-Fenster + 16 interaktive Tabs** | ✅ |
| **6 Hotkeys** (Numpad +/−/∗/0//, ESC) | ✅ |
| **Save-Format 33 chunked** (TLV, Tag-Skipping) | ✅ |

Vollhistorie: [`CHANGELOG.md`](CHANGELOG.md).

---

## Definition of Done

Vor jedem Merge/Build muss gelten:

1. `mvn validate` BUILD SUCCESS — alle 4 Gates grün:
   - Stam-Doku-Sync (alle 7 Docs ↔ `pom.xml`)
   - Code-Audit (kein `catch(Throwable)`, kein `printStackTrace`)
   - Version ↔ Changelog + `_Info.txt`-Template-Konsistenz
   - Adapter ↔ Engine-Signaturen (5 Adapter, 19 Methoden/Felder)
2. `mvn test` — alle JUnit-Tests grün (138+).
3. Manuell: `bash tools/bump-version.sh patch --dry-run` zeigt nur den nächsten Patch-Schritt.
4. Stam-Dokumente haben oben den Versions-Stempel `**Version:** v0.13.2` o. ä.

---

## Wie man einen Drift findet

```bash
# Stam-Doku-Sync explizit
bash tools/verify-doc-sync.sh

# Drift-Heuristik (deprecated Behauptungen)
bash tools/docs-truth-consistency.sh

# Alle vier Build-Gates im Strict-Mode
bash tools/build-gate.sh --strict

# Version Drift in pom.xml/CHANGELOG/_Info.txt
bash tools/verify-version-consistency.sh
```
