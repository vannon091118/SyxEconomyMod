# HANDOFF T-101 — WarehouseMarket Hybrid-Facade Extraktion

**Status:** Active (Phase 1 abgeschlossen, in Stash geparkt)
**Branch:** `feature/m1-warehouse-market` (geforkt von main @ `96273f6` post M-3 Merge)
**Stash:** `stash@{0}` mit Label `t101-phase1-backup`
**Author dieser Spec:** Buffy (mit `git stash push -u` geparkt, post-merge wiederhergestellt)
**Naechste Session:** Agent/Session, die T-102..T-108 sequenziell durchzieht

---

## Block 1 — Ist-Zustand in Zahlen (HARD-METRIKEN, kein Gefuehl)

### Lokale Lagerung feature/m1-warehouse-market

```
$ wc -l src/vannon/syx/economy/core/WarehouseMarket.java
1902 src/vannon/syx/economy/core/WarehouseMarket.java   ← minus 9 vs main's 1911 (IdentityMap-Kommentar + 3 Register-Calls weg)

$ wc -l src/vannon/syx/economy/core/warehouse/market/MarketSharedState.java
51 src/vannon/syx/economy/core/warehouse/market/MarketSharedState.java   ← neu erstellt in T-101 Phase 1
```

> **Achtung Spalten-Artefakt:** `tools/god-class-guard/` Tools nur auf `main` (nach M-3 Merge), branch `feature/m1-warehouse-market` wurde VOR dem M-3 Merge geforkt. `bash tools/god-class-guard.sh --mode=dry --json` ist auf dieser Branch nicht lauffaehig. Falls God-Class-Gard verfügbar sein soll: zuerst auf aktualisierten main rebasen, dann `mvn verify install -DskipTests`. Alternative: `python3 tools/god-class-guard/parse_metrics.py <file>` (auf main verfügbar) liefert effektive LOC/PubM/Fields ohne raw-grep-Lärm.

### Metriken via `grep` (auf branch feature/m1-warehouse-market)

| Metrik | Wert | Quelle |
|---|---|---|
| **LOC raw** | 1902 | `wc -l` |
| **Public Methods (PMC)** | 44 | `grep -cE '^\s*public\s+[A-Za-z]'` |
| **`MarketSharedState.java`** | 51 LOC neu erstellt | `wc -l` (Pfad `src/vannon/syx/economy/core/warehouse/market/MarketSharedState.java`) |
| **`this.sharedState.X` Zugriffe** | 17 | nach T-101 sed-Rename |
| **`this.X` Legacy-Zugriffe (NICHT umbenannt) Total** | **89** | siehe Feld-Aufschlüsselung unten |
| **`IdentityMapRegistry.register` (verbleibend)** | 0 | T-101 hat sie alle in MarketSharedState-Konstruktor verlegt |
| **Inner Records (`public static final class`)** | 8 Records | Book, DirectClaim, RetailBook, RetailLot, 4x Pending* |
| **Transient Records (`public static final class`)** | 3 Records + CrownStorage/Purchase/etc. | T-102 (WholesaleEngine)-Responsibility |

### `this.X` Legacy-Zugriffe pro Feld (Genaue Aufschlüsselung, B-001 Source-Daten)

| Feld | `this.X` Aufrufe | Status |
|---|---|---|
| `this.books` | 13 | offen |
| `this.retailBooks` | 7 | offen |
| `this.intakeLocks` | 7 | offen |
| `this.directClaims` | 10 | offen |
| `this.crownUnits` | 30 | offen (Haupt-Brocken!) |
| `this.abandonedUnits` | 16 | offen |
| `this.inferCrownFromLoose` | 6 | offen |
| `this.pending` | 0 | [x] umbenannt |
| `this.pendingIntakeLocks` | 0 | [x] umbenannt |
| `this.pendingDirectClaims` | 0 | [x] umbenannt |
| `this.pendingRetailBooks` | 0 | [x] umbenannt |
| **TOTAL** | **89** | **89 offen, 0 done** |

> **Klare Wahrheit:** T-101 Phase 1 ist nicht "fertig" sondern halbfertig. Felder sind nach `MarketSharedState` umgezogen, **89 von 89** Field-Aufrufen zeigen noch auf das alte `this.X`-Pattern. **Phase 1 abschließen = 89 sed-Renames + Compile-Verifikation.**

### Drift-Schwellen (aus `tools/god-class-baselines.yml`)

| Datei | Baseline-LOC | Drift-Cap % | Hard-Block ab |
|---|---|---|---|
| `WarehouseMarket.java` (Tier-1 grandfathered) | 1785 | ±5% | >850 |

**Post-T-101 Soll-LOC:** ~150 (reine Facade) — aktuell 1902 = **1752 LOC Reduktions-Bedarf**.

**Verbleibender Hebel:** Nach T-101..T-108 splitten die 44 public-Methods + ~20 private helpers raus. Hauptarbeit ist T-102 (Wholesale ~480 LoC) bis T-106 (Maintenance ~190 LoC).

---

## Block 2 — Extraction-Map (deterministisch, kein Prosa)

Status-Spalte: `[ ]` offen, `[~]` in-progress (T-101), `[x]` extracted, `[!]` blocker.

### 2.1 Felder (Daten-Holder)

| Feld | Typ | Ziel | Status |
|---|---|---|---|
| `crownUnits` | `long[]` | MarketSharedState | [x] |
| `abandonedUnits` | `long[]` | MarketSharedState | [x] |
| `inferCrownFromLoose` | `boolean` | MarketSharedState (Config-Flag) | [x] Thinker rec 5 fraglich |
| `books` | `IdentityHashMap<StockpileInstance, Book[]>` | MarketSharedState | [x] |
| `retailBooks` | `IdentityHashMap<RoomInstance, RetailBook[]>` | MarketSharedState | [x] |
| `intakeLocks` | `IdentityHashMap<StockpileInstance, Map<Integer, Integer>>` | MarketSharedState | [x] |
| `directClaims` | `HashMap<Integer, ArrayList<DirectClaim>>` | MarketSharedState | [x] |
| `pending` | `ArrayList<PendingBook>` | MarketSharedState | [x] |
| `pendingIntakeLocks` | `ArrayList<PendingIntakeLock>` | MarketSharedState | [x] |
| `pendingDirectClaims` | `ArrayList<PendingDirectClaim>` | MarketSharedState | [x] |
| `pendingRetailBooks` | `ArrayList<PendingRetailBook>` | MarketSharedState | [x] |
| `lastBought/Sold/...` | tracks | Engine-Stats (bleibt T-102..T-108) | [ ] T-102..T-108 |

### 2.2 Public Methods (Public-API, 44 Total)

| Methode | Ziel-Klasse (Engine) | Status |
|---|---|---|
| `<init>(StateWarehouses, FlowPrices)` | WarehouseMarket (Facade) | [~] Phase 1 fertig |
| `buy(FlowMeter, FlowPrices, Roster, Wallets, FirmLedger)` | T-102 WholesaleEngine | [ ] |
| `sellInputs(FlowMeter, FlowPrices, Roster, Wallets, FirmLedger)` | T-102 WholesaleEngine | [ ] |
| `recordProducerlessOutput(FlowMeter)` | T-103 CrownTitleEngine | [ ] |
| `crownUnits(RESOURCE)` | T-103 CrownTitleEngine | [ ] |
| `ensureCrownCapacity()` (private) | T-103 CrownTitleEngine | [ ] |
| `buyCheaperCrownGoods(Roster, Wallets)` | T-103 CrownTitleEngine | [ ] |
| `buyRemainingCrownGoods(Roster, Wallets)` | T-103 CrownTitleEngine | [ ] |
| `buyStoredCrownGoods(Roster, Wallets, boolean)` (private) | T-103 CrownTitleEngine | [ ] |
| `purchaseCrown(StockpileInstance, RESOURCE, int, int, Roster, Wallets)` (private) | T-103 CrownTitleEngine | [ ] |
| `buyOutput(FlowMeter.FirmSnapshot, RESOURCE, int, int, Roster, Wallets, FirmLedger)` (private) | T-102 WholesaleEngine | [ ] |
| `stateBuy(FlowMeter.FirmSnapshot, RESOURCE, int, int, Roster, Wallets, FirmLedger)` (private) | T-102 WholesaleEngine | [ ] |
| `purchase(StockpileInstance, FlowMeter.FirmSnapshot, RESOURCE, int, int, Roster, Wallets, FirmLedger)` (private) | T-102 WholesaleEngine | [ ] |
| `settleSeizures(Roster, Wallets)` | T-106 MarketMaintenanceEngine | [ ] |
| `marketTitledUnits(RESOURCE, int, int)` (private) | T-103 CrownTitleEngine | [ ] |
| `directClaimUnits(int)` (private) | T-103 CrownTitleEngine | [ ] |
| `consumeCrownTitle(int[])` (private overload 1) | T-103 CrownTitleEngine | [ ] |
| `consumeCrownTitle(RESOURCE, int)` (private overload 2) | T-103 CrownTitleEngine | [ ] |
| `observeRetailDeliveries()` | T-104 RetailSyncEngine | [ ] |
| `retailWholesaleQuote(RoomInstance, int[])` | T-104 RetailSyncEngine | [ ] |
| `syncRetail(RoomInstance, int[])` (private) | T-104 RetailSyncEngine | [ ] |
| `waiveOwnerlessRetailClaims(int[], int[])` | T-103 CrownTitleEngine (oder T-104 RetailSyncEngine, entscheidung ❶) | [!] |
| `distributeSale(int[], int, Roster, Wallets, FirmLedger)` | T-102 WholesaleEngine | [ ] |
| `distributeSaleDetailed(int[], int, Roster, Wallets, FirmLedger, boolean, boolean)` (private) | T-102 WholesaleEngine | [ ] |
| `distributeToMerchants(int[], int[], int, Roster, Wallets, FirmLedger)` (private) | T-102 WholesaleEngine | [ ] |
| `distributeToDirectClaimants(int[], int, Roster, Wallets, FirmLedger)` (private) | T-102 WholesaleEngine | [ ] |
| `recordDirectClaim(RoomInstance, RESOURCE, int)` (private) | T-102 WholesaleEngine | [ ] |
| `settle(Book, int, int, Roster, Wallets)` (private) | T-102 WholesaleEngine | [ ] |
| `observeConstructionWithdrawals()` | T-105 AutoProcurementEngine | [ ] |
| `buyConstructionMaterials(int[], int[], Roster, Wallets, FirmLedger)` | T-105 AutoProcurementEngine | [ ] |
| `consumeStateConstructionTitle(RESOURCE, int)` (private) | T-105 AutoProcurementEngine | [ ] |
| `observeExportWithdrawals()` | T-105 AutoProcurementEngine | [ ] |
| `buyExports(int[], Roster, Wallets, FirmLedger)` | T-105 AutoProcurementEngine | [ ] |
| `taxInventory(Roster, Wallets, FirmLedger)` | T-107 MarketTaxEngine | [ ] |
| `inventoryValue(StockpileInstance)` (private) | T-107 MarketTaxEngine | [ ] |
| `stateStock(RESOURCE)` | Read-Through-Facade (kein Engine) | [ ] |
| `dealers(RESOURCE, boolean, Roster)` (private) | T-102 WholesaleEngine | [ ] |
| `prune(Roster)` | T-106 MarketMaintenanceEngine | [ ] |
| `reconcileOwners(Book, ArrayList<Humanoid>)` (private static) | T-106 MarketMaintenanceEngine | [ ] |
| `lockIntake(StockpileInstance)` (private) | T-106 MarketMaintenanceEngine | [ ] |
| `unlockIntake(StockpileInstance)` (private) | T-106 MarketMaintenanceEngine | [ ] |
| `save(FilePutter)` | WarehouseMarket (Facade, Saveable impl) | [ ] T-108 Save-V8-Bump |
| `load(FileGetter)` | WarehouseMarket (Facade, Saveable impl) | [ ] T-108 Save-V8-Bump + V7-Fallback |
| `clear()` | WarehouseMarket (Facade) | [ ] |
| `beginTick()` | WarehouseMarket (Facade) | [ ] |
| `beginPurchases()` | T-106 MarketMaintenanceEngine | [ ] |
| `lastBought()/.../lastTaxPayers()` (Read-Accessors) | WarehouseMarket (Facade) | [ ] |
| `lastConstructionPaid()/lastExportBought()` (Read-Accessors) | T-105 AutoProcurementEngine | [ ] |
| `crownUnits(RESOURCE)` (Read-Accessor) | T-103 CrownTitleEngine | [ ] |
| `inferCrownFromLoose()` Method (private) | T-106 MarketMaintenanceEngine | [ ] |
| `resolvePending()` (private) | T-106 MarketMaintenanceEngine | [ ] |

### 2.3 Records (Klassen-Zuordnung nach T-101 RFC + Thinker-Rec 1)

| Record | Sichtbarkeit aktuell | Ziel-Position | Status |
|---|---|---|---|
| `Book` | `public static final` | MarketSharedState.nested (T-101 Phase 2 oder T-102) | [~] public gemarkt, noch in WarehouseMarket |
| `DirectClaim` | `public static final` | MarketSharedState.nested (T-101) | [~] public gemarkt |
| `RetailBook` | `public static final` | T-104 RetailSyncEngine.nested | [~] public gemarkt |
| `RetailLot` | `public static final` | T-104 RetailSyncEngine.nested | [~] public gemarkt |
| `PendingBook` | `public static final` | T-106 Maintenance.nested | [~] public gemarkt |
| `PendingIntakeLock/DirectClaim/RetailBook` | `public static final` | T-106 Maintenance.nested | [~] public gemarkt |
| `CrownStorage, Purchase, DirectSale, WarehouseHolding` | `public static final` | T-102 WholesaleEngine.nested | [ ] T-102 Phase 2 |
| `Settlement, RetailQuote, OwnerlessRetailClaims` | `public static final` | T-103/T-104.nested | [ ] Phase 2 |
| `Merchant/DirectDistribution, SaleDistribution` | `public static final` | T-102.nested | [ ] Phase 2 |

---

## Block 3 — Handoff-Blocker (warum 2h ohne LOC-Reduktion)

### B-001: Sed-Rename unvollstaendig (HIGH) — 89 von 89 `this.X` Aufrufen offen

**Korrigiert nach re-verifikation:** Mein T-101 sed-Pattern matchte `this\.books\b` etc. — Resultat nur Teil-Renames. Per-feld-Breakdown:

| Pattern | Erwartet | Tatsächlich nach sed | Verbleibend |
|---|---|---|---|
| `this.books` | 0 | 13 | **13 offen** |
| `this.retailBooks` | 0 | 7 | **7 offen** |
| `this.intakeLocks` | 0 | 7 | **7 offen** |
| `this.directClaims` | 0 | 10 | **10 offen** |
| `this.crownUnits` | 0 | 30 | **30 offen** |
| `this.abandonedUnits` | 0 | 16 | **16 offen** |
| `this.inferCrownFromLoose` | 0 | 6 | **6 offen** |
| `this.pending*` | 0 | 0 | **[x] done** |

**Pattern-Coverage-Loesung (3 Wege):**

**Pattern-Coverage-Loesung (3 Wege):**

1. **Paecz-Whole-`\bbooks\b`-Replace** (global): Risiko lokal-Variablen-Kollisionen (`int books = 5`). Verifikation danach via grep zwingend.
2. **Access-Wrapper in MarketSharedState** (Sicher): `sharedState.X()`-Getter statt Field-Rename. Etwas mehr LoC, aber typisiert.
3. **Hybrid**: Erst alle `^\s*\bbooks\b` zu `this.sharedState.books\b` via sed (mit Whitelist-Check danach). Pragmatisch.

### B-002: Records-Visibility-API-Leak (MEDIUM)

T-101 hat 8 Records auf `public static final` gehoben, weil MarketSharedState sie sonst nicht referenzieren kann (cross-package). Saubere Loesung waere Move in MarketSharedState in Phase 2, aber dann alle 87 Field-Renames neu (Book statt WarehouseMarket.Book). **Entscheidung ❶ noetig:** Records-Move in MarketSharedState ODER Package-private via Reflection-Trick.

### B-003: inferCrownFromLoose Placement-Frage (MEDIUM)

Diese Bool-Variable ist Config-Flag, kein Runtime-State (sie wird in `prune()` einmal gelesen). Eigentlich sollte sie in T-103 Engine oder T-106 Maintenance-Engine. Aktuell liegt sie in MarketSharedState, was semantisch nicht ganz passt.

**Entscheidung ❷ noetig:** Belassen in MarketSharedState (Phase 1 fertig) ODER Verschieben in CrownTitleEngine wenn T-103 startet.

### B-004: 44 Public-Methods sind Mehrheit Engine-Logic (HIGH fuer Sprint-Erfolg)

Phase 1 hat nur die **Felder** extrahiert, **null von 44 Methoden**. Die Methoden-Extract-Phase ist der eigentliche Sprint-Inhalt (T-102..T-108). Bei 48 Std bleibt das gleiche Problem wenn naechste Session wieder bei Phase 1 (Field-Extract) anfaengt statt direkt Engines zu bauen.

**Aktion:** Branch auf neuem `main`-Stand rebasen (post-merge), dann direkt Phase 2 (`WholesaleEngine`) starten mit sed-und-Method-Move Pattern.

### B-005: save/load/clear V7→V8 ist nicht-trivial (HIGH, Sprint-Ende)

T-108 hat Save-Migration V7→V8. Das betrifft:
- `WarehouseMarket.FORMAT` 7→8
- `load(FileGetter)` mit Branch `if (version == 7)` fuer Legacy
- Reihenfolge der `IdentityMapRegistry.register`-Calls kritisch mit V7-Chunk-Parsing
- Tests (`WarehouseMarketIsolationTest`) migrieren mit

**Startzeitpunkt:** Sprint-Ende (Phase 8), nicht frueh starten.

### B-006: God-Class-Guard Hard-Mode zahlt erst nach `mvn verify` auf Haupt-Branch (MEDIUM)

Aktuell kein `bash tools/god-class-guard.sh --mode=dry --json` auf feature/m1-warehouse-market moeglich, weil God-Class-Guard-Tool nur auf main liegt (nach M-3 Merge). Rebase von feature/m1-warehouse-market auf neuen main ist Vorbedingung fuer Tool-Nutzung.

**Pre-Bedingung fuer Phase 2:** `git rebase main` auf feature/m1-warehouse-market.

---

## Konkrete Naechste-Aktion-Liste (Reihenfolge ist Pflicht)

```
Phase-Pre:  git checkout feature/m1-warehouse-market
            git rebase main                              ← B-006, sonst Guard-Tools fehlen
            git stash pop                               ← holt T-101 Phase 1 zurueck
            mvn verify install -DskipTests -Dskip.bump=true
                                                            ← Sanity-Check dass nichts kaputt ist

Phase-2a:   sed-Rest-Fix fuer 87 direkten Field-Zugriffe  (B-001)
            Whitelist-Check (kein Var/Param-Namens-Kollision)
            mvn verify                                   ← Compile-Gate

Phase-2b:   Code-Reviewer-Pass                          (HIGH #1 + B-001 Renames verifiziert)

Phase-2c:   Entscheidung ❶ Records-Move ODER Package-private (B-002)
            Entscheidung ❷ inferCrownFromLoose Placement (B-003)

Phase-2d:   Sprint-Phase-2 Start: WholesaleEngine (T-102) — ~480 LOC Ziel-Reduktion
            Method-Extract mit sed-Pattern UND manuelle Validierung
            mvn verify nach jeder Sub-Methode
```

---

## Annahmen + Constraints

- Diese Session hat M-3 (God-Class-Guard) nach `main` gemerged — Sprint-Tooling ist jetzt kanonisch. Alle Sprint-M-1-Phasen koennen mit dem Guard arbeiten.
- Sprint-End-Commit per agents.md Rule 12: EIN atomic Commit auf `feature/m1-warehouse-market` am Sprint-Ende. KEIN Push bis Sprint done.
- ROADMAP.md hat aktuell `§Sprint M-Series — Retro-God-Class-Sanierung` mit B-102 Epic + T-110..T-113 fuer Sprint M-4 — diese IDs sind Future-Sprints, M-1 referenziert B-101 + T-101..T-108.
- Naming-Konvention: `feature/<sprint-name>` aus `main`, kein `--no-ff M-Series`-Branding pro Phase.

---

## Reproducibility

Diese Spec exakt reproduzieren via:

```bash
cd /home/vannon/Schreibtisch/SyxEconomyMod_Workspace
git checkout feature/m1-warehouse-market
git stash pop
wc -l src/vannon/syx/economy/core/WarehouseMarket.java
grep -cE '^[[:space:]]*public[[:space:]]+[A-Za-z]' src/vannon/syx/economy/core/WarehouseMarket.java
grep -cE 'this\.[[:space:]]*sharedState' src/vannon/syx/economy/core/WarehouseMarket.java
grep -cE 'this\.[[:space:]]*(books|retailBooks|intakeLocks|directClaims|pending|pendingIntakeLocks|pendingDirectClaims|pendingRetailBooks|crownUnits|abandonedUnits|inferCrownFromLoose)\b' src/vannon/syx/economy/core/WarehouseMarket.java
```
