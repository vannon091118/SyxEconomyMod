# SyxEconomyMod — Master-Audit für Phase 5

> **Stand:** 2026-07-23 | **Basis:** v0.1.2 (JAR) + aktueller Source | **Methodik:** 4-Perspektiven-Audit + Vanilla-System-Inventur
>
> Dieses Dokument konsolidiert: GAP_ANALYSIS_PHASE5.md, 4-Perspektiven-Audit (User/Architekt/Reverse-Engineer/Game-Designer), Vanilla-System-Audit (6 Hebel-Bereiche).
> **Fokus:** Struktur & Stabilität — Balance ist explizit ausgenommen.

---

## Executive Summary — Top-5 Risiken VOR Phase 5

| # | Risiko | Severity | LOC-Impact | Phase-5-Blocker |
|---|--------|----------|------------|-----------------|
| 1 | **EconomySim = God-Class** (1.513 LOC, 25+ Subsysteme hartkodiert) | Kritisch | −400 | Ja — DI/Observer nicht einführbar ohne Aufbrechen |
| 2 | **Phase-4-Claim "0 direkte EngineSeams-Calls" ist FALSCH** (20+ in core/) | Kritisch | −120 | Ja — Reflection-Migration unvollständig |
| 3 | **27× catch(Throwable), 89× catch(Exception), 13 leere Catches, 124× printStackTrace** | Hoch | −80 | Ja — Stabilität nicht haltbar |
| 4 | **foodAffordabilityGate=false + HandoutRelief=400 = Geld-Druck-Exploit** | Kritisch | 0 (Config) | Ja — Cheat-Loop zerstört Gini |
| 5 | **AccessAutomation Binary Cliff + AffordabilityGate binär** | Hoch | −20 | Teilweise — Death Spirals statt gradueller Verschlechterung |

**Phase 5 sollte nicht starten, bevor Risiko 1–3 strukturell angegangen sind.**

---

## TEIL A: 4-Perspektiven-Audit

---

### Perspektive A — User Experience

| Symptom | Lücke |
|---------|-------|
| Debug-Tab zeigt Zahlen ohne "was tun?"-Hinweise | Reine Anzeige, keine Handlungsempfehlung |
| EventLog-Mix aus INFO/WARN/ERROR nur per Augenscheid unterscheidbar | Fehlende Severity-Filter / pro-Tag-Buckets |
| Dropdowns/Slider für Settings fehlen | EconConfig 174 Felder, davon 0 UI-exposed |
| "WARNUNG: Gini > 0.35" — Spieler weiß nicht welche Lohn-Schraube | Diagnose ohne Therapie |
| 3 CSV-Files werden unendlich groß | Keine Rotation/Truncate-Strategie |

**UI-Fundament für Phase 5:**
- `EconomyWindow` 2.999 LOC → **muss** in Panel-Klassen aufgeteilt werden
- Empfehlung: `ui/tabs/{Overview,Markt,Löhne,Betriebe,Berater,Debug}Panel.java` (~300 LOC statt 3000)

---

### Perspektive B — System Architecture

#### B1. EconomySim: 1.513 LOC Monolith
- Konstruktor instanziiert 25+ `new`-Aufrufe
- Kein Subsystem ist isoliert testbar
- **Fix:** Constructor nimmt `SimContext` (Factory) → EconomySim ca. 600 LOC, 11 Parameter, testbar

#### B2. Phase-4-Lüge: 20+ direkte EngineSeams in core/
- Betroffene Dateien: `SupplyExportService`, `AccessAutomation`, `WarehouseAutomation`, `EconomicRoles`, `FlowMeter`, `MainScript`, `InstanceScript`, `Roster`, `LocalPrices`, `FoodPlanController`, `RationOptimizer`, `Liturgy`, `MeticImmigration`, `DebtDiplomacyBuffer`, `GrainDole`, `WealthHappiness`, `PropertyHappiness`, `StateWarehouses`, `TransportMarket`, `EconProgression`
- **Fix:** Migrationsplan: erst Top-15, dann sukzessive

#### B3. Save/Load: Hardcoded TAG_* Kaskaden
- 50+ `out.i(TAG_X, …)` mit statischen Int-Konstanten
- `loadLegacy()` enthält `if (version >= 47)` Blöcke für zehn Versionen
- **Fix:** `SaveRegistry` mit `register(Field, tag, version)` → −200 LOC, testbar

#### B4. EconConfig: 174 mutable static fields
- Alle `public static int XX = 50;` — von überall her schreibbar
- Kein Diff möglich, kein Hot-Reload, keine Save-Validierung
- **Fix:** Immutable Record + `EconConfigLoader` + `assets/init/econ.json`

#### B5. Datei-Größe-Top-Liste

| Datei | LOC | Split-Empfehlung |
|-------|-----|------------------|
| EconomyWindow | 2.999 | 8 Panel-Klassen |
| WarehouseMarket | 1.904 | UI in Panel, Logik in `MarketMath` |
| EconomySim | 1.513 | Factory + 4 Manager-Klassen |
| FoodTransactionPlan | 629 | TransactionPlan, FoodGate, FailureHandle |
| StateWarehouses | 640 | 3 Klassen |
| FirmLedger | 614 | 2 Klassen |
| Wallets | 541 | 2 Klassen |
| TreasuryCrisis | 447 | Strategy-Klassen |
| DiagnosticExporter | 407 | 1 Klasse pro CSV |

**Gesamt-Potential:** −2.500 LOC strukturell, ohne Funktionsverlust

---

### Perspektive C — Reverse Engineering & Stabilität

#### C1. Exception-Hygiene
- **27×** `catch (Throwable)` — fängt auch `OutOfMemoryError`, `ThreadDeath`
- **89×** `catch (Exception)` — zu breit, maskiert Bugs
- **13×** leere Catch-Blöcke — Phantom-Bugs
- **124×** `printStackTrace()` — verschluckt in stderr
- **Fix-Reihenfolge:**
  1. Leere Catches → loggen+re-throw oder explizit dokumentieren
  2. `catch (Throwable)` → `catch (Exception)`, separate `Error`-Behandlung
  3. `printStackTrace()` → `EventLog.logThrow("SEAM", t)`
  4. Generische Catches → spezifischer pro Exception-Typ

#### C2. IdentityHashMap-Reliance
- `IdentityHashMap<RoomInstance, …>` in mehreren Klassen
- Save/Load instanziiert `RoomInstance` neu → andere Identity → Map leer → **stiller Datenverlust**
- **Fix:** Koordinaten-basierte Keys (`"X,Y"`) statt Referenz-Keys

#### C3. Volatile Render Caches ohne Snapshot
- `cachedRichestCitizen`, `cachedWorkplaces` — `volatile` aber Liste nicht kopiergeschützt
- Render-Thread iteriert während Sim-Update → `ConcurrentModificationException`
- **Fix:** `AtomicReference<List<X>>` oder `ImmutableList`

#### C4. Hardcoded Settlement-Limits
- `MAX_SLOTS = 60000` an mehreren Stellen
- **Fix:** `EconConfig.maxSlots` + Konsolidierung

#### C5. Race-Conditions in DiagnosticExporter
- 3 Dateien schreiben ohne Lock
- **Fix:** `synchronized (writeLock)` um die Print-Writer-Calls

---

### Perspektive D — Game Design (strukturell)

#### D1. Decktgrad-Indikator ohne Reaktions-Loop
- `Decktgrad 0.06` (Holz) ist sichtbar, aber automatische Import/Subvention reagiert nicht schnell genug
- **Lücke:** Kein geschlossener Kreis `Scarcity → Subsidy → Import → Stockpile`

#### D2. Bimodaler Gini als Architektur-Indiz
- Gini 0.80: 31 Bürger @4 Denari vs. 17 @300 Denari — keine Mitte
- **Wurzel:** Initialer Vermögens-Spread bei Spawn ohne Stage-Korrelation

#### D3. Treasury-Spirale: Eskalation unvollständig
- −900M Treasury ohne Wirtschaftskollaps
- **Lücke:** Tier 5 muss destruktiver sein → Zwangsprivatisierung via PropertyLedger

#### D4. AccessAutomation NPE-Flood
- 20+ NPE-Logs pro Tick → Log unleserlich
- **Fix:** Rate-Limit (bereits implementiert, aber instanz-basiert statt global)

---

## TEIL B: Vanilla-System-Inventur — 6 Hebel-Bereiche

---

### 1. ZUGANG (Access)

**Aktuelle Nutzung:**
- `AccessAutomation`: `STATS.HOME().targetSet(target, c, null, res)` — setzt Einrichtungsziele
- `EconomicRoles`: 13 statische `instanceof`-Klassifizierungen

**Brachliegende Hebel:**
- `STATS.HOME().coverage()` wird nie gelesen — kein "wie viel % sind gedeckt?"-Feedback
- `ServiceMarket.isFreePublicRoomType` hardcoded Whitelist statt generischem Hook

| Pfad | Strategie | Aufwand |
|------|-----------|---------|
| A1: Coverage-Feedback-Loop | `AccessAutomation` liest `coverage()` nach 60 Ticks, EventLog bei <30% | 0.5d |
| A2: Zugangs-Belohnung | BValueInduOnly auf HAPPI für hohe Coverage (+5% Happiness) | 1d |
| A3: Decouple Public-Service-Erkennung | Generischer `isPublicService()` Helper in EconomicRoles | 0.5d |

---

### 2. INDIVIDUELLE STATS

**Aktuelle Nutzung:**
- `WealthHappiness`: BValueInduOnly für HAPPI (relativeWealth) + Steuer-Schmerz
- `PropertyHappiness`: BValueInduOnly für Hausbesitzer-Boost
- `EconProgression`: BValueInduOnly für Admin-Boost

**Brachliegende Hebel:**
- `BOOSTABLES.BEHAVIOUR()` bietet außer HAPPI noch **SANITY, LOYALTY, LAWFULNESS, SUBMISSION** — keine wird genutzt
- `STATS.WORK().WORK_TIME` — pro-Bürger "wie lange im Job" — **komplett ungenutzt**
- `STATS.NEEDS().SNEEDS.get(i)` — Hunger/Durst/Schlaf etc. — nur in ServiceMarket, nicht als Feedback

| Pfad | Strategie | Aufwand |
|------|-----------|---------|
| S1: SANITY/LOYALTY als Gini-Verstärker | BValueInduOnly auf SANITY (hohe Gini → Sanity-Verlust → Emigration) | 1.5d |
| S2: Erfahrenheits-Bonus | `WORK_TIME` lesen → BValueInduOnly für Produktion (+15% bei >30 Tagen) | 2d |
| S3: Needs-Export | `SNEEDS.get(0..4)` als DiagnosticExporter-Spalte | 0.5d |

---

### 3. PRIORITÄTEN

**Aktuelle Nutzung:**
- `EngineSeams.overwritePlan()` — direkter Plan-Override
- `LaborMarket` manipuliert Room-Priority-Slider (0-20)

**Brachliegende Hebel:**
- `AISUB` (AI-Subroutinen) — Mikroprioritäten ("erst Stein, dann Holz")
- `WGROUP.all()` — Job-Gruppen, nicht direkt gelesen
- Vanilla Work-Priorities UI (5 Slots) — kein Hook für "SyxEconomy: profitabelster Job"

| Pfad | Strategie | Aufwand |
|------|-----------|---------|
| P1: Profit-driven Priority-Hook | 5. Slot = "SyxEconomy: profitabelster Job" via overwritePlan | 3d |
| P2: AISUB-Reihenfolge-Steuerung | overwritePlan erweitern um optionale AISUB-Reorder | 2d |
| P3: Decouple Plan-Erkennung | VanillaAIAdapter: instanceof statt getSimpleName() | 1d |

---

### 4. LAWS

**Aktuelle Nutzung:** **NULL.** Kein einziger Import/Aufruf einer `LAWS.*`-Klasse.

**Brachliegende Hebel:**
- Vanilla-Laws existieren: Steuererlass, Nahrungsmittelrationierung, Mietdeckel, Tempel-Schließung, Ausgangssperre
- Bild 3 zeigt "Verordnungen"-UI mit 4 Reglern — alle vanilla, keine SyxEconomy-Verordnung exposed

| Pfad | Strategie | Aufwand |
|------|-----------|---------|
| L1: Auto-Steuer-Erlass bei Krise | TreasuryCrisis Tier ≥3 → `LAWS.tax().setActive(false)` | 1d |
| L2: Mietdeckel-Trigger | Gini > 0.50 → `LAWS.rent().setMax(0.5)` | 1d |
| L3: Verordnungen-UI erweitern | 2 neue Regler "Subventionen" und "Miet-Limit" exposed | 2d |

---

### 5. ROOMS

**Aktuelle Nutzung:**
- `EconomicRoles`: 13 statische `instanceof`-Methoden
- `MaintenanceMarket`: iteriert `SETT.ROOMS().ins()`
- `AccessAutomation`: iteriert `RESOURCES.ALL()` × `HCLASSES.ALL()`

**Brachliegende Hebel:**
- `SETT.ROOMS().ins()` enthält ALLE Blueprints — neue Mods mit Custom-ROOM brechen still
- `RoomBlueprintImp.industries()` (IndustryResource[] out, in) — nicht für Output-basierte Klassifikation genutzt
- `RoomInstance.employees()` — kein Skill-Level pro Job-Slot

| Pfad | Strategie | Aufwand |
|------|-----------|---------|
| R1: Output-basierte Room-Klassifikation | `isFarmByOutput()` = `industries().out` enthält `ResGProduce` | 1d |
| R2: Mod-Resilient Whitelist | `isModRoomAllowed()` mit Reflection-Whitelist | 1d |
| R3: Standort-Aggregation | `clustersWithinRadius(coord, r)` für Cluster-Subvention | 1.5d |

---

### 6. GOODS

**Aktuelle Nutzung:**
- `AffordabilityGate`: `RESOURCES.EDI().all()`, `RESOURCES.DRINKS().all()`, `RACES.res().all(popCL)` für Wearables
- `LocalPrices.goodPrice()`: lokaler Waren-Preis
- `WearableResource.needed()`: individueller Warenbedarf

**Brachliegende Hebel:**
- `GOODS.all()` / `GOODS.byId()` — **niemand** greift darauf zu, obwohl unser System auf Geld basiert
- `RESOURCES.EDI().categories` (Meat/Fruit/Grain) — nicht differenziert erfasst
- `WearableResource` wird als "was braucht der Bürger" gelesen, aber nie "was besitzt er gerade"
- **Kein Decay-Modell:** Vanilla-Lagerung hat keinen Verlust, unser System auch nicht

| Pfad | Strategie | Aufwand |
|------|-----------|---------|
| G1: Wearable-Inventar pro Bürger | Wallets-Slot-Pattern für WearableResource ownership[] | 3d |
| G2: Goods-Decay-Modell | 1%/Tag Verlust bei Stein/Holz/Getreide | 1d |
| G3: Food-Kategorie-Differenzierung | foodBundleQuote pro Kategorie (Meat/Fruit/Grain) | 1.5d |

---

## TEIL C: System-Inventur & Cheat-Checks

---

### Phasen-basierte Feature-Wirkung

| System | SUBSISTENZ (20-50) | HANDEL (50-150) | INDUSTRIE (150-500) | WOHLSTAND (500+) |
|--------|-------------------|-----------------|--------------------|--------------------|
| GrainDole (cap=100) | ❌ Rettet alle | ✅ Deckt ~50% | ⚠️ Deckt ~30% | ❌ Irrelevant |
| HandoutRelief (400D) | ❌ Mehr als Lohn! | ⚠️ Konkurriert | ✅ Top-up | ❌ Irrelevant |
| Kopfsteuer (Freigrenze 500) | ❌ Keiner zahlt | ⚠️ Minimal | ✅ Proportional | ✅ Einnahmequelle |
| guildSurplusShare (0.25) | ⚠️ Fast nichts | ✅ Wirkt | ✅ Stabilisiert | ✅ Stabilisiert |
| GiniConsequences | ❌ Immer 0 oder 1 | ⚠️ Schwankt | ✅ Aussagekräftig | ✅ Hauptindikator |
| Corvée (20%) | ❌ Unter Threshold | ⚠️ Spürbar | ✅ Produktiv | ✅ Wirtschaftslenkung |
| LaborMarket | ❌ 1-2 Firmen | ✅ Prioritäten | ✅ Haupthebel | ✅ Optimiert |

---

### Kausale Feedback-Schleifen

```
[POSITIV — Stabilisierend]
Geldmenge↑ → Kaufkraft↑ → Firmenumsatz↑ → Profit↑ → Lohn↑ → Geldmenge↑

[NEGATIV — Destabilisierend]
Gini↑ → Loyalty↓ → Emigration↑ → Arbeitskräftemangel↑ → Scarcity↑ → Preise↑ → Gini↑
Poverty↑ → Happiness↓ → Emigration↑
Debt↑ → Enslavement → Arbeitskräfteverlust → weniger Produktion → mehr Poverty
Corvée↑ → Staatsproduktion↑, aber private Arbeitskräfte↓ → private Firmen sterben

[CHEAT-LOOP — muss gefixt werden]
foodAffordabilityGate=false + HandoutRelief=400:
  Bürger bekommt 400D (kostenlos) + gratis Essen (Gate=off)
  → akkumuliert Geld ohne Sinks → kauft Firmenanteile → Gini explodiert
```

---

### Essensausgabe: Cheat-Check

**`foodAffordabilityGateEnabled = false` (Default):**
- Bürger essen **kostenlos** bei Eatery/Canteen → vanilla-Bypass
- `AffordabilityGate.requestFood()` → immer `Admission(true, 0, true)` (free=true)
- `settleFood()` → bill=0

**Kombination mit HandoutRelief:**
- Bürger: +400D/Saison, −0D Essen = reiner Gewinn
- Bei 200 Bürgern: 80.000D/Saison reine Geldschöpfung pro Saison
- **Ist das balanced?** NEIN — systemischer Exploit.

---

### Migration & Emigration

| System | Wirkung | Hebel |
|--------|---------|-------|
| MeticImmigration | Reduziert Immigration basierend auf Steuern | `meticImmigrationDepth/Steepness` |
| WealthHappiness | Reichere → happier → weniger Emigration | `happinessAtPoorest/Richest` |
| PovertyPressure | Arme+Arbeitslose → unglücklicher | `povertyPressureHappinessMin` |
| GiniConsequences | Hohe Gini → Loyalty↓ | `loyaltyAtMaxGini` |
| DebtBondage | Versklavt → KEINE Emigration | `debtSlaveThreshold` |
| GrainDole | Verhindert Tod der Ärmsten | `doleHeadcap` |

**Kritische Lücke:** Keine dynamische Einwanderungsbremse bei Arbeitslosigkeit. `MeticImmigration.vGet()` prüft nur Steuern, nicht `unemploymentRate`.

---

## TEIL D: Phase-5-Voraussetzungen (priorisiert)

---

### Strukturkorrekturen (VOR Feature-Arbeit)

| # | Aufgabe | LOC-Reduktion | Stabilitäts-Gewinn | Aufwand |
|---|---------|---------------|---------------------|---------|
| 1 | `EconomySim` Constructor → Factory-Pattern | −900 | DI testbar | 3d |
| 2 | `EngineSeams`-Migration Top-15 Core-Calls | −120 | V72-Sicherheit | 2d |
| 3 | `EconomyWindow` 2.999 → 8 Panel-Klassen | −1.800 | UI-Wartbarkeit | 2d |
| 4 | `try/Throwable/Exception` Konsolidierung (129 Catches) | −80 | Keine Phantom-Bugs | 1d |
| 5 | `SaveRegistry` statt hardcoded `TAG_*` | −200 | Save-Versionierung testbar | 2d |
| 6 | `printStackTrace` → `EventLog.logThrow` (124×) | −40 | Logs auswertbar | 0.5d |
| 7 | `EconConfig` immutable Record + JSON-Loader | −60 | Hot-Reload-fähig | 1.5d |
| 8 | `IdentityHashMap` → Koordinaten-Keys | −30 | Keine Save/Load-Datenverluste | 1d |
| 9 | `AccessAutomation` defensive + global Rate-Limit | −20 | Log nicht geflutet | 0.5d |
| 10 | TreasuryCrisis Tier 5 → Zwangsprivatisierung | +50 | Game-Stop bei −X | 1d |

**Gesamt:** −3.200 LOC, +50 LOC bewusst, **14.5 Tage**

**Phase-5-Blocker:** #1, #2, #4, #6, #8 — diese fünf müssen VOR Feature-Arbeit stehen.

---

### Vanilla-Hebel-Aktivierung (parallel zu Struktur)

| # | Pfad | System | Strategie | Aufwand |
|---|------|--------|-----------|---------|
| 1 | S1 | SANITY/LOYALTY | Neue Mechanik | 1.5d |
| 2 | L1 | LAWS Auto-Steuer | Zweckentfremden | 1d |
| 3 | A1 | Zugang Coverage-Loop | Entkoppeln | 0.5d |
| 4 | R1 | Output-Klassifikation | Entkoppeln | 1d |
| 5 | S3 | Needs-Export | Implementieren | 0.5d |

**Gesamt:** 4.5 Tage für 5 strukturelle Verbesserungen.

---

### Feature-Fundament (nach Struktur)

| # | Feature | Aufwand | Phase |
|---|---------|---------|-------|
| 1 | Job-XP-Array (Wallets-Pattern + BValueInduOnly) | 3d | 5a |
| 2 | Oddjob-Clamp (Wage ≤ 80% von Regular) | 0.5d | 5a |
| 3 | Gradual Access statt Binary Cliff | 1d | 5a |
| 4 | GrainDole cap stage-gated | 0.5d | 5a |
| 5 | foodAffordabilityGateEnabled=true Default | 0d (Config) | 5a |
| 6 | Fractional Purchasing (AffordabilityGate) | 2d | 5b |
| 7 | StateWarehouses → PropertyLedger Integration | 3d | 5b |
| 8 | Player Override für Lohnsteuerung | 1d | 5b |
| 9 | Wearable-Inventar pro Bürger | 3d | 6 |
| 10 | Profit-driven Priority-Hook (5. Slot) | 3d | 6 |

---

### Empfohlene Reihenfolge

```
Phase 4.7 (Stabilisierung) — 14.5 Tage
  │  EconomySim-Factory, EngineSeams-Migration, Exception-Hygiene,
  │  SaveRegistry, EconomyWindow-Split, EconConfig immutable
  ↓
Phase 5a (Sofort-Fixes + Core-Features) — 5 Tage
  │  foodAffordabilityGate=true, Oddjob-Clamp, GrainDole-stage,
  │  Gradual Access, Job-XP, SANITY/LOYALTY, Coverage-Loop
  ↓
Phase 5b (System-Integration) — 6 Tage
  │  Fractional Purchasing, StateWarehouses→PropertyLedger,
  │  Player-Override-Löhne, Output-Room-Klassifikation, LAWS
  ↓
Phase 6 (Neue Features) — 10+ Tage
  │  Wearable-Inventar, Profit-Priority-Hook, Advisor-Visualisierung,
  │  ML-Training-Vector, Decay-Modell
```

---

## Definition of Done für Phase 5

- [ ] `EconomySim` < 600 LOC, Factory-Pattern, DI-testbar
- [ ] 0 direkte EngineSeams-Calls in core/ (alle via Adapter)
- [ ] 0 leere Catch-Blöcke, 0 `catch(Throwable)` außerhalb adapter/
- [ ] 0 `printStackTrace()` — alles via EventLog
- [ ] `EconomyWindow` in 8 Panel-Klassen aufgeteilt
- [ ] `EconConfig` immutable, JSON-backed
- [ ] `SaveRegistry` statt hardcoded TAG_*
- [ ] `IdentityHashMap<RoomInstance, …>` durch Koordinaten-Keys ersetzt
- [ ] `foodAffordabilityGateEnabled = true` als Default
- [ ] Job-XP-System funktional mit BValueInduOnly-Injektion
- [ ] SANITY/LOYALTY-Booster registriert
- [ ] Coverage-Feedback-Loop in AccessAutomation
- [ ] `mvn compile` → BUILD SUCCESS, 3 Gates bestanden
- [ ] Alle bestehenden Tests grün
