# Import Layers — SyxEconomyMod

> **Stand:** v0.13.89 | **Songs of Syx** V71.44 | **2026-07-28**
>
> **Status:** **NON-STAM-DOC** — diese Spec ist nachgeordnet. Die kanonische
> Wahrheit für Datei-/LOC-Anzahlen bleibt ARCHITECTURE.md § Modul-Bilanz.
> verify-doc-sync.sh prüft heute nur pom.xml-Version-Anchors; ein Drift
> zwischen dieser Spec und ARCHITECTURE.md wird daher **nicht** durch Gates
> erkannt. Vor Sprint-Aktivierung: §7-Commands ausführen, evtl. Drift manuell
> in ARCHITECTURE.md nachsyncen (Sprint I-1, agents.md Rule 5 + Rule 2).
>
> Doc-only, kein Sprint-Ceremony nötig (agents.md Rule 11 betrifft Code-Touches).

Diese Spec bündelt die Java-Import-Konventionen des Mods. Sie ist die
kanonische Antwort auf **"wo gehört dieser Import hin?"** und definiert
eine **fünf-Layer-Topologie** mit Abwärts-Regel: höhere Layer dürfen NICHT
niedrigere importieren.

**Bekannte Stam-Doc-Drift:** ARCHITECTURE.md / CLAUDE.md / README.md
behaupten 163 Java-Dateien, Realität ist 167. Diese Spec ist die erste
Stelle, die das entdeckt. Auflösung erfordert Sprint I-1 mit Rule-2-Sync.

---

## 1. Inventur-Grundlage (Snapshot @ 2026-07-28, verifiziert)

Snapshot-Daten — Werte können in späteren Sprints abweichen.
Verifikations-Befehle in §7 regenerieren die Zahlen.

**Drift-Warning:** ARCHITECTURE.md / CLAUDE.md / README.md sagen 163
Java-Dateien. Verifizierter Ist-Wert ist **167** (siehe §0 Stam-Doc-Status).
Diese Spec ist die einzige Quelle mit der korrekten Zahl, bis Sprint I-1
die 3 Stam-Docs nachsyncht.

| Kennzahl | Wert |
|---|---:|
| `.java`-Dateien in `src/` | **167** |
| Import-Zeilen gesamt | **1.644** |
| Distinct Imports | **303** |
| Dateien mit mindestens einer Vanilla-Import | **134** (80,2 %) |
| Per-Sub-Package (bestätigt 2026-07-28) | core=118, adapter=28, ui=5, adapter/seam=5, core/save=2, core/io=2, integration=1, core/warehouse/market=1, benchmark=1, 4× settlement-Brücken=1 + 1 + 1 + 1 |

Per-Layer-Verteilung der 1.644 Imports (siehe §2 für Layer-Definition):

| Layer | Anzahl | Anteil | Semantik |
|---|---:|---:|---|
| L0 Standard-Library (`java.*`) | 263 | 16,0 % | JDK |
| L1 Maven-Deps | 0 | 0 % | nur `game.jar` provided, kein Java-Import-Sichtbar |
| L2 Vanilla Songs-of-Syx | 1.028 | 62,5 % | 6 Sub-Layer (s.u.) |
| L3 Eigenes Projekt `vannon.*` | 342 | 20,8 % | Wirtschafts-Sim Sources |
| L4 Package-Private-Brücken | 0 | 0 % | im Vanilla-Namespace, kein `import` nötig (gleiches Package) |
| **Top-Heavy Per File** | | | `FoodTransactionPlan.java` 35 / `EngineSeams.java` 28 |

---

## 2. Fünf-Layer-Topologie (kanonisch)

```
┌─────────────────────────────────────────────────────────────────┐
│ L4 — Package-Private-Brücken (außerhalb vannon.*, im Vanilla-  │
│     Namespace `settlement/room/...` — teilen das Package mit   │
│     der Spiel-Klasse → keine Reflection nötig)                 │
│   Dateien:                                                     │
│     src/settlement/room/main/employment/LaborMarketAccess.java      │
│     src/settlement/room/service/food/tavern/EconomyTavernAccess.java │
│     src/settlement/room/service/food/eatery/EconomyEateryAccess.java│
│     src/settlement/room/service/food/canteen/EconomyCanteenAccess.java│
└─────────────────────────────────────────────────────────────────┘
            ▲ darf nicht importieren
            │
┌─────────────────────────────────────────────────────────────────┐
│ L3 — Eigenes Projekt `vannon.syx.economy.*`                    │
│   Sub-Pakete (Stand 2026-07-28):                                │
│     core               118 Files   Wirtschafts-Sim + Engines   │
│     core/save            2 Files   TLV-Save-Helper              │
│     core/io              2 Files   IO-Analysis (Sprint IO-1)    │
│     core/warehouse/      1 File    MarketSharedState            │
│       market                                       (Sprint M-1) │
│     adapter            28 Files   EngineMirror + ISyx*         │
│     adapter/seam         5 Files   BypassGate SDK               │
│     ui                   5 Files   Window* + EconWindowBase     │
│     integration          1 File    VanillaUIIntegration         │
│     benchmark            1 File    Reflection-vs-MethodHandle    │
└─────────────────────────────────────────────────────────────────┘
            ▲ darf nicht importieren
            │
┌─────────────────────────────────────────────────────────────────┐
│ L2 — Vanilla Songs-of-Syx (6 Sub-Layer, eigene Semantik)       │
│   2a — snake2d.*   172 Importe   UI-/2D-Engine-Lib              │
│   2b — util.*        31 Importe   snake2d-Helfer                │
│   2c — script.*       2 Importe   SCRIPT/SCRIPT_INSTANCE        │
│   2d — init.*       192 Importe   RESOURCE/RACES/HCLASSES       │
│   2e — game.*       190 Importe   FACTIONS/TIME/PCredits       │
│   2f — settlement.* 443 Importe   SETT/STATS/RoomInstance       │
└─────────────────────────────────────────────────────────────────┘
            ▲ darf nicht importieren
            │
┌─────────────────────────────────────────────────────────────────┐
│ L1 — Maven-Compile-Classpath (provided scope, leer im src/-Import-Bild)│
│   com.songsofsyx:songsofsyx:71.44   Songs-of-Syx-Game-JAR            │
│   (Mono-Bundle — alle Vanilla-Klassen kommen über L2 herein,         │
│    nicht über L1; L1 ist Compile-Hook ohne Java-Import-Manifestation. │
│    Layer-Eintrag bleibt für Maven-Reproduzierbarkeit dokumentiert.)  │
└─────────────────────────────────────────────────────────────────┘
            ▲ darf nicht importieren
            │
┌─────────────────────────────────────────────────────────────────┐
│ L0 — Java Standard-Library (`java.*`)                          │
│   263 Importe — java.io, java.util, java.lang.invoke etc.       │
│   (junit/mockito nicht in src/, nur in test/ → siehe §6)       │
└─────────────────────────────────────────────────────────────────┘
```

### 2a. Sub-Layer L2f (`settlement.*`) ist der Hotspot

| Klasse | Importe | Hauptkonsumenten |
|---|---:|---|
| `settlement.entity.humanoid.Humanoid` | 58 | core/FoodTransactionPlan, core/Wallets, core/FirmLedger |
| `settlement.room.main.RoomInstance` | 43 | core/LaborMarket, core/RoomCoordinateKey, core/RoomAccess |
| `settlement.main.SETT` | 40 | core/EngineSeams, core/FlowMeter, core/EngineMirror-Verbinder |
| `settlement.stats.STATS` | 24 | core/EconomySim (Reentry-Guard), core/STATS-Snapshots |
| `settlement.stats.Induvidual` | 20 | core/Wallets, core/AutoProcurement, core/SubjectJob |
| `settlement.room.main.RoomBlueprintImp` | 23 | core/RoomCoordinateKey, adapter/RoomAccessImpl |

**Konvention:** L2f-Importe gehen via Adapter-Layer (EngineMirror-SDK)
in `core/`, NICHT direkt aus Wirtschafts-Engines. Ausnahmen: die 4
Bridge-Dateien in L4 dürfen direkt zugreifen (Package-Private).

---

## 3. Abwärts-Regel: Was darf woraus importieren?

`L_i → L_j` ist erlaubt **nur wenn `j ≤ i`**. Höhere Schichten dürfen
nicht in niedrigere herein-greifen (modulare Hygiene).

| Quell-Layer | Darf importieren aus | Verboten aus | Hinweis |
|---|---|---|---|
| L4 (Brücken) | L0, L1, L2, L3 | Layer-intern kein Selbstimport (gleiches Package) | Brücken leben im Vanilla-Namespace |
| L3 (Projekt) | L0, L1, L2, L3 (anderes Sub-Paket) | L4 (Brücken) | siehe Clean-Switching-Regel unten |
| L2 (Vanilla) | L0, L1, L2 (anderes Sub-Layer) | L3, L4 | via EngineMirror-SDK in core/ |
| L1 (Maven) | L0, L1 | L2, L3, L4 | leeres Layer (siehe §2 Hinweis) |
| L0 (Java) | L0 | L1, L2, L3, L4 | JDK |

**Clean-Switching-Regel für L3-Subpakete:**

- `core/` darf `adapter/` importieren (lesend)
- `adapter/` darf **NICHT** aus `core/` importieren (zyklische Dependency
  wäre God-Class-Symptom — agents.md Rule 14)
- `ui/` darf `core/` und `adapter/` importieren
- `benchmark/` darf alle lesen
- `integration/` darf alle lesen

---

## 4. Sortier-Konvention (innerhalb einer Datei)

Aktueller Stand (Stichprobe): **KEINE strikte Layer-Sortierung existiert**.
`RoomAccessImpl.java` z.B. mischt `java.*`, `settlement.*`, `init.*`, `snake2d.*`,
`vannon.*` wild. Das ist *funktional* OK aber *audit-schwach*.

### 4a. Kanonische Reihenfolge (Soll)

```java
// Block 1 — Java Stdlib (aufsteigend alphabetisch)
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

// Block 2 — Maven-Deps (aufsteigend, normalerweise leer)

// Block 3 — Vanilla Songs-of-Syx (Sub-Layer-Reihenfolge 2a→2f, je alphabetisch)
import game.faction.FACTIONS;
import game.faction.FCredits;
import game.time.TIME;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.type.HCLASSES;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.stats.STATS;
import settlement.stats.Induvidual;
// snake2d + util — engineered libraries
import snake2d.LOG;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import snake2d.util.sets.LIST;
// bootstrap interface (selten, im SCRIPT-Pfad)
import script.SCRIPT;

// Block 4 — Eigenes Projekt (Sub-Pakete alphabetisch, je Sub-Paket alphabetisch)
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.adapter.HumanoidAccessImpl;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.Roster;
import vannon.syx.economy.core.Wallets;
```

### 4b. Block-Trennung & Alignment

- Genau **EIN Leerzeile zwischen Blöcken**.
- Innerhalb jedes Blocks: **alphabetisch aufsteigend** (Voll-Pfad, nicht
  nur Klassenname).
- Wildcard-Importe (`import x.*;`) sind **verboten** außer für
  explizit genehmigte Enum-Sets (kein aktueller Fall).

### 4c. Was beim Sortier-Bruch helfen kann (kein must-do)

Ide-unterstütztes Reformat:
- IntelliJ: Settings → Editor → Code Style → Java → Imports →
  "Import layout" mit dem oben genannten Schema.
- Eclipse: Organize Imports Preferences → Custom Layout (Block-Trennung).
- Alternativ: `tools/check-imports.sh` (siehe §7b) kann Verstöße listen.

---

## 5. Hotspot-Dateien (Top-15 Vanilla-Importer)

Wenn eine Datei in der folgenden Liste wächst, MUSS die Vanilla-Kopplung
hinterfragt werden — das ist ein Symptom für "core/wurde-zu-vanilla-heavy".

| Vanilla-Imports | Datei | Diagnose |
|---:|---|---|
| 35 | `core/FoodTransactionPlan.java` | Adapter-Pattern prüfen — gehört das nach EngineMirror? |
| 28 | `core/EngineSeams.java` | Legacy-Seams-File, B-008 Migration läuft |
| 23 | `core/StateWarehouses.java` | akzeptabel — direkter Warehouse-Zugriff |
| 22 | `core/LocalPrices.java` | akzeptabel |
| 19 | `core/FlowMeter.java` | akzeptabel |
| 19 | `core/DrinkTransactionPlan.java` | siehe FoodTransactionPlan |
| 17 | `core/FirmLedger.java` | akzeptabel |
| 16 | `core/GoodsTransactionPlan.java` | siehe FoodTransactionPlan |
| 16 | `core/EconomicRoles.java` | akzeptabel |
| 16 | `adapter/HumanoidAccessImpl.java` | korrekt — Adapter-Layer Seite |
| 15 | `core/RetailSyncEngine.java` | akzeptabel |
| 15 | `core/MeticImmigration.java` | akzeptabel |
| 15 | `core/FoodRollback.java` | akzeptabel |
| 14 | `core/MaintenanceMarket.java` | akzeptabel |
| 14 | `core/EconomySim.java` | akzeptabel (Orchestrator, notgedrungen) |

**Trigger:** Wenn eine dieser Dateien **+5 Importe** in einem Sprint
wächst, Sprint-Review-Pflicht (agents.md Rule 14).

---

## 6. Maven-Dependencies (sub-Sicht)

`pom.xml <dependencies>`:

| GroupId | Artifact | Scope | Sichtbar als Import? |
|---|---|---|---|
| `com.songsofsyx` | `songsofsyx:71.44` | `provided` | nein (Mono-Bundle → L2) |
| `org.junit.jupiter` | `junit-jupiter:5.11.3` | `test` | nein (in `test/` nicht `src/`) |
| `org.mockito` | `mockito-core:5.14.2` | `test` | nein |
| `org.mockito` | `mockito-junit-jupiter:5.14.2` | `test` | nein |

**Konsequenz:** L1 ist **leer** im Sinne von Layer-1-Importen. Die
Provided-Dependency ist nur ein Compile-Classpath-Hook für L2-Klassen.

In `test/` finden sich `junit.*` und `mockito.*` Importe — diese sind
in dieser Layer-Spec **nicht erfasst**, weil sie ausschließlich Test-Scope
sind. Layer-Analoge in `test/`: `L0(java)|L1(test-frameworks)|L2(src/)`.

---

## 7. Verifikations-Snippets (Drift-Detection)

### 7a. Manual Counts

```bash
# File count (Soll: 167)
find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
    -name "*.java" | wc -l

# Total imports (Soll: 1644)
find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
    -name "*.java" -exec grep -h "^import " {} + | wc -l

# Per-Layer summiert
for prefix in "java\." "javax\." "com\." "org\." "vannon\." \
              "settlement\." "script\." "snake2d\." "game\." \
              "init\." "util\."; do
  c=$(find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
       -name "*.java" -exec grep -hE "^import ${prefix}" {} + 2>/dev/null | wc -l)
  printf "%-15s → %d\n" "$prefix" "$c"
done
```

### 7b. Anti-Pattern Checks (Soll-Werte)

```bash
# Anti-Pattern 1: Wildcard-Imports + statische Imports (Soll: 0)
find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
    -name "*.java" -exec grep -lE "^import [a-zA-Z0-9_.]+\.\*;" {} +
find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
    -name "*.java" -exec grep -lE "^import static [a-zA-Z0-9_.]+\." {} +
# Erwartet: keine Treffer

# Anti-Pattern 2: Doppelimporte pro Datei (Soll: 0)
for f in $(find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
    -name "*.java"); do
  dups=$(grep -E "^import " "$f" | sort | uniq -d | wc -l)
  [ "$dups" -gt 0 ] && echo "$f hat $dups Doppelimporte"
done

# Anti-Pattern 3: Ungenutzte Imports (Soll: wenige, IDE-managed)
# — Out-of-scope diese Spec, würde Compiler-Warnings erzeugen
#   (siehe pom.xml compilerArgs: -Xlint:all)

# Anti-Pattern 4: Zyklische Imports L4→L3 (sollte nie vorkommen)
find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
    -name "*.java" -path "*/settlement/*" \
    -exec grep -HE "^import vannon\." {} +
# Erwartet: keine Treffer

# Anti-Pattern 5: Cyclic L3-cross-package (core ↔ adapter; core/io ↔ core/save)
# Kein core/-File darf adapter/ importieren wenn beide > 800 LOC-Range sind.
for src_pkg in core adapter ui integration core/io core/save core/warehouse/market; do
  cross=$(find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src/vannon/syx/economy/$src_pkg \
      -name "*.java" -exec grep -lE "^import vannon\.syx\.economy\.(adapter|adapter/seam|ui)\." {} + 2>/dev/null | wc -l)
  printf "%-25s -> %d files importing higher-L3-pkg\n" "$src_pkg" "$cross"
done
# Erwartet: core/ darf adapter/ importieren (EngineMirror);
#           adapter/ darf NICHT in core/ herein-greifen (Zyklus-Symptom, Rule 14).
```

### 7c. Hotspot-Wachstum-Watch (siehe §5)

```bash
# Pro Datei die Vanilla-Import-Counts:
for f in $(find /home/vannon/Schreibtisch/SyxEconomyMod_Workspace/src \
    -name "*.java"); do
  c=$(grep -cE "^import (game|settlement|init|snake2d|util|script)\." "$f" 2>/dev/null)
  [ "$c" -gt 14 ] && echo "$c $f"
done | sort -rn | head -15
```

Wenn eine Datei aus der §5-Liste in einem Sprint +5 Vanilla-Imports
ansammelt, ist das ein Symptom für "Refactor-Bedarf hinter dem
Adapter-Layer".

---

## 8. Bezug zu agents.md

Diese Spec erzwingt **keine** neuen Build-Gates — sie ist Doku. Verbindlich
ist die §5 Hotspot-Watchlist als **manueller Review-Punkt** in der
Hardening-Phase jedes Sprints (agents.md Rule 11, Phase 3).

agents.md Rule 9 ("Never use raw reflection") und Rule 14 ("God-Class-Guard")
bleiben unberührt — die IMPORT_LAYERS.md ist orthogonal zu Pattern-Gates.

agents.md Rule 15 ("No clinit-Touchable Engine Singletons") betrifft
**nicht** die Imports, sondern die `static final`-Init-Reihenfolge — wird
in einer separaten Spec dokumentiert.

---

## 9. Bezug zum Choir-Pattern

Diese Spec adaptiert das **3-Schicht-Import-Konzept** aus dem
Choir-Fremdframework (`choir.api.*` / `choir.internal.*` / `choir.adapter.*`,
siehe Workshop-Cache `1162750/3766045175`) auf eine **5-Schicht-Topologie**,
die an SyxEconomyMod's Single-Consumer-Realität angepasst ist:

| Choir | SyxEconomyMod | Adaptions-Hinweis |
|---|---|---|
| `choir.api.*` (public, für externe Konsumenten-Mods) | **kein Mapping** — SyxEconomyMod hat keine externen Mod-Konsumenten | Choir braucht das Layer wegen Multi-Mode-Compat; wir nicht |
| `choir.internal.*` | `vannon.syx.economy.core.*` | faktisch internal, derzeit kein Public-Surface deklariert |
| `choir.adapter.*` | `vannon.syx.economy.adapter.*` (Fassade) + `adapter/seam/*` (BypassGate-SDK) | feinere Trennung Public-vs-Internal-SDK |
| (existiert nicht in Choir) | `settlement/room/.../...Access.java` | Package-Private-Brücken (einzigartig in SyxEco) |

**Eigentliche Adaptions-Logik:** Choir hat 3 Schichten **weil** es ein
Multi-Consumer-Compat-Framework ist (andere Mods kompilieren gegen
`choir.api.*`). SyxEconomyMod ist **Single-Consumer** (das Mod ruft sich
selbst auf) — daher wäre Choirs 3-Schicht-Split überengineered. Unsere
5-Schicht-Topologie spiegelt die **Vanilla-Coupling-Realität** (6 Vanilla-
Sub-Layer + Package-Private-Brücken) **besser** als Choirs generisches
Schema.

Falls SyxEconomyMod jemals **externe Konsumenten** zulässt: Adaptions-
Trigger ist, dass eine zweite Mod-JAR eine Klasse aus `vannon.*` mit
stabiler API referenzieren will. Bis dahin: L3 als implizit "internal-only"
betrachten.
