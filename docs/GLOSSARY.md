# SyxEconomyMod — Glossar

> **Version 0.1.0** | **112 Java-Klassen** | **Stand: 2026-07-23**
>
> Jeder Eintrag: Name, Kategorie, was der Name suggeriert vs. was die Klasse **tatsächlich** tut.

---

## 🟦 Kategorie 1: VANILLA WRAPPER

*Adapter und Brücken-Klassen, die Songs-of-Syx-Engine-APIs zugänglich machen.
Dies sind die einzigen Dateien im gesamten Projekt, die Vanilla-Klassen direkt importieren oder per Reflection anfassen.*

### Adapter-Interfaces (5 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`ISyxAI`** | "KI-Interface" | Kapselt 6 `Class.forName()`-Aufrufe zur Erkennung package-privater AI-Pläne (`PlanOddjobber`, `F_SPlanEatery`, `F_SPlanCanteen`, `F_PlanEat`, `PlanTavern`, `M_PlanMarket`). Sagt: „macht dieser Bürger gerade X?". |
| **`ISyxTransport`** | "Transport-Interface" | Liest das private `distance`-Feld (float) von `TransportInstance` — die kürzeste Entfernung zwischen zwei Stationen. |
| **`ISyxWarehouse`** | "Lager-Interface" | Ruft die private Methode `storingSet(boolean)` auf `StockpileInstance` auf — sperrt/entsperrt die physische Einlagerung in ein Staatslager. |
| **`ISyxBoosting`** | "Boosting-Interface" | Sucht per `getDeclaredFields()` nach dem `GOV`-Boostable in `BOOSTABLES.CIVICS()`. Wird für den +20%-Admin-Boost beim INDUSTRIE-Aufstieg gebraucht. |
| **`ISyxDiplomacy`** | "Diplomatie-Interface" | Liest/schreibt vier private Felder von `DipWarPlayer`: `upI` (Kriegsbereitschaft), `pPow` (Macht), `coalitionPow` (Koalitionsmacht), `bWilling` (Kriegswille). `willing`-Liste wird via Public-Getter bezogen (kein Reflection). |

### Vanilla-Implementierungen (8 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`VanillaAIAdapter`** | "Standard-KI-Adapter" | `Class.forName(name, true, Humanoid.class.getClassLoader())` — der ClassLoader-Fix. Vergleicht AI-Plan-Namen per `getSimpleName().equals()`. Alle 6 Pläne werden im Konstruktor einmal geladen. |
| **`VanillaTransportAdapter`** | "Standard-Transport-Adapter" | `Field.setAccessible(true)` + `getFloat(instance)`. Einziger Ort im Projekt, der `distance` direkt anfasst. |
| **`VanillaTransportAdapterMH`** | "Optimierter Transport-Adapter" | Wie `VanillaTransportAdapter`, aber per `VarHandle` statt `Field.getFloat()` — 3-6× schneller. Fallback auf Reflection wenn VarHandle-Init fehlschlägt. |
| **`VanillaWarehouseAdapter`** | "Standard-Lager-Adapter" | `Method.invoke(instance, true/false)` für `storingSet(boolean)`. |
| **`VanillaWarehouseAdapterMH`** | "Optimierter Lager-Adapter" | Wie `VanillaWarehouseAdapter`, aber per `MethodHandle` — 3-6× schneller. |
| **`VanillaBoostingAdapter`** | "Standard-Boosting-Adapter" | Iteriert `BOOSTABLES.CIVICS().getClass().getDeclaredFields()` und sucht nach einem Feld vom Typ `Boostable` mit passendem Namen. |
| **`VanillaDiplomacyAdapter`** | "Standard-Diplomatie-Adapter" | `Field.setInt()`/`getInt()` für `pPow`, `coalitionPow`, `upI`; `getBitmap()` für `bWilling`; `war.willing()` (public Getter) für die Fraktionsliste. |
| **`VanillaDiplomacyAdapterMH`** | "Optimierter Diplomatie-Adapter" | Wie `VanillaDiplomacyAdapter`, aber per `VarHandle` — 3-6× schneller. |

### Fallback-Implementierungen (4 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`FallbackTransportAdapter`** | "Notfall-Transport" | `getDistance()` → 0. Wenn Reflection auf `distance` scheitert: Transport ist gratis (kein Crash). |
| **`FallbackWarehouseAdapter`** | "Notfall-Lager" | `setStoring()` → No-Op. Wenn Reflection auf `storingSet` scheitert: Sperre via Pricing-Lock (kein Crash). |
| **`FallbackBoostingAdapter`** | "Notfall-Boosting" | `getAdminBoostable()` → null. Wenn Reflection auf `GOV` scheitert: kein Admin-Boost im INDUSTRIE-Stadium. |
| **`FallbackDiplomacyAdapter`** | "Notfall-Diplomatie" | Alle Getter → 0/null, alle Setter → No-Op. `DebtDiplomacyBuffer` überspringt dann die Puffer-Logik komplett. |

### Package-Private Brücken-Klassen (4 Dateien)

*Diese Klassen existieren nur, weil Vanilla package-private Felder hat. Sie liegen im exakt gleichen Package wie die Vanilla-Klasse, auf die sie zugreifen — kein Reflection, compile-time sicher.*

| Klasse | Package | Was sie tut |
|--------|---------|-------------|
| **`LaborMarketAccess`** | `settlement.room.main.employment` | Liest/schreibt `RoomEmployment.Priority` (package-private). `freeShare()`, `restorePriority()`, `employmentOf()`. Ohne diese Klasse: kein Arbeitsmarkt. |
| **`EconomyTavernAccess`** | `settlement.room.service.food.tavern` | Liest `TavernInstance`-Felder (package-private): aktueller Drink-Vorrat, Maximalbestand. |
| **`EconomyEateryAccess`** | `settlement.room.service.food.eatery` | Liest `EateryInstance`-Felder (package-private): aktueller Food-Vorrat, Maximalbestand. |
| **`EconomyCanteenAccess`** | `settlement.room.service.food.canteen` | Liest `CanteenInstance`-Felder (package-private): aktueller Food-Vorrat, Maximalbestand. |

---

## 🟩 Kategorie 2: SIMULATION

*Die eigentliche Wirtschaftslogik — Geld, Preise, Firmen, Bürger, Märkte. Keine Vanilla-Importe (außer über Adapter).*

### Orchestrator (2 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`EconomySim`** | "Wirtschafts-Simulation" | **Das Herz des Mods.** Zentrale Instanz, tickt jede Stunde (`update()`). Orchestriert ALLE Subsysteme in fester Reihenfolge: Roster → Preise → Firmen → Arbeit → Services → Steuern → Transfers → Audit → Krisen → Export. 600+ Zeilen, ~40 Subsystem-Aufrufe pro Tick. |
| **`InstanceScript`** | "Instanz-Skript" | Eintrittspunkt vom Spiel: erstellt `EconomySim`, `WindowOverview`, `WindowEconomy`, `WindowState`, `SubjectWallet`. Ruft `EconConfig.init()` auf (Lazy-Vanilla-Init). |

### Geld & Brieftaschen (7 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`Wallets`** | "Geldbörsen" | HashMap `Humanoid → int`. Jeder Bürger hat genau ein Wallet. Stage-gated: SUBSISTENZ=200 D, HANDEL=500 D, INDUSTRIE=2000 D, WOHLSTAND=5000 D Startkapital. `exchange()` = Yard-Sale-Transfer (Bürger kauft von anderem Bürger). NICHT nur ein Datencontainer — enthält Transferlogik. |
| **`SubjectWallet`** | "Einzel-Wallet" | Das Popup das erscheint wenn du auf einen Bürger klickst. Zeigt Vermögen, Einkommen, Klasse, Steuern. READ-ONLY. |
| **`WealthStats`** | "Vermögens-Statistik" | Gini-Koeffizient, Median, Top-10%-Anteil, Durchschnittsvermögen. Berechnet aus `Wallets`-HashMap. Wird jede Saison aktualisiert. |
| **`WealthHappiness`** | "Vermögens-Glück" | Registriert einen `BValue.BValueInduOnly` auf `BOOSTABLES.BEHAVIOUR().HAPPI`: reichere Bürger (relativ zum Median) sind glücklicher. Individuell, nicht gruppenbasiert. |
| **`Fiscal`** | "Fiskalpolitik" | Staatliche Ausgaben-Engine: `disburse()`. Zahlt Staatsgehälter (Lager, Transport, Militär), Subventionen. Liest `FCredits.CTYPE` für detaillierte Buchhaltung. NICHT Steuereinnahmen (das ist `Taxes`). |
| **`Escrow`** | "Treuhandkonto" | Prüft ob ein Bürger sich eine Transaktion leisten KANN (nicht: ob er sie tätigt). `spendable()`, `canAfford()`. Der „Geldbeutel-Check vor dem Kauf". |
| **`EscrowKernel`** | "Treuhand-Kern" | Reine Berechnungs-Hilfsfunktionen für `Escrow`. Kein State. |

### Preise & Ressourcen-Flüsse (8 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`FlowMeter`** | "Fluss-Messer" | **Die Vanilla-Quelle für Materialströme.** Liest `FResources` pro Ressource: `producedPerDay`, `consumedPerDay`, `stockTotal`, `tradeBalance`. Baut `FirmSnapshot`-Records für `FirmLedger`. NICHT unser Code — wrappt Vanilla `FResources`. |
| **`FlowPrices`** | "Fluss-Preise" | Angebot/Nachfrage → Preis. `price(resource)` = Ankerpreis × f(coverage). Coverage < 0.5 → Preis steigt, Coverage > 2.0 → Preis fällt. Formel: `anchorPrice × (1 + max(0, 1 - coverage)²)`. |
| **`LocalPrices`** | "Lokale Preise" | Liest Vanilla `BOOSTABLE_O` für lokale Marktpreise (Food, Drink). Zwei One-Shot SEAM-Logs bei Zugriffsfehler. |
| **`PolityPriceAnchor`** | "Reichs-Preisanker" | Liest den Handelsanker-Preis einer Ressource aus `FactionNPC`/`RD`. Der Anker ist der „faire" Preis im gesamten Reich. |
| **`ScarcitySignal`** | "Knappheits-Signal" | 0.0–1.0 Score pro Ressource. Kombiniert zwei Indikatoren: (1) `stockChangePerDay` negativ → Bestand schrumpft, (2) `stock=0` + `demand>0` → sofortige Krise. |
| **`InflationOff`** | "Inflation aus" | Registriert einen `BValue`-Booster auf `BOOSTABLES.CIVICS().DEFLATION` — unterdrückt die Vanilla-Inflation komplett. |
| **`RationOptimizer`** | "Rationen-Optimierer" | Berechnet optimale Rationen-Verteilung für `GrainDole`. Rein mathematisch, kein State. |
| **`Histogram`** | "Histogramm" | Schreibt Vermögens-Verteilungs-Dumps als CSV: 10 Bins, Bürger pro Bin, kumulativ. Früher periodisch via `dumpIntervalTicks`, jetzt manuell via UI. |

### Firmen & Betriebe (6 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`FirmLedger`** | "Firmen-Hauptbuch" | **Betriebsbuchhaltung + Analytics + Export-Source.** Pro Firma: Input/Output-Werte, Profit, `marginalProfit`, `workersUnpaid`. Berechnet, ob eine Firma strukturell unprofitabel ist. Baut `FirmFinancialSnapshot`-Records für `DiagnosticExporter`. |
| **`FirmEconomyKernel`** | "Firmen-Wirtschafts-Kern" | Reine Berechnungs-Hilfsfunktionen für `FirmLedger`. `value()` = Output-Wert − Input-Kosten. Kein State. |
| **`LaborMarket`** | "Arbeitsmarkt" | Lohnfindung, Workforce-Allokation, `meanPositiveMarginal()`. Ordnet Arbeiter den profitabelsten Firmen zu. Kommuniziert mit Vanilla `RoomEmployment` via `LaborMarketAccess`. |
| **`StateWageMarket`** | "Staatslohn-Markt" | Staatliches Lohn-Dumping: `forceHire()` stellt Bürger in Staatsbetriebe ein, auch wenn der Lohn unter Marktniveau liegt. |
| **`OddjobMarket`** | "Tagelöhner-Markt" | Bezahlt Tagelöhner (`oddjobWagePerTask`) aus der Staatskasse. Jeder erledigte Odd-Job = −X D vom Staat, +X D zum Bürger. |
| **`OddjobAutomation`** | "Tagelöhner-Automation" | Passt `oddjobWagePerTask` automatisch an: viele offene Firmen-Jobs → Oddjob-Lohn sinkt (Arbeiter werden in Firmen gedrängt). Arbeiterknappheit → Lohn steigt (Anreiz). |

### Konsum & Essen (9 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`AffordabilityGate`** | "Bezahlbarkeits-Schranke" | Entscheidet, ob ein Bürger essen/trinken DARF: `freeRation()` für GrainDole/Sklaven/Waisen, sonst `escrow.canAfford()`. **Der Blanko-Safety-Net-Bug wurde hier behoben** (vorher: JEDER mit <500 D bekam gratis Essen). |
| **`FoodPlanController`** | "Essens-Plan-Steuerung" | Sagt dem Bürger WIE er essen soll: `isFoodPlan()`-Erkennung, dann `FoodTransactionPlan` oder `BrokeFoodPlan` zuweisen. |
| **`FoodTransactionPlan`** | "Essens-Transaktion" | Führt den tatsächlichen Essenskauf durch: Geld vom Bürger → Staat, Ressource aus Lager entfernt. |
| **`BrokeFoodPlan`** | "Pleite-Essens-Plan" | Wenn ein Bürger kein Geld für Essen hat und nicht GrainDole-berechtigt ist: **verhungert**. EventLog "STARVATION". |
| **`FoodGateKernel`** | "Essens-Schranke-Kern" | Reine Hilfsfunktionen für `AffordabilityGate`. Kein State. |
| **`FoodRollback`** | "Essens-Rückabwicklung" | Wenn ein Bürger beim Essenskauf stirbt oder der Plan wechselt: bereits bezahltes Essen wird rückabgewickelt (Geld zurück, Ressource zurück). |
| **`FoodRollbackKernel`** | "Essens-Rückabwicklung-Kern" | Reine Hilfsfunktionen für `FoodRollback`. Kein State. |
| **`DrinkTransactionPlan`** | "Getränke-Transaktion" | Analog zu `FoodTransactionPlan`, aber für Drinks (Tavernen). |
| **`GrainDole`** | "Kornspende" | Registrierte Bürger (unter `doleWealthThreshold`) bekommen gratis Essen. `doleHeadcap` begrenzt die Anzahl. |

### Services & Dienstleistungen (7 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`ServiceMarket`** | "Service-Markt" | Bürger nutzen bezahlte Dienstleistungen (Bäder, Tavernen, etc.). `serviceCost()` pro Service-Typ. |
| **`ServicePlanController`** | "Service-Plan-Steuerung" | O(1)-Service-Lookup via Cache. Sagt dem Bürger WELCHEN Service er nutzen soll. |
| **`BrokeServicePlan`** | "Pleite-Service-Plan" | Wenn ein Bürger keinen Service bezahlen kann: tut nichts (kein Service, aber auch kein Verhungern). |
| **`ReligionMarket`** | "Religions-Markt" | Tempel-Spenden: Bürger spenden an Tempel, Staat kassiert Tempel-Steuer (`religionTaxRate`). |
| **`Liturgy`** | "Liturgie" | Religiöse Zeremonien: Bürger zahlen für Liturgie-Dienste. Geld fließt an den Staat. |
| **`PurchasePlanController`** | "Kauf-Plan-Steuerung" | Steuert WANN ein Bürger einkaufen geht (Güter, nicht Essen/Trinken). |
| **`Purchases`** | "Einkäufe" | Führt Güterkäufe durch: Bürger kauft Ware vom Markt, Geld transferiert. |

### Immobilien & Wohnen (3 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`HousingMarket`** | "Wohnungsmarkt" | Miete + Zwangsräumung. `collectRent()` zieht Miete ein. Wenn Bürger nicht zahlen kann → `evict()`. Hauskauf/-verkauf via `PropertyLedger`. |
| **`PropertyLedger`** | "Eigentums-Hauptbuch" | **Grundbuch + Firmenanteile + Dividenden.** Wer besitzt welches Haus/welche Firma? `payDividends()` schüttet Gewinne an Anteilseigner aus. `cleanupGoneRooms()` entfernt Geister-Einträge. |
| **`PropertyHappiness`** | "Eigentums-Glück" | Registriert einen `BValue`-Booster auf `BOOSTABLES.BEHAVIOUR().HAPPI`: Eigenheim-Besitzer sind glücklicher. Aktiviert ab WOHLSTAND-Stufe. |

### Steuern & Abgaben (3 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`Taxes`** | "Steuern" | Zieht Steuern ein: `perHeadTax` (Kopfsteuer), `wealthTaxRate` (Vermögenssteuer), `religionTaxRate` (Religionssteuer). NICHT Marktsteuer (die ist in `WarehouseMarket`). |
| **`CorveeController`** | "Fronarbeits-Steuerung" | Zwangsarbeit: Bürger müssen unbezahlt arbeiten. `corveeDraftPercent` der Bevölkerung wird eingezogen. |
| **`DebtBondage`** | "Schuldknechtschaft" | Bürger mit Schulden werden zu Sklaven. `enslave()` wenn `debt > threshold`. |

### Krisen & Druck (6 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`TreasuryCrisis`** | "Staatskassen-Krise" | **5-stufige Krisenmechanik + Hard-Floor-Verhalten in Tier 5**, mit Hysterese. Tier 1 (−5K): Subventionen aus. Tier 2 (−50K): 15 Lohnkonstanten halbiert. Tier 3 (−250K): Zwangs-Liquidation. Tier 4 (−1M): Kopfsteuer=500, Marktsteuer=50%. Tier 5 (−5M): ALLE Systeme deaktiviert + Hard Floor. |
| **`PovertyPressure`** | "Armuts-Druck" | Registriert einen `BValue`-Booster: Armut → Unzufriedenheit. |
| **`HealthPressure`** | "Gesundheits-Druck" | Registriert einen `BValue`-Booster auf `BOOSTABLES.PHYSICS().HEALTH`: wirtschaftliche Lage → Gesundheit. |
| **`GiniConsequences`** | "Gini-Konsequenzen" | Registriert einen `BValue.BValueInduOnly` auf `BOOSTABLES.BEHAVIOUR().LOYALTY`: Gini 0→1 = Loyalty 100%→`loyaltyAtMaxGini` (Default 85%). Gruppeneffekt. EventLog "UNREST" bei Gini>0.35. |
| **`EconIndicators`** | "Wirtschafts-Indikatoren" | **Ring-Buffer aus 6 `EconSnapshot`s** mit Trend-Erkennung. Erkennt: `wagesFalling`, `treasuryDeclining`, `populationDeclining`. Kappt `doleHeadcap` bei sinkenden Einnahmen. |
| **`EconSnapshot`** | "Wirtschafts-Schnappschuss" | Ein einzelner Datenpunkt: alle Metriken zu einem Zeitpunkt (Population, Gini, Treasury, meanWage, foodDays, deaths, etc.). Wird von `EconIndicators` alle 60 Ticks erstellt. |

### Bürger & Demographie (5 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`Roster`** | "Dienstplan" | **Bevölkerungsverwaltung.** `rebuild()` iteriert alle lebenden Humanoids, filtert nach `HCLASSES.CITIZEN`, `WGROUP.WORKER`, etc. Baut Listen für alle anderen Subsysteme. |
| **`CitizenClass`** | "Bürger-Klasse" | Sozial-Klassifikation: BOSS (>30% Firmenanteil), HEIR (Erbe), MIGRANT, POOR, MIDDLE, UPPER. Bestimmt Kaufentscheidungen (Haus, Firmen-Anteile). |
| **`HandoutRelief`** | "Almosen-Hilfe" | **Nur noch für ARBEITENDE Bürger** (Bugfix: vorher für alle). Zahlt bis zu `handoutWalletAmount` wenn `netWorth < doleWealthThreshold`. Betrag skaliert mit Bedarf. |
| **`DebtDiplomacyBuffer`** | "Schulden-Diplomatie-Puffer" | Liest DipWarPlayer-Felder via `ISyxDiplomacy`-Adapter. Berechnet: „wie viele Fraktionen sind gerade abgeschreckt?". Loggt "DIPLO" ins EventLog (max 1×/Saison). |
| **`ProximityPairSource`** / **`RandomPairSource`** / **`PairSource`** | "Paar-Quelle" | Interface + 2 Implementierungen für Bürger-Paar-Selektion. `ProximityPairSource` = nahe beieinander, `RandomPairSource` = zufällig. Genutzt von `Wallets.exchange()`. |

### Produktion & Bau (5 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`ProductionSubsidies`** | "Produktions-Subventionen" | Staat zahlt Firmen pro produzierter Ressource. `subsidyPerUnit × producedPerDay` → Geld vom Staat zur Firma. |
| **`ConstructionHoardController`** | "Bau-Hort-Steuerung" | **Swarming-Schutz für Bauarbeiter.** Reservierungs-Karte (`targetedResources`): ein Bauauftrag ist nur wählbar wenn `remaining > targeted`. Verhindert 50 Bauarbeiter auf 1 Holz. |
| **`ConstructionHoardPlan`** | "Bau-Hort-Plan" | AI-Plan für Bauarbeiter: was tragen sie wohin? Nutzt `ConstructionHoardController` für Reservierungen. |
| **`MaintenanceMarket`** | "Instandhaltungs-Markt" | Gebäude-Instandhaltung: Firmen zahlen für Reparaturen. Geld von Firmen → Staat. |
| **`FurnishingAutomation`** | "Einrichtungs-Automation" | Passt Behausungs-Einrichtungsziele automatisch an: Bürger-Wohlstand → Möblierungs-Level. |

### Stufen & Progression (1 Datei)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`EconProgression`** | "Wirtschafts-Fortschritt" | **5-Stufen-System.** SUBSISTENZ(0)→HANDEL(1)→INDUSTRIE(2)→WOHLSTAND(3)→IMPERIUM(4). `checkAdvance()` prüft Bedingungen. `onStageAdvance()` schaltet Systeme frei (Privatisierung, Aktien, Admin-Boost). Save-Migration v33. |

### Transport & Militär (3 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`TransportMarket`** | "Transport-Markt" | Bürger zahlen Transport-Pauschale (`transportFeePer100TileDay`) für Station-Nutzung. Geld von Bürger → Staat. |
| **`MilitaryPayroll`** | "Militär-Gehaltsliste" | Staat zahlt Soldaten-Gehälter. `armySupplyWagePerDay` etc. |
| **`MeticImmigration`** | "Metöken-Einwanderung" | Registriert einen `BValue`-Booster auf `BOOSTABLES.CIVICS().IMMIGRATION`: Wirtschaftskraft → mehr Einwanderer. |

### Speicherung (1 Datei)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`ChunkedSave`** | "Blockweise Speicherung" | Tag-Length-Value (TLV) Helper für robustes Save/Load. Jedes Subsystem bekommt ein `TAG_*`. Unbekannte Tags werden übersprungen statt abzubrechen. |

---

## 🟨 Kategorie 3: INFRASTRUCTURE

*Export, Logging, Konfiguration, Utilities, Build-Support. Keine Wirtschaftslogik, keine Vanilla-Abhängigkeiten (außer Config).*

### Konfiguration (2 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`EconConfig`** | "Wirtschafts-Konfiguration" | **140+ statische Regler.** ALLE Schalter des Mods: `defaultWage=50`, `perHeadTax=0`, `marketTaxRate=0.05`, `grainDoleEnabled=true`, `propertyMarketEnabled=false`, 15 Lohnkonstanten, Stage-gated `initialWallet`. `init()` = Lazy-Vanilla-Init. |
| **`WorkplaceDefaults`** | "Arbeitsplatz-Vorgaben" | Setzt Vanilla-Workplace-Prioritäten zurück wenn das Mod aktiv ist. Verhindert dass Vanilla-Job-Prioritäten mit unseren Löhnen konkurrieren. |

### Export & Diagnostik (1 Datei)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`DiagnosticExporter`** | "Diagnose-Exporteur" | **3 CSV-Dateien pro Spieltag.** `rebalance_macro_<epoch>.csv` (31 Spalten), `rebalance_resources_<epoch>.csv` (7 Spalten × alle Ressourcen), `rebalance_firms_<epoch>.csv` (8 Spalten × alle Firmen). Hard-Threshold-Alert-Layer: [REBALANCE]-EventLog bei Gini>0.40. |

### Logging (1 Datei)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`EventLog`** | "Ereignis-Log" | **Live-Chronik.** Kategorien: TREASURY, STAGE, UNREST, TREND, STARVATION, SEAM, DIPLO, REBALANCE. One-Shot-Guards verhindern Spam. Schreibt auch Datei-Log. `logSampled()` via Vanilla `RND.rFloat()`. |

### Utilities (3 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`SimpleHistory`** | "Einfache Historie" | Minimaler fixed-size Ring-Buffer für `GChart`. Speichert N letzte double-Werte. Kein Timestamp. |
| **`CompactNumber`** | "Kompakte Zahl" | Formatiert Zahlen: 1.500 → "1.5K", 2.300.000 → "2.3M". |
| **`EngineSeams`** | "Engine-Nahtstellen" | **Legacy-Fassade.** Alle Methoden sind jetzt `@Deprecated`-Wrapper die an `economySim.aiAdapter()` delegieren. Existiert nur noch für Code der noch nicht auf Adapter migriert wurde. |

### Benchmark (1 Datei)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`AdapterReflectionBenchmark`** | "Adapter-Reflection-Benchmark" | Misst Mean/P99-Latenz von Reflection vs. VarHandle/MethodHandle für 1000 Aufrufe. 5 Adapter, 3 Modi (Reflection, MH, Fallback). Output: CSV. |

---

## 🟥 Kategorie 4: UI

*Alles was der Spieler sieht. Render-Methoden, Tabs, Texte, Chart-Panel.*

### Fenster & Tabs (2 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`EconomyWindow`** | "Wirtschafts-Fenster" | **Legacy God-File (3.081 LOC).** 18 Tabs. Wird durch den 3-Fenster-Refactor ersetzt — siehe Plan `docs/superpowers/plans/2026-07-24-3-window-ux-refactor.md`. |
| **`ChartPanel`** | "Diagramm-Panel" | Dünner public Wrapper um Vanilla `GChart` (package-private). Ohne diese Klasse: kein Chart im Mod-UI. |

### Texte & Rollen (2 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`EconTexts`** | "Wirtschafts-Texte" | Alle UI-Strings (DE/EN) via `util.text.D`. Tab-Namen, Tooltips, Meilenstein-Texte, Advisor-Zeilen. |
| **`EconomicRoles`** | "Wirtschafts-Rollen" | Definiert pro Room-Typ: `wageConstant`, `wageSliderRange`, `isStateJob`. „Steckbrief" für jeden Firmen-Typ. |

### Eintrittspunkte (1 Datei)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`MainScript`** | "Haupt-Skript" | Registriert alle Booster beim Spielstart: `WealthHappiness`, `InflationOff`, `MeticImmigration`, `PropertyHappiness`, `GiniConsequences`, `PovertyPressure`, `HealthPressure`, `AccessAutomation`. |

### Zusätzliche UI-Helfer (3 Dateien)

| Klasse | Was der Name suggeriert | Was sie tatsächlich tut |
|--------|----------------------|------------------------|
| **`AccessAutomation`** | "Zugangs-Automation" | Steuert Behausungs-Zugangsrechte (CLASS-basierte Einlass-Politik). |
| **`ExchangeKernel`** | "Tausch-Kern" | Reine Hilfsfunktionen für `Wallets.exchange()`. Kein State. |
| **`AuditKernel`** | "Prüf-Kern" | **Geldmengen-Konservierung.** `checkConservation()` = Summe aller Wallets + Staatskasse + Firmenkonten = konstant? Jede Abweichung → EventLog "AUDIT". |
| **`Saveable`** | "Speicherbar" | Interface: `save(FilePutter)`, `load(FileGetter)`. Alle Subsysteme mit mutablem State implementieren dies. |
| **`WarehouseKernel`** | "Lager-Kern" | Reine Hilfsfunktionen für `WarehouseMarket`. Kein State. |
| **`WarehouseMarket`** | "Lager-Markt" | Staatlicher Im-/Export: Kauf/Verkauf von Ressourcen via `StockpileInstance`. Marktsteuer (`marketTaxRate`) wird hier abgeschöpft. |
| **`StateWarehouses`** | "Staatslager" | Verwaltung aller Staatslager: `setAllLiquidating()`, `setStoring()`. Nutzt `ISyxWarehouse`-Adapter. |
| **`WarehouseAutomation`** | "Lager-Automation" | **Proaktive Auto-Bewirtschaftung.** Aktiviert automatisch `buy` für alle knappen Ressourcen, alle Bau-Materialien, und 3-Tage-Nahrungs-Puffer. Budget-aware: keine Käufe wenn Treasury ≤ 0. |

---

## 📊 Zusammenfassung: **120 Klassen** in 4 Kategorien

| Kategorie | Typ | Anzahl | Leitsatz |
|-----------|-----|--------|----------|
| 🟦 **Vanilla Wrapper** | Adapter, Fallbacks, Brücken | 21 | "Das Einzige was Vanilla berührt" |
| 🟩 **Simulation** | Wirtschaft, Bürger, Firmen, Preise, Krisen | 71 | "Das Herz des Mods" |
| 🟨 **Infrastructure** | Config, Export, Log, Utilities, Benchmark | 8 | "Alles was den Betrieb ermöglicht" |
| 🟥 **UI** | Fenster, Tabs, Texte, Automation | 8 | "Alles was der Spieler sieht" |

---

## ❓ Die 10 verwirrendsten Namen — aufgelöst

| Name | Erster Eindruck | Tatsächliche Bedeutung |
|------|----------------|----------------------|
| **`IncomeCarry`** | "Einkommens-Trage" — RPG-Buff? | Nicht ausgeschütteter Gewinn einer Firma (steht in `FirmLedger.FirmState`) |
| **`CashRate`** | "Bargeld-Rate" — Wechselkurs? | Netto-Geldfluss pro Tag in `FirmLedger`: Einnahmen − Ausgaben |
| **`Advisor`** | "Berater" — NPC der Tipps gibt? | UI-Tab in `WindowOverview` (AdvisorTab): 10 Meilenstein-Indikatoren + KPI-Grid + Warnungen. KEIN NPC, nur Visualisierung. |
| **`FlowMeter`** | "Fluss-Messer" — Sensor/Hardware? | Wrapper um Vanilla `FResources` — liest Materialströme aus der Engine. Kein eigenes Tracking. |
| **`FirmLedger`** | "Firmen-Hauptbuch" — Buchhaltung? | Buchhaltung PLUS Analytics (Profit-Tracking) PLUS Export-Source (CSV). Drei Rollen in einer Klasse. |
| **`EconomicRoles`** | "Wirtschafts-Rollen" — RPG-Klassen? | Mapping: Room-Typ → Lohn-Konstante, Slider-Range, Staatsjob-Flag. Reine Konfiguration. |
| **`MarketTarget`** | "Markt-Ziel" — Trading-Strategie? | UI-Konzept in `WindowEconomy` (PricesTab): der Ziel-Marktpreis den der Spieler per Slider einstellt. |
| **`DebtDiplomacyBuffer`** | "Schulden-Diplomatie-Puffer" — ??? | Liest `DipWarPlayer`-Felder und berechnet wie viele Fraktionen gerade abgeschreckt sind. Militär-Puffer, nicht Schulden-Puffer. |
| **`EngineSeams`** | "Engine-Nahtstellen" — Integrations-Layer? | Legacy-Fassade — alle Methoden sind `@Deprecated` und delegieren an Adapter. Wird entfernt sobald alle Caller migriert sind. |
| **`Yard-Sale`** | "Hof-Flohmarkt" — Event? | Peer-to-Peer-Geldtransfer: Bürger kauft direkt von anderem Bürger, kein Markt. Der Name stammt aus `Wallets.exchange()`. |

---

> **Pflege-Hinweis:** Neue Klassen SOFORT hier eintragen — das Glossar verrottet schneller als der Code, wenn es nicht aktiv gepflegt wird.
