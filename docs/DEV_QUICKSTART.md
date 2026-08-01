# Dev-Quickstart — SyxEconomyMod

> Schneller Einstieg für lokales Bauen, Pushen und CI. Sprint v0.13.131+Quickstart.

Wenn Du nur **eine Sache mitnimmst**, dann diese Tabelle:

## Build-Schnellpfade

| Use-Case | Kommando | Was läuft | Was läuft NICHT |
|---|---|---|---|
| **Fast-Iter** (Refactor im Core) | `mvn -Dgate.skip=true -DskipTests=true -o clean package` | Compile + Shade + Install in `~/.local/share/songsofsyx/mods/SyxEconomyMod/` | Gates, Tests, JaCoCo-Check, Doc-Sync |
| **Dev** (mit Tests, ohne Gates) | `mvn -Dgate.skip=true -o clean test package` | + JUnit (402 Tests) | Gates, Doc-Sync |
| **Pre-Push CI** | `mvn -o clean verify install && bash tools/gate.sh release` | Alles (Build-Gate + Balance + Bump) | — |
| **Pre-Commit Hook** | `bash tools/gate.sh precommit` | Phase-4.7-Shield (delta) + Doku-Sync + God-Class-Guard | Build, Tests |

## Skip-Flags im Detail

Maven hat drei Schalter, alle orthogonal kombinierbar:

```
-Dgate.skip=true            # bypass alle Gates (pre-compile validate-Phase + pre-push)
-DskipTests=true            # Surefire-Tests komplett skippen (KEIN JaCoCo-Report)
-Djacoco.check.skip=true    # JaCoCo-Coverage-Gate bypass (verify-Phase)
```

Plus env-Variante (z. B. in CI-Logs schnell drehen):
```
GATE_SKIP=true mvn -o clean package
```

## Phase-4.7-Shield — was er misst

Der Shield zählt vier Pattern-Klassen im `core/`-Verzeichnis:

| Pattern | Sinn | Stand |
|---|---|---|
| `new IdentityHashMap` (außerhalb Identity-Registry-Dateien) | Hash-Konsistenz | **11** |
| `EngineSeams.…(…)` Direkt-Calls | Architektur-Lock-in | **1** |
| `catch (Throwable …)` | Verschluckte Errors | **9** |
| `printStackTrace()` als Default-Log | Schlechte Diagnostik | **0** |

**Default-Modus ist `delta-only`** (Sprint v0.13.131+Doc-Diet). Pre-existing Counts werden nicht geprüft — nur was du neu hinzufügst failt den Pre-Commit. Das ist der ganze Trick: den Counter hat es immer gegeben, jetzt blockiert er nicht mehr auf der Altlast.

Ohne `.git/hooks/.phase47-baseline` fällt das Skript automatisch auf `absolute` zurück und gibt einen WARN aus. Wenn du eine Baseline setzen willst:

```
bash tools/phase47-baseline.sh capture
```

Für eine volle Phase-4.7-Bilanz (Pre-existing sichtbar machen, z. B. vor einem Refactor-Sprint):

```
bash tools/phase47-shield.sh --mode=absolute --strict-target
```

## Tests / Mocks — wann laufen sie, wann nicht?

`mockito-core 5.17.0` + `byte-buddy 1.18.3` ist auf Java 25 gepinnt. Wenn Du lokal Java 21 fährst, brauchst Du nichts weiter.

Mockito-Tests (`EconomySimMockitoTest`, `KpiSectionTest`, `PriorityRegistryMockitoTest`, …) sind im Surefire-Lauf enthalten und brauchen `mockito-junit-jupiter`. Das Block-Compile klappt auch ohne Tests:

```
mvn -Dgate.skip=true -DskipTests=true -o clean package
```

Wenn Du explizit **nur Mockito-Tests** willst:
```
mvn -o test -Dtest='*MockitoTest,*MockTest'
```

## Was Du beim **Push** machen musst

1. Wenn alles grün ist: `mvn -o clean verify install` (Full-CI, kein Skip). Das läuft **alle** Gates + Tests + JaCoCo.
2. Wenn Du bewusst ohne Gates pushen willst: `git commit --no-verify` und im Commit-Body `gate-bypass: rationale` dokumentieren. One-Time-Notfall, nicht der Default-Pfad.

## Hooks installieren / deinstallieren

```
bash tools/install-hooks.sh            # alle Gates verkabeln (siehe --help)
rm .git/hooks/pre-commit               # alle Gates abklemmen (Fast-Iter)
bash tools/install-hooks.sh --help     # zeigt welche Modi verfügbar sind
```

## "Mod läuft seit gestern nicht mehr, ich tracke nichts"

Triage-Reihenfolge:

1. Schau in `~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/` ob `rebalance_macro_<epoch>.csv` wächst. Wenn NEIN → Engine oder `exportDay()`-Hook kaputt.
2. `tail -50 ~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/economy_events.log` für EventLog-Einträge (Rebalance/Crisis/Player-Action).
3. In-Game → EconHud: zeigt BuildStamp-Identity. Wenn `DIRTY-…` → Code-Stand ≠ Build. Wenn `main…` → Code-Stand OK aber etwas im Pfad krumm.
4. `git log --oneline -10` — vergleich mit Deinem BuildStamp. Wenn beide gleich sind, ist Mod-Code synchron; wenn nicht, `mvn -DskipTests=true -o clean install`.
5. **EngineMirror fehlt?** VanillaQueries braucht SETT.ENTITIES() / STATS.POP()-Pfade. Wenn Sprint v0.13.131+VanillaQueriesDedupe-Refactor das Single-Reflection-Pattern eingeführt hat, läuft residentCount() defensiv mit `LinkageError`-Sentinel → 0. Beim Live-Test: gucken ob `EconomySim.active() != null` ist.

## Tasks, die "schon fertig" sind (oft doc-only noch rot)

Vor jedem Push prüfen:
```
git log --all --oneline | grep -iE "<TaskPrefix>"
```
Wenn der Code-Commit existiert aber das ROADMAP-Bullet noch 🟡/🟠 ist → Doc-Sync fehlt. Beispiel Sprint v0.13.131: `LOG-02` (Commit `42b2de7`) wurde am 2026-08-01 zusammen mit `Sprint v0.13.131+ROADMAP-Recon` markiert.
