# SyxEconomyMod — API-Referenz

> **Version:** v0.1.0 | **Stand:** 2026-07-23 | **Spiel:** V71.44 | **Save:** v33

Verifizierte Vanilla- & Mod-APIs. Enthält auch die Runtime-Verifikations-Checkliste (ehemals VANILLA_VERIFICATION.md).

---

## Übersicht

Das Mod nutzt 179 Vanilla-Klassen. Die wichtigsten Schnittstellen sind:

---

## Script-System (Einstiegspunkte)

```java
// MainScript implementiert:
script.SCRIPT
  ├── name(), desc()                    // Metadata
  ├── initBeforeGameCreated()           // Vor Spiel-Ladevorgang
  ├── initBeforeGameInited()            // Nach Laden, vor Spielstart
  ├── createInstance()                  // Factory für InstanceScript
  ├── isSelectable()                    // Im Mod-Menü sichtbar
  └── forceInit()                       // Immer aktivieren

// InstanceScript implementiert:
script.SCRIPT.SCRIPT_INSTANCE
  ├── update(deltaSeconds)              // Jeder Tick
  ├── render(renderer)                  // Jeder Frame
  ├── mouseClick()                      // Maus-Interaktion
  ├── hover()                           // Tooltip-Hook (leer)
  ├── save() / load()                   // Serialisierung
  └── handleBrokenSavedState()          // Korrupte Saves
```

---

## Boosting-System (Glück, Loyalität)

```java
// Verfügbare Booster:
BOOSTABLES.BEHAVIOUR().HAPPI     // Glück der Bürger
BOOSTABLES.BEHAVIOUR().LOYALTY     // Loyalität (GiniConsequences v1.7.0)
BOOSTABLES.CIVICS().GOV          // Admin-Punkte (INDUSTRIE-Booster v1.7.0)

// Booster registrieren:
new BoosterValue(
    (BValue) bValue,           // BValue.BValueInduOnly
    new BSourceInfo(icon, sprite),
    minValue,                  // z.B. 0.75
    maxValue,                  // z.B. 1.25
    invert                     // true = höher ist besser
).add(BOOSTABLES.BEHAVIOUR().HAPPI);
```

---

## Kern-APIs die das Mod nutzt

### Wirtschaft
```java
settlement.stats.Induvidual    // Einzelner Bürger
  ├── wallets().coins()        // Aktuelles Geld
  └── clas()                   // Soziale Klasse

settlement.room.main.RoomInstance
  ├── employees()              // RoomEmploymentIns
  │   └── needed()             // Benötigte Arbeiter
  └── key()                    // Raum-Schlüssel

settlement.room.main.RoomBlueprintImp
  ├── key()                    // Klassen-Key
  └──mployees().needed()       // Benötigte Arbeiter
```

### Preisbildung
```java
// FlowMeter.Snapshot — Ressourcen-Daten pro Tick:
flow.size()                    // Anzahl Ressourcen
flow.supplyPerDay(i)           // Angebot pro Tag
flow.demandPerDay(i)           // Nachfrage pro Tag
flow.stock(i)                  // Aktueller Bestand
flow.stockChangePerDay(i)      // Bestandsänderung
```

### Finanzen
```java
// Fiscal — Staatseinnahmen:
fiscal.headTaxCollected()      // Kopfsteuer-Einnahmen
fiscal.marketReceipts()        // Markt-Abschöpfung
fiscal.rationOut()             // Ausgaben Rationen

// EconConfig — Konfiguration (alle public static):
EconConfig.perHeadTax          // Kopfsteuer-Satz
EconConfig.marketTaxRate       // Markt-Abschöpfungs-Satz
EconConfig.oddjobWagePerTask   // Gelegenheitslohn
EconConfig.defaultWage         // Standard-Lohn
EconConfig.wagesEnabled        // Lohnsystem an/aus
```

---

## Von uns hinzugefügte Thin Wrapper

Da die Vanilla-API keine direkten Setter für Steuern/Löhne bereitstellt, haben wir Wrapper eingeführt:

```java
// In Fiscal.java:
public int headTax()               // liest EconConfig.perHeadTax
public void setHeadTax(int v)      // setzt EconConfig.perHeadTax
public double marketLevy()         // liest EconConfig.marketTaxRate
public void setMarketLevy(double v)// setzt EconConfig.marketTaxRate

// In OddjobMarket.java:
public int pay()                   // liest EconConfig.oddjobWagePerTask
public void setPay(int v)          // setzt EconConfig.oddjobWagePerTask
```

---

## Bekannte API-Lücken

| Was fehlt | Warum | Workaround |
|-----------|-------|------------|
| `EVENTS.fire()` | Mods können keine Vanilla-Events triggern | Eigener Advisor-Tab |
| Notification-System | Kein Popup-System nutzbar | Nur UI-Tab |
| `Fiscal.setRate()` | Kein direkter Setter | Thin Wrapper über EconConfig |
| `OddjobMarket.setPay()` | Kein direkter Setter | Thin Wrapper über EconConfig |

---

## Vanilla-Abhängigkeiten nach Paket

```
settlement.*  ████████████████████  ~40%  ← Höchstes Risiko
game.*        ████████             ~20%  ← Hoch
init.*        ███████              ~18%  ← Hoch
snake2d.*     ██████               ~15%  ← Mittel
view.*/util.* ███                  ~7%   ← Mittel
```
# ECONOMY_API_REFERENCE.md — Verifizierte Wirtschafts-API für Songs of Syx V71

> **Wichtig:** Alles in dieser Datei ist direkt im Code verifiziert.
> Stand: 23. Juli 2026 | Mod-Version: 0.1.0 | Save: v33 | 5-Stufen-System | Phase 4 Adapter

## 1. Verfügbare Datenquellen (alle verifiziert)

### FlowMeter.Snapshot — Ressourcen-Tracking (V71 Consumption API)
```java
// Verifiziert in: FlowMeter.java:348-416
FlowMeter.Snapshot snapshot = flowMeter.snapshot();

snapshot.size()                    // Anzahl Ressourcen
snapshot.supplyPerDay(good)        // Angebot pro Tag
snapshot.demandPerDay(good)        // Nachfrage pro Tag
snapshot.stock(good)               // Aktueller Lagerbestand
snapshot.stockChangePerDay(good)   // Lagertrend (berechnen!)
snapshot.firmInputsPerDay(good)    // Verbrauch durch Betriebe
snapshot.room()                    // Zugehöriger Raum
snapshot.outputCount()             // Anzahl Outputs
snapshot.inputCount()              // Anzahl Inputs
```

**Nutzung im Mod:** FlowPrices, EconomyWindow.renderPrices()
**Nicht genutzt:** stockChangePerDay, firmInputsPerDay → Kandidaten für Advisor

---

### WealthStats — Vermögensstatistik
```java
// Verifiziert in: WealthStats.java:10-53
WealthStats stats = wealthStats;

stats.people       // int — Anzahl Bürger
stats.median       // int — Median des Vermögens
stats.mean         // double — Durchschnitt
stats.gini         // double — Gini-Koeffizient (0-1)
stats.max          // int — Reichster Bürger
stats.histogram[]  // int[16] — Verteilungshistogramm
stats.bucketWidth  // int — Breite eines Histogramm-Buckets
stats.tallest      // int — Höchster Wert im Histogramm
```

**Nutzung im Mod:** EconomyWindow.renderDistribution(), renderCitizens()
**Nicht genutzt:** Histogramm-Trend → Kandidat für Advisor

---

### FirmLedger — Betriebsbuchhaltung
```java
// Verifiziert in: FirmLedger.java:62-78
FirmLedger ledger = firmLedger;

ledger.lastIncomeDue()          // long — Geschuldete Löhne
ledger.lastIncomePaid()         // long — Bezahlte Löhne
ledger.lastWorkersPaid()        // int — Anzahl bezahlter Arbeiter
ledger.lastWorkersUnpaid()      // int — Anzahl unbezahlter Arbeiter ⚠️
ledger.meanPositiveMarginal()   // double — Durchschnittlicher Grenzertrag
ledger.profitPerDay(room)       // double — Profit pro Tag pro Raum
```

**Nutzung im Mod:** EconomyWindow.renderWages()
**Nicht genutzt:** lastWorkersUnpaid als Signal → Kandidat für Advisor

---

### Fiscal — Staatshaushalt
```java
// Verifiziert in: Fiscal.java:42-54
Fiscal fiscal = this.fiscal;

fiscal.headTaxCollected()    // long — Kopfsteuer-Einnahmen
fiscal.marketReceipts()      // long — Markt-Einnahmen
fiscal.rationOut()           // long — Rationen-Ausgaben
fiscal.producerIncome()      // long — Produzenten-Einkommen
```

**Nutzung im Mod:** EconomySim.logLedger(), Audit
**Nicht genutzt:** Trend über Zeit → Kandidat für Advisor

---

### Taxes — Steuersystem
```java
// Verifiziert in: Taxes.java:43-55
Taxes taxes = this.taxes;

taxes.floor()              // int — Steuerfreigrenze
taxes.rate()               // int — Steuersatz
taxes.lastCollected()      // long — Letzte Einnahmen
taxes.lastPayers()         // int — Anzahl Zahler
taxes.foreignTaxModifier() // int — Ausländischer Modifikator
taxes.immigrationMultiplier() // double — Zuwanderungs-Multiplikator
```

**Nutzung im Mod:** EconomyWindow.renderTaxes()

---

### LaborMarket — Arbeitsmarkt
```java
// Verifiziert in: LaborMarket.java:40
LaborMarket labor = this.laborMarket;

labor.meanWage()           // double — Durchschnittslohn
```

**Nutzung im Mod:** EconomyWindow.renderWages()

---

### Wallets — Geldbeutel
```java
// Verifiziert in: Wallets.java:370
Wallets wallets = this.wallets;

wallets.circulating()      // long — Gesamtgeldmenge im Umlauf
```

**Nutzung im Mod:** EconomySim.totalLiving()

---

### WarehouseMarket — Warenhandel
```java
// Verifiziert in: WarehouseMarket.java:132-164
WarehouseMarket wm = this.warehouseMarket;

wm.lastBought()             // long — Kaufpreis
wm.lastSold()               // long — Verkaufspreis
wm.lastUnitsBought()        // int — Gebuchte Einheiten
wm.lastUnitsSold()          // int — Verkaufte Einheiten
wm.lastConstructionPaid()   // long — Baumaterial-Kosten
wm.lastExportBought()       // long — Export-Kosten
wm.lastTaxed()              // long — Lagersteuer
wm.lastTaxPayers()          // int — Anzahl Steuerzahler
```

**Nutzung im Mod:** EconomyWindow.renderStateWarehouses(), renderCrownMarket()

---

## 2. NEU v1.7.0 — Mod-interne APIs

### GiniConsequences — Gini → Loyalty Coupling
```java
// Verifiziert in: GiniConsequences.java:27-64
GiniConsequences.register();                    // Einmalig beim Spielstart (MainScript)
GiniConsequences.announceIfCrossed(snap, season); // Einmal pro Saison (EconomySim.update)

/* Funktionsweise:
 * - Registriert BValue.BValueInduOnly auf BOOSTABLES.BEHAVIOUR().LOYALTY
 * - Gini 0.0 → 1.0x Loyalty (kein Effekt)
 * - Gini 1.0 → EconConfig.loyaltyAtMaxGini (Default 0.85 = -15% Loyalty)
 * - Gruppeneffekt (nicht individuell wie WealthHappiness)
 * - EventLog "UNREST" bei Gini > EconIndicators.GINI_WARNING (0.35)
 */
```

**Config-Felder (EconConfig):**
```java
public static boolean giniAffectsLoyalty = true;
public static double loyaltyAtMaxGini = 0.85;
```

---

### EconProgression — Wirtschaftsstufen & Feature-Freischaltung
```java
// Verifiziert in: EconProgression.java
public enum Stage {
    SUBSISTENZ(0, "Subsistenz"),
    HANDEL(1, "Handel"),
    INDUSTRIE(2, "Industrie"),    // NEU v1.7.0
    WOHLSTAND(3, "Wohlstand"),
    IMPERIUM(4, "Imperium");
    // .next(), .fromLevel(), .displayName
}

EconProgression prog = economySim.progression;
prog.stage              // Stage — aktuelle Stufe
prog.stageDays          // int — Tage in aktueller Stufe
prog.cumulativeWagesPaid
prog.cumulativeExportValue
prog.daysSinceInsolvency
prog.daysLowGini
prog.daysVeryLowGini

// Aufruf pro Tick (EconomySim.update):
prog.update(EconSnapshot snap);

// Save/Load (chunked format):
prog.save(FilePutter file);
prog.load(FileGetter file) throws IOException;

// Freischaltung in onStageAdvance():
// HANDEL: MeticImmigration.register()
// INDUSTRIE: registerAdminBooster() (CIVIC_ADMIN/GOV +20% via Reflection — GOV existiert in V71.44)
// WOHLSTAND: homePurchaseEnabled=true, propertyMarketEnabled=true, PropertyHappiness.register()
// IMPERIUM: workplaceSharesEnabled=true
```

**Config-Felder (EconConfig):**
```java
public static boolean propertyMarketEnabled = false;     // Default geändert v1.7.0
public static boolean homePurchaseEnabled = false;       // Default geändert v1.7.0
public static boolean workplaceSharesEnabled = false;    // Default geändert v1.7.0
```

---

### EconIndicators — Trend-Erkennung mit EventLog & Konsequenzen
```java
// Verifiziert in: EconIndicators.java
EconIndicators indicators = economySim.econIndicators();

indicators.update(EconSnapshot snap);   // Aufruf pro Saison (EconomySim)

// Trend-Flags (Getter):
indicators.isInequalityRising()      // boolean
indicators.isWagesFalling()          // boolean
indicators.isTreasuryDeclining()     // boolean
indicators.isEmigrationSpike()       // boolean

// Schwellwerte:
EconIndicators.GINI_WARNING = 0.35;
EconIndicators.EMIGRATION_SPIKE = 3;
EconIndicators.TREND_PERIODS = 3;

// Intern (Change-Detection):
wasWagesFalling        // vorheriger Wert
wasTreasuryDeclining   // vorheriger Wert

// Konsequenz bei treasuryDeclining:
EconConfig.doleHeadcap = (int)(EconConfig.doleHeadcapBase * 0.85);
// sonst:
EconConfig.doleHeadcap = EconConfig.doleHeadcapBase;
```

**Config-Felder (EconConfig):**
```java
public static int doleHeadcapBase = 100;  // NEU v1.7.0 — Spieler-Einstellung
public static int doleHeadcap = 100;       // Laufzeit-Wert (von EconIndicators modifiziert)
```

---

### EventLog — Chronik-Buffer (deterministisches Sampling)
```java
// Verifiziert in: EventLog.java
EventLog.init();                           // Einmalig (MainScript/InstanceScript)

EventLog.log("KATEGORIE", "Nachricht");    // Immer loggen
EventLog.logSampled("KATEGORIE", "Msg");   // 10% Sampling via RND.rFloat()

// Kategorien im Mod:
"SYSTEM", "STAGE", "UNREST", "TREND", "HOUSING", "DEBT", 
"PROPERTY", "CONSUMPTION", "LATENT_DEMAND"

// In-Memory Buffer (für UI):
List<EventLog.EventEntry> events = EventLog.getRecentEvents();
// EventEntry: category, message, timestamp (HH:mm:ss)

// Datei-Logging (nur wenn EconConfig.debugPriceLogging):
// economy_events.log im Spiel-Verzeichnis

EventLog.close();  // Beim Speichern/Beenden
```

---

### EconSnapshot — Ring-Buffer Element
```java
// Verifiziert in: EconSnapshot.java
EconSnapshot snap = indicators.latest();
// oder
snap = indicators.get(index);  // 0 = ältester, count-1 = neuester

// Felder:
snap.tick
snap.season
snap.people
snap.totalMoney
snap.meanWage
snap.gini
snap.wageShare
snap.headTax
snap.marketReceipts
snap.rationOut
snap.foodBasketPrice
snap.drinkBasketPrice
snap.warehouseSold
snap.incomePaid
snap.emigrations
snap.workersUnpaid
```

---

### LaborMarketAccess — Package-Private Bridge (NEU v1.7.0)
```java
// Package: settlement.room.main.employment
// Verifiziert in: LaborMarketAccess.java

LaborMarketAccess.employmentOf(RoomEmployment e)     // RoomEmploymentIns
LaborMarketAccess.getPriority(RoomEmployment e)      // int (0-100)
LaborMarketAccess.setPriority(RoomEmployment e, int v)
LaborMarketAccess.minPriority(RoomEmployment e)      // int
LaborMarketAccess.maxPriority(RoomEmployment e)      // int
LaborMarketAccess.freeShare(RoomEmployment e)        // double
LaborMarketAccess.restorePriority(RoomEmployment e)  // void
```

**Wichtig:** Kein Reflection, kein instanceof — direkter Package-Private Zugriff.
Compile-time sicher solange Vanilla `RoomEmployment$Priority` Felder/Methoden stabil bleiben.

---

## 3. Private Felder die Getter brauchen (Stand v1.7.0)

### EconomySim
```java
private int deaths = 0;               // ❌ Kein Getter
private int emigrations = 0;          // ❌ Kein Getter
private int inherited = 0;            // ❌ Kein Getter
private int heirless = 0;             // ❌ Kein Getter
private long warehouseTaxCollected = 0L; // ❌ Kein Getter
private long reportedAuditDelta = 0L;     // ❌ Kein Getter
```

**Aktion:** Getter hinzufügen bevor Advisor gebaut wird.

---

## 4. Verfügbare BOOSTABLES (verifiziert V71)

| BOOSTABLE | Quelle | Status |
|-----------|--------|--------|
| `BOOSTABLES.BEHAVIOUR().HAPPI` | WealthHappiness, PropertyHappiness | ✅ Genutzt |
| `BOOSTABLES.CIVICS().IMMIGRATION` | MeticImmigration | ✅ Genutzt |
| `BOOSTABLES.CIVICS().DEFALTION` | InflationOff | ✅ Genutzt |
| `BOOSTABLES.BEHAVIOUR().LOYALTY` | GiniConsequences | ✅ Genutzt [NEU v1.7.0] |
| `BOOSTABLES.BEHAVIOUR().LAWFULNESS` | — | 🟡 Nicht genutzt |
| `BOOSTABLES.PHYSICS().HEALTH` | — | 🟡 Nicht genutzt |
| `BOOSTABLES.PHYSICS().REPRODUCTION_SPEED` | — | 🟡 Nicht genutzt |
| `BOOSTABLES.CIVICS().INNOVATION` | — | 🟡 Nicht genutzt |
| `BOOSTABLES.CONSUMPTION` | FlowMeter | ✅ Genutzt [NEU v1.7.0] |

> **Strategische Hebel-Evaluierung (Juli 2026):**
> * **Makro-Events (Streiks/Subventionen):** Da Vanilla-Popups nicht nutzbar sind, können Streiks nur "stumm" über `RoomEmploymentIns.neededSet(0)` (Betriebs-Zwangsschließung) umgesetzt und im Advisor-Tab angezeigt werden.
> * **Soziale Reaktionen (Loyalität/Reproduktion):** Da die Booster `LOYALTY` und `BOOSTABLES.PHYSICS().REPRODUCTION_SPEED` verfügbar sind, können diese über das bewährte `BValue`-Pattern (siehe `WealthHappiness.java`) an den Gini-Index oder Räumungs-Quoten (Housing Market) gekoppelt werden. Dies ist der sicherste und wirkungsvollste Hebel für zukünftige Updates, da er keine fehleranfälligen UI-Hacks erfordert.
> *(Hinweis: `FERTILITY` existiert nicht in der Vanilla-API — korrigiert in v1.7.3. Korrekt: `BOOSTABLES.PHYSICS().REPRODUCTION_SPEED`.)*

---

## 5. Save/Load System

### Version
- Aktuelle Version: **33** (Wallets / EconomySim chunked format, INDUSTRIE-Migration)
- Backward compatible: **19**
- Neue Felder müssen version-gated werden
- Neue Subsysteme sollten ein eigenes `TAG_*` im chunked save/load bekommen

### Speicherformat
```java
file.l(long)      // Long speichern
file.i(int)       // Int speichern
file.bool(boolean) // Boolean speichern
```

**Achtung:** Reihenfolge in `save()` und `load()` muss IDENTISCH sein!

---

## 6. Vanilla-API-Verifikation (nur kurze Übersicht, Details in VANILLA_VERIFICATION.md)

| Kategorie | Beispiele | Risiko |
|-----------|-----------|--------|
| Reflection | `DebtDiplomacyBuffer` → `DipWarPlayer` | 🟢 Fail-Safe |
| String-Checks | AI-Pläne (`getSimpleName()`) | 🟡 Mittel |
| instanceof | `EconomicRoles`, `WarehouseMarket`, `LaborMarketAccess` | 🟢 Niedrig |
| Compile-time | `FACTIONS.player()`, `BOOSTABLES.*`, `SETT.ROOMS()` | 🟢 Kein Risiko |

---

## 7. Build & Deploy Verification

```bash
cd <repo-root>

# Clean build
mvn clean install -DskipTests

# Verify deployed JAR (keine externen Assets mehr — EconTexts.txt wurde entfernt v1.7.0)
unzip -l ~/.local/share/songsofsyx/mods/SyxEconomyMod/V71/script/SyxEconomyMod.jar \
  | grep -E "GiniConsequences|EconConfig|EconIndicators|EconProgression|EventLog|LaborMarketAccess"

# Hinweis: EconTexts.txt existiert nicht mehr (gelöscht v1.7.0). Alle UI-Texte sind hartkodiert in EconTexts.java.
```# Vanilla-Verifikation — Runtime-Abhängigkeiten

> **Zweck:** Alle Stellen im Mod, die zur Laufzeit auf Vanilla-Klassen zugreifen und NICHT
> durch den Java-Compiler abgesichert sind. Diese Liste dient als Checkliste nach jedem
> Spiel-Update bei Engine-API-Änderungen.
>
> Stand: 23. Juli 2026 | Spiel: Songs of Syx V71.44 | Mod: v1.7.0 | Save: v33

---

## 1. Reflection-Zugriffe

### 1.1 DebtDiplomacyBuffer — `DipWarPlayer` private Felder

**Datei:** `src/vannon/syx/economy/core/DebtDiplomacyBuffer.java`

| Feldname | Typ | Verwendung | Fail-Safe |
|----------|-----|-----------|-----------|
| `upI` | `int` | Update-Index (verhindert Doppel-Berechnung) | Ja — deaktiviert Subsystem |
| `pPow` | `double` | Spieler-Macht | Ja — deaktiviert Subsystem |
| `coalitionPow` | `double` | Koalitions-Macht | Ja — deaktiviert Subsystem |
| `bWilling` | `Bitmap1D` | Bitmap der kriegswilligen Fraktionen | Ja — deaktiviert Subsystem |
| `willing` | `ArrayList` | Liste der kriegswilligen Fraktionen | Ja — deaktiviert Subsystem |

**Verify-Befehl gegen JAR:**
```bash
javap -p -cp SongsOfSyx.jar game.faction.diplomacy.DipWarPlayer | grep -E "upI|pPow|coalitionPow|bWilling|willing"
```

**Erwartetes Ergebnis (V71):**
```
private int upI;
private double pPow;
private double coalitionPow;
private snake2d.util.sets.Bitmap1D bWilling;
private snake2d.util.sets.ArrayList willing;
```

---

### 1.2 TransportMarket — `ROOM_TRANSPORT` Instanz-Feld `distance`

**Datei:** `src/vannon/syx/economy/core/TransportMarket.java`

| Feldname | Typ | Verwendung | Fail-Safe |
|----------|-----|-----------|-----------|
| `distance` | `float` | Echte Transport-Distanz (statt geometrischer Schätzung) | Ja — geometrischer Fallback |

**Verify-Befehl:**
```bash
# Die Instanz-Klasse von ROOM_TRANSPORT ist implementationsabhängig.
# Prüfen via:
javap -p -cp SongsOfSyx.jar settlement.room.infra.transport.ROOM_TRANSPORT
# Dann die konkrete Instanz-Klasse auf Feld "distance" prüfen.
```

---

### 1.3 StateWarehouses — `StockpileInstance` Methode `storingSet`

**Datei:** `src/vannon/syx/economy/core/StateWarehouses.java`

| Methode | Signatur | Verwendung | Fail-Safe |
|---------|----------|-----------|-----------|
| `storingSet` | `void storingSet(boolean)` | Physische Lager-Sperre für Hoarding | Ja — Pricing-Lock-Fallback |

**Verify-Befehl:**
```bash
javap -p -cp SongsOfSyx.jar settlement.room.infra.stockpile.StockpileInstance | grep storingSet
```

**Erwartetes Ergebnis (V71):**
```
void storingSet(boolean);
```

---

## 2. String-basierte Klassen-Erkennung

### 2.1 AI-Pläne — Simple-Name-Vergleiche

**Datei:** `src/vannon/syx/economy/core/EngineSeams.java`

Diese AI-Plan-Klassen sind **package-private** in Vanilla, daher kein `instanceof` möglich.
Erkannt werden sie via `plan.getClass().getSimpleName()`.

| Konstante | Klassen-Simple-Name | Verwendung | Risiko |
|-----------|-------------------|-----------|--------|
| `ODDJOBBER_PLAN` | `PlanOddjobber` | Corvée/Gelegenheitsarbeit | 🟡 Mittel |
| `FOOD_EATERY_PLAN` | `F_SPlanEatery` | Essensplan-Steuerung | 🟡 Mittel |
| `FOOD_CANTEEN_PLAN` | `F_SPlanCanteen` | Kantinen-Erkennung | 🟡 Mittel |
| `FOOD_RAW_PLAN` | `F_PlanEat` | Rohessen-Erkennung | 🟡 Mittel |
| `TAVERN_PLAN` | `PlanTavern` | Tavernen-Erkennung | 🟡 Mittel |
| `MARKET_PLAN` | `M_PlanMarket` | Markt-Erkennung | 🟡 Mittel |

---

### 2.2 Bau-Arbeiter-Plan — Fully-Qualified-Name-Vergleich

**Datei:** `src/vannon/syx/economy/core/ConstructionHoardController.java`

| Konstante | Vollständiger Klassenname | Verwendung | Risiko |
|-----------|--------------------------|-----------|--------|
| `WORK_WAIT_PLAN` | `settlement.entity.humanoid.ai.work.PlanHangArround` | Erkennt untätige Bau-Arbeiter für Hortungs-Jobs | 🟡 Mittel |

**Hinweis:** Dies ist der EINZIGE fully-qualified class name check im Mod.
Alle anderen String-Checks nutzen `getSimpleName()`.

**Verify-Befehl:**
```bash
jar tf SongsOfSyx.jar | grep -E "PlanOddjobber|F_SPlanEatery|F_SPlanCanteen|F_PlanEat|PlanTavern|M_PlanMarket"
```

---

## 3. instanceof-Checks

### 3.1 EconomicRoles — Staatlich finanzierte Raumtypen

**Datei:** `src/vannon/syx/economy/core/EconomicRoles.java`

Alle compile-verifiziert. Bei Vanilla-Updates können neue Raumtypen hinzukommen
(→ nicht als state-funded erkannt) oder bestehende verschoben werden.

| Methode | instanceof | Risiko |
|---------|-----------|--------|
| `stateFundedMilitary` | `ROOM_M_TRAINER` | 🟢 Niedrig |
| `stateFundedExportDepot` | `ROOM_EXPORT` | 🟢 Niedrig |
| `stateFundedHauler` | `ROOM_HAULER` | 🟢 Niedrig |
| `stateFundedArmySupply` | `ROOM_SUPPLY` | 🟢 Niedrig |
| `stateFundedLaboratory` | `ROOM_LABORATORY` | 🟢 Niedrig |
| `stateFundedLibrary` | `ROOM_LIBRARY` | 🟢 Niedrig |
| `stateFundedEmbassy` | `ROOM_EMBASSY` | 🟢 Niedrig |
| `stateFundedCannibal` | `ROOM_CANNIBAL` | 🟢 Niedrig |
| `stateFundedPolice` | `ROOM_POLICE` | 🟢 Niedrig |
| `stateFundedGuard` | `ROOM_GUARD` | 🟢 Niedrig |
| `stateFundedStockade` | `ROOM_STOCKADE` | 🟢 Niedrig |
| `stateFundedPrison` | `ROOM_PRISON` | 🟢 Niedrig |
| `stateFundedPublicWorks` | `ROOM_TEMPLE \| WELL \| HEARTH` | 🟢 Niedrig |
| `stateFundedWaterworks` | `"_WATERPUMP".equals(b.key)` | 🟡 Mittel (String!) |

---

### 3.2 WarehouseMarket — Retail-Raumtypen

**Datei:** `src/vannon/syx/economy/core/WarehouseMarket.java`

| Zeile | instanceof | Verwendung | Risiko |
|-------|-----------|-----------|--------|
| 724 | `ROOM_EATERY` | Retail-Erkennung | 🟢 Niedrig |
| 724 | `ROOM_CANTEEN` | Retail-Erkennung | 🟢 Niedrig |
| 724 | `ROOM_TAVERN` | Retail-Erkennung | 🟢 Niedrig |
| 724 | `ROOM_MARKET` | Retail-Erkennung | 🟢 Niedrig |
| 740 | `RoomDistribution.RoomDistributionIns` | Verteiler-Stock lesen | 🟡 Mittel (innere Klasse) |
| 175, 470 | `StockpileInstance` | Lager von Firmen unterscheiden | 🟢 Niedrig |

---

### 3.3 ServiceMarket — Kostenlose öffentliche Dienste

**Datei:** `src/vannon/syx/economy/core/ServiceMarket.java`

| Zeile | Vergleich | Verwendung | Risiko |
|-------|----------|-----------|--------|
| 133 | `roomType == ROOM_HEARTH.class` | Herd als kostenlos erkennen | 🟢 Niedrig |
| 133 | `roomType == ROOM_WELL.class` | Brunnen als kostenlos erkennen | 🟢 Niedrig |

**Hinweis:** Nutzt `Class<?>`-Vergleich statt `instanceof`. Compile-verifiziert.

---

### 3.4 FlowMeter — Produzenten-Instanz

**Datei:** `src/vannon/syx/economy/core/FlowMeter.java`

| Zeile | instanceof | Verwendung | Risiko |
|-------|-----------|-----------|--------|
| 98 | `ROOM_PRODUCER_INSTANCE` | Industrie-Produktion sampeln | 🟢 Niedrig |

---

### 3.5 FirmLedger / StateWarehouses — Infrastruktur-Checks

**Dateien:** `FirmLedger.java`, `StateWarehouses.java`

| Datei | instanceof | Verwendung | Risiko |
|-------|-----------|-----------|--------|
| FirmLedger:87 | `StockpileInstance` | Staatliche Lager von Firmen trennen | 🟢 Niedrig |
| FirmLedger:221 | `RoomBlueprintIns` | Service-Instanzen zählen | 🟢 Niedrig |
| StateWarehouses:135,429,430,471,472,515 | `StockpileInstance` | Lager-Operationen | 🟢 Niedrig |
| StateWarehouses:429,471 | `RoomInstance` | Room-Map-Cast | 🟢 Niedrig |
| LaborMarket:71 | `RoomEmployment` | Employment-Modul-Cast | 🟢 Niedrig |

---

### 3.6 LaborMarket — RoomEmployment Priority Access

**Datei:** `src/vannon/syx/economy/core/LaborMarket.java`

Verwendet die Package-Private Bridge `LaborMarketAccess` (gleiches Package `settlement.room.main.employment`):

| Methode | Bridge-Methode | Risiko |
|---------|---------------|--------|
| `employmentOf()` | `LaborMarketAccess.employmentOf()` | 🟢 Niedrig (compile-time) |
| `getPriority()` | `LaborMarketAccess.getPriority()` | 🟢 Niedrig |
| `setPriority()` | `LaborMarketAccess.setPriority()` | 🟢 Niedrig |
| `minPriority()` | `LaborMarketAccess.minPriority()` | 🟢 Niedrig |
| `maxPriority()` | `LaborMarketAccess.maxPriority()` | 🟢 Niedrig |
| `freeShare()` | `LaborMarketAccess.freeShare()` | 🟢 Niedrig |
| `restorePriority()` | `LaborMarketAccess.restorePriority()` | 🟢 Niedrig |

**Hinweis:** Kein Reflection, kein instanceof — direkte package-private Zugriff via Bridge-Klasse.

---

## 4. Compile-verifizierte Vanilla-Aufrufe (kein Risiko)

Diese Methoden werden durch den Java-Compiler gegen die JAR geprüft. Nur bei **Signatur-Änderungen**
in der JAR brechen sie. Das ist extrem selten (würde alle Mods brechen).

| Methode | Aufrufer |
|---------|----------|
| `AIManager.overwrite(Humanoid, AIPLAN)` | EngineSeams.overwritePlan() |
| `RoomEmploymentIns.neededSet(int)` | EngineSeams.setFirmTarget(), FirmLedger, WorkplaceDefaults, StateWageMarket |
| `RoomEmploymentIns.hardTarget()` | FirmLedger, WorkplaceDefaults, StateWageMarket |
| `Humanoid.ai()` → `HAI` → Cast zu `AIManager` | EngineSeams (mehrfach) |
| `STATS.REL().reference(Induvidual)` | Wallets.touch() |
| `STATS.WORK().EMPLOYED.get(Induvidual)` | EngineSeams, WarehouseMarket, FirmLedger |
| `NEEDS.TYPES().HUNGER.stat().stat().indu().get/set()` | EngineSeams |
| `FACTIONS.player().credits().inc()` | EconomySim, FirmLedger, Fiscal, Taxes, etc. |
| `BOOSTABLES.BEHAVIOUR().HAPPI` | WealthHappiness, PropertyHappiness |
| `BOOSTABLES.CIVICS().IMMIGRATION` | MeticImmigration |
| `BOOSTABLES.CIVICS().DEFALTION` | InflationOff |
| `BOOSTABLES.BEHAVIOUR().LOYALTY` | GiniConsequences [NEU v1.7.0] |
| `BOOSTABLES.CONSUMPTION` | FlowMeter [NEU v1.7.0 - V71 API] |
| `Industry.consumption()` | FlowMeter [NEU v1.7.0] |
| `RoomConsumptionAbs.boost` | FlowMeter [NEU v1.7.0] |
| `STATS.BATTLE().WAR.data().getD(null)` | EconSnapshot.battleThreat [v1.7.2] — **⚠️ Reflexion, kein Compile-Check** — SEAM-Log in catch vorhanden (v1.7.3-Fix). Feld derzeit nicht verkabelt (kein UI). |
| `RoomServiceAccess.ALL()` | ServicePlanController [v1.7.x] — **Cache-Lookup** via `serviceCache` (Map<Object, RoomServiceAccess>), befüllt in `refreshServiceCacheIfNeeded()`. Statisch einmalig befüllt beim ersten Aufruf (da Service-Typen zur Laufzeit unveränderlich sind). Ersetzt O(Bürger × ServiceTypes)-Scan. |

---

## 5. Verify-Checkliste (nach Spiel-Update)

```bash
# 1. DebtDiplomacyBuffer Felder
javap -p -cp SongsOfSyx.jar game.faction.diplomacy.DipWarPlayer \
  | grep -E "upI|pPow|coalitionPow|bWilling|willing"

# 2. StockpileInstance storingSet
javap -p -cp SongsOfSyx.jar settlement.room.infra.stockpile.StockpileInstance \
  | grep storingSet

# 3. AI-Plan-Klassen (Simple Names)
jar tf SongsOfSyx.jar \
  | grep -E "PlanOddjobber|F_SPlanEatery|F_SPlanCanteen|F_PlanEat|PlanTavern|M_PlanMarket"

# 3b. Bau-Arbeiter-Plan (Fully Qualified)
jar tf SongsOfSyx.jar \
  | grep "settlement/entity/humanoid/ai/work/PlanHangArround"

# 4. Wirtschafts-Raumtypen (EconomicRoles + WarehouseMarket + FlowMeter)
jar tf SongsOfSyx.jar \
  | grep -E "ROOM_M_TRAINER|ROOM_EXPORT|ROOM_HAULER|ROOM_SUPPLY|\
ROOM_LABORATORY|ROOM_LIBRARY|ROOM_EMBASSY|ROOM_CANNIBAL|\
ROOM_POLICE|ROOM_GUARD|ROOM_STOCKADE|ROOM_PRISON|\
ROOM_TEMPLE|ROOM_WELL|ROOM_HEARTH|ROOM_EATERY|\
ROOM_CANTEEN|ROOM_TAVERN|ROOM_MARKET|\
ROOM_PRODUCER_INSTANCE"

# 4b. RoomDistribution (innere Klasse)
jar tf SongsOfSyx.jar \
  | grep "RoomDistribution\$RoomDistributionIns"

# 5. Transport distance Feld
# (muss auf der konkreten Instanz-Klasse von ROOM_TRANSPORT geprüft werden)
javap -p -cp SongsOfSyx.jar settlement.room.infra.transport.ROOM_TRANSPORT

# 6. Boostable-Kategorien (V71 Structure)
jar tf SongsOfSyx.jar | grep "BOOSTABLES\$"
# Erwartet: Behaviour, Civics, Physics, Battle, Activity, Noble
```

---

## 6. Neu in v1.7.0 (zusätzliche Checks)

```bash
# 7. GiniConsequences - BEHAVIOUR.LOYALTY existiert
javap -p -cp SongsOfSyx.jar game.boosting.BOOSTABLES\$Behaviour \
  | grep LOYALTY

# 8. FlowMeter V71 Consumption API
javap -p -cp SongsOfSyx.jar game.industry.Industry \
  | grep consumption
javap -p -cp SongsOfSyx.jar game.boosting.BOOSTABLES \
  | grep CONSUMPTION

# 9. EconConfig Lazy Init Pattern - keine statischen Vanilla-Zugriffe mehr
grep -r "static.*FACTIONS\|static.*BOOSTABLES\|static.*SETT" src/vannon/syx/economy/core/EconConfig.java
# Sollte KEINE Treffer mehr haben (alle in init() verschoben)
```

---

## 7. Bekannte Halluzinationen (korrigiert in v1.7.0)

| Claim | Status | Korrektur |
|-------|--------|-----------|
| `BOOSTABLES.CIVICS().LOYALTY` | ❌ Falsch | Richtig: `BOOSTABLES.BEHAVIOUR().LOYALTY` (GiniConsequences v1.7.0) |
| `BOOSTABLES.ACTIVITY().WORK_ETHIC` | ❌ Existiert nicht | Nicht in V71 — Alternative: `ROOM_*` Boostables |
| `BOOSTABLES.CIVICS().INNOVATION` accessor | ⚠️ Prüfen | Java-Methode heißt `CIVICS()` nicht `CIVIC()` — `BOOSTABLES.CIVICS().INNOVATION` ist korrekt |
| `FACTIONS` vs `FACTONS` | ✅ Korrigiert | `FACTIONS` (mit I) — Vanilla-Typo |
| `BOOSTABLES.CIVICS().ADMIN` | ❌ Existiert nicht | `BOOSTABLES.CIVICS().GOV` existiert — ADMIN_FIELD_CANDIDATES probiert beide, GOV funktioniert |
| `FERTILITY` (Booster-Name) | ❌ Existiert nicht | Korrekt: `BOOSTABLES.PHYSICS().REPRODUCTION_SPEED` — in API_REFERENCE.md Zeile ~492 als Prosa-Tipp eingeschlichen (v1.7.3 korrigiert) |

---

## 8. Build & Deploy Verification

```bash
cd <repo-root>

# Clean build
mvn clean install

# Verify deployed JAR
unzip -l ~/.local/share/songsofsyx/mods/SyxEconomyMod/V71/script/SyxEconomyMod.jar \
  | grep -E "GiniConsequences|EconConfig|EconIndicators|EconProgression|EventLog|LaborMarketAccess|PropertyLedger"

# Hinweis: Keine externen Asset-Dateien mehr (EconTexts.txt, SyxEconomy.txt gelöscht v1.7.0).
# Alle UI-Texte sind hartkodiert in EconTexts.java.
```