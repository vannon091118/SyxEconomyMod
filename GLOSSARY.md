# SyxEconomyMod — Klassen-Glossar

> **Version:** v0.13.8 | **Stand:** 2026-07-25
>
> Stam-Doku-Synchron-Anker: Die obenstehende Versions-Zeile MUSS identisch mit `pom.xml` `<version>` sein.
> Der Sync-Gate `tools/verify-doc-sync.sh` validiert dies vor jedem `mvn compile`.
>
> Strukturierte Übersicht der **128 Java-Dateien** des Mods.

---

## 🟦 Kategorie 1: VANILLA WRAPPER (21 Dateien)

### Bypass-SDK (`adapter/seam/`, 4 Dateien — Phase A)

- **BypassGate** — Zentraler Entry-Point für Private-Access-Bypasses. Hält MethodHandles.Lookup, typisierte Factories für FieldAccessor (IntField, DoubleField, FloatField, RefField<T>), MethodAccessor (VoidMethod, BooleanMethod) und ClassResolver. All-or-nothing: ein Feld-Fehler → isAvailable()=false.
- **FieldAccessor** — Typisierte Feld-Zugriffe mit VarHandle (primär, 3–6× Speedup) und Reflection-Fallback. Statische Varianten via getStatic()/setStatic().
- **MethodAccessor** — Typisierte Methoden-Zugriffe mit MethodHandle (primär) und Reflection-Fallback (invoke).
- **ClassResolver** — Class.forName mit Game-ClassLoader (Humanoid.class.getClassLoader()) für package-private Engine-Klassen. isInstance()-Helper.

### Adapter-Interfaces (5)

*Adapter und Brücken-Klassen — die einzigen Dateien, die Vanilla-Klassen direkt importieren oder per Reflection anfassen.*

### Adapter-Interfaces (5 Dateien in `src/vannon/syx/economy/adapter/`)

| Klasse | Was sie tut (zusammengefasst) |
|---|---|
| **`ISyxAI`** | Kapselt 6 `Class.forName(name, true, Humanoid.class.getClassLoader())`-Aufrufe zur Erkennung package-privater AI-Pläne (`PlanOddjobber`, `F_SPlanEatery`, `F_SPlanCanteen`, `F_PlanEat`, `PlanTavern`, `M_PlanMarket`). |
| **`ISyxTransport`** | Liest das private `distance`-Feld (float) von `TransportInstance`. |
| **`ISyxWarehouse`** | Ruft `storingSet(boolean)` auf `StockpileInstance` auf — sperrt/entsperrt Einlagerung. |
| **`ISyxBoosting`** | Sucht per `getDeclaredFields()` nach `GOV`-Boostable in `BOOSTABLES.CIVICS()` (für INDUSTRIE-Stufen-Admin-Boost). |
| **`ISyxDiplomacy`** | Liest/schreibt 4 Felder von `DipWarPlayer`: `upI`, `pPow`, `coalitionPow`, `bWilling`; `willing()` via Public-Getter. |

### Vanilla-Implementierungen (8 Dateien)

Reflection-Variante (5): `VanillaAIAdapter`, `VanillaTransportAdapter`, `VanillaWarehouseAdapter`, `VanillaBoostingAdapter`, `VanillaDiplomacyAdapter`.

MethodHandle-Variante (3, 3–6× schneller): `VanillaTransportAdapterMH`, `VanillaWarehouseAdapterMH`, `VanillaDiplomacyAdapterMH`.

### Fallback-Implementierungen (4 Dateien)

| Klasse | Bei Reflection-Scheitern |
|---|---|
| `FallbackTransportAdapter` | Distance = 0 |
| `FallbackWarehouseAdapter` | No-Op (Pricing-Lock übernimmt) |
| `FallbackBoostingAdapter` | kein Admin-Boost |
| `FallbackDiplomacyAdapter` | keine Diplomatie-Pufferung |

### Package-Private Brücken (4 Dateien in `src/settlement/room/`)

| Klasse | Vanilla-Package | Zweck |
|---|---|---|
| `LaborMarketAccess.java` | `settlement.room.main.employment` | Direktzugriff auf `RoomEmployment.Priority` |
| `EconomyTavernAccess.java` | `settlement.room.service.food.tavern` | Liest `TavernInstance`-Vorrat |
| `EconomyEateryAccess.java` | `settlement.room.service.food.eatery` | Liest `EateryInstance`-Vorrat |
| `EconomyCanteenAccess.java` | `settlement.room.service.food.canteen` | Liest `CanteenInstance`-Vorrat |

Diese Klassen liegen im selben Java-Package wie die Vanilla-Klassen → kein Reflection, Compiler prüft die Zugriffe.

---

## 🟩 Kategorie 2: SIMULATION (100 Dateien in `src/vannon/syx/economy/core/`)

### Orchestrator (3 Dateien)

| Klasse | LOC | Was sie tut |
|---|---:|---|
| **`EconomySim`** | 1.459 | Zentraler Tick-Orchestrator. Re-Entry-Guarded. 33 Chunked-Save-Subsysteme. Update-Pfad: Roster → Wallets → Firmen → Märkte → Steuern → Audit. |
| **`InstanceScript`** | 137 | Eintrittspunkt. Erstellt EconomySim + 4 Fenster + SubjectWallet + SubjectJob + EconHud. 6 Hotkeys mit Edge-Detection (`pollHotkeys`). |
| **`MainScript`** | ~80 | Registriert alle Booster beim Spielstart (WealthHappiness, InflationOff, MeticImmigration, GiniConsequences etc.). |

### Geld & Wallets (5 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`Wallets`** | HashMap `Humanoid → int`. Stage-gated: 200/500/2000/5000 D Seed. `add()`, `spend()`, `applyExchange()` (Yard-Sale), `spendable()`, `moneyOf()`. |
| **`SubjectWallet`** | Bürger-Klick-Popup — Vermögen, Einkommen, Klasse, Steuern. READ-ONLY. |
| **`WealthStats`** | Berechnet Gini-Koeffizient, Median, Top-10%-Anteil, Durchschnittsvermögen aus Wallets. |
| **`Fiscal`** | Staatliche Ausgaben-Engine. `settlePurchase`, `settleRation`, `settleMerchantRemainder`, `settleCrownWholesale`, `headTaxCollected`, `marketReceipts`, `rationOut`. |
| **`ExchangeKernel`** | Reine Hilfsfunktionen für `Wallets.applyExchange` (Yard-Sale). Kein State. |

### Preise & Ressourcen-Flüsse (7 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`FlowMeter`** | Liest Vanilla `FResources`. Pro Ressource: `producedPerDay`, `consumedPerDay`, `stockTotal`, `tradeBalance`. Snapshot für FirmLedger/Preise. |
| **`FlowPrices`** | `price(r) = anchor × (1 + max(0, 1−coverage)²)`. Coverage < 0.5 → Preis +. Coverage > 2.0 → Preis −. |
| **`LocalPrices`** | Liest Vanilla `BOOSTABLE_O` für lokale Marktpreise (Food/Drink). Zwei One-Shot SEAM-Logs. |
| **`PolityPriceAnchor`** | Handelsanker-Preis: „fairer" Reichsniveau-Preis aus `FactionNPC`. |
| **`ScarcitySignal`** | 0..1-Score: stockDelta < 0 → +; stock=0 + demand>0 → instant. |
| **`InflationOff`** | Registriert `BValue` auf `BOOSTABLES.CIVICS().DEFLATION` — Vanilla-Inflation aus. |
| **`Histogram`** | Vermögens-Verteilung-Dump. 10 Bins, Bürger-pro-Bin, kumulativ. |

### Firmen & Betriebe (8 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`FirmLedger`** | Hauptbuch + Analytics + Export-Source. Pro Firma: Input/Output, Profit, marginal (gecappt `wageMax=1000`), `workersUnpaid`. Cold-Start-Guard: `state.hill!=null`. |
| **`FirmEconomyKernel`** | `value(producedValue, inputCost)` = Output − Input. Kein State. |
| **`LaborMarket`** | Workforce-Allokation. `meanWage()` = echte Bürger-Löhne (nicht Grenzgewinn). `meanPositiveMarginal()`. |
| **`LaborMarketAccess`** | (siehe oben — Brücke) |
| **`StateWageMarket`** | `forceHire()` — staatliche Lohnunterbietung. carry: `HashMap<String,Double>` key=`blueprint.key`. |
| **`OddjobMarket`** | Bezahlt Tagelöhner (`oddjobWagePerTask`) aus Staatskasse; Cap nicht hart (`B-005` offen). |
| **`OddjobAutomation`** | Auto-Tuned Tagelöhner-Lohn: viele offene Firmenjobs → ↓, Knappheit → ↑. |
| **`RoomOperatingModeController`** | Pro-Room `opModes` + `effectiveOpModeCostScale`. (aus FirmLedger extrahiert) |

### Konsum & Ernährung (8 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`AffordabilityGate`** | Bürger essen lassen oder nicht. Default `freeRation()` für GrainDole/Sklaven/Waisen; sonst `escrow.canAfford()`. `foodAffordabilityGateEnabled=true` Default (`v0.1.3` Bugfix). |
| **`FoodPlanController`** | Sagt Bürger WIE essen — `FoodTransactionPlan` oder `BrokeFoodPlan`. |
| **`FoodTransactionPlan`** | Führt Essenskauf durch: Bürger-Geld → Staat, Ressource raus. |
| **`BrokeFoodPlan`** | Bürger kann nicht zahlen + nicht GrainDole-berechtigt → `STARVATION`-Log + Tod. |
| **`FoodGateKernel`** | `bill()`-Mathematik. Kein State. |
| **`FoodRollback`** | Bürger stirbt beim Kauf oder Plan wechselt → Rollback (Geld+Ressource zurück). |
| **`FoodRollbackKernel`** | `allocateCapped` + `allocateUnbounded` round-trip Korrektur. Kein State. |
| **`DrinkTransactionPlan`** | Analog FoodTransactionPlan für Tavernen-Drinks. |

### Services (4 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`ServiceMarket`** | Service-Kosten pro Service-Typ. Bürger-Substrat für `ServicePlanController`. |
| **`ServicePlanController`** | O(1) Service-Lookup via statischen Blueprint→Service-Cache (`serviceCache`). |
| **`BrokeServicePlan`** | Bürger kann nicht zahlen → keine Service-Aktion, kein Verhungern. |
| **`ReligionMarket`** | Tempel-Spenden → Staat (`religionTaxRate`). |

### Liturgie / Corvee / Oddjob / Wartung / Subventionen (5)

| Klasse | Was sie tut |
|---|---|
| **`Liturgy`** | Zeremonien-Gebühren von Bürgern an Staat. |
| **`CorveeController`** | `corveeDraftPercent` der Bevölkerung → Zwangsarbeit (Corvée). |
| **`MaintenanceMarket`** | Gebäude-Instandhaltung. Firmen → Staat. |
| **`ProductionSubsidies`** | Staat → Firma pro produzierter Einheit. |
| **`GrainDole`** | Registrierte Bürger (`< doleWealthThreshold`) bekommen Gratis-Essen. `doleHeadcap` gedeckelt auf 85% von `doleHeadcapBase` bei `treasuryDeclining`. |

### Staatslager & Handel (8 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`StateWarehouses`** | Verwaltet alle Staatslager: `setAllLiquidating/setStoring`, Trade-Modi `NORMAL/BUY_ONLY/SELL_ONLY`, persistierte Buy-Preise via `WarehouseLedger`. |
| **`WarehouseAutomation`** | Auto-Bewirtschaftung: knappe Ressourcen, Bau-Materialien, 3-Tage-Nahrungs-Puffer. Budget-aware. |
| **`WarehouseMarket`** | Staatlicher Im-/Export (Kauf/Verkauf via `StockpileInstance`). Marktsteuer (`marketTaxRate`) abgeschöpft. |
| **`WarehouseKernel`** | Reine Hilfsfunktionen für WarehouseMarket. Kein State. |
| **`ConstructionHoardController`** | Reservierungs-Karte (`targetedResources`). Verhindert 50 Bauarbeiter auf 1 Holz. |
| **`ConstructionHoardPlan`** | AI-Plan für Bauarbeiter, nutzt Controller. |
| **`TransportMarket`** | `transportFeePer100TileDay` für Station-Nutzung, Bürger → Staat. |
| **`ForeignTradeLedger`** | Aggregator für NPC-Handelsflüsse auf Tages-Basis. |

### Steuern & Demographie (5 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`Taxes`** | `perHeadTax` + `wealthTaxRate` + `religionTaxRate`. `perHeadTaxExemptionThreshold=500` für Armutsfreigrenze. |
| **`DebtBondage`** | Bürger mit `debt > debtSlaveThreshold` → Sklave (Sklaverei). |
| **`DebtDiplomacyBuffer`** | Liest `DipWarPlayer` via `ISyxDiplomacy`. Berechnet „abgeschreckte Fraktionen". Loggt "DIPLO" 1×/Saison. |
| **`EconProgression`** | 5-Stufen-System. `checkAdvance()` HANDEL→WOHLSTAND nutzt `actualMeanWage` (echte Bürger-Löhne). |
| **`Roster`** | Bevölkerungsverwaltung. `rebuild()` iteriert `Humanoid`s, filtert nach `HCLASSES`/`WGROUP`. |

### Vermögen & Property (3 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`HousingMarket`** | Miete + Zwangsräumung. `collectRent()` jeden Tick; `evict()` wenn nicht zahlbar. PropertyLedger lesen/schreiben. |
| **`PropertyLedger`** | Grundbuch + Firmenanteile + Dividenden. Key: tile-Koordinate + `blueprintKey.hashCode()` (Composite-Long). `cleanupGoneRooms()` entfernt Geister. |
| **`PropertyMarketController`** | (aus EconomySim extrahiert) Property-Markt: Hauskauf, -verkauf, Räumung, Schonfrist. |

### Krisen & Indikatoren (5 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`TreasuryCrisis`** | 5-stufige Kaskade + Hard-Floor in Tier 5 (siehe [`ARCHITECTURE.md`](ARCHITECTURE.md)). |
| **`CrisisDispatch`** | Update-Wrapper für TreasuryCrisis. 27 LoC, aus EconomySim extrahiert (Phase 5e). |
| **`EconIndicators`** | Ring-Buffer aus 6 Snapshots. Trend-Detection: `wagesFalling`, `treasuryDeclining`, `populationDeclining`. |
| **`EconSnapshot`** | Datenpunkt eines Ticks: Population, Gini, Treasury, meanWage, foodDays, deaths. Wird alle 60 Ticks erstellt. |
| **`GiniConsequences`** | Booster Gini → `BOOSTABLES.BEHAVIOUR().LOYALTY`. EventLog "UNREST" bei Gini>0.35. |

### Happiness / Boosters (5 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`WealthHappiness`** | `BOOSTABLES.BEHAVIOUR().HAPPI` Booster: Reichere Bürger sind glücklicher (relativ zum Median). |
| **`PropertyHappiness`** | Eigenheim-Bonus auf HAPPI (ab WOHLSTAND-Stufe). |
| **`PovertyPressure`** | Armut → Unzufriedenheit (BEHAVIOUR/HAPPI Booster). |
| **`HealthPressure`** | Wirtschaftslage → Gesundheit (`BOOSTABLES.PHYSICS().HEALTH` Booster). |
| **`MeticImmigration`** | Wirtschaftskraft → Einwanderung (`BOOSTABLES.CIVICS().IMMIGRATION`). |

### Bürger & Klassen (4 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`CitizenClass`** | Klassifikation: BOSS (>30% Firmenanteile), HEIR, MIGRANT, POOR, MIDDLE, UPPER. |
| **`SubjectJob`** | Bürger-Klick-Overlay: AI-Plan + Wirtschafts-Kontext beim Hover. |
| **`HandoutRelief`** | Nur für ARBEITENDE Bürger: bis `handoutWalletAmount` wenn `netWorth < doleWealthThreshold`. |
| **`AccessAutomation`** | Raum-Zugang nach CLASS. Rate-limitet Status-Meldungen (1× alle 100 Ticks). |

### Pair-Selection (3 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`PairSource`** | Interface `encounters(roster, n, consumer)`. |
| **`RandomPairSource`** | Zufällige Paare. |
| **`ProximityPairSource`** | Räumlich nahe Paare. Per Config wählbar (`EconConfig.pairMode`). |

### Militär & Ehren (2 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`MilitaryPayroll`** | Soldaten-Gehälter via `armySupplyWagePerDay`. |
| **`Wages`** | Standard-Lohn-Pool (Bürger erhalten ihre `defaultWage`-Zahlungen). |

### Konfiguration, Logging, Export, Utilities (10 Dateien)

| Klasse | Was sie tut |
|---|---|
| **`EconConfig`** | 140+ statische Regler. Default-Werte aller Schalter. `init()` lazy Vanilla-Init. |
| **`WorkplaceDefaults`** | Vanilla-Workplace-Prioritäten zurücksetzen. |
| **`EventLog`** | Live-Chronik. One-Shot SEAM-Logs gegen Spam. `logSampled()` via Vanilla `RND.rFloat`. |
| **`DiagnosticExporter`** | 3 CSV/Tag (Macro 32 Spalten, Resources Long, Firms 13 Spalten). |
| **`CompactNumber`** | 1500 → "1.5K", 2_300_000 → "2.3M". |
| **`SimpleHistory`** | Ring-Buffer (60 letzte Werte) für Charts. |
| **`AuditKernel`** | Geldmengen-Konservierungs-Check + Drift-Akkumulator. |
| **`EngineSeams`** | Legacy-Fassade: alle Methoden `@Deprecated`, delegieren an `economySim.aiAdapter()` etc. |
| **`ChunkedSave`** | TLV-Helper für Save/Load. Unbekannte Tags werden übersprungen. |
| **`Saveable`** | Interface: `save(FilePutter)`, `load(FileGetter)`. |

### Furnishing / Burial & Verschiedene (4)

| Klasse | Was sie tut |
|---|---|
| **`FurnishingAutomation`** | Behausungs-Einrichtungs-Ziel automatisch nach Wohlstand. |
| **`ReentryGuard`** | Boolean Re-Entry-Wächter für `EconomySim.update()`. |
| **`DebugTracer`** | TraceBuffer für Code-Flow-Sichtbarkeit (`DebugTracer.SCRP`, `VIEW`, `SYS` etc.). |
| **`GrainDoleImport`** | Sub-Modul-Helper für GrainDole — eigentlich inline. |

---

## 🟥 Kategorie 3: UI (5 Dateien in `src/vannon/syx/economy/ui/`)

### Fenster (5)

| Klasse | LOC | Hotkey | Tabs |
|---|---:|---|---|
| **`EconWindowBase`** | 368 | — | abstrakte Basis |
| **`WindowOverview`** | 744 | Numpad + | 4 (Dashboard, Demografie, Berater, Immobilien) |
| **`WindowEconomy`** | 510 | Numpad − | 6 (Märkte, Preise, Betriebe, Löhne, Subventionen, Bücher) |
| **`WindowState`** | 528 | Numpad ∗ | 6 (Lager, Finanzen, Werke, Soziales, Glaube, Debug-versteckt) |
| **`WindowQuickview`** | 195 | Numpad 0 | (kompakte Anzeige) |

### Hotkey (1, im Kern)

| Klasse | Was sie tut |
|---|---|
| **`EconHud`** | HUD-Rendering für alle 4 Fenster; Iconstack; `initPosition()` auf `C.WIDTH()-200`. |

### 16 inhärente Tab-Klassen (statische innere Klassen)

Stand v0.13.2 — keine separate `OverviewTabs.java`/`EconomyTabs.java`/`StateTabs.java` mehr. Tabs sind als statische innere Klassen direkt in den Fenster-Files:

| Fenster | Tab-Klassen |
|---|---|
| WindowOverview | `DashboardTab`, `DemographicsTab`, `AdvisorTab`, `PropertyTab` |
| WindowEconomy | `MarketsTab`, `PricesTab`, `FirmsTab`, `WagesTab`, `SubsidiesTab`, `BooksTab` |
| WindowState | `WarehousesTab`, `FiscalTab`, `PublicWorksTab`, `SocialTab`, `FaithTab`, `DebugTab` (versteckt seit v0.13.1, Klasse bleibt für Dev-Referenz) |

Jede Tab-Klasse implementiert das `TabContent`-Interface, das in `EconWindowBase` definiert ist (`title()`, `build()`). Vorhandene Frames in `EconWindowBase`: KPI-Header (Treasury, Gini, Stage), TabBar mit Window-Switcher-Buttons, Tastatur-Edge-Detection.

---

## 🟨 Kategorie 4: ENTRY & BRIDGE (3 Dateien)

| Datei | Pfad | Was sie tut |
|---|---|---|
| **MainScript.java** | `src/vannon/syx/economy/core/` | Registriert alle Booster beim Spiel-Start. |
| **AdapterReflectionBenchmark** | `src/vannon/syx/economy/benchmark/` | Reflection vs MethodHandle Benchmark. |
| **`settlement/room/.../*Access.java`** (4 Dateien) | `src/settlement/...` | Package-Private Brücken, kein Reflection nötig. |

---

## 📊 Bilanz

| Kategorie | Anzahl Dateien | Schicht |
|---|---:|---|
| 🟦 Vanilla Wrapper | 21 | Adapter + Brücken |
| 🟩 Simulation | 100 | Wirtschaftslogik |
| 🟥 UI | 5 | 4 Fenster + Base |
| 🟨 Entry + Benchmark | 2 | Main + Benchmark |
| **GESAMT** | **128** | Σ `find src -name '*.java'` |

Diese Zahlen sind **maschinell** verifizierbar:
```bash
find src -name '*.java' | wc -l                                       # 128
ls src/vannon/syx/economy/core/*.java | wc -l                         # 100
ls src/vannon/syx/economy/adapter/*.java | wc -l                       # 17
ls src/vannon/syx/economy/ui/*.java | wc -l                            # 5
grep -rE 'class [A-Z][A-Za-z]+Tab' src/vannon/syx/economy/ui/ | wc -l  # 16
```

---

## ❓ Die verwirrendsten Klassennamen — aufgelöst

| Name | Tatsächliche Bedeutung |
|---|---|
| **`IncomeCarry`** | Nicht ausgeschütteter Firmen-Gewinn (`FirmLedger.FirmState.incomeCarry`). |
| **`CashRate`** | Netto-Geldfluss/Tag (`FirmLedger`): Einnahmen − Ausgaben. |
| **`FlowMeter`** | Wrapper um Vanilla `FResources` — KEIN eigener Resource-Tracker. |
| **`FirmLedger`** | Buchhaltung + Analytics + CSV-Export-Source — drei Rollen. |
| **`DebtDiplomacyBuffer`** | Militär-Puffer: zählt abgeschreckte Fraktionen, nicht Schulden. |
| **`EngineSeams`** | Legacy-Fassade: alle Methoden `@Deprecated`, delegieren an Adapter. Wird entfernt wenn alle Caller migriert sind. |
| **`Yard-Sale`** | P2P-Geld-Transfer (Bürger→Bürger) via `Wallets.applyExchange`. |
| **`SubjectJob`** | Bürger-Hover-Overlay: AI-Plan + Wirtschafts-Kontext. |
| **`AccessAutomation`** | Raum-Zugangs-Politik nach Bürger-Klasse. |

> **Pflege-Hinweis:** Jede NEUE .java-Datei sofort oben nachtragen. Klassen-Glossar verrottet schneller als der Code, wenn es nicht aktiv gepflegt wird.
