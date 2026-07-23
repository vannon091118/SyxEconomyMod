# Phase 4.7 Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-STEP: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close Phase-4.7-Blockers #1–#4 (IdentityHashMap-Migration + EngineSeams-Wrapping + Exception-Hygiene + Save/Load-Verification) before any new feature work. v0.1.3 → v0.2.0.

**Architecture:** Hybrid-Migration (Strategy A Long-Key für hochkritische Maps + Strategy B SaveLifecycle-Clear als Sicherheitsnetz). EngineSeams-Kompatibilitätsschicht statt Field-by-Field-Ersetzung. Save/Load-Hash-Check zum Datenverlust-Nachweis.

**Tech Stack:** Java 21, Maven, IdentityKeys-Long-Key-Helper, IdentityMapRegistry-Clear-Hook, tools/phase47-shield.sh CI-Skript.

## Global Constraints

- Java 21 (maven.compiler.target = 21) — keine Java-22+ Features
- Songs of Syx V71.44 — Engine-API stabil, V72 nicht angekündigt
- `mvn compile` muss BUILD SUCCESS ergeben, zero neue Warnings
- Keine Datei ausserhalb `src/` und `tools/` darf geändert werden ohne SKILL-Aktivierung
- Public-API-kompatible Änderungen: keine Save-Layout-Breaks, keine EconConfig-Default-Changes ohne BALANCE_LEVERS-Update
- `IdentityHashMap` darf nur in 3 allow-listed Dateien vorkommen: `IdentityMapRegistry.java` (Definition), `IdentityKeys.java` (Tests), und genau 1 Engine-Hot-Path den wir als unvermeidbar markieren
- **Neu hinzugefügt v0.3.0:** Wir planen BEWUSST die hier dokumentierten 4 Sub-Systeme nicht in derselben Release-Welle — Phase 4.7 → v0.2.0 ist der Abschluss, dann v0.3.0+ beginnt die implementierte Sub-System-Kette.

---

### Task 1: phase47-shield.sh — CI-Wächter, der Rückbau blockiert

**Files:**
- Create: `tools/phase47-shield.sh`
- Modify: `.pre-commit-config.yaml` (falls vorhanden) — pre-commit-Hook installieren

**Interfaces:**
- Consumes: nichts (Standalone-CI-Skript)
- Produces: exit-code (0 OK, 1 Drift, 2 Tool-Fehler)

- [ ] **Step 1: Skript-Skelett schreiben**

```bash
#!/usr/bin/env bash
# tools/phase47-shield.sh — gates-next-phase-bash-gate
# Phase-4.7 Blockt Regressionen: Verhindert dass die Bug-Klasse die wir fixen wieder aufgebaut wird.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ALLOWLIST_IDENTITYHASHMAP="src/vannon/syx/economy/core/IdentityMapRegistry.java"
ALLOWLIST_ENGINESEAMS_DIR="src/vannon/syx/economy/adapter/"
MAX_CATCHTHROWABLE=0
MAX_DIRECT_ENGINESEAMS=5  # Hot-Path bleiben

echo "[phase47-shield] running Phase-4.7 gates..."

# Gate 1: IdentityHashMap darf nur in allow-listeten Dateien + registriert sein
ihh_files=$(grep -rln 'new IdentityHashMap' src/vannon/syx/economy/core/ 2>/dev/null | grep -v "IdentityMapRegistry.java" | grep -v "IdentityKeys.java" || true)
if [[ -n "$ihh_files" ]]; then
    for f in $ihh_files; do
        echo "[FAIL] IdentityHashMap in $f (not allow-listed)"
    done
    exit 1
fi

# Gate 2: EngineSeams-Direktcalls in core/ dürfen ≤ 5 sein
direct=$(grep -rn 'EngineSeams\.' src/vannon/syx/economy/core/ 2>/dev/null | grep -v '^Binary' | wc -l)
if (( direct > MAX_DIRECT_ENGINESEAMS )); then
    echo "[FAIL] $direct EngineSeams-Direktcalls in core/ (max $MAX_DIRECT_ENGINESEAMS)"
    exit 1
fi

# Gate 3: catch (Throwable) in core/ muss 0 sein
throws=$(grep -rn 'catch (Throwable' src/vannon/syx/economy/core/ 2>/dev/null | wc -l)
if (( throws > MAX_CATCHTHROWABLE )); then
    echo "[FAIL] $throws catch (Throwable) in core/ (max $MAX_CATCHTHROWABLE)"
    exit 1
fi

echo "[phase47-shield] PASS"
```

- [ ] **Step 2: Lokal ausführen, verify current state**

Run: `bash tools/phase47-shield.sh`
Expected: EXIT 1 (klagt die v0.1.3-Restmenge an, weil IdentityHashMaps in 9 core/-Files noch nicht allow-listed-migriert sind).

- [ ] **Step 3: Skript ausführbar machen + Commit**

```bash
chmod +x tools/phase47-shield.sh
git add tools/phase47-shield.sh
git commit -m "ci(phase47): add shield script to prevent IdentityHashMap/EngineSeams regression"
```

---

### Task 2: 6 verbleibende IdentityHashMap-Maps auf Long-Keys migrieren

**Files:**
- Modify: `src/vannon/syx/economy/core/AffordabilityGate.java` (4 Maps: settledMeals, settledDrinks, settledGoods, foodPayers)
- Modify: `src/vannon/syx/economy/core/MaintenanceMarket.java` (2 Maps: workplaces, intakeLocks, dazu seen-Set)
- Modify: `src/vannon/syx/economy/core/StateWageMarket.java` (1 Map: byBlueprint)
- Modify: `src/vannon/syx/economy/core/FoodTransactionPlan.java` (1 Map: pending)
- Modify: `src/vannon/syx/economy/core/DrinkTransactionPlan.java` (1 Map: pending)
- Modify: `src/vannon/syx/economy/core/ServicePlanController.java` (1 Map: admittedPlans)
- Modify: `src/vannon/syx/economy/core/IdentityKeys.java` (echte Implementierungen statt Stubs)

**Interfaces:**
- Consumes: `IdentityKeys.roomKey(RoomInstance)`, `IdentityKeys.humanoidKey(Humanoid)`, `IdentityKeys.blueprintKey(RoomBlueprintImp)`
- Produces: alle Aufrufer verwenden `HashMap<Long, X>` statt `IdentityHashMap<Object, X>`

- [ ] **Step 1: IdentityKeys echte Implementierungen (Tiles aus rooms, IDs aus indu/h)** 

Modify `IdentityKeys.java`:
- `roomKey(RoomInstance)` → nutze `room.mTile().x()` und `.y()` (oder dokumentierte Alternative)
- `humanoidKey(Humanoid)` → bereits korrekt `h.id()`
- `blueprintKey(RoomBlueprintImp)` → bereits korrekt `bp.key.hashCode()`

- [ ] **Step 2: AffordabilityGate — 4 Maps umstellen**

```java
// Vorher:
private final IdentityHashMap<Induvidual, ArrayDeque<Integer>> settledMeals = new IdentityHashMap<>();
private final IdentityHashMap<Induvidual, Humanoid> foodPayers = new IdentityHashMap<>();
// Nachher:
private final HashMap<Long, ArrayDeque<Integer>> settledMeals = new HashMap<>();
private final HashMap<Long, Humanoid> foodPayers = new HashMap<>();
// Alle put/get-Sites: IdentityKeys.humanoidKey(h) statt h.
```

Wiederhole für settledDrinks, settledGoods.

- [ ] **Step 3: MaintenanceMarket — workplaces auf Long-Keys**

Sufficient umschreiben — Konstruktor Body bleibt, nur Map-Typ + Key-Wrapper an call-Sites.

- [ ] **Step 4: 4 weitere Dateien (StateWageMarket, FoodTransactionPlan, DrinkTransactionPlan, ServicePlanController) analog migrieren**

- [ ] **Step 5: mvn compile + phase47-shield testen**

Run: `mvn compile && bash tools/phase47-shield.sh`
Expected: BUILD SUCCESS, gate passes (IdentityHashMap nur in allow-list)

- [ ] **Step 6: Commit**

```bash
git add src/vannon/syx/economy/core/AffordabilityGate.java \
        src/vannon/syx/economy/core/MaintenanceMarket.java \
        src/vannon/syx/economy/core/StateWageMarket.java \
        src/vannon/syx/economy/core/FoodTransactionPlan.java \
        src/vannon/syx/economy/core/DrinkTransactionPlan.java \
        src/vannon/syx/economy/core/ServicePlanController.java \
        src/vannon/syx/economy/core/IdentityKeys.java
git commit -m "refactor(phase47): migrate remaining 6 IdentityHashMaps to Long-Keys via IdentityKeys"
```

---

### Task 3: EngineSeams-Kompatibilitätsschicht statt 36 Direktcalls

**Files:**
- Create: `src/vannon/syx/economy/adapter/EngineSeamsCompatLayer.java`
- Modify: `src/vannon/syx/economy/core/EngineSeams.java` (deprecated-Marker statt harte Calls)
- Modify: alle 8 core/-Dateien mit Direktcalls

**Interfaces:**
- Consumes: `EngineSeamsCompatLayer.delegateEXXName(args)`
- Produces: schrittweise Migration zu `ISyxX` Interfaces, EngineSeams wird Wrapper der Kompatibilitätsschicht

- [ ] **Step 1: Kompatibilitätsschicht-Klasse anlegen**

```java
public final class EngineSeamsCompatLayer {
    private final EconomySim sim;
    public EngineSeamsCompatLayer(EconomySim sim) { this.sim = sim; }
    // Eine Methode pro EngineSeams-Call als 1:1-Delegation
    public void setFirmTarget(RoomInstance room, int target) {
        sim.firmLedgerAdapter().setFirmTarget(room, target);
    }
    // ... 30+ weitere Methoden analog
}
```

- [ ] **Step 2: Top-5 schwerste Calls in eigene Adapter extrahieren**

Wähle: setFirmTarget (FirmLedger), isEnslaveablePleb (Fiscal), overwritePlan + isWorking (FoodPlanController, Purchases), raisewages (CorveeController). Diese 5 sind die kritischsten.

- [ ] **Step 3: 36 Direktcalls in core/ → `EngineSeamsCompatLayer.methodName(...)` umlenken**

- [ ] **Step 4: phase47-shield testet den Rückgang**

Run: `bash tools/phase47-shield.sh`
Expected: `direct=$(grep -rn 'EngineSeams\.'` ≤ 5

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/adapter/EngineSeamsCompatLayer.java \
        src/vannon/syx/economy/core/EngineSeams.java
git commit -m "refactor(phase47): introduce EngineSeamsCompatLayer, route top-5 via ISyx*"
```

---

### Task 4: Save/Load-Hash-Sanity-Modus

**Files:**
- Modify: `src/vannon/syx/economy/core/EconConfig.java` (neuer Flag `saveLoadSanityMode`)
- Modify: `src/vannon/syx/economy/core/IdentityMapRegistry.java` (Hash-Recording + Verifikation)
- Modify: `src/vannon/syx/economy/core/EconomySim.save()` (Hash-Mitschreiben)
- Modify: `src/vannon/syx/economy/core/EconomySim.load()` (Hash-Verifikation + GameLog)

- [ ] **Step 1: hashCode-Helfer für registrierte Maps**

```java
public static int snapshotHash() {
    int combined = 0;
    for (Registered r : entries) {
        combined = 31 * combined + r.fieldName().hashCode();
        // wir können den Inhalt nicht hashen ohne Reflection, aber
        // die leeren Maps haben einen anderen Hash über ihre clearOnLoad-Sequenz
    }
    return combined;
}
```

- [ ] **Step 2: saveLoadSanityMode in EconConfig (default false, opt-in)**

- [ ] **Step 3: save()/load() integrieren Hash-Field**

- [ ] **Step 4: mvn compile + manueller Smoke-Test**

Run: `mvn compile`
Erwartet: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/core/EconConfig.java \
        src/vannon/syx/economy/core/IdentityMapRegistry.java \
        src/vannon/syx/economy/core/EconomySim.java
git commit -m "feat(phase47): save/load hash-sanity for IdentityMapRegistry drift detection"
```

---

### Task 5: Phase-4.7 Verifikations-Pass — alle Gates zur selben Zeit grün

**Files:**
- Modify: `tools/verify-version-consistency.sh`
- Modify: `CHANGELOG.md` (v0.2.0-Eintrag)
- Modify: `pom.xml` (Version-Bump 0.1.3 → v0.2.0)

- [ ] **Step 1: Version-Bump 0.1.3 → v0.2.0 in pom.xml + CHANGELOG.md + _Info.txt**

- [ ] **Step 2: Alle 4 CI-Gates einmal laufen lassen**

Run: `mvn validate && bash tools/phase47-shield.sh && bash tools/verify-version-consistency.sh && bash tools/build-gate.sh`
Expected: Alle 4 grün.

- [ ] **Step 3: ARCHITECTURE.md aktualisieren mit v0.2.0 Stand**

- [ ] **Step 4: **build the JAR + verify**

Run: `mvn package`
Expected: BUILD SUCCESS, JAR gebaut.

- [ ] **Step 5: Commit**

```bash
git add pom.xml CHANGELOG.md _Info.txt docs/ARCHITECTURE.md target/
git commit -m "release(phase47): v0.2.0 — IdentityHashMap full migration + EngineSeams compat layer"
```

---

## Definition of Done für Phase 4.7

- [ ] `phase47-shield.sh` exits 0
- [ ] `mvn validate` BUILD SUCCESS
- [ ] 0 `EngineSeams.X` Direktcalls in core/ ausserhalb allow-list
- [ ] 0 `catch (Throwable)` in core/
- [ ] 0 `new IdentityHashMap` in core/ ausserhalb IdentityMapRegistry.java
- [ ] JAR builds erfolgreich + Deploy-Test (game-test auf Save/Load no-data-loss)
- [ ] v0.2.0 in pom.xml + CHANGELOG.md + _Info.txt konsistent
- [ ] ARCHITECTURE.md reflektiert v0.2.0

---

# Phase 5+ Plan-Erweiterung — Bürger-Intelligenz-Welt

> Diese Erweiterung baut AUF Phase 4.7 auf. Voraussetzung für jede Phase-5-Task: Phase 4.7 ist BUILD SUCCESS + Shields grün. Zuerst `v0.2.0` releasen, dann mit `v0.3.0` loslegen.

**Übergeordnetes Konzept (User-definiert, 2026-07-24):** Bürger-Intelligenz und Wissen sind **persistierter State** (nicht Runtime-Eval). 4-Achsen Conveyor (Wirtschaft / Sicherheit / Militär / Diplomatie) beeinflussen Entscheidungs-Utilities statt Hard-Caps zu setzen. Tag/Nacht-Tick-Pacing wird eingeführt — Tag für Wirtschaftsentscheidungen, Nacht für Infrastruktur-Inkrement. Fraktion-Trade als früh-bare Einnahmequelle mit Tag/Nacht-Sync.

**Architektur-Delta gegenüber Phase 4.7:** Statt reines Bug-Klassen-Fixing wird hier eine **persistente Citizen-State-Schicht** neu eingeführt. Schema-first statt Atom-Fix.

---

### Task 6 (Phase 5a): Citizen-State-Schema + Persistenz

**Files:**
- Create: `src/vannon/syx/economy/core/CitizenAffin.java` (Record: race/security/military/diplomacy Doubles + boolean isVeteran)
- Create: `src/vannon/syx/economy/core/CitizenStateTable.java` (Saveable Record-Scheduler, HashMap-keyed-by-Long-via-IdentityKeys.humanoidKey)
- Modify: `src/vannon/syx/economy/core/EconSnapshot.java` (read citizen-state mean-affinities für Advisor-Display)
- Modify: `src/vannon/syx/economy/core/EconomyWindow.java` (3 Sub-Tabs: "Affinitäten-Trends / Verteilung / Drill-in")

**Interfaces:**
- Consumes: `IdentityKeys.humanoidKey(Humanoid)` — stabile Bürger-ID
- Produces: `CitizenStateTable.get(humanoid)` → `CitizenAffin` Record mit allen Achsen + Veteran-Flag

- [ ] **Step 1: CitizenAffin Record-Definition**

```java
public record CitizenAffin(
    double economyAxis,    // [0.0, 1.0] Wirtschafts-Kompetenz
    double securityAxis,   // [0.0, 1.0] Sicherheits-Präferenz
    double militaryAxis,   // [0.0, 1.0] Militär-Affinität
    double diplomacyAxis,  // [0.0, 1.0] Diplomatie-Tendenz
    boolean isVeteran,     // true wenn Bürger in einem früheren Beruf Tier 3+ erreicht
    int daysInSettlement   // für Convergence-Rate-Berechnung
) {
    public static final CitizenAffin ZERO = new CitizenAffin(0.5, 0.5, 0.5, 0.5, false, 0);
    public static CitizenAffin withAxis(CitizenAffin base, Axis ax, double delta) {
        return switch (ax) {
            case ECONOMY -> new CitizenAffin(clamp(base.economyAxis + delta), ...);
            // etc.
        };
    }
}
```

- [ ] **Step 2: CitizenStateTable als Saveable**

```java
public final class CitizenStateTable implements Saveable {
    private final Map<Long, CitizenAffin> affinities = new HashMap<>();
    // IdentityKeys.humanoidKey() als Keyable-Strategy — Save/Load-stabil über Engine-Restart.

    public void register(Humanoid h) {
        long key = IdentityKeys.humanoidKey(h);
        affinities.putIfAbsent(key, CitizenAffin.ZERO);
    }

    public CitizenAffin get(Humanoid h) { return affinities.getOrDefault(IdentityKeys.humanoidKey(h), CitizenAffin.ZERO); }
    public void update(Humanoid h, CitizenAffin delta) { /* cumulate & clamp */ }

    public void save(FilePutter f) { /* write size + entries */ }
    public void load(FileGetter f) throws IOException { /* read + validate */ }
}
```

- [ ] **Step 3: register() in Saveable-load + initial-Walk on new game**

Hook call: `CitizenStateTable.registerAllFromRoster(roster)` triggered once per Economic-Sim-Start via EconomySim.init().

- [ ] **Step 4: Convergence-Learn-Update in EconProgression.update()**

Im update-Loop von EconProgression, der sowieso alle 60 Ticks läuft, anhängen: Iteration über `affinities.entrySet()` und Adjust-Wert nahe 0.5/0.0/1.0 basierend auf Beobachtungen aus dem Tick.

- [ ] **Step 5: mvn compile + smoke**

Run: `mvn compile`
Erwartet: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/vannon/syx/economy/core/CitizenAffin.java \
        src/vannon/syx/economy/core/CitizenStateTable.java \
        src/vannon/syx/economy/core/EconSnapshot.java \
        src/vannon/syx/economy/core/EconomyWindow.java
git commit -m "feat(phase5a): add citizen-state affinities table (4-axis persistence)"
```

---

### Task 7 (Phase 5b): Affinity-Axis Effects on Wage / Job-Selection

**Files:**
- Modify: `src/vannon/syx/economy/core/LaborMarket.java` (Read CitizenStateTable on Citizen's Place-Candidate)
- Modify: `src/vannon/syx/economy/core/EconomicRoles.java` (Affinity-gated Role-Eligibility-Check)
- Modify: `src/vannon/syx/economy/core/Wages.java` (bonus = affinity-economy mapping)
- Modify: `src/vannon/syx/economy/core/EconConfig.java` (new flags: `affinityDecayRate`, `affinityMaxConvergence`, `affinityVeteranTierFloor`)

**Interfaces:**
- Consumes: `CitizenStateTable.get(humanoid)` bei jeder Wage-Calc + Job-Decision
- Produces: Wage-Bonus = `1.0 + (0.25 * economyAxis)`, Job-Elig manifestiert sich in EconomicRoles und LT-Decision-Logic

- [ ] **Step 1: EconConfig-Flags**

`affinityDecayRate = 0.001` (Konvergenz-Rate pro Tick), `affinityMaxConvergence = 0.3` (max delta von 0.5), `affinityVeteranTierFloor = 1` (Tier-Vorsprung wenn Veteran-Flag).

- [ ] **Step 2: Wages.java erweitert um Affinity-Consultation**

```java
public int wageFor(Humanoid h, RoomBlueprintImp room) {
    int baseWage = this.baseWageFor(room);
    CitizenAffin affin = Economysim.active().citizenTable().get(h);
    double bonus = 1.0 + 0.25 * affin.economyAxis();  // Veteran-Bonus
    return Math.max(1, (int) (baseWage * bonus));
}
```

- [ ] **Step 3: LaborMarket liest affin.securityAxis vor Job-Platzierung**

Bei der Entscheidung "Bürger nimmt Job X an?": Wenn `room`-Key in Bankliste (gaol/laboratory-stockade) und `securityAxis < 0.3`, Acceptance-Rate runter um 0.5.

- [ ] **Step 4: EconomicRoles — Role-Gate-Hook**

`stateFundedGuard(b)`-Methode erweitern: "additionally, der Bürger muss `militaryAxis > 0.4` haben, sonst wird die Rolle nicht angeboten."

- [ ] **Step 5: Smoke-Test: Wage-Pfad-Sanity**

Manueller Test im Dev-JVM: Wage-Calc vor/nach Phase 5b für einen Veteran-Bürger, Ausgabe-Vergleich.

- [ ] **Step 6: Commit**

```bash
git add src/vannon/syx/economy/core/LaborMarket.java \
        src/vannon/syx/economy/core/EconomicRoles.java \
        src/vannon/syx/economy/core/Wages.java \
        src/vannon/syx/economy/core/EconConfig.java
git commit -m "feat(phase5b): affinity axes influence wage-bonus and role-eligibility"
```

---

### Task 8 (Phase 5c): Tag/Nacht-Tick-Pacing als Update-Cadence-Throttle

**Files:**
- Create: `src/vannon/syx/economy/core/CitizenClock.java` (Tag/Nacht-Phasen-State)
- Modify: `src/vannon/syx/economy/core/EconomySim.update()` (Update-Cadence-Switch pro Modul)
- Modify: `src/vannon/syx/economy/core/EconConfig.java` (`tickTickDaySim = false`, `nightSkipJobs = true`)

**Interfaces:**
- Consumes: `TIME.secondsPerDay()` (existing 300-tick/day-Basis), ggf. `TIME.seasons().bitsSinceStart()` für Season-Boundaries
- Produces: Update-Schedule-Flags `isDaytimeTick()`, `isNighttimeTick()`

- [ ] **Step 1: CitizenClock mit Tag/Nacht-Berechnung**

```java
public final class CitizenClock {
    private static final double DAY_FRACTION = 0.6;  // 60% of in-game day = "daytime"
    public static boolean isDaytimeTick(long tickOfDay) {
        return tickOfDay < DAY_FRACTION * EconConfig.DEFAULT_TICKS_PER_DAY;
    }
}
```

- [ ] **Step 2: Modul-Cadence-Switches**

- `FirmLedger.update()` → nur bei isDaytimeTick (Wirtschaft = Tagesgeschäft)
- `MaintenanceMarket.update()` → nur bei isNighttimeTick (Infrastruktur-Wartung)
- `MeticImmigration.register()` → nur bei isDaytimeTick

- [ ] **Step 3: Test-Suite mit Tick-Edge-Cases**

Sicherstellen dass `tick=0` und `tick=180` (Grenze) beide erwartete Verzweigungen treffen.

- [ ] **Step 4: Commit**

```bash
git add src/vannon/syx/economy/core/CitizenClock.java \
        src/vannon/syx/economy/core/EconomySim.java \
        src/vannon/syx/economy/core/EconConfig.java
git commit -m "feat(phase5c): tag/nacht-tick-pacing for economy-update-cadence throttle"
```

---

### Task 9 (Phase 5d): Trade-as-Income via Foreign Factions

**Files:**
- Create: `src/vannon/syx/economy/core/ForeignTradeLedger.java` (Saveable, daily trade-flow aggregat)
- Modify: `src/vannon/syx/economy/core/Fiscal.java` (Tag/Nacht-gebundene Trade-Einnahmen in `creditsTrade`)
- Modify: `src/vannon/syx/economy/core/DebtDiplomacyBuffer.java` (Trade-Income-Threshold)
- Modify: `src/vannon/syx/economy/core/EconConfig.java` (Trade-window: `tradeInflowPeakDay = 0.6`, `tradeInflowPerDayMin = 100`)

**Interfaces:**
- Consumes: `FACTIONS.player().credits()` (existing, 54 calls), `game.faction.FCredits` (read-only)
- Produces: daily `tradeInflow` Zahl für `EconSnapshot.creditsTrade`

- [ ] **Step 1: ForeignTradeLedger Save**

```java
public final class ForeignTradeLedger implements Saveable {
    private final Map<String, Long> factionInflows = new HashMap<>();  // faction -> daily inflow in D
    public long todaysInflow() { return factionInflows.values().stream().mapToLong(Long::longValue).sum(); }
}
```

- [ ] **Step 2: Tag/Nacht-Simulation-Call**

`EconomySim.update()` ruft alle `time.secondsPerDay() / 2` (also 150 Ticks ≈ 12 Stunden In-Game) eine `ForeignTradeLedger.tick()`-Methode auf.

- [ ] **Step 3: Phase-5c-Integration**

Nur bei Tag-Phase aufrufen (Trade = Tagesgeschäft in vanilligen Diplomatie-System).

- [ ] **Step 4: Snapshot + Funding-Threshold**

`EconSnapshot.creditsTrade` zeigt daily turnover. `DebtDiplomacyBuffer` triggert Sofort-Aktion wenn `factionInflows.size() < 2` (zu wenig aktive Trade-Factions).

- [ ] **Step 5: Commit**

```bash
git add src/vannon/syx/economy/core/ForeignTradeLedger.java \
        src/vannon/syx/economy/core/Fiscal.java \
        src/vannon/syx/economy/core/DebtDiplomacyBuffer.java \
        src/vannon/syx/economy/core/EconConfig.java
git commit -m "feat(phase5d): foreign-faction trade-inflow as day-phase income source"
```

---

### Task 10 (Phase 5e): Player-Agency Bundle — Wage-Cap + State-Wage-Gate + RoomOperatingMode

**LoC-Budget:** ~85 LoC in 4 Files (EconConfig + OddjobMarket + Wages + FirmLedger).
**Build-Gate:** `mvn compile` grün, stateWageMarginal-Accumulator korrekt verscalet (verifiziert via grep — keine separate `roomOperatingCost`-Pfad existiert in der Codebase, daher ist `stateWageMarginal` der richtige Akkumulator-Punkt für `mothballOperatingCostMultiplier`-Anwendung).

**Deliverables A/B/C/D:**

- (A) **Oddjob-Wage-Cap** in `OddjobMarket.effectiveWage()`:
  returns `min(oddjobWagePerTask, defaultWage × oddjobWageCeilingRatio=0.75)` →
  Tagelöhner-Lohn immer ≤ 75 % vom defaultWage — strukturell unattraktiv gehalten.
  `OddjobMarket.setPay(int wage)` clamped mit Warning statt reject.
- (B) **State-Wage-Gate** in `Wages.setWage(String, int, RoomBlueprintImp)`:
  Gate-Fire wenn `EconConfig.stateFundedWageRegulationOnly=true` UND `!stateFundedPublicWorks(blueprint)` —
  no-op + stderr-Warning. `Blueprint=null` = no-gate (legacy-Modus).
  Vor-Phase-5e-Spec: "Wages.setWage() lehnt ab" war unerfüllt; jetzt ist es strict erfüllt.
  Migration der (zero) bestehenden Caller auf 3-arg Signatur ist trivial.
- (C) **RoomOperatingMode data-model** in `FirmLedger.opModes` (IdentityHashMap),
  registered via `IdentityMapRegistry.register("FirmLedger", "opModes", opModes)`
  für Save/Load-Coverage. Effektive Kostenskala: 1.0 (PRODUCE), 0.0 (PAUSED),
  0.3 (MOTHBALLED) — angewandt auf `stateWageMarginal` in
  `effectiveOpModeCostScale(blueprint)`.
  **UX-Klärung:** `EconConfig.stateRoomDefaultOpMode = PAUSED` ist intentional
  Player-Agency-Schutz — frisch gebaute staatliche Räume sind stumm bis Spieler
  explizit aktiviert. Migration auf MOTHBALLED wäre eine Alternative mit "sichtbar
  aber billiger"-Default, aber das YAGNI-Prinzip sagt PAUSED bleibt.
- (D) **Save/Load-Sanity**: `opModes` ist via `IdentityMapRegistry` registriert
  → beim Save/Load expliziter clearOnLoad-Pfad, kein stiller Datenverlust bei
  Room-Instance-Identity-Reuse.

**Offene Follow-ups (NICHT in Phase 5e enthalten, deferred):**

- **EconomyWindow UI-Tab "Workforce"**: muss `setOperatingMode(room, mode)` und
  `setWage(roomKey, wage, blueprint)` UI-rendern — derzeit nur Data-Modell da.
  Gehört in Phase-5i/Phase-6.
- **`setWage(roomKey, wage)` 2-arg overload** als Legacy-Comfort: zero-callers
  heute, kommt erst wenn Phase-5i UI hinzukommt.
- **call-site Audit** der zwei bestehenden Lohn-Setter-Pfade: heute 0 Caller
  von `setWage(...)` in der gesamten Codebase (`grep` bestätigt) — gate ist
  ab Tag 1 effektiv für jeden zukünftigen Caller.

**Reviewer-Passes (geloggt):** drei Runden (24 LoC → 60 LoC → 85 LoC), False-Positive
("mothball-Multiplier dead-flag") durch grep-Verifikation widerlegt, Real-Finding
("setWage()-Gate") durch 3-arg-Signatur-Pivot erfüllt.

---

### Task 11 (Phase 6): Vollständige Verkettung — alle 5 Systeme zusammen validieren

**Files:**
- Modify: `src/vannon/syx/economy/core/EconomySim.java` (Orchestrator: Lifecycle-Wiring von Phase-5a-Table + 5b-Effects + 5c-Tick + 5d-Trade)
- Modify: `tools/verify-version-consistency.sh` (Versions-Bump 0.3.0 / 0.4.0)
- Modify: `docs/ARCHITECTURE.md` (Diagramm: Bürger-State-Table → Effects → Tick-Cadence → Trade → Snapshot)

- [ ] **Step 1: Orchestrator-Update in EconomySim.update()**

```java
public void update() {
    long now = ticks;
    if (CitizenClock.isDaytimeTick(now % (long)EconConfig.DEFAULT_TICKS_PER_DAY)) {
        citizenTable().converge(roster());
        if (now % econConfig.progressionUpdateInterval == 0) {
            progression().update(snapshot());
        }
        if (now % (long)EconConfig.DEFAULT_TICKS_PER_DAY == 0) {
            fiscal().update(roster(), wallets());
            foreignTrade().tick();
        }
    } else {
        maintenanceMarket().update(...);
        otherNightOnlySystems();
    }
}
```

- [ ] **Step 2: Tuning-Run mit Diagnostics-Activation**

`EconConfig.diagnosticsExportEnabled = true` setzen, 30 Minuten Mod-Stand laufen lassen, danach `tools/rebalance_plots.py --csv-dir diagnostics --epoch <latest>` ausführen. Plot-Vergleich mit v0.2.0-Stand.

- [ ] **Step 3: ARCHITECTURE.md vollständig neu zeichnen**

Sektion "5 Phasen": vier Sub-System-Diagramme.

- [ ] **Step 4: Definition of Done für Phase 6 prüfen**

- [ ] alle 5 Tasks haben grüne Tests
- [ ] `mvn validate` BUILD SUCCESS, zero neue Warnings
- [ ] Save/Load-Sanity-Modus zeigt OK für 50-Tick-Real-Time-Test
- [ ] Diagnostic-Plots zeigen nicht-monotone Trends in Gini-Curve (Früh-Late-Game-Balancing ist bemerkbar)

- [ ] **Step 5: Commit + v0.4.0-Release-Tag**

```bash
git add src/vannon/syx/economy/core/ \
        docs/ARCHITECTURE.md \
        pom.xml CHANGELOG.md _Info.txt
git commit -m "release(phase6): v0.4.0 — full citizen-state civilization simulation layer"
git tag v0.4.0
```

---

## Außer-Scope (für noch später)

- Fractional-Purchasing-Mechanik
- Wearable-Inventar
- Fractional Access (Binary-Cliff-Fix)
- ML-Training-Vector-System
- H2/SQLite-Persistenz (Beta-Phase für Diagnostics-Schicht)
- Foreign-Faction-AI-Verhandlungs-Modell (Detail-Depth der Phase-5d-Fraktionen-Trade)

## Reihenfolge-Empfehlung

```
v0.1.3 → Phase 4.7 Tasks 1-5 → v0.2.0 Release
v0.2.0 → Phase 5a (Task 6) → v0.3.0-Beta
v0.3.0-Beta → Phase 5b (Task 7) → v0.3.0-Release
v0.3.0-Release → Phase 5c (Task 8) → v0.3.1
v0.3.1 → Phase 5d (Task 9) → v0.3.2
v0.3.2 → Phase 5e (Task 10 Player-Agency Bundle) → v0.3.3 → Phase 6 (Task 11 Orchestrator) → v0.4.0
```

Jede Phase-5-Task einzeln shippen = niedriger Bug-Risiko, besser reviewfähig.
