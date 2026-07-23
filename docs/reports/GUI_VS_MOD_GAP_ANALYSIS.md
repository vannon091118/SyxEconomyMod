# GUI vs. Mod Gap-Analyse — SyxEconomyMod v1.7.2

> **Datum:** 2026-07-23 | **Build:** SUCCESS | **Fokus:** Was zeigt das Spiel-GUI an, das der Mod (noch) nicht liest?

---

## 1. Executive Summary

Der Mod liest aktuell **~31 Datenpunkte** aus der Vanilla-Engine (via EconSnapshot). Das Spiel-GUI zeigt hingegen **~800+ Datenpunkte** an. Der Großteil davon liegt auf Ebenen (individuelle Bürger-Physik, Persönlichkeits-Traits, Pöbel-Aggregate wie RoomStats), die der Mod für seine wirtschaftliche Kernfunktion nicht benötigt — aber für tiefere Diagnose und Policy-Steuerung nutzbar wären.

**Größte Hebel (kleinster Aufwand → größter Nutzen):**
1. **STATS.BATTLE()** — existiert in V71.44, ungenutzt → Militärökonomie
2. **BOOSTABLES.PHYSICS().HEALTH** — existiert, ungenutzt → Gesundheits-Malus bei Armut
3. **RoomStats-Aggregate** (Tätigkeit, Dienstleistungen, Umwelt) — Package-Private, brauchen Bridge-Klasse
4. **Individuelle Traits** (Faul 27%, Ehrgeizig 26%) → Arbeitsmarkt-Differenzierung

---

## 2. Mod-Zugriffe — Vollständiges Inventar

### 2.1 STATS Subcategories (7 von ~7 verfügbaren genutzt)

| STATS.* | Genutzt? | Verwendung |
|---|---|---|
| `STATS.POP()` | ✅ | Population, Emigration (Wallets, EconSnapshot) |
| `STATS.FOOD()` | ✅ | Nahrungs-Decree, Verhungern (FoodRollback, FoodTransactionPlan) |
| `STATS.WORK()` | ✅ | Beschäftigung, Profession (PovertyPressure, FirmLedger, Wages) |
| `STATS.REL()` | ✅ | Religions-Referenz (Wallets) |
| `STATS.HOME()` | ✅ | Behausung (HousingMarket) |
| `STATS.RELIGION()` | ✅ | (ReligionMarket) |
| `STATS.MULTIPLIERS()` | ✅ | (FlowMeter) |
| `STATS.BATTLE()` | ❌ | **EXISTIERT — UNGENUTZT** |

### 2.2 BOOSTABLES Subcategories (3 genutzt, 6+ ungenutzt)

| BOOSTABLE | Genutzt? | Verwendung |
|---|---|---|
| `BEHAVIOUR().HAPPI` | ✅ | WealthHappiness, PropertyHappiness, PovertyPressure |
| `BEHAVIOUR().LOYALTY` | ✅ | GiniConsequences |
| `CIVICS().IMMIGRATION` | ✅ | MeticImmigration |
| `CIVICS().DEFALTION` | ✅ | InflationOff |
| `CIVICS().GOV` | ✅ | EconProgression (INDUSTRIE-Booster via Reflection) |
| `CONSUMPTION` | ✅ | FlowMeter |
| `BEHAVIOUR().LAWFULNESS` | ❌ | **EXISTIERT — UNGENUTZT** |
| `PHYSICS().HEALTH` | ❌ | **EXISTIERT — UNGENUTZT** |
| `PHYSICS().REPRODUCTION_SPEED` | ❌ | **EXISTIERT — UNGENUTZT** |
| `CIVICS().INNOVATION` | ❌ | **EXISTIERT — UNGENUTZT** |

### 2.3 EconSnapshot — 31 Felder (was der Mod tatsächlich snapshottet)

| Kategorie | Felder |
|---|---|
| **Bevölkerung** | people, deaths, emigrations, inherited, heirless |
| **Wohlstand** | totalMoney, median, mean, gini, maxWealth |
| **Beschäftigung** | incomeDue, incomePaid, workersUnpaid, meanWage, actualMeanWage |
| **Fiskal** | headTax, marketReceipts, rationOut |
| **Ressourcen** | supplyPerDay[], demandPerDay[], stock[] |
| **Warenhandel** | warehouseBought, warehouseSold, unitsBought, unitsSold |
| **Advisor** | foodBasketPrice, unpaidRatio, wageShare |
| **v1.7.1 Makro** | treasuryCurrent, foodDays |

### 2.4 SETT Subcategories (14 genutzt)

`ROOMS, JOBS, PATH, MAINTENANCE, TERRAIN, WEATHER, THINGS, ENTITIES, INVADOR, TWIDTH, THEIGHT, TAREA, TILE_BOUNDS, IN_BOUNDS`

---

## 3. GUI-Datenpunkte — Was das Spiel anzeigt, das der Mod NICHT liest

### 3.1 Kategorie A — Bürger Fulfillment (Individual)

| GUI-Datenpunkt | Mod-Zugriff | Vanilla-Quelle | Zugänglich? |
|---|---|---|---|
| Reichtumer % (93%) | ❌ | `Humanoid.indu()` → STATS | ✅ möglich |
| Ausrüstung (11 Slots) | ❌ | `RACES.res().all()` | ✅ möglich |
| Beschäftigt % | ✅ | `STATS.WORK().EMPLOYED` | ✅ genutzt |
| Arbeitszeit % | ❌ | `STATS.WORK()` | ✅ möglich |
| Erfüllung % | ❌ | `STATS.WORK()` | ✅ möglich |
| Ruhestand % | ❌ | `HCLASSES` | ✅ möglich |
| Behausungen % | ✅ | `STATS.HOME()` | ✅ genutzt |
| Einrichtung % | ⚠️ | `FurnitureModule` | ❌ Package-Private |
| Am Verhungern % | ✅ | `STATS.FOOD().STARVATION` | ✅ genutzt |
| Nahrungspräferenz % | ❌ | `STATS.FOOD()` | ✅ möglich |
| Food Servings % | ❌ | `RoomService` intern | ❌ Package-Private |
| Getränkeportionen % | ❌ | `RoomService` intern | ❌ Package-Private |

### 3.2 Kategorie B — Physik + Verhalten (per Citizen)

| GUI-Datenpunkt | Mod-Zugriff | Vanilla-Quelle | Zugänglich? |
|---|---|---|---|
| Gewicht, Ausdauer, Geschwindigkeit, Beschleunigung | ❌ | `Humanoid.bodies()` → `PhysicsBody` | ✅ möglich |
| Gesundheit | ❌ | `STATS.HEALTH` oder `Humanoid` Getter | ⚠️ prüfen |
| Kälte-/Hitzeresistenz | ❌ | `Humanoid.race().stats` | ✅ möglich |
| Verschmutzung | ❌ | `SETT.ENV()` | ⚠️ prüfen |
| Lebenserwartung | ❌ | `RaceStats` | ✅ möglich |
| Gesetzestreue, Unterwerfung, Gemeinschaft | ❌ | `Humanoid.trait()` → `PersonalityTrait` | ✅ möglich |
| Aggression, Stolz, Ehre, Gnade, Kompetenz, Toleranz | ❌ | `Humanoid.trait()` | ✅ möglich |
| Trait-Verteilung (Ehrgeizig 26%, Faul 27%, etc.) | ❌ | `POPULATION` Aggregate | ⚠️ prüfen |

### 3.3 Kategorie C — Pöbel-Panel (Population Aggregate)

| GUI-Datenpunkt | Wert (Beispiel) | Mod-Zugriff | Vanilla-Quelle | Zugänglich? |
|---|---|---|---|---|
| Loyalität | 82% | ⚠️ | `BOOSTABLES.BEHAVIOUR().LOYALTY` | Nur schreiben, nicht lesen |
| Happiness | 90-96% | ⚠️ | `BOOSTABLES.BEHAVIOUR().HAPPI` | Nur schreiben, nicht lesen |
| Bevölkerung | 14.000/17.000 | ✅ | `POPULATION.size()/cap()` | ✅ genutzt |
| Zugang | 10.410/35.500 | ❌ | `RoomStats` | ❌ Package-Private |
| Dienstleistungen | 3.218/37.500 | ❌ | `RoomStats` | ❌ Package-Private |
| Umwelt | 21.805/32.000 | ❌ | `RoomStats` | ❌ Package-Private |
| Religion | 3.000/12.500 | ✅ | `RELIGIONS` | ✅ genutzt |
| Tätigkeit | 0.176/12.000 (1.5%) | ❌ | `RoomStats` | ❌ Package-Private |
| Regierung | 2.862/3.250 | ❌ | `RoomStats` | ❌ Package-Private |

**⚠️ KRITISCH: Tätigkeit 1.5%** — Dieser Wert wurde im GUI-Audit als "katastrophal niedrig" markiert. Direkt mit Einrichtung und Dienstleistungsversorgung verknüpfbar. Der Mod kann ihn NICHT lesen, weil RoomStats Package-Private ist. Braucht eine Bridge-Klasse analog zu `LaborMarketAccess`.

### 3.4 Kategorie D — Wirtschaft-Panel

| GUI-Datenpunkt | Mod-Zugriff | Vanilla-Quelle | Zugänglich? |
|---|---|---|---|
| Schatzkammer 220K | ✅ | `FACTIONS.player().credits()` | ✅ genutzt |
| Reinvermögen 562K | ❌ | `FACTIONS.player().res()` Aggregate | ✅ möglich |
| Construction +8.07K | ⚠️ | `FCredits.CTYPE` | ✅ möglich |
| Trade-Einnahmen | ⚠️ | `FCredits.CTYPE.TRADE` | ✅ möglich |
| Tax-Einnahmen | ✅ | `Fiscal` | ✅ genutzt |
| Tourism-Einnahmen | ❌ | `TOURISM` Stats | ✅ möglich |
| Tribute | ❌ | `FACTIONS` Diplomacy | ⚠️ prüfen |
| Sklaven-Einnahmen | ❌ | `HCLASSES.SLAVE` + Markt | ⚠️ prüfen |
| Ressourcen-Preise 25% | ✅ | `FlowPrices` | ✅ genutzt |

---

## 4. Gap-Matrix — Zugänglichkeit vs. Nutzen

| Gap | Zugänglichkeit | Aufwand | Nutzen | Priorität |
|---|---|---|---|---|
| `STATS.BATTLE()` lesen | ✅ Direkt | Klein | Militärökonomie (Rüstung, Soldatenlohn) | 🟡 P1 |
| `BOOSTABLES.PHYSICS().HEALTH` nutzen | ✅ Direkt | Klein | Gesundheits-Malus bei Armut | 🟡 P1 |
| `BOOSTABLES.BEHAVIOUR().LAWFULNESS` nutzen | ✅ Direkt | Klein | Kriminalität bei Ungleichheit | 🟡 P2 |
| Individuelle Traits (Faul, Ehrgeizig) lesen | ✅ `Humanoid.trait()` | Mittel | Arbeitsmarkt-Differenzierung | 🟡 P2 |
| `FurnitureModule` → Einrichtung % | ❌ Package-Private | Groß | Bridge-Klasse nötig | 🔴 P3 |
| RoomStats (Tätigkeit, Zugang, etc.) | ❌ Package-Private | Groß | Bridge-Klasse nötig | 🔴 P3 |
| `FCredits.CTYPE` Einnahmen-Kategorien | ✅ Direkt | Klein | Detaillierte Staatsbuchhaltung | 🟢 P2 |
| `FACTIONS.player().res()` NetWealth | ✅ Direkt | Klein | Reinvermögen-Trend im Advisor | 🟢 P2 |
| `RACES.res().all()` Ausrüstung | ✅ Direkt | Mittel | Ausrüstungs-Monitor | 🟢 P3 |
| Happiness/Loyalty WERTE lesen | ❌ Nur Booster-Set | Groß | API-Limit — nicht lesbar | 🔴 Unlösbar |

---

## 5. Handlungsempfehlungen (priorisiert)

### 🟡 P1 — Quick Wins (heute machbar, kein Reflection)

1. **`STATS.BATTLE()` einlesen** — `EconSnapshot` um `battleThreat` Feld erweitern. Existiert in V71.44. Ermöglicht Militärökonomie (automatische Rüstungspriorisierung bei Kriegsgefahr).

2. **`BOOSTABLES.PHYSICS().HEALTH` Booster registrieren** — Analog zu PovertyPressure: Gesundheits-Malus für arme/unbeschäftigte Bürger (oder Bonus für reiche). Direkt nutzbar.

### 🟡 P2 — Mittlerer Aufwand

3. **Individuelle Traits sampeln** — `Humanoid.trait()` → Faulheit/Arbeitsmoral in `PovertyPressure` einbeziehen. Ein fauler Bürger sollte stärkeren Happiness-Malus bei Arbeitslosigkeit haben als ein ehrgeiziger.

4. **`FCredits.CTYPE`-Einnahmen aufschlüsseln** — Statt nur `treasuryCurrent` die einzelnen CTYPEs (CONSTRUCTION, TRADE, TAX, TOURISM) snapshotten → detailliertere Staatsbuchhaltung im Bücher-Tab.

### 🔴 P3 — Braucht Bridge-Klassen (analog LaborMarketAccess)

5. **RoomStats-Bridge für Tätigkeit/Dienstleistungen/Umwelt** — Package `settlement.room.stats` → neue `RoomStatsAccess.java`. Die "Tätigkeit 1.5%" ist der kritischste unzugängliche Wert im gesamten GUI.

---

## 6. Was der Mod BEWUSST nicht liest (Architekturentscheidungen)

| Datenpunkt | Grund |
|---|---|
| Happiness/Loyalty WERTE lesen | BOOSTABLES sind Write-Only. Es gibt keinen öffentlichen Getter für den aktuellen Booster-Wert. |
| Food/Drink Servings % | Interne RoomService-Metrik, nicht öffentlich. |
| Einrichtung % (FurnitureModule) | Package-Private. Workaround: `FurnishingAutomation` nutzt Holz-FlowMeter als Proxy. |
| Physik-Daten (Gewicht, Geschwindigkeit) | Irrelevant für Wirtschaftssimulation. |
| Persönlichkeits-Traits | Nur relevant wenn Arbeitsmarkt-Differenzierung implementiert wird. |

---

## 7. Datei-Referenzen

| Datei | Relevanz |
|---|---|
| `EconSnapshot.java` | 31 Felder — komplette Mod-Datenerfassung |
| `EngineSeams.java` | 20+ Methoden — Brücke zu Vanilla-AI und Ressourcen |
| `FlowMeter.java` | V71 Consumption API — Ressourcen-Tracking |
| `STATS.java` (Vanilla V71.44) | FOOD, WORK, POP, REL, HOME, BATTLE, MULTIPLIERS |
| `BOOSTABLES.java` (Vanilla V71.44) | BEHAVIOUR, CIVICS, PHYSICS, CONSUMPTION |
| `Humanoid.java` (Vanilla) | trait(), bodies(), race(), indu(), ai() |

---

*Report erstellt: 2026-07-23 | 5 Handlungsempfehlungen | 3 Architekturentscheidungen dokumentiert*
