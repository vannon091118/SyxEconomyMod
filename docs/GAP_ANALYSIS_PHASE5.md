# SyxEconomyMod — Gesamt-Inventur & Gap-Analyse für Phase 5

> **Stand:** 2026-07-23 | **Basis:** v0.1.2 (JAR) + aktueller Source | **Autor:** Automatische Analyse gegen Source-Code

---

## 1. Phasen-basierte Feature-Wirkung

**Problem:** Viele Systeme wirken ab Tag 1 mit voller Kraft — egal ob 20 oder 2000 Bürger.

| System | SUBSISTENZ (20-50) | HANDEL (50-150) | INDUSTRIE (150-500) | WOHLSTAND (500+) |
|--------|-------------------|-----------------|--------------------|--------------------|
| GrainDole (cap=100) | ✅ Rettet alle → kein Überlebensdruck | ✅ Deckt fast alle → kein Druck | ⚠️ Deckt ~30% → Druck entsteht | ❌ Irrelevant (viele haben >500D) |
| HandoutRelief (400D) | ❌ Zu hoch — mehr als Lohn! | ⚠️ Konkurriert mit Löhnen | ✅ Angemessen als Top-up | ❌ Zu wenig, aber irrelevant |
| Kopfsteuer | ❌ Null-Bürger zahlen nichts (Freigrenze 500) | ⚠️ Minimal | ✅ Wirkt proportional | ✅ Einnahmequelle |
| guildSurplusShare | ⚠️ 25% bei Firmen mit 1 Arbeiter = fast nichts | ✅ Fängt an zu wirken | ✅ Verteilt Gewinne | ✅ Stabilisiert |
| firmSizingEnabled | ❌ Hillclimber bei 1 Firma = sinnlos | ✅ Optimiert 3-5 Firmen | ✅ Hauptmechanik | ✅ Hauptmechanik |
| GiniConsequences | ❌ 2 Bürger → Gini immer 0 oder 1 | ⚠️ Schwankt stark | ✅ Aussagekräftig | ✅ Hauptindikator |
| HousingMarket | ⚠️ Miete bei 20D/Tile schmerzt bei 200D Startgeld | ✅ Träglicher Druck | ✅ Marktmechanik | ✅ Immobilienmarkt |
| Corvée | ❌ Unter popThreshold(100) = keine Zwangsarbeit | ⚠️ 20% Draft → spürbar | ✅ Produktiv | ✅ Wirtschaftslenkung |
| LaborMarket | ❌ Sinnlos bei 1-2 Firmen | ✅ Prioritätssteuerung | ✅ Haupthebel | ✅ Optimiert |
| DebtBondage | ❌ Keiner hat 5000D Schulden | ⚠️ Vereinzelte Fälle | ✅ Druckmittel | ⚠️ Selten |

**Kern-Problem:** GrainDole + HandoutRelief zusammen killen den Überlebensdruck komplett in SUBSISTENZ. Ein Bürger bekommt 400D Handout + gratis Essen über GrainDole — das ist mehr als ein Arbeiter mit 50D Lohn minus Essen.

**Empfehlung:**
- GrainDole cap = `EconConfig.doleHeadcapBase * stageMultiplier` → SUBSISTENZ: 20, HANDEL: 50, INDUSTRIE: 100
- HandoutRelief: nur wenn `EMPLOYED != null` (bereits so) UND Betrag = `min(400, doleWealthThreshold - netWorth)` (bereits so, ABER: wenn foodAffordabilityGate=false essen Bürger gratis → Handout ist reiner Gewinn)
- **Kritisch:** `foodAffordabilityGateEnabled = false` kombiniert mit `handoutWalletAmount = 400` erzeugt einen Geld-Druck-Exploit

---

## 2. Zugang & Bedürfnisse: Binarität

### Was "binär" bedeutet

**AccessAutomation.java (Zeile ~46-52):**
```java
if (stock > 50) { target = Math.min(3, maxTarget); }
else if (stock > 0) { target = 1; }
// else: target = 0 (kein Zugang)
```
Das ist ein **Stufen-Cliff**: Bei Bestand=51 gibt es 3 Möbel-Einheiten, bei Bestand=50 nur 1, bei 0 gar keine. Keine graduellen Übergänge.

**AffordabilityGate.java:**
- `requestFood()`: Bürger kann den Preis → Admission(true). Kann nicht → Admission(false). **Binär.**
- Kein "ich kaufe die Hälfte" oder "ich kaufe billigeres Essen statt teurem".
- `freeRation()`: Sklaven, Waisen, GrainDole-Empfänger → **komplett kostenlos**. Alle anderen → **voller Preis**. Null Übergang.

**FoodPlanController.java:**
- Entscheidung: `NEEDS.TYPES().HUNGER.stat().getPrio(h) <= 0` → wird übersprungen.
- Vanilla `getPrio()` arbeitet mit CHUNK-Werten (0x10 Schritte) — das ist graduell, aber der Controller entscheidet binär darüber.

**BrokeFoodPlan.java:**
- Wenn Bürger kein Essen bekommt → `desperate`-Modus → sucht Terrain/Leichen.
- `markStarvedIfLethal()`: Hunger bei Maximum → `CAUSE_LEAVES.STARVED()`. **Cliff: kein langsames Sterben, sondern Schalter.**

### Vanilla-Needs (nicht direkt sichtbar, aber impliziert)

`NEEDS.TYPES()` liefert: HUNGER, THIRST, SLEEP, DECORATION, HYGIENE, FAITH, etc.
- `stat().indu().getD(indu)` → Float 0.0-1.0 (graduell!)
- `stat().getPrio(indu)` → Int in CHUNK-Schritten (0x10 = 16 Stufen)
- Vanilla **degradiert Needs langsam** — unser Mod entscheidet aber binär ob der Bürger überhaupt zum Essen kommt.

**Gap:** Vanilla ist smooth, unser Gate ist binär → Death Spirals statt gradueller Verschlechterung.

**Empfehlung:**
- Fractional Purchasing: Bürger mit 50% des Preises bekommt 50% der Portion.
- Gradual Access: `target = clamp(stock / targetStockDays, 0, maxTarget)` statt Stufen-Cliff.
- Niedrigere Stock-Schwelle für target>0: 50→10 (nur 10 Einheiten nötig statt 50).

---

## 3. Job-Erfahrung

### Aktueller Stand: **KEIN Erfahrungssystem**

**Was existiert:**
- `STATS.WORK().profession` (vanilla) → aktuelle Berufszuweisung pro Bürger
- `STATS.WORK().WORK_TIME` (vanilla) → Zeit am Arbeitsplatz (bereits getrackt!)
- `STATS.WORK().WORK_FULFILLMENT` (vanilla) → Erfüllungsgrad
- `EconomySim.update()` → kein XP-Hook
- `Wallets.java` → `lambdaBp[]` als individueller Skalar (0.0-0.99) → **perfektes Pattern für XP**

**Was fehlt:**
- Kein Array das speichert "Bürger X war 50 Tage Bäcker"
- Kein Boostable das Produktions-Effizienz aus Erfahrung ableitet
- `BValue.BValueInduOnly` existiert → Injektionspunkt vorhanden
- `IUpdater` existiert → chunked Update über SLOTS möglich

### Day-Lohner vs. Arbeiter

**OddjobMarket.java:** `oddjobWagePerTask = 3` pro abgeschlossener Aufgabe.
**Wages.java:** `defaultWage = 50` pro Saison.

Das Verhältnis hängt davon ab, wie viele Tasks pro Saison abgeschlossen werden. Wenn ein Day-Lohner 20+ Tasks/Saison schafft, verdient er 60D — mehr als ein regulärer Arbeiter mit 50D.

**Gap:** Kein `Math.min(oddjob_daily_yield, regular_minimum_wage)` Clamp.

**Empfehlung:**
1. `int[] xp = new int[60000]` in neuer Klasse `CitizenExperience` (gleiches Pattern wie Wallets)
2. In `EconomySim.update()`: pro Bürger `profession` auslesen → XP[inuSlot]++
3. BValueInduOnly auf Produktionsspeed registrieren (z.B. `BOOSTABLES.CIVICS()` oder custom)
4. XP bindet an Beruf: bei Berufswechsel → 50% XP-Verlust (motiviert Stabilität)
5. Oddjob-Clamp: `oddjobWagePerTask * tasksPerSeason <= defaultWage * 0.8`

---

## 4. Produktions-Raum-Löhne

### Wer reguliert?

**Aktuell:**
1. `Wages.java` → HashMap pro Room-Key, Default=50, vom **Mod automatisch** gesetzt
2. `StateWageMarket.java` → bezahlt Löhne für state-funded Räume (Militär, Export, etc.)
3. `EconConfig.defaultWage = 50` → globaler Fallback
4. **Kein UI-Slider** um Löhne pro Raum einzustellen (außer im Advisor-Tab via `Wages.setWage()`)

**Problem:** Der Mod setzt Löhne automatisch, der Spieler sieht das erst im Nachhinein. `LaborMarket` verschiebt Prioritäten basierend auf Profitabilität — das ist Lohnsteuerung ohne explizites Player-Consent.

**Vanilla hat:** Room-Priority-Slider pro Blueprint-Instanz (0-20). `LaborMarket` manipuliert genau diese Slider über `e.priority.set(priority)`.

**Gap:** Kein Mechanismus für den Spieler zu sagen "Ich will die Löhne in meiner Bäckerei selbst kontrollieren." Alles ist Mod-gesteuert.

**Empfehlung:**
- "Player Override" Flag pro Blueprint: wenn Player den Slider manuell bewegt hat (`playerIntervened` wird bereits in LaborMarket erkannt!), Lohnsteuerung für diesen Raum deaktivieren.
- Das `playerIntervened`-Pattern existiert bereits! → Nur die Konsequenz fehlt: aktuell wird es nur für `baseline`-Tracking genutzt, nicht für eine "Laissez-faire"-Option.

---

## 5. Privates vs. Staatliches Lager

### Aktuell

**StateWarehouses.java:** `HashSet<Long> owned` → jeder Raum mit Koordinaten-Paar als Key ist "state-owned".
- `setStateOwned(room, true/false)` → manuell oder per UI
- `isHoarding()` / `isLiquidating()` → Verkaufsmodus
- Staatliche Lager kaufen Waren über Budget, verkaufen über Preis-Schwelle
- Private Lager: werden von Händlern (Mitarbeitern des Stockpile-Rooms) betrieben → kaufen mit eigenem Geld, verkaufen mit eigenem Gewinn

**WarehouseMarket.java:** `Book[]` pro Stockpile pro Resource → wer hält was, zu welchem Preis
- `stateOwned`-Flag pro Book → wird bei Besitzerwechsel aktualisiert
- `stakes` Map → welcher Händler hat wie viel investiert

**PropertyLedger.java:** Hat bereits `STATE = -1L` als ownerId, Shares-System, Kauf/Verkauf, Dividenden.

### Gap

Zwei getrennte Eigentumssysteme:
1. `StateWarehouses.owned` (HashSet<Long>) → Koordinaten-basiert
2. `PropertyLedger` (HashMap<Long, Entry>) → Shares-basiert

**Empfehlung:**
- StateWarehouses **in** PropertyLedger integrieren: Staatliche Lager = `ownerId=STATE, shares=100`.
- Bei Bau: Spieler bekommt Wahl — "Direktkauf" (state, reduzierter Preis) oder "Aktien emittieren" (Bürger können Anteile kaufen, Staat behält Mehrheit).
- Automatische Subventionierung: wie Firmen-Dividenden, nur für Lager — saisonale Betriebskosten aus Staatskasse wenn state-owned.
- Bestehende `StateWarehouses.owned` durch PropertyLedger-Check ersetzen.

---

## 6. Nicht-produzierende Firmen: Pause oder Kosten

### Aktuell

**FirmEconomyKernel.shouldIdle():** Wenn `profit <= hysteresis` → Target auf minimum (1 Worker) oder 0.
**FirmLedger.size():** Hillclimber passt Target an — wenn Firma rot ist, gehen Arbeiter.

**Gap:** Kein Player-Choice. Wenn Zimmermann kein Holz bekommt → automatisch Entlassung. Spieler kann nicht sagen "haltet die Arbeiter, ich zahle draus."

**Empfehlung:**
- Neuer `EconConfig`-Toggle: `retainWorkersWhenIdle = false` (Default)
- Pro Firma (PropertyLedger Entry oder FirmState): boolean `retainWorkers`
- Wenn `retainWorkers=true`: `minimumWorkersPerWorkplace` wird ignoriert, stattdessen `max()` → Staat/Käufer zalt Löhne auch ohne Produktion
- Nur für staatliche/private eigene Firmen relevant (wenn Shares > 0 oder state-owned)
- Kosten: `retentionCost = maxTarget * defaultWage` pro Saison

---

## 7. Kausaler Impact-Map

### Feedback-Schleifen

```
[POSITIV — Stabilisierend]
Geldmenge↑ → Kaufkraft↑ → Firmenumsatz↑ → Firmenprofit↑ → Lohn↑ → Geldmenge↑
                                      ↑
                               Scarcity↓ (mehr Produktion)

[NEGATIV — Destabilisierend]
Gini↑ → Loyalty↓ → Emigration↑ → Arbeitskräftemangel↑ → Scarcity↑ → Preise↑ → Gini↑
Poverty↑ → Happiness↓ → Emigration↑
Debt↑ → Enslavement → Arbeitskräfteverlust → weniger Produktion → mehr Poverty
Corvée↑ → mehr Staatsproduktion, aber ↓ private Arbeitskräfte → private Firmen sterben

[CHEAT-LOOP — muss gefixt werden]
foodAffordabilityGate=false + HandoutRelief=400:
  Bürger bekommt 400D Handout (kostenlos) + gratis Essen (Gate=off)
  → Bürger akkumuliert Geld ohne Sinks
  → kauft Firmenanteile/Haus → Crash des Property-Markts
  → Gini steigt weil nur wenige profitieren
```

### Direkte Kausalketten

| Auslöser | → System 1 | → System 2 | → Endergebnis |
|----------|-----------|-----------|----------------|
| Bürger verliert Job | PovertyPressure (HAPPI ↓) | HealthPressure (HEALTH ↓) | Emigration oder Tod |
| Firma wird insolvent | guildSurplusShare=0 → keine Auszahlung | Arbeiter gehen → Oddjob | Weniger Produktion |
| Staat geht pleite | TreasuryCrisis (6 Tiers!) | Löhne nicht gezahlt | Emigration + Riots |
| Gini > 0.60 | GiniConsequences (LOYALTY ↓) | Vanilla Riots | Gebäude-Verluste |
| Essen zu teuer | AffordabilityGate → Denied | BrokeFoodPlan → Desperate | Starvation oder Kannibalismus |
| Corvée 20% | Firmen verlieren Arbeiter | Scarcity steigt | Preise steigen |

---

## 8. Vanilla-Raum-Inventar

### Vom Mod bereits genutzte Räume

| Vanilla Room | Mod-System | Status |
|-------------|-----------|--------|
| `ROOM_EATERY` (Gaststätte) | FoodTransactionPlan, WarehouseMarket | ✅ Aktiv, EconomyEateryAccess |
| `ROOM_CANTEEN` (Kantine) | FoodTransactionPlan, WarehouseMarket | ✅ Aktiv, EconomyCanteenAccess |
| `ROOM_TAVERN` (Taverne) | WarehouseMarket (retailBooks) | ✅ Aktiv, EconomyTavernAccess |
| `ROOM_MARKET` (Markthalle) | WarehouseMarket, ServicePlanController | ✅ Aktiv |
| `ROOM_HOME` (Haus) | HousingMarket, PropertyLedger | ✅ Aktiv |
| `ROOM_CHAMBER` (Kammer) | HousingMarket, PropertyLedger | ✅ Aktiv |
| `ROOM_M_TRAINER` (Militärtrainer) | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_EXPORT` (Export) | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_HAULER` (Transporteur) | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_SUPPLY` (Nachschub) | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_LABORATORY` | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_LIBRARY` | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_EMBASSY` | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_POLICE/GUARD/PRISON/STOCKADE` | EconomicRoles → StateWageMarket | ✅ Aktiv |
| `ROOM_TEMPLE/WELL/HEARTH` | EconomicRoles (publicWorks) | ✅ Aktiv |
| `ROOM_CANNIBAL` | EconomicRoles | ✅ Aktiv (wenn passiert) |
| `ROOM_STOCKPILE` (Lagerhaus) | StateWarehouses, WarehouseMarket | ✅ Aktiv |

### Vom Mod NICHT genutzte Vanilla-Räume

| Vanilla Room | Potenzial | Automatisierbar? |
|-------------|-----------|------------------|
| `ROOM_BATH` (Badehaus) | Hygiene-Need abdecken | Ja — ServicePlanController Pattern |
| `settlement.room.knowledge.school` | Berufserfahrung (XP-Quelle!) | Ja — BValueInduOnly Pattern |
| `settlement.room.knowledge.university` | Tech-Kosten als Boostable | Ja — via BOOSTABLES.CIVICS() |
| `ROOM_THRONE` (Thronsaal) | Loyalty/Sanity-Booster | Nicht sinnvoll automatisierbar |
| `ROOM_STATUE` | Happiness-Booster | Nicht sinnvoll automatisierbar |
| `settlement.room.military.training.barracks` | Kampf-XP | Ja — STATS.BATTLE().position(indu) |
| `settlement.room.military.training.archery` | Kampf-XP | Ja — gleicher Mechanismus |

### Vanilla Auto-Hire

Vanilla bietet **Room-Priority-Slider** (0-20 pro Blueprint) — das ist der einzige Steuerungshebel.
- `LaborMarket.java` manipuliert genau diese Slider → **das ist unsere Auto-Hire-Mechanik**
- Vanilla selbst stellt keine "automatisch einstellen" Option bereit — die Mod ist die Automation
- `WorkplaceDefaults.java` setzt Default-Targets → könnte als "Auto-Manage" Toggle missbraucht werden

---

## 9. Essensausgabe: Cheat-Check

### Ist es ein Cheat?

**Wenn `foodAffordabilityGateEnabled = false` (Default):**
- Bürger essen **kostenlos** bei Eatery/Canteen → vanilla-Bypass
- `AffordabilityGate.requestFood()` → immer `Admission(true, 0, true)` (free=true)
- `freeRation()` gibt nur für Sklaven/Waisen/GrainDole — aber da Gate=false ist, ist ALLES kostenlos
- `settleFood()` → bill=0, `recordDoledMeal()` wird nicht aufgerufen (da free=true über Gate kommt, nicht über GrainDole)

**Kombination mit HandoutRelief:**
- Bürger bekommt 400D pro Saison (wenn employed)
- Bürger zahlt 0D für Essen (Gate=false)
- Netto: +400D/Saison ohne Ausgaben
- Bei 200 Bürgern: 80.000D pro Saison an reiner Geldschöpfung

**Ist das balanced?** NEIN. Das ist ein systemischer Exploit:
1. Arbeiter akkumulieren Geld ohne Sinks
2. Reiche Bürger kaufen Firmenanteile → Gini steigt
3. Arme Bürger haben nichts → werden von GrainDole alimentiert
4. Ergebnis: Zwei-Klassen-Gesellschaft ohne Druck, sich zu verbessern

**Empfehlung:**
- `foodAffordabilityGateEnabled` auf `true` setzen als Default
- ODER: `handoutWalletAmount` auf max. 50% des Essen-Preises reduzieren
- ODER: HandoutRelief nur bei `foodAffordabilityGateEnabled = true` aktivieren (das wäre logisch konsistent)

---

## 10. Migration & Auswanderung

### Vanilla-Mechanismen (nicht modifiziert)

1. **Immigration:** Vanilla `BOOSTABLES.CIVICS().IMMIGRATION` → basiert auf Happiness, Loyalty, freien Wohnplätzen
2. **Emigration:** Vanilla triggert bei `LOYALTY < threshold` oder `HAPPI < threshold` → `CAUSE_LEAVES`
3. **Starvation:** `NEEDS.TYPES().HUNGER.stat().isMax(indu)` → `CAUSE_LEAVES.STARVED()`
4. **Enslavement:** `EngineSeams.enslave(h)` → Bürger wird HCLASSES.SLAVE

### Mod-Manipulation

| System | Wirkung auf Migration | Hebel |
|--------|----------------------|-------|
| `MeticImmigration` | Reduziert Immigration für Nicht-Rasse-Bürger basierend auf Steuern | `meticImmigrationDepth/Steepness` |
| `WealthHappiness` | Reichere Bürger → happier → weniger Emigration | `happinessAtPoorest/Richest` |
| `PropertyHappiness` | Hausbesitzer → happier | `propertyHappinessBoost` |
| `PovertyPressure` | Arme+Arbeitslose → unglücklicher → Emigration | `povertyPressureHappinessMin` |
| `HealthPressure` | Arme → schlechtere Gesundheit → Tod | Fester 0.85x Multiplier |
| `GiniConsequences` | Hohe Gini → Loyalty↓ → Emigration/Riots | `loyaltyAtMaxGini` |
| `DebtBondage` | Schuldknechte werden versklavt → KEINE Emigration | `debtSlaveThreshold` |
| `GrainDole` | Verhindert Tod/Emigration der Ärmsten | `doleHeadcap`, `doleWealthThreshold` |
| `BrokeFoodPlan` | Verhindert Starvation wenn Essen da ist | Überlebens-Netz |

### Kritische Lücke: Keine dynamische Einwanderungsbremse

Aktuell: `MeticImmigration` modifiziert den Booster, aber es gibt keinen Mechanismus der sagt "bei 20% Arbeitslosigkeit → Immigration stoppen." Vanilla regelt das über Happiness/Loyalty indirekt, aber nicht granular.

**Empfehlung:**
- `MeticImmigration` um Arbeitslosenquote erweitern: `unemploymentRate > 0.20` → Booster auf 0.0 setzen
- Das ist eine 3-Zeilen-Änderung in `MeticImmigration.java` (die `vGet()` Methode prüft bereits `sim.taxes()`)
- Immigration-Politik als Phase-Gate: SUBSISTENZ → offen, HANDEL → moderat, INDUSTRIE+ → nach Bedarf

---

## Zusammenfassung: Top-5 Sofort-Maßnahmen

| # | Maßnahme | Dateien | Aufwand | Impact |
|---|---------|---------|---------|--------|
| 1 | `foodAffordabilityGateEnabled=true` Default | `EconConfig.java` | 1 Zeile | Kritisch — stoppt Geld-Druck-Exploit |
| 2 | Gradual Access statt Binary Cliff | `AccessAutomation.java` | ~20 Zeilen | Hoch — smootheres Gameplay |
| 3 | Oddjob-Clamp (Wage ≤ 80% von Regular) | `OddjobMarket.java` | ~5 Zeilen | Hoch — Day-Lohner-Exploit fix |
| 4 | GrainDole cap stage-gated | `EconConfig.java` | ~10 Zeilen | Mittel — Early-Game-Druck |
| 5 | Job-XP-Array (Wallets-Pattern) | Neue Klasse + EconomySim Hook | ~200 Zeilen | Hoch — fundamentales neues Feature |
