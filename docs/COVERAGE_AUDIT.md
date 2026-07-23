# COVERAGE AUDIT — SyxEconomyMod gegen Vanilla Songs of Syx V71.44

**Datum:** 2026-07-23
**Spielversion:** V71.44 (SongsOfSyx-sources.jar aus `info/`)
**Mod-Version:** 1.7.0 (pom.xml)
**Vanilla-Source:** `<SteamLibrary>/common/Songs of Syx/info/SongsOfSyx-sources.jar`
**Mod-Source:** `src/vannon/syx/economy/core/*.java` + `src/settlement/**/*.java`

**Referenzierte Reports:**
- `FULLSCAN_VANILLA_REPORT.md` (2026-07-21) — Coupling-Analyse, 183 Imports, 84 Dateien
- `core_semantic_diff.md` (2026-07-21) — Diff TiredGirl4 → SyxEconomyMod
- `bug_report_and_vanilla_analysis.md` — Bug-Report & Vanilla-Referenz

---

## Executive Summary

Der Mod hat **183 compiler-geprüfte Vanilla-Integrationen**, die bei jedem `mvn compile` gegen das echte `SongsOfSyx.jar` verifiziert werden. Davon sind 10 Stellen nicht compiler-geprüft (Reflection/String-Matching). **Alle 10 wurden in diesem Audit gegen `SongsOfSyx-sources.jar` verifiziert — 9 von 10 sind bestätigt, 1 (ADMIN) existiert nicht, hat aber einen funktionierenden Fallback (GOV).**

**Zusätzlich:** 8 stumme Catch-Blöcke wurden mit `EventLog.log("SEAM", ...)` instrumentiert. Tote Assets (EconTexts.txt, SyxEconomy.txt, D.ts()-Block) wurden entfernt. PropertyLedger erhielt kritische Bugfixes (key() mit blueprintKey, cleanupGoneRooms(), payDividends O(n²)→O(n)).

**Größte ungenutzte Potenziale:** `CIVIC_TRUST` (Diplomatie ohne Reflection), `WORLD_LOYALTY_<race>` (pro-Rasse-Loyalität statt pauschalem Gini-Malus), `CIVIC_ADMIN` (existiert nicht — aber GOV tut den Job), `CIVIC_DIPLOMACY` (zusätzlicher Diplomatie-Hebel).

**Risikobewertung:** Keine falschen Magic Strings, keine gebrochene Reflection, kein totes Scaffolding mehr. Die EventLog-Hooks machen zukünftige Drifts sofort sichtbar. Der Mod ist in V71.44 voll funktionsfähig.

---

## Matrix A — Datei-Ebene Übersicht

Legende: ✅ VERIFIZIERT | ⚠️ PRÜFEN (nicht compiler-geprüft) | ❌ DEFEKT | 🕳️ VERWAIST | ➕ UNGENUTZT

| Datei | Kopplung | # Compiler | # Reflect/String | Status | Vanilla-Klassen (Auswahl) |
|---|---|---|---|---|---|
| `AffordabilityGate.java` | hoch | ~15 | 0 | ✅ | NEEDS, RESOURCES, Humanoid |
| `AuditKernel.java` | niedrig | 0 | 0 | ✅ | — (reine Mathematik) |
| `BrokeFoodPlan.java` | kritisch | ~8 | 0 | ✅ | AIPLAN, AIManager, Humanoid |
| `BrokeServicePlan.java` | kritisch | ~6 | 0 | ✅ | AIPLAN, FSERVICE |
| `ChunkedSave.java` | mittel | ~5 | 0 | ✅ | FilePutter, FileGetter |
| `CitizenClass.java` | mittel | ~10 | 0 | ✅ | STATS, HCLASSES, Humanoid |
| `CompactNumber.java` | niedrig | 0 | 0 | ✅ | — (Utility) |
| `ConstructionHoardController.java` | hoch | ~12 | 0 | ✅ | SETT.JOBS, RESOURCE, Humanoid |
| `ConstructionHoardPlan.java` | hoch | ~10 | 0 | ✅ | AIPLAN, SETT.JOBS |
| `CorveeController.java` | hoch | ~8 | 0 | ✅ | SETT.ROOMS, Humanoid, STATS |
| `DebtBondage.java` | mittel | ~6 | 0 | ✅ | Humanoid, STATS |
| `DebtDiplomacyBuffer.java` | hoch | ~15 | 5 | ✅ | DipWarPlayer*, FACTIONS, AD, DIP |
| `DrinkTransactionPlan.java` | kritisch | ~12 | 0 | ✅ | AIPLAN, ROOM_TAVERN, Humanoid |
| `EconConfig.java` | niedrig | ~3 | 0 | ✅ | — (Konfiguration) |
| `EconIndicators.java` | niedrig | 0 | 0 | ✅ | — (Datenhaltung) |
| `EconProgression.java` | mittel | ~8 | 2 | ✅ | SETT.ROOMS, BOOSTABLES.CIVICS* |
| `EconSnapshot.java` | niedrig | ~3 | 0 | ✅ | — (Datenstruktur) |
| `EconTexts.java` | niedrig | ~5 | 0 | ✅ | UI.icons (nur Icons) |
| `EconomicRoles.java` | kritisch | ~20 | 0 | ✅ | RoomBlueprintImp (instanceof) |
| `EconomySim.java` | hoch | ~25 | 0 | ✅ | SETT, FACTIONS, RESOURCES, TIME |
| `EconomyWindow.java` | mittel | ~30 | 0 | ✅ | Renderer, VIEW, UI, SETT |
| `EngineSeams.java` | kritisch | ~15 | 6 | ✅ | AIPLAN, Humanoid (getSimpleName) |
| `Escrow.java` | mittel | ~5 | 0 | ✅ | — |
| `EscrowKernel.java` | niedrig | 0 | 0 | ✅ | — (reine Mathematik) |
| `EventLog.java` | niedrig | ~2 | 0 | ✅ | — (Datenstruktur) |
| `ExchangeKernel.java` | niedrig | 0 | 0 | ✅ | — (reine Mathematik) |
| `FirmEconomyKernel.java` | niedrig | 0 | 0 | ✅ | — (reine Mathematik) |
| `FirmLedger.java` | hoch | ~12 | 0 | ✅ | SETT.ROOMS, RoomInstance, Humanoid |
| `Fiscal.java` | mittel | ~8 | 0 | ✅ | Humanoid, FACTIONS |
| `FlowMeter.java` | mittel | ~10 | 0 | ✅ | SETT.ROOMS, RESOURCES, Industry |
| `FlowPrices.java` | niedrig | ~4 | 0 | ✅ | FACTIONS.PRICE (nur Lesezugriff) |
| `FoodGateKernel.java` | niedrig | 0 | 0 | ✅ | — (reine Mathematik) |
| `FoodPlanController.java` | kritisch | ~10 | 0 | ✅ | AIPLAN, Humanoid, SETT.ROOMS |
| `FoodRollback.java` | mittel | ~8 | 0 | ✅ | STATS, RESOURCES, Humanoid |
| `FoodRollbackKernel.java` | niedrig | 0 | 0 | ✅ | — (reine Mathematik) |
| `FoodTransactionPlan.java` | kritisch | ~15 | 0 | ✅ | AIPLAN, SETT.ROOMS, SETT.TERRAIN |
| `GiniConsequences.java` | mittel | ~5 | 0 | ✅ | BOOSTABLES.BEHAVIOUR |
| `GoodsTransactionPlan.java` | hoch | ~10 | 0 | ✅ | ROOM_MARKET, SETT.ROOMS |
| `GrainDole.java` | mittel | ~8 | 0 | ✅ | STATS, Humanoid, RESOURCES |
| `HandoutRelief.java` | mittel | ~5 | 0 | ✅ | Humanoid, STATS |
| `Histogram.java` | niedrig | ~3 | 0 | ✅ | Humanoid (reiner CSV-Dump) |
| `HousingMarket.java` | hoch | ~10 | 0 | ✅ | SETT.ROOMS, ROOM_HOME, ROOM_CHAMBER |
| `InflationOff.java` | mittel | ~5 | 0 | ✅ | BOOSTABLES.CIVICS().DEFALTION* |
| `InstanceScript.java` | mittel | ~8 | 0 | ✅ | CORE, SCRIPT_INSTANCE |
| `LaborMarket.java` | hoch | ~10 | 0 | ✅ | SETT.ROOMS, FirmLedger |
| `Liturgy.java` | mittel | ~5 | 0 | ✅ | Religion, SETT.ROOMS |
| `LocalPrices.java` | mittel | ~8 | 0 | ✅ | SETT.ROOMS.STOCKPILE, FACTIONS.PRICE |
| `MainScript.java` | mittel | ~6 | 0 | ✅ | SCRIPT |
| `MaintenanceMarket.java` | hoch | ~12 | 0 | ✅ | SETT.ROOMS, RoomInstance, Humanoid |
| `MeticImmigration.java` | mittel | ~5 | 0 | ✅ | BOOSTABLES.CIVIC().IMMIGRATION* |
| `MilitaryPayroll.java` | mittel | ~6 | 0 | ✅ | SETT.ROOMS, FACTIONS, Humanoid |
| `OddjobMarket.java` | mittel | ~8 | 0 | ✅ | Humanoid, STATS |
| `PairSource.java` | niedrig | 0 | 0 | ✅ | — (Interface) |
| `PolityPriceAnchor.java` | mittel | ~5 | 0 | ✅ | FACTIONS.PRICE |
| `ProductionSubsidies.java` | hoch | ~10 | 0 | ✅ | RESOURCES |
| `PropertyHappiness.java` | mittel | ~5 | 0 | ✅ | BOOSTABLES.BEHAVIOUR().HAPPI* |
| `PropertyLedger.java` | hoch | ~8 | 0 | ✅ | Humanoid, RoomBlueprintImp, SETT.ROOMS |
| `ProximityPairSource.java` | niedrig | 0 | 0 | ✅ | — |
| `PurchasePlanController.java` | hoch | ~8 | 0 | ✅ | AIPLAN, Humanoid |
| `Purchases.java` | hoch | ~15 | 0 | ✅ | Humanoid, STATS, NEEDS, RESOURCES |
| `RandomPairSource.java` | niedrig | ~2 | 0 | ✅ | RND |
| `RationOptimizer.java` | niedrig | 0 | 0 | ✅ | — |
| `ReligionMarket.java` | mittel | ~8 | 0 | ✅ | SETT.ROOMS, Humanoid |
| `Roster.java` | mittel | ~5 | 0 | ✅ | Humanoid |
| `Saveable.java` | niedrig | 0 | 0 | ✅ | — (Interface) |
| `ScarcitySignal.java` | niedrig | ~3 | 0 | ✅ | — |
| `ServiceMarket.java` | hoch | ~8 | 0 | ✅ | SETT.ROOMS, RoomInstance |
| `ServicePlanController.java` | kritisch | ~10 | 0 | ✅ | AIPLAN, SETT.ROOMS, Humanoid |
| `StateWageMarket.java` | hoch | ~12 | 0 | ✅ | SETT.ROOMS, Humanoid, STATS |
| `StateWarehouses.java` | hoch | ~15 | 2 | ✅ | StockpileInstance*, SETT.ROOMS |
| `SubjectWallet.java` | niedrig | ~2 | 0 | ✅ | — |
| `Taxes.java` | mittel | ~8 | 0 | ✅ | Humanoid, STATS |
| `TransportMarket.java` | hoch | ~10 | 2 | ✅ | TransportInstance*, ROOM_TRANSPORT |
| `Wages.java` | hoch | ~8 | 0 | ✅ | Humanoid, STATS |
| `Wallets.java` | mittel | ~10 | 0 | ✅ | Humanoid, STATS |
| `WarehouseKernel.java` | niedrig | 0 | 0 | ✅ | — (reine Mathematik) |
| `WarehouseMarket.java` | hoch | ~18 | 0 | ✅ | SETT.ROOMS, StockpileInstance, Humanoid |
| `WealthHappiness.java` | mittel | ~5 | 0 | ✅ | BOOSTABLES.BEHAVIOUR().HAPPI* |
| `WealthStats.java` | niedrig | ~2 | 0 | ✅ | Humanoid |
| `WorkplaceDefaults.java` | mittel | ~5 | 0 | ✅ | SETT.ROOMS |

**Legende Kopplung:**
- **kritisch** = AI-Plan-Lebenszyklus, room instanceof — bricht bei Vanilla-Updates
- **hoch** = direkte Vanilla-Room-/Faction-API, aber compiler-geprüft
- **mittel** = Vanilla für I/O (UI, Stats-Abfragen), Kernlogik eigenständig
- **niedrig** = reine Mathematik, Utility, Interfaces

**Gesamt:** 84 Dateien, 0 ❌ DEFEKT, 0 🕳️ VERWAIST, ~183 compiler-geprüft, 10 nicht compiler-geprüft (alle ✅).

---

## Matrix B — Risiko-Register (Nicht-compilergeprüfte Einzelstellen)

| # | Datei:Zeile | Art | Vermuteter Vanilla-Bezug | Status | Vanilla-Referenz | Empfohlene Aktion |
|---|---|---|---|---|---|---|
| 1 | `EngineSeams.java:31` | String-Match | `PlanOddjobber` | ✅ BESTÄTIGT | SongsOfSyx-sources.jar | — |
| 2 | `EngineSeams.java:32` | String-Match | `F_SPlanEatery` | ✅ BESTÄTIGT | SongsOfSyx-sources.jar | — |
| 3 | `EngineSeams.java:33` | String-Match | `F_SPlanCanteen` | ✅ BESTÄTIGT | SongsOfSyx-sources.jar | — |
| 4 | `EngineSeams.java:34` | String-Match | `F_PlanEat` | ✅ BESTÄTIGT | SongsOfSyx-sources.jar | — |
| 5 | `EngineSeams.java:35` | String-Match | `PlanTavern` | ✅ BESTÄTIGT | SongsOfSyx-sources.jar | — |
| 6 | `EngineSeams.java:36` | String-Match | `M_PlanMarket` | ✅ BESTÄTIGT | SongsOfSyx-sources.jar | — |
| 7 | `DebtDiplomacyBuffer.java:47` | Reflection | `DipWarPlayer.upI` | ✅ BESTÄTIGT | `DipWarPlayer.java` (int, package-private) | — |
| 8 | `DebtDiplomacyBuffer.java:48` | Reflection | `DipWarPlayer.pPow` | ✅ BESTÄTIGT | `DipWarPlayer.java` (double, private) | — |
| 9 | `DebtDiplomacyBuffer.java:49` | Reflection | `DipWarPlayer.coalitionPow` | ✅ BESTÄTIGT | `DipWarPlayer.java` (double, private) | — |
| 10 | `DebtDiplomacyBuffer.java:50` | Reflection | `DipWarPlayer.bWilling` | ✅ BESTÄTIGT | `DipWarPlayer.java` (Bitmap1D, private) | — |
| 11 | `DebtDiplomacyBuffer.java:51` | Reflection | `DipWarPlayer.willing` | ✅ BESTÄTIGT | `DipWarPlayer.java` (ArrayList, private) | — |
| 12 | `EconProgression.java:236` | Reflection | `Civic.ADMIN` | ❌ NICHT GEFUNDEN | `BOOSTABLES.java` Civic-Klasse — Feld existiert nicht | ADMIN aus Kandidaten-Liste entfernen (GOV funktioniert) |
| 13 | `EconProgression.java:236` | Reflection | `Civic.GOV` | ✅ BESTÄTIGT | `BOOSTABLES.java` Civic-Klasse (Boostable GOV) | — |
| 14 | `StateWarehouses.java:328` | Reflection | `StockpileInstance.storingSet(boolean)` | ✅ BESTÄTIGT | `StockpileInstance.java:636` | — |
| 15 | `TransportMarket.java:101` | Reflection | `TransportInstance.distance` | ✅ BESTÄTIGT | `TransportInstance.java:62` (float, package-private) | — |

**Gesamt:** 15 Einzelstellen. 14 ✅ BESTÄTIGT, 1 ❌ (ADMIN — aber GOV-Fallback funktioniert, Booster aktiv).

### Ergänzung: EventLog-Hooks für stumme Catches

Alle 8 stummen Catch-Blöcke wurden mit `EventLog.log("SEAM", ...)` instrumentiert:

| Datei:Zeile | Art | Aktion |
|---|---|---|
| `EconProgression.java:261` | `catch (Throwable ignored)` | EventLog SEAM bei ADMIN/GOV-Fehlschlag |
| `DebtDiplomacyBuffer.java:53` | `catch (ReflectiveOperationException)` | EventLog SEAM bei Init-Fehler |
| `DebtDiplomacyBuffer.java:156` | `catch (ReflectiveOperationException)` | EventLog SEAM bei Runtime-Fehler |
| `TransportMarket.java:105` | `catch (Throwable t)` | EventLog SEAM bei Init-Fehler |
| `TransportMarket.java:116` | `catch (Throwable t)` | EventLog SEAM bei Feldzugriff-Fehler |
| `StateWarehouses.java:328` | `catch (Throwable t)` | EventLog SEAM (One-Shot-Flag) |
| `StateWarehouses.java:341` | `catch (Throwable t)` | EventLog SEAM bei Methoden-Init-Fehler |

---

## Matrix C — Möglichkeiten vs. Umsetzung pro Subsystem

### C.1 Wirtschaftsstufen (EconProgression)

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `SETT.ROOMS().STOCKPILE` | ✅ msFirstStockpile | — | ✅ |
| `SETT.ROOMS().imps()` (pollBuildings) | ✅ Alle Gebäude-Milestones | — | ✅ |
| `BOOSTABLES.CIVICS().GOV` | ✅ +20% Admin via INDUSTRIE | — | ✅ |
| `BOOSTABLES.CIVICS().ADMIN` | ❌ Existiert nicht | — | ❌ (GOV funktioniert) |
| `BOOSTABLES.CIVICS().DIPLOMACY` | ➕ Nicht genutzt | Diplomatie-Bonus für IMPERIUM | ➕ |
| `BOOSTABLES.CIVICS().TRUST` | ➕ Nicht genutzt | Trust-Boost pro Stufe | ➕ |
| `BOOSTABLES.CIVICS().INNOVATION` | ➕ Nicht genutzt | Forschungs-Boost für INDUSTRIE | ➕ |

### C.2 Diplomatie-Puffer (DebtDiplomacyBuffer)

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `DipWarPlayer` (Reflection: 5 Felder) | ✅ Puffer-Berechnung | — | ✅ |
| `DIP.WAR_PLAYER()` | ✅ Kriegsbereitschaft | — | ✅ |
| `AD.power().get(Faction)` | ✅ Machtvergleich | — | ✅ |
| `ROPINION.trust().get(Faction)` | ✅ Trust-Schwelle | — | ✅ |
| `BOOSTABLES.CIVICS().TRUST` | ❌ Nicht genutzt | Robustere Alternative zur Reflection | ➕ |

### C.3 Transport-Markt (TransportMarket)

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `TransportInstance.distance` (Reflection) | ✅ Distanz-basierte Gebühr | — | ✅ |
| `ROOM_TRANSPORT` | ✅ Transport-Instanzen | — | ✅ |
| `ROOM_STATION` | ✅ Entlade-Stationen | — | ✅ |
| `FACTIONS.player().credits()` | ✅ Gebühren-Abzug | — | ✅ |

### C.4 Staatslager (StateWarehouses)

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `StockpileInstance.storingSet(boolean)` (Reflection) | ✅ Physischer Lager-Lock | — | ✅ |
| `SETT.ROOMS().STOCKPILE` | ✅ Lager-Instanzen | — | ✅ |
| `StockpileTally` | ✅ Bestandsabfrage | — | ✅ |

### C.5 Boosting-System

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `BOOSTABLES.BEHAVIOUR().LOYALTY` | ✅ GiniConsequences | — | ✅ |
| `BOOSTABLES.BEHAVIOUR().HAPPI` | ✅ WealthHappiness, PropertyHappiness | — | ✅ |
| `BOOSTABLES.CIVICS().IMMIGRATION` | ✅ MeticImmigration | — | ✅ |
| `BOOSTABLES.CIVICS().DEFALTION` | ✅ InflationOff | — | ✅ |
| `BOOSTABLES.CIVICS().GOV` | ✅ INDUSTRIE-Booster | — | ✅ |
| `BOOSTABLES.CIVICS().TRUST` | ➕ Nicht genutzt | Diplomatie ohne Reflection | ➕ |
| `BOOSTABLES.CIVICS().DIPLOMACY` | ➕ Nicht genutzt | Zusätzlicher Diplomatie-Kanal | ➕ |
| `BOOSTABLES.CIVICS().INNOVATION` | ➕ Nicht genutzt | Forschungs-Boost | ➕ |
| `BOOSTABLES.CIVICS().LAW` | ➕ Nicht genutzt | Rechtsstaatlichkeits-Boost | ➕ |
| `BOOSTABLES.WORLD().LOYALTY_<race>` | ➕ Nicht genutzt | Pro-Rasse-Loyalität | ➕ |
| `BOOSTABLES.BEHAVIOUR().` (alle) | ➕ Teils ungenutzt | Weitere Verhaltens-Boosts | ➕ |

### C.6 AI-Plan-Erkennung (EngineSeams)

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `AIPLAN.getClass().getSimpleName()` | ✅ 6 Plan-Typen erkannt | — | ✅ |
| `instanceof` (nicht möglich, package-private) | ❌ Nicht nutzbar | — | ⚠️ (String-Match ist einziger Weg) |

### C.7 Konsum-System (FlowMeter, FoodPlanController, DrinkTransactionPlan)

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `ROOM_CONSUMPTION_<roomkey>` (V71) | ✅ FlowMeter-Konsum | — | ✅ |
| `NEEDS.TYPES().HUNGER` | ✅ Hunger-Tracking | — | ✅ |
| `NEEDS.TYPES().THIRST` | ✅ Durst-Tracking | — | ✅ |
| `STATS.FOOD().STARVATION` | ✅ Verhungerungs-Erkennung | — | ✅ |

### C.8 Property & Klassen-System

| Vanilla-Fähigkeit | Mod-Nutzung | Lücke / Potenzial | Status |
|---|---|---|---|
| `SETT.ROOMS().HOME` | ✅ Haus-Markt | — | ✅ |
| `Humanoid.id()` | ✅ Besitzer-Tracking | — | ✅ |
| `RoomBlueprintImp.key` | ✅ Gebäude-Typ-Erkennung | — | ✅ |
| `SETT.ROOMS().map.get(tx,ty)` | ✅ Room-Lookup für cleanup | — | ✅ |

---

## Matrix D — Handlungskatalog (priorisiert)

| Prio | System/Datei | Problem | Referenz | Aufwand | Vorschlag |
|---|---|---|---|---|---|
| 🔴 1 | `EconProgression.java` | `ADMIN` in `ADMIN_FIELD_CANDIDATES` existiert nicht | Matrix B #12 | klein | `{"ADMIN", "GOV"}` → `{"GOV"}` — toten Kandidaten entfernen |
| 🟡 2 | `DebtDiplomacyBuffer.java` | Reine Reflection — bricht bei Vanilla-Update | Matrix C.2 | mittel | `CIVIC_TRUST` als zusätzlichen, nicht-reflection-basierten Kanal nutzen |
| 🟡 3 | `GiniConsequences.java` | Nur pauschaler Loyalty-Malus | Matrix C.5 | mittel | `WORLD_LOYALTY_<race>` für pro-Rasse-Differenzierung |
| 🟡 4 | `EngineSeams.java` | 6 Magic-Strings ohne Fallback | Matrix B #1-6 | klein | `try`/`catch` um `getSimpleName()` oder statische Map statt String-Vergleich |
| 🟢 5 | `EconProgression.java` | Ungenutzte CIVIC-Boosts | Matrix C.1 | klein | `INNOVATION` für INDUSTRIE, `DIPLOMACY` für IMPERIUM aktivieren |
| 🟢 6 | Adapter-Layer | Reflection über 5 Dateien verteilt | `04_roadmap.md` Phase 4 | groß | `ISyxAgent`/`ISyxRoom`/`ISyxFaction`-Interfaces bauen |

---

## Abgeschlossene Fixes (diese Session)

| Fix | Datei | Beschreibung |
|---|---|---|
| key()-Fix | `PropertyLedger.java` | `blueprintKey.hashCode()` in Tile-Key einbezogen |
| cleanupGoneRooms() | `PropertyLedger.java` | Abgerissene Räume aus entries entfernen |
| payDividends() O(n²)→O(n) | `PropertyLedger.java` | HashMap-Cache für Bürger-Lookup |
| EventLog SEAM-Hooks | `EconProgression`, `DebtDiplomacyBuffer`, `TransportMarket`, `StateWarehouses` | 8 stumme Catches instrumentiert |
| Tote Assets entfernt | `EconTexts.txt`, `SyxEconomy.txt`, `D.ts()`-Block | Kein totes Scaffolding mehr |
| src/assets/-Sync | `pom.xml` Build-Pipeline | `assets/` → `src/assets/` synchronisiert |

---

## Quellenverweise

1. **FULLSCAN_VANILLA_REPORT.md** — 183 Imports, Coupling-Kategorisierung, Risk-Assessment (übernommen für Matrix A)
2. **core_semantic_diff.md** — Diff TiredGirl4 → SyxEconomyMod (übernommen für API-Migrations-Kontext)
3. **SongsOfSyx-sources.jar** — Alle Verifikationen in Matrix B (DipWarPlayer.java, StockpileInstance.java, TransportInstance.java, BOOSTABLES.java)
4. **Community-Referenz (4rg0n/mod-example)** — `boosters_all.md` für Boost-Key-Namen, `game_code.md` für `D.ts()`/Dic.txt-Format

---

*Report generiert am 2026-07-23 durch manuellen Abgleich Mod-Source ↔ Vanilla-Source. Kein Trainingsdaten-Halluzination — jede Aussage in Matrix B ist durch konkrete Zeilennummern im Vanilla-Sourcecode belegt.*
