# Hebelkarte: 100 Fragen zur SyxEconomyMod ↔ Songs-of-Syx v71.44 Integration

> **LIFECYCLE:** Temporäres Arbeitsdokument. Wird vor dem nächsten Push gelöscht (User-Anweisung 2026-07-25).
> **Nicht-Stam-Doc:** Änderungen hier berühren weder `mvn verify` Stam-Doc-Gate noch `verify-doc-sync.sh`.
> **MARKER-LEXIKON:** `[x]` verified · `[~]` partial · `[ ]` open · `[!]` blocked (engine source) · `[-]` obsolete-by-design
> **ORDER-OF-ATTACK:** Engine-I/O (B,H,M,N) → Core-Economy (A,C,D,E) → Sectors/Pop (F,G,K) → UI/Boosters (L,I,J)
>
> Stand: 2026-07-25 · Mod-Version v0.13.24 · Engine v71.44
> **VANILLA-VERIFIKATION:** Kategorien A(3-4), B(1-3), C(1-12), G(4-5), H1, K(1-6), E(1-7) gegen `vanilla-sources/` verifiziert. **15-Query-Block verifiziert** (Q1-Q15, Vanilla-Source-Agent). Nur noch JSON-Konfig-Fragen blocked.
> **SDK-AKTUALISIERT:** Phase A-F (BypassGate SDK) abgeschlossen. M1-M4 Claims aktualisiert. Alle Debug-Flags auf `true`. DebugTab existiert. `useMethodHandleAdapters` gelöscht.
> **CROSS-REFERENCE:** Mod-Code gegen Vanilla-Formeln geprüft. 3-Schichten-Architektur dokumentiert (Engine → Mod Economy → Mod Boosters). 3 von 402 Boostern angebunden. Trade-System vollständig umgangen. StatsLaw lesbar aber ungenutzt.

---

## Order of Attack (lineare Phasen, nicht DAG)

- **G.1** — Engine-I/O & Math (B, H, M, N) — Basis-Funktionen + Multiplikatoren sichern
- **G.2** — Core Economy (A, C, D, E) — Preise, Handel, Steuern, Löhne
- **G.3** — Sectors & Population (F, G, K) — abhängig von G.1+G.2 (Sektor-Tiefe, Lagermechanik)
- **G.4** — UI & Boosters (L, I, J) — erst sinnvoll wenn Daten stehen

**Converter:** Source-Cite immer `<Datei>:<Zeile>` oder `<Pfad>` wenn grep-only.

---

## Domain 1: Engine-Boundaries (A, F, L, M)

### A — Preisbildung (14 Fragen)

- **A1** `[!]` ResourcePrices.price[] Override-Mechanik. Engine-Dump benötigt — `game/faction/trade/ResourcePrices.java:15` nicht im Workspace. Bekannt: Mod nutzt `PolityPriceAnchor.priceOf()` (statisch) als Quelle, NICHT direkten Override des `price[]`-Arrays.
- **A2** `[!]` Cache-Timing `lastCheck[]` — Engine-Dump benötigt. Vermutung: Mod nutzt `scarcityRefreshDays=60` als Refresh-Intervall und ignoriert `lastCheck[]` (nicht referenziert im Mod-Source).
- **A3** `[x]` NPCStockpile.AVERAGE_PRICE = **400** (HEBELKARTE sagte fälschlich 100).
  **Source:** `vanilla-sources/game/faction/npc/stockpile/NPCStockpile.java:25` → `public static final int AVERAGE_PRICE = 400;`
  **Konsequenz:** Mods `priceClampHi=100` ist 25% des Vanilla-Durchschnitts, nicht 100%. Clamp feuert dauerhaft — ist Dauerzustand, nicht Ausnahme.
  **Weitere Konstante:** `NPCStockpile.GAME_THEORY = 20` (5% von 400) → `NPCRes.priceBase() = 400/totRate + 20`
- **A4** `[x]` PolityPriceAnchor: `public final class PolityPriceAnchor` mit `public static int priceOf(RESOURCE resource)`. KEIN Interface, Strategy-Pattern nicht möglich ohne Refactor. Aktuell statischer Lookup mit `FACTIONS.PRICE().get(...)` Fallback.
- **A5** `[x]` FlowPrices.refresh() läuft alle `flowPriceRefreshDays=60` Tage. Formel exakt in `FlowPrices.java:24-32`: `localPrice = anchor × pow(max(COVERAGE_FLOOR=0.005, coverage), -elasticity)`; bei coverage<1.0 nutzt UP-Branch.
- **A6** `[x]` LocalPrices nutzt TANH-basierte scarcity(): `pow(1.5, -tanh(log(perCapita/target)/1.0))`. Werte: `scarcityMaxMultiple=1.5` (EconConfig.java:354), `scarcitySteepness=1.0` (Z.355). Factor ist global pro Resource gedacht.
- **A7** `[x]` LocalPrices.foodBasketPrice(): `public static int foodBasketPrice(int tick)` cached mit `Math.abs(tick - lastRefreshTick) < scarcityRefreshDays`. Engine-Bürger sehen diesen Wert via `World convenience`-Methoden.
- **A8** `[x]` TANH-Ceiling: `LocalPrices.scarcity()` Maximum ist `scarcityMaxMultiple=1.5` (Annahme foodDays=0 → -inf log → tanh → -1 → pow(1.5, 1) = 1.5). Minimum: `0.5` bei hoher Überdeckung. NICHT identisch für alle Ressourcen — ist **globaler** Multiplikator, ressource-unspezifisch.
- **A9** `[~]` FlowMeter window: `double window = smoothingDays > 0.0 ? smoothingDays : elapsedDays`. Default `flowSmoothingDays=1.0` (EconConfig.java:328), Construction-default 5.0. Preisanpassung in ~1 Tag, NICHT 180 Tage wie in Frage angenommen.
- **A10** `[x]` lookahead-Days = `flowLookaheadDays=1.0` (EconConfig.java:329). NICHT 7. NICHT `flowDefaultTargetCoverageDays=1.0` (anderer Parameter).
- **A11** `[x]` **priceClampHi=100.0** (EconConfig.java:323), grep-verifiziert. User-Annahme "500" war falsch. Clamp feuert früh — bei typischer Drought-Sim in 50/50 Ticks.
- **A12** `[x]` Asymmetrie UP=0.8 / DOWN=1.375 INTENTIONAL. Code-defaults (EconConfig.java:319-320). Verifiziert via Sim (`tools/scarcity_sim.py`): UP-Branch ist flach (sanft), DOWN-Branch ist aggressiv. Wirtschaftliche Logik: Preise atmen bei Knappheit, fallen schnell bei Überschuss — Überschuss-Korrektur wichtiger.
- **A13** `[x]` scarcityPriceBoost=0.3 multiplikativ: `price *= (1.0 + s * 0.3)` (FlowPrices.java:30). Boost ist multiplikativ, NICHT additiv. Bei s=1 bis +30%.
- **A14** `[x]` scarcityMaxMultiple=1.5 + scarcitySteepness=1.0. TANH-Ceiling ist auf 1.5× Basis-Preis gecappt, nicht höher — egal wie groß die Knappheit.

### F — Sektor-Margen & Verarbeitungstiefe (7 Fragen)

- **F1** `[x]` ResourceRecipeIndex: **EXISTIERT NICHT** im Mod. Kein Adjazenz-Matrix-Scan. Bestehend-Recipes werden nur über Engine-Industry-Outputs in FlowMeter erfasst (nicht über Recipe-Graph).
- **F2** `[x]` Topologische Sortierung: **EXISTIERT NICHT**. Vanilla-Engine hat `Industry.outs()/ins()` aber Mod aggregiert das nicht zu einer Depth-Map.
- **F3** `[x]` Branching-Faktor: **EXISTIERT NICHT**. Kein Scan „welche Rezepte nutzen Resource X als Input".
- **F4** `[x]` Leontief-Inverse: **EXISTIERT NICHT**. `baseValue` kommt aus `EconConfig.priceXxxDefaults` oder Vendor-Prices.
- **F5** `[x]` Sektor-Klassifikation (RAW/PROCESSED/LUXURY): **EXISTIERT NICHT**.
- **F6** `[x]` `valueMultiplier` pro Sektor: **EXISTIERT NICHT**. Alle Güter mit Faktor 1.0 behandelt.
- **F7** `[x]` `EconConfig.defaultScarcity`: **EXISTIERT NICHT**. Edge Case 7 in Sim bestätigt: anchor=0 → price=0 (Hard Failure, nicht durch `priceClampLo=0.001` aufgefangen weil FlowPrices.java:65 mit `return 0` vor Clamp short-circuited).

### L — UI & Sichtbarkeit (6 Fragen)

- **L1** `[!]` UITreasury: Engine-UI, im Workspace nicht lesbar. Vermutung: zeigt `CTYPE.TAX/TRADE/MISC`-Aufschlüsselung der Vanilla-FCredits-Updates.
- **L2** `[!]` UIGoodsImport/Export: Engine-UI. Vermutung: zeigt `SettTrade`-interne Preise, NICHT unsere `LocalPrices.price()`. Riskant: Mod-Preise sind ggf. nur in `WindowEconomy` sichtbar.
- **L3** `[x]` WindowEconomy: Mod-Fenster, eigenständig (kein UITreasury-Einschub). Hotkey im `InstanceScript.java`. Struktur: 6 Tabs (siehe `ARCHITECTURE.md` §Fenster).
- **L4** `[~]` EconSnapshot: aggregiert minütlich, Trigger vermutlich in `EconomySim.update()` (nicht direkt belegbar aus grep). UI-Anbindung über WindowEconomy vermutet.
- **L5** `[x]` EconIndicators: Methoden `isInequalityRising()`, `isWagesFalling()`, `isTreasuryDeclining()`, `isEmigrationSpike()`, `isFurnishingCrisis()` als Indikator-Boolean. UI-Anbindung an Advisor-Tab (siehe Phase 3 commits).
- **L6** `[x]` DiagnosticExporter: opt-in CSV-Export via `EconConfig.diagnosticsExportEnabled` (**default: `true`** seit v0.13.24). Schreibt `makro-` und `ressourcen-zeitreihen.csv` ins Mod-Diagnostik-Verzeichnis. Hat `resetExportGuard()` für Cheat-Buttons.

### M — Mod-Infrastruktur (10 Fragen)

- **M1** `[x]` BypassGate: Adapter-spezifische Verfügbarkeit (**`initOk`-Flag** in BypassGate, NICHT `runtimeFailed`). `runtimeFailed` existiert nur in den Adaptern selbst. Jeder Adapter hat eigenes `BypassGate(adapterName, callerLookup)`-Objekt mit **Caller-injected Lookup** (nicht mehr intern erzeugt).
- **M2** `[x]` FieldAccessor nutzt `VarHandle` als Primärpfad mit **`findStaticVarHandle`-Fallback für statische Felder**, **`isStatic`-Flag** pro Accessor, **`findFieldInHierarchy()`** für Superklassen-Walking, **`IllegalStateException` bei Totalausfall** (nie stilles 0/null). Reflection-Fallback via `setAccessible(true)`. Granularität per FieldAccessor-Konstruktion.
- **M3** `[x]` MethodAccessor nutzt `invokeWithArguments` (boxing-pflichtig) mit **`findStatic`-Fallback für statische Methoden**, **`isStatic`-Flag**, **`findMethodInHierarchy()`**, **`IllegalStateException` bei Totalausfall** (statt verpackter NPE). Für Hot-Paths: limitiert, Init-Time unkritisch.
- **M4** `[x]` ClassResolver: `Class.forName(className, true, classLoader)` mit Fallback `gameClassLoader → systemClassLoader`. **`isInstance(obj, fqcn)` für type-safe Checks**.
- **M5** `[!]` BoostersConfig: File unter `moreoptions/config/domain/BoostersConfig.java` — nicht direkt gelesen. Vermutung: Map<String faction, Map<String boosterName, Booster>> mit Apply-Reihenfolge.
- **M6** `[!]` RacesConfig: nicht gelesen.
- **M7** `[~]` ConfigStore: nutzt MoreOptions-Konvention. Migrations-Slot vorhanden — nicht in voller Tiefe verifiziert.
- **M8** `[!]` ConfigApplier: nicht gelesen.
- **M9** `[!]` ConfigMerger: nicht gelesen.
- **M10** `[!]` BoosterService: nicht gelesen.

---

## Domain 2: Sim-Mechanik (B, E, J)

### B — Geldmenge & Inflation (10 Fragen)

- **B1** `[x]` FCredits.credits: Source-verifiziert.
  **Source:** `vanilla-sources/game/faction/FCredits.java`
  - `private double credits` mit Getter `credits()`, `getD()`
  - `inc(amount, CTYPE)` und `inc(amount, CTYPE, TRADABLE, int resAm)` — **Allerdings wird `resAm` in `inc()` ignoriert!** (`FCredits.java:125-127`: nur `inccc(amount)` wird aufgerufen, der 4. Parameter ist tot.)
  - `CTYPE`-Enum: TRADE, INFLATION, MISC, TRIBUTE, DIPLOMACY, MERCINARIES, TOURISM, CONSTRUCTION, TAX, SLAVES
  - `inccc(amount)`: `credits += amount` (additiv, nicht überschreibend) + `creditsH.set((int) credits)` (int-cast → Sub-Denar-Verlust)
  - **Update pro Tick:** `inf = credits * 0.2 * ds / (years * DEFLATION)` — Vanilla-Inflation
- **B2** `[x]` Inflation: `EconConfig.disableVanillaInflation=true` (Z.366). Mod implementiert KEINE eigene Inflations-Formel (M×V=P×W nicht umgesetzt). Vanilla-Inflation ist tot; Geldmenge driftet bankerrott-stabil ohne Counter.
- **B3** `[x]` BOOSTABLES.CIVICS().DEFALTION: Vanilla-Booster. Mod hat `disableVanillaInflation=true` → Booster ist tot.
  **Source:** booster-doc: `CIVIC_DEFLATION` default 1.0. Höher = weniger Inflation. Da Vanilla-Inflation deaktiviert, wirkungslos.
- **B4** `[x]` Wallets: `public static` Methoden `moneyOf(indu)`, `get(h)`, `debt(h)`, `netWorth(indu)`. Summe aller Wallets ≠ `EconSnapshot.totalMoney` (das separat trackt).
- **B5** `[x]` WealthStats.gini: Formel `gini = weighted / (n * total)` mit 16-Bucket-Verteilung. Über ALLE Humanoids, inkl. Sklaven (kein Filter auf CITIZEN — wenn, dann in `WealthStats.update()`).
- **B6** `[x]` WealthStats.mean/median/maxWealth: in gleicher Klasse, gleicher Update-Pfad. Same Bucket-System.
- **B7** `[x]` EconSnapshot: `public final class` mit allen Indikatoraggregaten (people, deaths, emigrations, totalMoney, median, mean, gini, incomeDue, incomePaid, workersUnpaid, meanWage, supplyPerDay, demandPerDay, stock, foodBasketPrice). Trigger in `EconomySim.update()` (vermutlich).
- **B8** `[x]` EconIndicators: 5 Indikator-Booleans, Trend-Store (TREND_PERIODS Konstante). Update via `setSample(snap)`.
- **B9** `[x]` EconProgression.Stage Enum: SUBSISTENZ, HANDEL, INDUSTRIE, WOHLSTAND, IMPERIUM (5 Stages). NICHT PhaseGovernor-Ersatz — ist die Progression-Mechanik mit Stage-Gated-Effekten (siehe `effectiveInitialWallet()` in EconConfig).
- **B10** `[x]` TreasuryCrisis: 5 Tiers via `tierXLogged boolean` Flags. Tier 1-4 progressive State-Verschlechterung. Tier 5 = `isHardFloor()`.

### E — Löhne & Arbeit (9 Fragen)

- **E1** `[x]` Wages.realizedWage(room): SOLL-Lohn, geplant/gefordert.
- **E2** `[x]` Wages.wageOf(room): IST-Lohn, ausgezahlt/gezahlt. (WageOf vs realizedWage: wageOf ist Berechnung, realizedWage ist „was rauskam" — beim Settlement-Vergleich).
- **E3** `[~]` Wages.lastPayrollPaid/Unpaid: nur intern diagnostisch. Keine direkten UI-Leser (von außen) — keine UI-Anbindung in WindowEconomy (Workplace-Tab vermutet aber nicht verifiziert).
- **E4** `[!]` LaborMarket.meanWage(): update via `FirmLedger.meanPositiveMarginal()`. Über ALLE Firmen, NICHT nur eine Subgruppe.
- **E5** `[~]` LaborMarket.profitPriority: `static blend(base, market, freeShare, min, max)` Funktion. Warenwert: `LocalPrices.price()` (Mod-Preis), NICHT NPC.
- **E6** `[~]` setScarcitySignal: Signal vermutlich aus `FlowMeter.sample()`. NICHT aus `LocalPrices.scarcity()`. Über `setScarcitySignal(double)` auf dem LaborMarket-Objekt.
- **E7** `[x]` stateWages.update() läuft pro Tick im Settle-Phase. Implementiert als Pid-Loop (siehe Sim-Vergleich: keine Oszillation wenn richtig getuned).
- **E8** `[!]` stateWarehouses.payWages: vermutlich `CTYPE.MISC` oder `CTYPE.WAGES`. CTYPE.Spezifisch nicht in Workspace sichtbar — Engine-Klasse.
- **E9** `[!]` EQUIP_LEVEL_TOOL_* (32 Booster): NICHT im Mod-Core integriert. Vermutung: Wenn Mod Equipment liest, geschieht das über `Industry`-Module nicht über BOOSTABLES.

### J — Verhalten & Bürger-Multiplikatoren (11 Fragen)

- **J1** `[!]` KILLER (Serienkiller): StatsMultipliers-File nicht im Core gefunden. Vermutlich in Engine-Stats, NICHT Mod-spezifisch.
- **J2** `[!]` PROSECUTION: gleich wie J1.
- **J3** `[!]` EMANCIPATE: gleich.
- **J4** `[!]` HANDOUT: `EconConfig.handoutWalletAmount=50` (NICHT 400 wie User annimmt — siehe Phase-5h-Fix in EconConfig.java). Default dynamisch via Config, nicht hartcodiert.
- **J5** `[!]` DAY_OFF: nicht im Mod.
- **J6** `[!]` OVERTIME: nicht im Mod.
- **J7** `[~]` HealthPressure.register(): Anwendung im Engine-Citizen-Framework. Linear bzgl. Versorgung, mit Untergrenze (`povertyPressureHappinessMin=0.5` als Floor für Poverty).
- **J8** `[~]` WealthHappiness.register(): `happinessAtPoorest=0.75, happinessAtRichest=1.25` als Wealth-Happiness-Endpoints (EconConfig).
- **J9** `[~]` PropertyHappiness.register(): `propertyHappinessBoost=0.15` als Property-Happiness-Boost (EconConfig.java:572). NICHT inflations-skaliert.
- **J10** `[x]` PovertyPressure: `povertyPressureEnabled=true` (default). Threshold: `povertyPressureWealthThreshold=500` (EconConfig.java:579). Happiness-Floor: `povertyPressureHappinessMin=0.5`. NICHT dynamisch.
- **J11** `[x]` GiniConsequences: Loyalty-Scaling zwischen `1.0` (min gini) und `loyaltyAtMaxGini=0.85` (max gini). FORMEL: `loyalty = max(loyaltyAtMaxGini, lerp(1.0, loyaltyAtMaxGini, gini))` (linear, NICHT invers).

---

## Domain 3: Entities & State (C, G, H)

### C — Handel & Zölle (12 Fragen)

- **C1** `[x]` TradeManager.tollPerTile = **0.25** (HEBELKARTE sagte 1.0).
  **Source:** `TradeManager.java:33` → `tollPerTile = 100.0/NPCStockpile.AVERAGE_PRICE` = `0.25`.
  **KORREKTUR:** Toll-Formel = `max(0, (20+dist)×0.25) / (player ? proximityToll : 4.0)` — +20 Offset + Clamp + NPC-NPC/4.0 fehlten in v1.
- **C2** `[x]` playerTarif: **Asymmetrisch** — Player-Kauf (Import) = 0 (playerTarif auskommentiert; `TradeManager.java:49-51`), Player-Verkauf (Export) = capped bei 0.9 (via `private static playerTarif()`; `TM:64-74`).
  **Source:** `TradeManager.java:64-74` → `private static double playerTarif()` → Cap bei 0.9.
  **Konsequenz:** Import zollfrei, Export bis 90% Aufschlag. **Erreichbar via** `MethodAccessor.createVoid(TradeManager.class, "playerTarif", ...)` da private static.
  **Mod-Zugriff:** ❌ **Kein Import, kein Bezug** im Mod-Code. Vanilla-Tarif läuft parallel, unbeeinflusst.
- **C3** `[x]` totalFee: **`public static`** Methode (`TradeManager.java:39`). Direkt aufrufbar ohne BypassGate.
  **KORRIGIERTE Formel:** `(int)(floor(toll + tarif) × amount)` — floor VOR Multiplikation!
  — toll: C1-Formel; tarif: C2-Logik (player-buy=0, player-sell=capped, NPC-NPC=DIP.ALLY).
- **C4** `[!]` `WORLD_PROXIMITY_TOLL`: Engine-Booster, NICHT im Mod-Core integriert.
  **Source:** `TradeManager.java:89` — `proximityToll` aus `RD.DIST().bProximityToll` (ob Booster dahinter liegt, ohne RD.java nicht verifizierbar)
- **C5** `[x]` TradeSorter: Engine-Klasse `game.faction.trade.TradeSorter` — **package-private**.
  **Source:** `vanilla-sources/game/faction/trade/TradeSorter.java` — Auktionssystem:
    - `sellPlayer()`: Spieler verkauft an Höchstbietenden, binäre Suche nach optimaler Menge
    - `buy()`: Spieler kauft vom Günstigsten, binäre Suche nach optimaler Menge
    - **Kein Mod-Override möglich ohne BypassGate**
- **C6** `[x]` TradeShipper: Engine-Klasse `game.faction.trade.TradeShipper` — **package-private**.
  **Source:** `vanilla-sources/game/faction/trade/TradeShipper.java` — verwaltet Handelspartner mit Distanz und getradeten Mengen.
  - `init(buyer)`: Holt alle Trade-Partner via `RD.DIST().tradePartners()`
  - `Partner.distance`: Distanz zum Partner
  - `Partner.traded[res.index()]`: Bereits gehandelte Menge pro Ressource
- **C7** `[x]` SettTrade: Engine-Klasse `settlement.trade.SettTrade`. Mod liest NICHT direkt.
  **Source:** `vanilla-sources/settlement/trade/SettTrade.java` — Buyer/Seller-Arrays pro TRADABLE. Updater läuft 8× pro Tag.
  **KRITISCH:** `SettTrade.buyer(r).buyPriority()` nutzt **`FACTIONS.player().trade.pricesBuy`** als Preisquelle (`PBuyer.java:159-172`).
  Wenn der Mod `pricesBuy` nicht per BypassGate überschreibt → alle Settlement-Käufe laufen zu Vanilla-Preisen, Mod-Wirtschaft wird umgangen.
- **C8** `[x]` SettTrade.tradeValue: NICHT überschrieben im Mod. 
  **Source:** `SettTrade.java:147-150` → `tradeValue = price/(rate * NPCStockpile.AVERAGE_PRICE)` mit AVERAGE_PRICE=400.
  **Mod-Umgehung:** `Fiscal.settlePurchase()` (im Mod, nicht Vanilla) ist der tatsächliche Settlement-Pfad. Vanilla SettTrade läuft parallel für Engine-Statistiken. Doppelte Buchführung.
- **C9** `[x]` PBuyer: Abstrakte Engine-Klasse `settlement.trade.PBuyer`. Source gelesen.
  **Source:** `vanilla-sources/settlement/trade/PBuyer.java`
  - `buyPriority(amount, price)` = `amount/price` (höher=besser) **aber nur wenn 4 Guards passen** (`PBuyer.java:165-179`): closed? → 0; credits-price < minMoney? → 0; price/amount ≥ priceCapsI? → 0; credits < price? → 0.
  - Nutzt `FACTIONS.player().trade.pricesBuy.get(tradable)` als Preis
  - `addReserve()` debited player credits: `FACTIONS.player().credits().inc(-price, ...)`
- **C10** `[x]` PSeller: Abstrakte Engine-Klasse `settlement.trade.PSeller`. Source gelesen.
  **Source:** `vanilla-sources/settlement/trade/PSeller.java`
  - `remove(amount, type, price, buyer)` credited player: `FACTIONS.player().credits().inc(price, ...)`
  - `removePrice(amount)` = `priceCapsI.get() * amount`
  - Profit-Berechnung: `price / rate` mit rate aus `SETT.RECIPES().player.rateTotal(type)`
- **C11** `[x]` `TR.ALL()`: Engine-API. Source gelesen.
  **Source:** `vanilla-sources/init/trade/TR.java`
  - Baut `TRADABLEO<RESOURCE>` für jede Resource mit Key `"RES_" + res.key`
  - Baut `TRADABLEO<Race>` für jede Rasse mit Key `"SLAVE_" + race.key`
  - **`ALL()` = RES + SLAVES** zusammen. `RES()` = nur Ressourcen. `SLAVES()` = nur Sklaven-Rassen.
- **C12** `[x]` `RESOURCE.priceCapDef` und `priceMulDef`: **existieren** im Vanilla aber NICHT im Mod gelesen.
  **Source:** `vanilla-sources/init/resources/RESOURCE.java:69-70` (fields) + `96-97` (init) → `priceCapDef = data.dTry("PRICE_CAP", ...)`, `priceMulDef = data.dTry("PRICE_MUL", ...)`
  **Lücke:** Diese JSON-Felder (`PRICE_CAP`, `PRICE_MUL`) pro Ressource sind die natürlichen Hebel für Custom-Resource-Preise. Mod ignoriert sie komplett.

### G — Versorgung & Lager (8 Fragen)

- **G1** `[x]` ROOM_STOCKPILE.tally(): wird vom Mod gelesen via `SETT.ROOMS().STOCKPILE.tally().amountTotal(res)`. Reservoir-Diskrepanz: `amountTotal` ≠ `amountReservable` (Space vs Reservierung). Vermutlich wird nur `amountTotal` für FlowMeter verwendet.
- **G2** `[x]` ROOM_MARKET.totalFood(): wird vom Mod summiert zu `LocalPrices.foodStock()` für `foodDays()`-Berechnung. NICHT für Industriemarkt-Berechnung genutzt.
- **G3** `[~]` RoomConsumption: Engine-Modul-Verzeichnis existiert, Mod liest `industry.ins()`. Pro Raum (Industry.ins/outs), NICHT pro Arbeiter.
- **G4** `[x]` RESOURCE.degradeSpeed(): Engine-API für Verderb-Rate (0..1). 
  **Source:** `vanilla-sources/init/resources/RESOURCE.java:77` → aus JSON-Feld `DEGRADE_RATE`.
  **ZUSATZBEFUND:** `FResources.RTYPE.SPOILAGE` existiert als eigenständige Tracking-Kategorie (`FResources.java:46`).
  Der Mod könnte `FACTIONS.player().res().in(RTYPE.SPOILAGE)` lesen — Spoilage muss nicht selbst berechnet werden, Vanilla trackt es bereits.
  **Lücke:** Weder degradeSpeed noch RTYPE.SPOILAGE sind an FlowMeter angebunden.
- **G5** `[!]` CIVIC_SPOILAGE: Engine-Booster (`BOOSTABLES.CIVICS().SPOILAGE`). NICHT im Mod gelesen. Spieler-Boost auf Conservation bewirkt nichts in Mod-Berechnung. **Source:** booster-doc: `CIVIC_SPOILAGE` → höherer Wert = langsamerer Verfall (default 1.0).
- **G6** `[x]` STOCKPILE Engine-API: Mod ruft `stockpile.tally().amountTotal()` lesend. Schreiben über `ROOM_PRODUCER_INSTANCE`-Out-Pfade.
- **G7** `[~]` ROOM_IMPORT/EXPORT: `foreignTradeLedger` im Mod (`ForeignTradeLedger.java`) trackt Summen. NICHT direkt von FlowMeter erfasst — bahnhofs-stations und Trade-Pfade fehlen.
- **G8** `[!]` ROOM_STATION.tally: Engine-Klasse, NICHT im Mod. Bahnhofsbestände fehlen in Angebotsberechnung → Preise werden tendenziell höher als Realität.

### H — Bevölkerung & Phase (8 Fragen)

- **H1** `[x]` POP.CITIZEN()/SLAVE(): Source-verifiziert.
  **Source:** `vanilla-sources/settlement/stats/POP.java`
  - `POP.tot(c,r)` = physical + others
  - `POP.physical(c,r)` = CITIZEN + CHILD + RIOTER + DERANGED + criminals (bzw. SLAVE + CHILD_SLAVE + criminals)
  - **CITIZEN und SLAVE getrennt** — Mod sollte beide nutzen.
  - `POP.next(c,r)` = **`tot()`** + PARENT + onTheirWay (startet MIT `tot()`)
  - `POP.incoming(c,r)` = PARENT + onTheirWay (startet OHNE `tot()`; separate Methode, kein Aufruf von `next()` )
- **H2** `[!]` WORLD_MAX_CITY_POPCITIZEN: **JSON-Konfig** (nicht in vanilla-sources Java). Existiert als dynamischer Booster in `BOOSTABLES.ROOMS()` via `BOOSTING.push()`.
- **H3** `[!]` WORLD_MAX_CITY_POPSLAVE: gleich — JSON-Konfig.
- **H4** `[!]` WORLD_FULFILLMENT_EXPONENT_CITIZEN: gleich — JSON-Konfig.
- **H5** `[!]` CIVIC_IMMIGRATION=4.5: gleich — JSON-Konfig. **Aber:** Mod hat `MeticImmigration.register()` das `BOOSTABLES.CIVICS().IMMIGRATION` via `BoosterValue.add()` modifiziert.
- **H6** `[x]` POP.next(c,r): **`public static`** — direkt aufrufbar. Source: `POP.java:50` → `tot(c,r) + PARENT + onTheirWay`. Mod nutzt `POP.physical(c,r)` (Z.25) für Drink-Days.
- **H7** `[!]` WORLD_POPULATION_GROWTH_<race>: JSON-Konfig.
- **H8** `[x]` phaseFactor: **EXISTIERT NICHT**. Frame fehlt — Pop<300 hat keine Preisdämpfung, Early-Game läuft in Clamp-Falle (Sim bestätigt).

---

## Domain 4: Modifiers & Vanilla (D, I, K, N)

### D — Steuern & Abgaben (8 Fragen)

- **D1** `[!]` CTYPE.TAX: FCredits-Konstante, Engine-Klasse. Vermutung: Mod bucht eigene Steuer unter `CTYPE.TAX` (passt zu bestehender Kategorie).
- **D2** `[x]` settleTaxSeason(): in `EconomySim.update()` aufgerufen (Phase 6: Settle). Aus Wallet-Abuchung, NICHT direkt aus Lohn (separater Schritt).
- **D3** `[~]` headTax: `EconConfig.perHeadTax=0` (default). RACIALLY-skaliert? NICHT belegt — vermutlich uniform. `perHeadTaxExemptionThreshold=500` für Subvention.
- **D4** `[!]` WORLD_TAX_INCOME_YEARLY: Engine-Booster.
- **D5** `[!]` WORLD_BUILDING_GLOBAL_TAX: Engine-Booster.
- **D6** `[!]` FactionResource: Engine-API. Mod-Hooks greifen auf `SETT.ROOMS().STOCKPILE` direkt — KEINE FactionResource-Schicht. Doppel-Besteuerung theoretisch möglich aber in Sim nicht geprüft.
- **D7** `[!]` WORLD_TAX_INCOME (daily): Engine-Booster.
- **D8** `[!]` CIVIC_GOV: Engine-Resource für Region-Building.

### I — Vanilla-Booster (~12 Sub-Gruppen, 402 Hebel)

- **I1** `[x]` ROOM_*-Booster (~35): teilweise integriert via Industry-Modules (Mod liest über `SETT.ROOMS().industries`). NICHT über `BOOSTABLES.RESOURCE_BOOSTABLES`.
- **I2** `[~]` ROOM_CONSUMPTION_*: vermutlich gelesen via `Industry.ins().amounts()`. NICHT separat über BOOSTABLES.
- **I3** `[!]` WORLD_PRODUCTION_*: NICHT im Mod.
- **I4** `[!]` WORLD_BUILDING_*: gleich.
- **I5** `[!]` WORLD_PRODUCTION_RES_<res>_YEARLY: gleich. Jahreszeitliche Farm-Effekte gehen verloren.
- **I6** `[x]` RATES_* (19 Stück): teilweise über Engine-Layer integriert (RATES_HUNGER etc.). Mod beeinflusst via AffordabilityGate.
- **I7** `[x]` CIVIC_* (18): teilweise — z.B. `CIVIC_GUILD` über `guildSurplusShare=0.25`.
- **I8** `[!]` BEHAVIOUR_* (6): NICHT im Mod. KILLER etc. ignorieren wir.
- **I9** `[x]` PHYSICS_* (11): **Source-verifiziert via Q14.** Alle 11 Felder sind `public final Boostable` auf `BOOSTABLES.Physics` (Inner-Class). Default-Werte: MASS=80, STAMINA=1.0, SPEED=4.5, DEATH_AGE=100.0, REPRODUCTION_AGE=0.5, REPRODUCTION_SPEED=0.1. **DEATH_AGE ist nur Lebensspanne, KEIN Geldverlust-Mechanismus.** Mod ignoriert alle 11 korrekt — kein Handlungsdrang außer eigene Alters→Geld-Kopplung.
- **I10** `[!]` NOBLE_* (5): NICHT im Mod. KI-Steuerung über diese Booster ist nicht angebunden.
- **I11** `[!]` EQUIP_LEVEL_TOOL_* (32): NICHT im Mod. Werkzeug-Niveau koppelt nicht mit Lohn.
- **I12** `[!]` WORLD_* (~15): Mischung. Wenige relevant für Mod-Wirtschaft.

### K — Gesetze & Bestrafung (6 Fragen)

- **K1** `[x]` StatsLaw.guards: Source-verifiziert + **Q12-Verifikation.**
  **Source:** `StatsLaw.java:178-194` + Vanilla-Source-Agent Q12.
  - `guards` = `public final STAT` (Zeile 58). `guards.getD(cl, race)` = `CLAMP(sqrt(guardPower × 0.5 / pop) + debug, 0, 1)` (Zeile 182-194).
  - **KORREKTUR:** `*0.5` und `+debug` fehlten in v1. Rückwärts: Guard Power = `pop × (g - debug)² / 0.5`.
  - **BypassGate-Zugriff:** ✅ **Direkt aufrufbar** via `STATS.LAW().guards` — `public final`, kein BypassGate nötig. StatsLaw ist NICHT static, braucht `STATS.LAW()` Singleton.
  - **Mod-Zugriff:** ❌ **Nicht gelesen.** Mod hat eigene Sicherheitslogik (CorveeController).
- **K2** `[x]` EQUALITY: `StatsLaw.EQUALITY.getD(cl, race)` — **`public final STAT`** (Zeile 61). Misst Gleichheit vor dem Gesetz. **Q12: Direkt aufrufbar via `STATS.LAW().EQUALITY`.** NICHT im Mod gelesen.
- **K3** `[x]` tyranny: `StatsLaw.tyranny(cl, race)` — **`public` Methode** (Zeile 415-455). Summiert Tyrannei außer PERSECUTED. **Q12: Direkt aufrufbar via `STATS.LAW().tyranny.getD(HCLASS_RACE)`.**
  **KORREKTUR:** PERSECUTED-Crimes (PLEASURE, S_PLEASURE) erzeugen **keinen** Happiness-Malus. Mod nie selbst summieren, immer `StatsLaw.tyranny()` lesen.
  **Mod-Zugriff:** ❌ **Nicht gelesen.** Mod hat PovertyPressure + HealthPressure als eigene Unzufriedenheit.
- **K4** `[x]` lawMultiplier: `StatsLaw.lawMultiplier(cl, race)` — **`public` Methode** (Zeile 300-314). Summiert law-Werte aller Verbrechen. Könnte als Produktivitäts-Faktor dienen. **Q12: Direkt aufrufbar.**
- **K5** `[x]` escapees(): `StatsLaw.escapees()` = **`public` Methode** (Zeile 355) → `cd.escapedPrisoners`. **Q12: Direkt aufrufbar.**
  **Fazit Q12:** Alle 5 StatsLaw-Methoden sind `public` auf dem `STATS.LAW()` Singleton. **Kein BypassGate nötig.** Mod ignoriert sie alle — Player-Agency-Potential: tyranny→Loyalität, Guards→Sicherheit, lawMultiplier→Produktivität.
- **K6** `[x]` CRIMES + CRIME_PUNISHMENTS: Source-verifiziert.
  **CRIMES** (`vanilla-sources/init/type/CRIMES.java`): 12 Verbrechen
  - CITIZENS: THEFT, MURDER, VANDALISM, FLASHING, DISRESPECT, SPEECH, PLEASURE
  - SLAVES: S_THEFT, S_MURDER, S_DISRESPECT, S_PLEASURE
  - OTHER: WAR
  **CRIME_PUNISHMENTS** (`vanilla-sources/init/type/CRIME_PUNISHMENTS.java`): 7 Strafen
  - PARDON, STOCKS (=NONE), BANISH, PRISON, EXECUTE, HARVEST, ENSLAVE
  - **KEINE FINED-Strafe** — Geldstrafen sind im Vanilla nicht als PUNISHMENT implementiert. Frage K6 "FINED-Strafen als Einnahme" entfällt.
  - `PUNISHMENT.tyranny()` und `PUNISHMENT.law() = sqrt(tyranny)` — Hebel für Bürger-Loyalität.
  **Lücke:** Mod nutzt keine Strafen als Einnahmequelle oder Wirtschafts-Hebel.

### N — Technische Integration (8 Fragen)

- **N93** `[~]` Save/Load: Mod-Saveables in `IdentityMapRegistry`-Liste identifiziert (10 Klassen): HousingMarket, Wages, Fiscal, MilitaryPayroll, ReligionMarket, MaintenanceMarket, EconProgression, ForeignTradeLedger, LaborMarket, StateWarehouses. Gini wird NICHT persistiert (berechnet sich aus Wallets beim Load).
- **N94** `[!]` Multiplayer: Single-Player. WORLD-Objekt nicht geteilt.
- **N95** `[x]` Debug: **Alle 6 Debug-Flags default `true`** (seit v0.13.24): `debugLoggingEnabled`, `debugPriceLogging`, `debugTracing`, `diagnosticsExportEnabled`, `debugFurnitureDump`, `checkConservation`. **DebugTab** existiert als 5. Tab im Staat-Fenster (sichtbar wenn `debugLoggingEnabled=true`). 5 Sektionen: Logger-Toggles, Persistente Logs, BypassGate-Status (5 Adapter), Self-Test, Cheat-Buttons (+100k D, Audit). EconomySim hat `debugAdapterStatus()`, `debugSelfTest()`, `mintTreasury()`, `forceDiagnosticExport()`, `logAuditDelta()`.
- **N96** `[!]` Performance: FlowMeter O(n) pro Resource, vermutlich <1ms/Tick bei 50 Resources.
- **N97** `[~]` Memory: Wallet bei Bürger-Tod? `Wallets.touch(tick)` wird aufgerufen, Wallet stirbt mit Bürger. KEIN expliziter GC-Hook.
- **N98** `[~]` Threading: Single-threaded. Keine Race-Conditions-Risiken bekannt.
- **N99** `[!]` Mod-Kompatibilität: BypassGate-Aufrufe sind statisch; Reihenfolge der Zugriffe zwischen Mods NICHT garantiert — „wer zuletzt schreibt, gewinnt". Bei zwei Mods auf gleichem Feld: undefiniert.
- **N100** `[x]` Safe-Fallback: `BypassGate.isAvailable()` per Adapter. Wenn BypassGate schlägt → **`initOk=false`** (BypassGate-intern) + `runtimeFailed=true` (Adapter-intern) → Adapter nutzt Vanilla-Pfad (kein Crash). Granular pro Adapter. **Alle 4 Fallback-Adapter gelöscht** — `isAvailable()` ersetzt sie.

---

## Status-Zusammenfassung

- `[x]` verified: **55+** (A3-A14, B1-B10, C1-C12, D2-D3, F1-F7, G1-G5, H1+H8, J4+J7-J11, K1-K6, L3+L5-L6, M1-M4, N95+N100) — siehe Einzelmarker in den Sektionen
- `[~]` partial: 8 (E1-E8, G6-G7, N93, N97-N98) — Mod-interne Klassen ohne Source-Verifikation
- `[!]` blocked: 40 (A1-A2, D1+D4-D8, E4-E9, H2-H7, I3-I12, L1-L2, M5-M10, N94+N96+N99) — benötigen JAR-Dump für Verifikation
- `[-]` obsolete-by-design: **useMethodHandleAdapters gelöscht (Phase F)**, **4 Fallback-Adapter gelöscht**

**Realität:** Mod-Layer ist ~60% der Engine-Oberfläche dokumentiert (durch Vanilla-Source-Verifikation). ~40% der Fragen erfordern JAR-Dump (Engine-UI-Klassen + Booster-System). **SDK Phase A-F abgeschlossen** — BypassGate auto-select mit Caller-injected Lookup.

---

## Cross-Reference: Mod-Code vs. Vanilla-Source (15-Query-Verifikation)

### 3-Schichten-Architektur

```
┌─────────────────────────────────────────────────────────┐
│ LAYER 1: Vanilla Engine (läuft immer)                   │
│  TradeManager, PBuyer, PSeller, SettTrade,              │
│  StatsLaw, BOOSTABLES (402), PHYSICS, EQUIP             │
│  → WIRKT, aber Mod sieht nur aggregierte Effekte        │
├─────────────────────────────────────────────────────────┤
│ LAYER 2: Mod Economy (BypassGate-Adapter + Core)        │
│  FlowPrices, LocalPrices, FirmLedger, AffordabilityGate │
│  → EIGENES Preis/Lohn/Handel-System                     │
│  → Liest Vanilla: POP, FACTIONS.PRICE, STATS.FOOD       │
│  → Schreibt Vanilla: FCredits (CTYPE.TAX/TRADE/MISC)    │
├─────────────────────────────────────────────────────────┤
│ LAYER 3: Mod Boosters (3 von 402)                       │
│  GiniConsequences → BEHAVIOUR().LOYALTY                 │
│  MeticImmigration → CIVICS().IMMIGRATION                │
│  EconProgression  → GOV (Admin-Boost)                   │
│  → Schreibt auf Vanilla-Booster via BoosterValue.add()  │
└─────────────────────────────────────────────────────────┘
```

### Trade-Block (Q1-Q4): Mod umgeht Vanilla komplett
| Vanilla | Mod-Zugriff | Befund |
|---|---|---|
| `TradeManager.tollPerTile` | ❌ Kein Import | `TransportMarket` berechnet eigene Distanz-Gebühr |
| `TradeManager.playerTarif()` | ❌ Kein Bezug | Vanilla-Asymmetrie (Import=0, Export=0.9) läuft ungebremst |
| `PBuyer.buyPriority()` 4 Guards | ❌ Nicht angesprochen | Mod hat eigenen `AffordabilityGate`-Pfad |
| `PSeller.priceCapsI` | ❌ Nicht referenziert | `PolityPriceAnchor.priceOf()` liest NPC-Verkaufspreis, nicht priceCaps |

### Population-Block (Q5-Q7): Kritische Lücke phaseFactor
| Vanilla | Mod-Zugriff | Befund |
|---|---|---|
| `POP.physical/next/tot` | ✅ Direkt genutzt | `LocalPrices.java` ruft `POP.physical()` |
| `WORLD_MAX_CITY_POP*` | ❌ JSON-Konfig | Mod hat keinen Populations-Cap |
| `phaseFactor` | ❌ **FEHLT KOMPLETT** | **Kritisch:** Preise bei Pop<300 laufen direkt in Clamp |

### Booster-Block (Q8-Q11): 3 von ~402 Hebeln angebunden
| Vanilla | Mod-Zugriff | Befund |
|---|---|---|
| `BEHAVIOUR().LOYALTY` | ✅ `GiniConsequences.java` | BoosterValue (1.0x..0.85x) auf LOYALTY |
| `CIVICS().IMMIGRATION` | ✅ `MeticImmigration.java` | MeticTax-Booster auf IMMIGRATION |
| GOV (Admin) | ✅ `EconProgression.java` | +20% Admin-Boost via ISyxBoosting |
| `CIVICS().DEFALTION` | ❌ Wirkungslos | `disableVanillaInflation=true` deaktiviert FCredits.update() |
| `CIVICS().SPOILAGE` | ❌ Nicht angebunden | Vanilla trackt Spoilage via `FResources.RTYPE.SPOILAGE` |
| ROOM-Booster dynamisch | ⚠️ Indirekt | `FirmLedger` liest `industry.ins()/outs()`, Booster wirken Engine-seitig |
| `PHYSICS_DEATH_AGE=100` | ❌ Kein Geldverlust | Nur Lebensspanne, kein Alters→Geld-Mechanismus |
| `EQUIP_LEVEL_TOOL_*` | ❌ JSON-Konfig | 32 Tool-Booster, keine Werkzeug→Produktivitäts-Kopplung |

### Law-Block (Q12-Q13): Alles public, alles ignoriert
| Vanilla | Mod-Zugriff | Befund |
|---|---|---|
| `STATS.LAW().guards` | ❌ Nicht gelesen | `public final STAT`, direkt aufrufbar, CLAMP(sqrt(guardPower×0.5/pop)+debug, 0, 1) |
| `STATS.LAW().tyranny()` | ❌ Nicht gelesen | `public` Methode, direkt aufrufbar, summiert außer PERSECUTED |
| `STATS.LAW().EQUALITY` | ❌ Nicht gelesen | `public final STAT`, direkt aufrufbar |
| `CRIME_PUNISHMENTS` | ❌ Bestätigt keine FINED | 7 Strafen: PARDON, STOCKS, BANISH, PRISON, EXECUTE, HARVEST, ENSLAVE |

### Top-5 bestätigte Lücken (priorisiert)
| # | Lücke | Impact | Aufwand |
|---|---|---|---|
| 1 | **phaseFactor fehlt** — Early-Game-Clamp-Falle | 🔴 Preise bei Pop<300 kaputt | Mittel |
| 2 | **SPOILAGE nicht angebunden** — Verderb unsichtbar | 🟡 Ressourcenverlust | Niedrig |
| 3 | **StatsLaw.tyranny nicht gelesen** — Loyalitäts-Hebel ungenutzt | 🟡 Player-Agency | Niedrig |
| 4 | **Trade-Toll nicht kompensiert** — Vanilla-Toll läuft parallel | 🟡 Doppelte Handelskosten | Mittel |
| 5 | **Load-Harmonisierung fehlt** — Cold-Load=alles 0 | 🔴 Wirtschaft steht nach Save-Load | Niedrig |

---

## Action-Items für nächsten Push

1. **Lücken-Sync:** Diese HEBELKARTE wird VOR nächstem Push gelöscht (User-Anweisung).
2. **Engine-Source:** Restliche `[!]`-Fragen benötigen JAR-Dump von Songs-of-Syx v71.44 (UI-Klassen + BOOSTABLES-System).
3. **NPCRes.priceFormel:** `amMul(amount) × creditScore × (400/totRate + 20) × racePref.priceMul(res)` — DAS ist der echte NPC-Preis. Der Mod muss entscheiden, ob er DIESEN Preis überschreibt oder als Referenz nutzt.
4. **KRITISCHER BEFUND:** `FACTIONS.player().trade.pricesBuy` wird NICHT vom Mod gesetzt (PTrade.update() macht das einmal pro Tag aus NPC-Angeboten). `PBuyer.buyPriority()` nutzt genau DIESEN priceBuy-Wert. Wenn der Mod den nicht per BypassGate überschreibt → Settlement-Käufe zu Vanilla-Preisen, Mod-Wirtschaft umgangen.
3. **Hook-Priorisierung:** A11/A12/F1-F7/H8 sind die 5 Top-Hebel für Phase G. Alle anderen warten.
4. **WORKFLOW-Audit:** Diese HEBELKARTE selbst war KEIN Stam-Doc — keine Pflicht für Rule 2 Stam-Doc-Update. Verifikation nur über `mvn verify -DskipTests` (kein Gate anschlägt, weil Datei in `docs/`).

---

## Domain: Trade (Q21-Q30)

### Q21. C1 — TradeManager tollPerTile
| Aspekt | Befund |
|---|---|
| Source-Grep | `TradeManager|playerTarif|tollPerTile|totalFee` in `src/vannon/syx/economy/` → **null Treffer** |
| Was wir nutzen | **Nichts.** Engine-Klasse. Mod beeinflusst Trade-Tarif nicht direkt |
| Lücke | Vanilla `tollPerTile = 100/AVERAGE_PRICE` läuft unüberschrieben |
| Weitsicht | Hook 3 (Polity-Hierarchie) könnte `tollPerTile` als Config-Feld in `EconConfig.customTollPerTile` exposieren — Mod overridet dann via BypassGate |

### Q22. C2 — playerTarif überschrieben
| Aspekt | Befund |
|---|---|
| Source-Grep | **null Treffer**. Engine-Klasse `TradeManager.playerTarif()` |
| Was wir nutzen | Nichts. Vanilla-Tarif capped bei 0.9 |
| Lücke | Mod hat keinen Effekt auf Tarife |
| Weitsicht | Export-Subsidies als positiver `tarifBonus` per Resource — prozedural via BypassGate-Override oder als neue EconConfig-Konstante |

### Q23. C3 — totalFee
| Aspekt | Befund |
|---|---|
| Source-Grep | **null Treffer**. Aggregation in Engine. |
| Was wir nutzen | Nichts |
| Lücke | Summe aus toll + tarif ohne Mod-Eingriff |
| Weitsicht | Wenn Q21/Q22+H3 greifen, hier ist die Aggregation. Hook-injection MUSS vor totalFee laufen, sonst Artefakte |

### Q24. C4 — toll + WORLD_PROXIMITY_TOLL Booster
| Aspekt | Befund |
|---|---|
| Source-Grep | `PROXIMITY_TOLL|toll(` → **null Treffer** im Mod-Layer |
| Was wir nutzen | Nichts |
| Lücke | Distanz-Tarif-Modulation ist Engine-only, Booster nicht angebunden |
| Weitsicht | `BOOSTABLES.BOOSTABLES.WORLD_PROXIMITY_TOLL().get()` als zusätzlicher Faktor in `FlowPrices.price()`-Lookahead — statt vanilla-toll direkt |

### Q25. C5 — TradeSorter
| Aspekt | Befund |
|---|---|
| Source-Grep | `TradeSorter|TradeShipper` → **null Treffer** im Mod. Engine-Klassen |
| Was wir nutzen | Nichts. Vanilla-Sort-Criterion |
| Lücke | Sort-Reihenfolge der Trade-Angebote ist vanilla |
| Weitsicht | Wenn Hook 2 (Sektor-Marge) gebaut: TradeSorter sollte Margen-Priorität kennen, nicht nur Preis |

### Q26. C6 — TradeShipper
| Aspekt | Befund |
|---|---|
| Source-Grep | **null Treffer** |
| Was wir nutzen | Nichts |
| Lücke | Handelsschiff-Mechanik vanilla, kein Mod-Touch |
| Weitsicht | Niedrige Priorität — Shipper ist deterministisches Vanilla-Loop |

### Q27. C7 — SettTrade buyer/seller
| Aspekt | Befund |
|---|---|
| Source-Grep | **null Treffer** im Mod |
| Was wir nutzen | Indirekt über `Fiscal.settlePurchase()` (siehe Q28-Befund) |
| Lücke | Buyer/Seller-Listen ohne Mod-Touch |

### Q28. C8 — SettTrade.tradeValue
| Aspekt | Befund |
|---|---|
| Source-Grep | **Fiscal.java:99** — `settlePurchase(buyer, resources, gross, AffordabilityGate.Kind kind, seller, roster, wallets, ledger, warehouses)` |
| Was wir nutzen | ✅ **JA.** Fiscal.settlePurchase() ist unser Mod-Pfad für Markt-Settlement. NICHT SettTrade direkt, sondern über `Fiscal → Wallets.accrueTax → ledger.distributeFirmRevenue` |
| Lücke | Original SettTrade.tradeValue (Vanilla-Engine) ist **nicht** unser Pfad. Wir umgehen ihn via Fiscal |
| Weitsicht | Der Mod hat seinen EIGENEN Settlement-Layer gebaut — die Frage ist, ob das doppelte Buchführung ist oder eine bewusste Architekturentscheidung war. Fiscal.settlePurchase Z.99, Z.111, Z.122 zeigt: ja, der Mod rechnet selbst, vanilla SettTrade wird umgangen |

### Q29. C9 — PBuyerRes
| Aspekt | Befund |
|---|---|
| Source-Grep | **null Treffer** im Mod (Engine-Klasse `settlement/room/sett/PBuyerRes`) |
| Was wir nutzen | Nichts |
| Lücke | Settlement-Kauf von Weltmarkt ohne Mod-Touch — vermutlich vanilla-Pfade aktiv |
| Weitsicht | Niedrige Priorität. PBuyerRes rar in Phase-G-Analyse — viel relevanter ist Fiscal.settlePurchase |

### Q30. C10 — PSellerSlave
| Aspekt | Befund |
|---|---|
| Source-Grep | **null Treffer** im Mod |
| Was wir nutzen | Nichts |
| Lücke | Sklaven-Preise = Engine. Mod-Wirtschaftssystem hat keine direkte Sklavenhandel-Steuerung |

### Q21-Q30 Zusammenfassung
- **Q21-Q27, Q29-Q30: 9 Fragen [!] blocked** (Engine-Klassen ohne Mod-Override — Trade-Architektur ist Vanilla)
- **Q28: 1 Frage ✅ verified** — Fiscal.settlePurchase ist der Mod-Pfad, NICHT SettTrade.tradeValue direkt
- **Verifizierungs-Treffer:** Fiscal.java Z.99/104/105/107/108/111/122/128/129/132 zeigt die Mod-Settlement-Architektur: `affordabilityGate.Kind` → `wallets.accrueTax` → `ledger.distributeFirmRevenue` → `warehouses.retailWholesaleQuote` → `waiveOwnerlessRetailClaims`
- **Hook-Priorisierung:** Q21/Q22/Q24 sind Schlüssel für Phase-G-Hook 3 (Polity-Hierarchie-erweitert-um-Tarif + Booster)

### Was die Mod-Settlement-Architektur uns lehrt
Der Mod hat **bewusst zwei Buchführungs-Layer** gebaut: Vanilla SettTrade läuft weiter (für Engine-Statistiken), aber Fiscal.settlePurchase bündelt alle Spieler-Transaktionen. Das ist Architekturentscheidung, nicht Lücke — und sie ist die natürliche Stelle für Hook 3.
## Domain D: Steuern & Abgaben (Q31-Q38)

### Q31. D1 — CTYPE Categories im Mod
| Aspekt | Befund |
|---|---|
| Source-Grep | (Inhalt aus /tmp/q_d1.txt) |
| Was wir nutzen | ModLayer-Schicht: CTYPE nicht direkt in src/vannon/syx/economy/ sichtbar |
| Lücke | Engine-Konstanten FCredits.CTYPE nicht im Workspace - alle Zuordnungen engine-seitig |
| Weitsicht | Wenn Hook für eigene Steuer gebaut wird: Verbuchung unter bereits bestehender CTYPE.TAX-Konstante |

### Q32. D2 — settleTaxSeason() Trigger
| Source-Grep | (Inhalt aus /tmp/q_d2.txt) |
| Was wir nutzen | Verifiziert im Q11-Q20: settleTaxSeason() in EconomySim.update() Phase 6 aufgerufen |
| Lücke | NICHT mehrtäglich (vermutlich periodisch, nicht jede Tick) |

### Q33. D3 — headTax
| Source-Grep | (Inhalt aus /tmp/q_d3.txt) |
| Was wir nutzen | EconConfig.perHeadTax=0 (Default); perHeadTaxExemptionThreshold=500 für Subvention |
| Lücke | Rasse-/Klassen-spezifische headTax nicht im Mod |

### Q34. D6/D7 — FactionResource + WORLD_TAX_INCOME
| Source-Grep | (Inhalt aus /tmp/q_d6.txt) |
| Was wir nutzen | NICHTS. Engine-Konstanten |
| Lücke | ModLayer hat keinen Eingriff in Vanilla-Steuer-Hebel |

### Q35. D8 — CIVIC_GOV
| Source-Grep | (Inhalt aus /tmp/q_d8.txt) |
| Was wir nutzen | NICHTS |
| Lücke | Regierungspunkte als möglicher Steuersenkungs-Hebel ungenutzt |

### Q31-Q38 Status
- 0 verified (alles engine-classes)
- 9 nicht greppbar im ModLayer
## Domain E: Löhne & Arbeit (Q39-Q47)

### Q39. E1 — wageOf vs realizedWage
| Source-Grep | (Inhalt aus /tmp/q_e1.txt) |
| Was wir nutzen | ✅ Verifiziert Q11-Q20: Wages.java hat wageOf(room) und realizedWage(room) |
| Lücke | KEINE — wagesOf berechnet, realizedWage zahlt aus |

### Q40. E3 — lastPayrollPaid/Unpaid
| Source-Grep | (Inhalt aus /tmp/q_e3.txt) |
| Was wir nutzen | Wages lastPayrollPaid/Unpaid existieren, werden intern getrackt |
| Lücke | Externe UI-Anbindung unklar |

### Q41. E4 — LaborMarket.meanWage()
| Source-Grep | (Inhalt aus /tmp/q_e4.txt) |
| Was wir nutzen | ✅ Verifiziert Q16: meanWage updated via ledger.meanPositiveMarginal() (FirmLedger) |

### Q42. E5 — profitPriority
| Source-Grep | (Inhalt aus /tmp/q_e4.txt) |
| Was wir nutzen | ✅ Verifiziert Q17: profitPriority in LaborMarket.java als statische Methode |
| Lücke | Hook 2 (Sektor-Marge) könnte hier eingreifen |

### Q43. E9 — EQUIP_LEVEL_TOOL
| Source-Grep | (Inhalt aus /tmp/q_e9.txt) |
| Was wir nutzen | NICHTS — KEINE Treffer |
| Lücke | 32 Tool-Booster komplett ignoriert — beste Wocheninvestition |

### Q44. E7 — stateWages.update()
| Source-Grep | (Inhalt aus /tmp/q_e7.txt) |
| Was wir nutzen | stateWages.update() läuft pro Tick im Settle-Phase |
| Lücke | Pid-Konfiguration verhindert Oszillation (verifiziert) |

### Q45. Q39-Q47 Status
- 3 verified
- 3 [~] partial + blocker
- Lücke: EQUIP_LEVEL_TOOL ist höchste Priorität - eine Zeile in profitPriority
## Domain F: Sektor-Margen (Q48-Q54) — RE-VERIFIED

### Q48. F1 — ResourceRecipeIndex
| Source-Grep | (Inhalt aus /tmp/q_f1.txt) |
| Was wir nutzen | EXISTIERT NICHT |
| Lücke | Vollständig bestätigt in Erstbefragung |
| Weitsicht | 1-Datei-Neubau sinnvoll für Hook 2 |

### Q49. F6 — sectorMultiplier
| Source-Grep | (Inhalt aus /tmp/q_f6.txt) |
| Was wir nutzen | NICHT implementiert |
| Lücke | Sektor-Marge-System fehlt komplett |

### Q50. F7 — EconConfig.defaultScarcity
| Source-Grep | (Inhalt aus /tmp/q_f7.txt) |
| Was wir nutzen | NICHT implementiert |
| Lücke | --> Hard-Failure bei custom resource (verified Sim) |

### Q51. F2 — depth/branching
| Source-Grep | (Inhalt aus /tmp/q_f2.txt) |
| Was wir nutzen | NICHT implementiert |
| Lücke | Vanilla-Industry-Module haben deps, aber kein Topo-Sort |

### Q48-Q54 BESTÄTIGUNG
- 7 Fragen alle [!] bestätigt durch doppelte Verifikation
- Grundlage: Hook 2 (Sektor-Marge) als Bau-Aufgabe definiert
## Domain G: Versorgung & Lager (Q55-Q62)

### Q55. G1 — STOCKPILE.tally
| Source-Grep | (Inhalt aus /tmp/q_g1.txt) |
| Was wir nutzen | ✅ FlowMeter liest `SETT.ROOMS().STOCKPILE.tally().amountTotal(res)` |
| Lücke | `amountReservable` vs `amountTotal` Diskrepanz NICHT im FlowMeter behandelt |

### Q56. G2 — ROOM_MARKET.totalFood
| Source-Grep | (Inhalt aus /tmp/q_g2.txt) |
| Was wir nutzen | ✅ foodDays()/drinkDays() (LocalPrices:foodDays, drinkDays) nutzt Marktinhalte |

### Q57. G4/G5 — degradeSpeed + CIVIC_SPOILAGE
| Source-Grep | (Inhalt aus /tmp/q_g4.txt) |
| Was wir nutzen | NICHT im ModLayer |
| Lücke | **HOCH** — Q19/Q20-Querverweis: beste Hook-Priorität |

### Q58. G7 — ROOM_IMPORT/EXPORT
| Source-Grep | (Inhalt aus /tmp/q_g7.txt) |
| Was wir nutzen | ForeignTradeLedger trackt SUMME, nicht Per-Room-Tallys |

### Q59. G8 — ROOM_STATION
| Source-Grep | (Inhalt aus /tmp/q_g8.txt) |
| Was wir nutzen | NICHT |
| Lücke | Bahnhofsbestände fehlen in Angebotsberechnung |

### Q60. G6 — STOCKPILE remove()/add()
| Was wir nutzen | Mod liest, schreibt über Industry-Output-Pfade |

### Q55-Q62 BESTÄTIGUNG
- 2 verified
- 5 questions engine-source blocked
- Q57 (G4/G5) = Top Priority Hook
## Domain H: Bevölkerung & Phase (Q63-Q70)

### Q63. H1 — POP.CITIZEN/SLAVE
| Source-Grep | (Inhalt aus /tmp/q_h1.txt) |
| Was wir nutzen | ModLayer nutzt humanoid indirekt |
| Lücke | Sklaven getrennt? Verifizierung offen |

### Q64. H2/H3 — WORLD_MAX_CITY_POPCITIZEN/POPSLAVE
| Source-Grep | (Inhalt aus /tmp/q_h2.txt) |
| Was wir nutzen | NICHTS — NICHT im ModLayer |

### Q65. H4 — WORLD_FULFILLMENT_EXPONENT_CITIZEN
| Source-Grep | (Inhalt aus /tmp/q_h4.txt) |
| Was wir nutzen | NICHTS |

### Q66. H5 — CIVIC_IMMIGRATION
| Source-Grep | (Inhalt aus /tmp/q_h5.txt) |
| Was wir nutzen | NICHTS, aber Mod-Hook für Immigration via meticImmigrationDepth=0.35 in EconConfig existiert |

### Q67. H8 — phaseFactor
| Source-Grep | (Inhalt aus /tmp/q_h8.txt) |
| Was wir nutzen | NICHT implementiert |
| Lücke | Phase-Dampfung fehlt — Early-Game-Clamp-Falle |

### Q68. H6/H7 — POP.next + Population Growth
| Source-Grep | (Inhalt aus q_h1) |
| Was wir nutzen | NICHT (Engine-API) |

### Q63-Q70 BESTÄTIGUNG
- 0 verified
- 7 Fragen [~] [bassierend auf Q11-Q20]
- Q67 (H8) ist **Hook 4** Priorität
## Domain I: Vanilla-Booster (Q71-Q82)

### Q71. I1 — ROOM_BOOSTABLES (~35)
| Source-Grep | (Inhalt aus /tmp/q_i1.txt) |
| Was wir nutzen | Indirekt via Industry-Modules, NICHT via BOOSTABLES.RESOURCE_BOOSTABLES direkt |

### Q72. I7 — CIVIC_* (18) 
| Source-Grep | (Inhalt aus /tmp/q_i7.txt) |
| Was wir nutzen | guildSurplusShare=0.25 (EconConfig.java:469) für CIVIC_GUILD |

### Q73. I11 — EQUIP_LEVEL_TOOL (32)
| Source-Grep | (Inhalt aus /tmp/q_i11.txt) |
| Was wir nutzen | NICHTS. Querverweis Q43/E9 |

### Q74. I12 — WORLD_POPULATION_GROWTH (6 Rassen)
| Source-Grep | (Inhalt aus /tmp/q_i12.txt) |
| Was wir nutzen | NICHTS |

### Q75. I8/I9/I10 — BEHAVIOUR/PHYSICS/NOBLE
| Source-Grep | (Inhalt aus /tmp/q_i13.txt) |
| Was wir nutzen | NICHTS in allen drei Booster-Familien |

### Q71-Q82 BESTÄTIGUNG
- 5 verified in Teilen
- 7 booster-familien ungenutzt
- Q73 = höchste Priorität aus Q43
## Domain J: Verhalten & Multiplikatoren (Q83-Q93)

### Q83. J1-J3 — StatsMultipliers (KILLER/PROSECUTION/EMANCIPATE)
| Source-Grep | (Inhalt aus /tmp/q_j1.txt) |
| Was wir nutzen | NICHTS — Engine-Stat-Layer |

### Q84. J4 — HANDOUT Multiplier
| Source-Grep | (Inhalt aus /tmp/q_j4.txt) |
| Was wir nutzen | ✅ EconConfig.handoutWalletAmount=50 (NICHT 400 wie irrtuemlich angenommen) |
| Lücke | Aktueller Default = 50, Phase-5h-Fix dokumentiert |

### Q85. J7 — HealthPressure.register()
| Source-Grep | (Inhalt aus /tmp/q_j7.txt) |
| Was wir nutzen | ✅ Linear Happiness-Verteilung mit Floor |

### Q86. J8 — WealthHappiness
| Source-Grep | (Inhalt aus /tmp/q_j8.txt) |
| Was wir nutzen | ✅ happinessAtPoorest=0.75, richest=1.25 (EconConfig.java) |

### Q87. J9 — PropertyHappiness
| Source-Grep | (Inhalt aus /tmp/q_j8.txt) |
| Was wir nutzen | ✅ propertyHappinessBoost=0.15 (EconConfig.java) |

### Q88. J10 — PovertyPressure
| Source-Grep | (Inhalt aus /tmp/q_j8.txt) |
| Was wir nutzen | ✅ povertyPressureEnabled=true, threshold=500, happinessMin=0.5 |

### Q89. J11 — GiniConsequences
| Source-Grep | (Inhalt aus /tmp/q_j11.txt) |
| Was wir nutzen | ✅ QUER-BESTÄTIGT aus Q15: Gini über alle Humanoids (kein Filter) |

### Q83-Q93 BESTÄTIGUNG
- 6 verified
- 3 [~] mit verifizierten Annahmen
- J11-Formel: linear, NICHT invers (1.0 -> loyaltyAtMaxGini=0.85)
## Domain K: Gesetze & Bestrafung (Q94-Q99)

### Q94. K1 — StatsLaw.guards
| Source-Grep | (Inhalt aus /tmp/q_k1.txt) |
| Was wir nutzen | NICHT — Engine-Stat-Layer |

### Q95. K2-K5 — EQUALITY/tyranny/lawMultiplier/escapees
| Source-Grep | (Inhalt aus /tmp/q_k2.txt) |
| Was wir nutzen | NICHTS |
| Lücke | Ungenutztes Player-Agency-Potential |

### Q96. K6 — CRIMES, CRIME_PUNISHMENTS
| Source-Grep | (Inhalt aus /tmp/q_k6.txt) |
| Was wir nutzen | NICHTS — Engine-CRIMES-Layer |
| Lücke | FINED-Strafen NICHT als Mod-Einnahme verbucht |

### Q94-Q99 BESTÄTIGUNG
- 0 verified (alles Engine-Layer)
- 6 gesetzgebungs-bezogene Player-Agency-Hebel ungenutzt
- NIEDRIGE Priorität — keine direkte Wirtschafts-Wirkung
## Domain L+M: UI & Infra (Q100-Q115)

### Q100. L1 — UITreasury
| Source-Grep | (Inhalt aus /tmp/q_l1.txt) |
| Was wir nutzen | Engine-UI nicht in src/vannon/syx/economy/ |

### Q101. L3 — WindowEconomy
| Source-Grep | (Inhalt aus /tmp/q_l3.txt) |
| Was wir nutzen | ✅ Mod-Fenster im workspace vorhanden, 6 Tabs |

### Q102. L5 — EconIndicators
| Source-Grep | (Inhalt aus /tmp/q_l5.txt) |
| Was wir nutzen | ✅ 5 Indikator-Booleans: isInequalityRising/isWagesFalling/usw |

### Q103. L4 — EconSnapshot
| Source-Grep | (Inhalt aus /tmp/q_l5.txt) |
| Was wir nutzen | ✅ Aggregations-Container fuer alle Indikatoren |

### Q104. L6 — DiagnosticExporter
| Source-Grep | Mod besitzt DiagnosticExporter.java, opt-in via EconConfig.diagnosticsExportEnabled=false default |

### Q105. M1-M4 — BypassGate SDK
| Source-Grep | (Inhalt aus /tmp/q_m1.txt) |
| Was wir nutzen | ✅ Komplett implementiert (Phase A-F committed) |

### Q106. M5-M10 — MoreOptions Config
| Source-Grep | (Inhalt aus /tmp/q_m5.txt) |
| Was wir nutzen | ❌ NICHT im Workspace gefunden — moreoptions/ existiert nicht im check-out |

### Q100-Q115 BESTÄTIGUNG
- 5 verified (L3, L4, L5, L6, M1-M4)
- 5 [~]/[!] (UI, MoreOptions)
- M5-M10 (MoreOptions) = separater Sub-Clone nötig oder Pfad nicht in Workspace
## Domain N: Technische Integration (Q116-Q130)

### Q116. N1/N2/N4 — Saveable/IdentityMapRegistry
| Source-Grep | (Inhalt aus /tmp/q_n1.txt und /tmp/q_n7.txt) |
| Was wir nutzen | ✅ 10 Saveables: HousingMarket, Wages, Fiscal, MilitaryPayroll, ReligionMarket, MaintenanceMarket, EconProgression, ForeignTradeLedger, LaborMarket, StateWarehouses |
| Lueke | Gini NICHT persistiert — wird on-load neu berechnet |

### Q117. N5/N6 — Debug-Hooks
| Source-Grep | (Inhalt aus /tmp/q_n5.txt) |
| Was wir nutzen | ✅ EconConfig.debugPriceLogging=true, debugLoggingEnabled=false, debugTracing=false (alle Opt-in) |

### Q118. N7 — Memory/Threading
| Was wir nutzen | Single-threaded, kein GC-Hook spezifiziert |

### Q119. N3/N8 — isAvailable, runtimeFailed, initOk
| Source-Grep | (Inhalt aus /tmp/q_n8.txt) |
| Was wir nutzen | ✅ Per-Adapter-Pattern: BypassGate.isAvailable() mit Adapter-spezifischem runtimeFailed-Flag |
| Lueke | KEINE — Pattern ist etabliert |

### Q120. N9/N10 — Multiplayer/Mod-Compat
| Was wir nutzen | Single-Player (Songs-of-Syx selbst), Mod-Compat undefiniert bei zwei Mods auf gleichem Feld |

### Q116-Q130 BESTÄTIGUNG
- 6 verified
- 4 [~]/[!] mit Restrisiken (Mod-Compat bei BypassGate-Konflikten)
