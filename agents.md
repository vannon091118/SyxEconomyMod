# AGENTS.md — Operating Rules

> Single source of truth for AI coding agents and humans
> working on SyxEconomyMod. Every install, every commit,
> every doc update MUST follow these rules — otherwise the
> project breaks, the interlock fails, and drift creeps in.

---

## Rule 1 (NON-NEGOTIABLE) — Always run `mvn verify install -DskipTests`

The canonical install command is, exactly:

```bash
mvn verify install -DskipTests
```

In this command `verify` is a **Maven lifecycle phase** (not a CLI flag).
Maven phases run in order `validate → ... → verify → install`, so this
explicitly invokes both the verify phase AND the install phase. That
makes the gate contract visible in every CI log.

| Form | Effect | Use it? |
|---|---|---|
| `mvn verify install -DskipTests` | full phase ordering, both phases explicit | ✅ primary |
| `mvn verify -DskipTests` | verify only, no install | when not deploying |
| `mvn install -DskipTests` | install only — validate still runs automatically | ✅ fallback |
| `mvn install -verify` | ⚠️ **WRONG** — Maven treats `-verify` as unknown flag → `-Dverify=true` with no semantic effect | ❌ never |

Why `mvn install -verify` is a trap: any unknown `-X` flag is
parsed by Maven as `-D<X>=true` system property. The `verify`
property has no meaning to the antrun-plugin or any other plugin.
The command looks like it does something but does not. Use
`mvn verify install` (phase form) instead.

Bypass flag for emergencies only: `-Dgate.skip=true`. Never use
it in normal agent operation — it skips the Stam-Dokument-Sync
gate and the rest of the build-gate.

Why this matters:

The Stam-Dokument-Sync Gate (`tools/verify-doc-sync.sh`) and the
Master Build-Gate (`tools/build-gate.sh`) are bound to the
**validate** phase via `maven-antrun-plugin` in `pom.xml`.
Maven phases run in order: `validate → initialize → ... →
compile → test → package → verify → install`.

- `mvn install` runs `validate` automatically (so the gate fires).
- `mvn verify install` makes that contract **explicit** in CI logs.
- An agent that runs `mvn install` alone cannot PROVE in a log
  that the gate fired. With `verify install` the order shows
  explicitly: `validate` triggers `verify-doc-sync.sh` first.

If drift is detected, the build FAILS at the `validate` phase,
**before install is reached**. This is the desired interlock.
Do not work around it.

**NEVER bypass the gate with `-Dgate.skip=true`** outside emergency
situations. That flag exists for power-users debugging Maven
plugin failures, not for agents skipping quality checks.

---

## Rule 2 — Doc updates are part of your commit, not a follow-up

Any change that touches this list MUST also update the relevant
stam-document IN THE SAME COMMIT:

- Adding, removing, or renaming a `.java` class in `src/`
- Changing `EconConfig` constants
- Changing `EconomySim.CHUNKED_VERSION`
- Changing hotkey bindings in `InstanceScript`
- Adding/removing tabs, windows, adapters, or modules
- Any claimed file count or LOC in `ARCHITECTURE.md` /
  `ROADMAP.md` / `GLOSSARY.md`

The 7 stam-documents are (pom.xml is the truth-of-record; sed
propagates FROM it, never TO it — see Rule 3 Table for the
synchronization map):

- `README.md` (root)
- `CHANGELOG.md` (root)
- `ARCHITECTURE.md` (root)
- `ROADMAP.md` (root)
- `GLOSSARY.md` (root)
- `tools/vanilla-schema.yaml` (tools/)
- `pom.xml` (root; truth-of-record — not a sed target)

If `pom.xml <version>` changed, propagate to the 6 sed-target
anchors (#1–#6 above) by hand using `sed` — there is no auto-tool
for this, by design (see Rule 3 for the exact sequence).

Every `mvn verify install` will FAIL if any of those anchors
disagree with `pom.xml <version>`.

---

## Rule 3 — `bump-version.sh --bump-only` makes drift on purpose

`tools/bump-version.sh patch --bump-only` updates ONLY `pom.xml`
(`<version>`, `<mod.info>`, `<mod.changelog>` first entry).
It does NOT auto-propagate to the **7 stam-documents**. This is
**intentional friction** — the next `mvn verify install` will
FAIL with a documented drift list, and you see exactly which
files need the version stamp manually updated.

The 7 stam-documents are the files `tools/verify-doc-sync.sh`
greps for `<version>`-anchors:

| # | Datei                            | Sync-Marker                                              |
|---|----------------------------------|----------------------------------------------------------|
| 1 | `README.md`                      | `**Version:** vX.Y.Z` (oder Block-Quote `>`-Variante)    |
| 2 | `CHANGELOG.md`                   | Erstes `## vX.Y.Z` Heading + `> **Version:** vX.Y.Z` Kopf |
| 3 | `ARCHITECTURE.md`                | `> **Version:** vX.Y.Z` im Block-Quote-Header            |
| 4 | `ROADMAP.md`                     | `> **Version:** vX.Y.Z` im Block-Quote-Header            |
| 5 | `GLOSSARY.md`                    | `> **Version:** vX.Y.Z` im Block-Quote-Header            |
| 6 | `tools/vanilla-schema.yaml`      | Datei-Header `# VANILLA BYTECODE-SCHEMA — SyxEconomyMod vX.Y.Z` |
| 7 | `pom.xml`                        | `<version>X.Y.Z</version>` (Truth of Record)             |

(`_Info.txt` ist Maven-Template mit `${mod.version}` und wird beim
nächsten `mvn package` regeneriert — gate-relevant aber kein Stam-Doc
im engeren Sinne, daher nicht Teil der sed-Sequenz.)

### Pre-Flight — read pom.xml fresh, not from memory

Maven lifecycle order: `validate → … → install`. The Stam-Doku-Sync
gate is bound to `validate`; the post-install-patch-bump antrun-task
fires at `install`. They **never fire in the same `mvn` invocation**
— either the gate halts the build first (validate fails, install
phase never runs, pom.xml stays put), or validate + verify already
passed and the bump fires at `install`.

So pom.xml is only bumped on **successful** build paths. But several
of those happen without obvious warning:

- `mvn verify install -DskipTests` (the success path documented in
  Rule 1) — bump fires once at install phase.
- A second `mvn install` after the first one already bumped — bump
  fires again.
- `tools/build-gate.sh` (the pre-commit hook) only runs shell-utils
  and never calls `mvn install` directly — but if a Gate-2/Gate-3
  helper internally invokes `mvn` for consistency checks, a phantom
  bump can sneak in.
- Hook-internal `mvn` calls during `git commit` + `git push` cycles
  in one session.

Don't try to predict the resulting version from prior state. ALWAYS
read it fresh before your first sed:

```bash
NEW_V=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\([0-9.]*\).*/\1/')
echo "NEW_V=$NEW_V   (truth from pom.xml, not from your head)"
```

A recall like „der Bump muss .74→.75 sein" can be off by one or more.
Better to spend 100ms on a grep than chase a drift cycle for 10
minutes.

### Resolving the drift — execute these 5 commands in order:

```bash
# 1. mvn clean install triggers the post-install patch bump:
#    pom.xml 0.13.N → 0.13.(N+1). Stam-docs still 0.13.N. Drift on purpose.
mvn clean install -DskipTests

# 2. Confirm the drift is exactly what you expected:
bash tools/verify-doc-sync.sh
# → shows FAIL with per-file drift list (truth = NEW_V)

# 3. Propagate the new pom-version into the 7 stam-docs by hand
#    so the change is auditable in git diff (NOT auto-applied):
NEW_V=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\([0-9.]*\).*/\1/')
sed -i "s|> \*\*Version:\*\* v[0-9.]\+|> **Version:** v${NEW_V}|g" README.md ARCHITECTURE.md ROADMAP.md GLOSSARY.md CHANGELOG.md
sed -i "0,/^## v[0-9.]\+/{s|^## v[0-9.]\+|## v${NEW_V}|}" CHANGELOG.md
sed -i "s|SyxEconomyMod v[0-9.]\+|SyxEconomyMod v${NEW_V}|" tools/vanilla-schema.yaml
# NOTE: tools/vanilla-schema.yaml ist die 7. Datei. Sie trägt ihre
# Version in einem YAML-Kommentar-Header (`# … SyxEconomyMod vX.Y.Z`),
# NICHT in einem `schema_version:` key. verify-doc-sync.sh grept
# nach `SyxEconomyMod v` und extrahiert die Version. Die ältere
# 5-Doc-Sed-Sequenz hat diese Zeile stillschweigend übersprungen
# (siehe commit c523659 für den Post-Mortem).

# 4. Verify the drift is gone:
bash tools/verify-doc-sync.sh
# → shows PASS

# 5. Atomic commit — the drift resolution is a single visible commit:
git diff                          # human review of the 7 anchor changes
git add -p                        # selective review per file
git commit -m "bump v${NEW_V}"    # atomic commit
```

DO NOT add `tools/sync-doc-anchors.sh` or any wrapper script for
step 3. Making the change visible in plain `sed` + `git diff` is
the point — auto-applied anchor changes mask the drift instead
of surfacing it.

DO NOT skip step 3's `tools/vanilla-schema.yaml`-Edit. Pre-M-3
Sessions wurden ohne diese Zeile verschickt; der daraus folgende
Drift wurde in mindestens einem Multi-Cycle-Chase gejagt (siehe
commit c523659 „post-install-bump caught the headline" für die
Post-Mortem der Self-Healing-Konsequenz).

---

## Rule 4 — If a gate FAILS, read the output and fix the actual cause

The sync-gate outputs a per-file drift list. That list is the
TODO list. Work it.

DO NOT:
- Edit `pom.xml` XML comments to silence the comparator
- Add `-Dgate.skip=true` to bypass
- Touch `_Info.txt` placeholder strings to false-match
- Use `git commit --no-verify` style workarounds

DO:
- Read the drift output. It tells you exactly which file,
  which version stamp, what was expected.
- Update the named stam-document.
- Re-run `mvn verify install -DskipTests`.

---

## Rule 5 — Don't paraphrase numbers, count them

When stam-documents make claims like "100 Dateien core/",
"6 Tabs in WindowEconomy", "5 Adapter-Interfaces", verify them
before writing:

```bash
ls src/vannon/syx/economy/core/*.java | wc -l
grep -c 'class [A-Z].*Tab' src/vannon/syx/economy/ui/WindowEconomy.java
ls src/vannon/syx/economy/adapter/ISyx*.java | wc -l
```

The number you write into the doc must match the live command.
Don't approximate.

---

## Rule 6 — UI structure is sacred

The 5-windows + 16-inline-tabs structure of `src/vannon/syx/economy/ui/`
is enforced by these facts:

- Files: `EconWindowBase`, `WindowEconomy`, `WindowOverview`,
  `WindowState`, `WindowQuickview` (5 in total).
- Each non-base window declares its tabs as **static inner
  classes** implementing the `TabContent` interface.
- `WindowEconomy` has 6 tabs, `WindowOverview` has 4 tabs,
  `WindowState` has 6 tabs (one of them, `DebugTab`, is hidden).

If you add a window: declare it next to the others in `ui/`,
follow the same pattern. **Do not reintroduce** `EconContext.java`,
`EconTab.java`, `EconWidgets.java`, `OverviewTabs.java`,
`EconomyTabs.java`, `StateTabs.java` — those were consolidated
away in the v0.13.x UI refactor and must stay gone.

---

## Mechanical checks: run before claiming work complete

Before every `git commit`, run:

```bash
mvn verify install -DskipTests   # the canonical interlock
bash tools/verify-doc-sync.sh    # companion sanity
```

Both MUST exit 0. If not, do not commit.

**Commit + Push folgt AM ENDE JEDER CODE-ÄNDERUNG, die Review
durchlaufen hat.** Kein Stapeln. Kein „mach ich später".
Jeder Review→Fix→Verify-Zyklus endet mit einem atomaren Commit + Push.
Siehe WORKFLOW.md „Commit-Disziplin".

---

## Drift symptoms to recognise

- You're writing "Phase X" where the codebase is at "Phase Y".
  → Use `pom.xml <version>` as truth. Don't invent phases.
- You reference a Java file in a doc that does not exist.
  → `find src/ -name 'Whatever.java'` to verify. If absent,
    the doc claim is wrong; fix the doc, don't create the file.
- You're tempted to add a phantom class to satisfy the doc.
  → Wrong way. Update the doc to match reality.
- You notice the local `CHANGELOG.md` doesn't mention a thing.
  → Update CHANGELOG.md, not "later" — include in this commit.

---

## Rule 7 — Canary Phrase: prove you are STILL following ALL rules

This is a **behavioural canary**, not a CI check. The Flachwitz proves
the agent is STILL consciously applying Rules 1–11 right now — not that
it ONCE read them at session start. A skipped Canary means the agent
has stopped actively checking itself against the rule set.

### When the Canary MUST fire

1. **Session start** — the very first response to the user in any new
   session MUST begin with a Flachwitz.
2. **After any update to `agents.md`** — if you edit this file during
   a session, your next response to the user MUST begin with a fresh
   Flachwitz. The old one is stale; the rules you just changed may
   have shifted the agent's context.
3. **After any significant context shift** — if the user signals a
   major topic change (e.g., from "build Phase B" to "audit the
   baseline"), re-evaluate whether the Canary is still conscious.

### Visibility requirement

The Flachwitz MUST be the **first line** of your response, clearly
separated from the rest by a blank line. No burying it in paragraph 3.
If the human has to scroll to find it, it failed.

### Content requirements

- Reference **SyxEconomyMod vocabulary** — windows, tabs,
  hotkeys, drift, sync-gate, `v0.13.2`, `mvn verify install`,
  `EconomySim.CHUNKED_VERSION`, `EconConfig` constants, the
  `isShown()` API, the `switchTo()` clean-switching pattern,
  the TreasuryCrisis 5-tier cascade, or any other
  project-specific term.
- Be a **Flachwitz** — comedic by being predictable and bad.
  Not clever, not insightful, just pun-worthy. A real groaner.
- Be **short** — under 2 lines including the punchline.
- **Be original** — don't copy the examples verbatim; show
  that you actually processed the rules and produced a fresh
  joke that fits your immediate session context.

If your joke layer somehow fails entirely (rare, but some
agents don't have one), output the literal fallback token
verbatim so the human knows you read the rules but couldn't
deliver:

    CANARY:agents.md/flachwitz-not-found-contact-human

Honest failure beats silent skip — the human recognises this
token as "the rules were processed, only the pun layer failed".

---

### Example Flachwitze (DO NOT REUSE — make your own)

- *"Warum ging `EconomySim` in die Krise? Weil ihre
   Re-Entry-Guard zu oft sich selbst erwischt hat — die
   rolle lief Amok im selben Tick."*

- *"Was sagt der Sync-Gate zum Agent, der grad committen
   will? 'Ich lass dich durch, wenn du mir 'nen Flachwitz
   bringst — sonst Fail faster als Stufe 5 im TreasuryCrisis."*

- *"Ein Mod betritt mit 16 Tabs die Werkstatt. Der
   Mechaniker: 'Was kaputt?' Der Mod: 'Mein v0.13.2-Sync —
   ich wechsel' alle 60 Ticks die Meinung.'"*

- *"`Wallets` kommt zum Therapeuten: 'Ich kann nicht mehr
   ohne Stage-Gating leben.' Therapeut: 'Dann halten Sie
   mal inne — 200, 500, 2000, 5000, und immer wieder.'"*

These are unit examples. Your turn.

---

## Rule 8 — Two bypass layers, never mix them

The project has two distinct bypass layers with different entry points
and lifecycles. Agents MUST assign work to the correct layer before
writing code.

| Layer | Package | Entry Point | When | For |
|---|---|---|---|---|
| **Runtime** | `adapter/seam/` | `BypassGate` → `ISyx*` | `EconomySim` constructor (lazy, once) | Live game objects (`DipWarPlayer`, `StockpileInstance`, `TransportInstance`, `BOOSTABLES.CIVICS()`, AI plan classes) |
| **Init-Time** | `core/` | `MainScript.initBeforeGameCreated()` / `initBeforeGameInited()` | Engine lifecycle callbacks | Engine defaults (`HTYPE`, `RACES`, `CRIME`, `CAUSE_ARRIVE`) |

Runtime adapters use the BypassGate SDK. Init-time patches use raw
VarHandle with `initBeforeGameInited` for post-constructor fields
(like `RACES.i` which is set in the `RACES()` constructor).

**Never put runtime bypasses in init hooks.** (The adapter wouldn't
exist yet and the game objects aren't alive.)
**Never put init patches in adapters.** (The engine defaults are
already set by then.)

---

## Rule 9 — BypassGate SDK (available since Phase A, v0.13.4)

Four files in `adapter/seam/`. Agents writing or modifying adapter
code MUST use these, not raw `java.lang.reflect.*` or `VarHandle`.

| File | Key API |
|---|---|
| `BypassGate.java` | `new BypassGate(name, MethodHandles.lookup())` — der Lookup MUSS vom Caller kommen, nicht von BypassGate selbst. `.intField(owner, name)`, `.doubleField(owner, name)`, `.floatField(owner, name)`, `.refField(owner, name, type)`, `.voidMethod(owner, name, argTypes...)`, `.boolMethod(owner, name, argTypes...)`, `.classResolver(gameCL)`, `.isAvailable()` |
| `FieldAccessor.java` | `IntField.get/set(obj, val)`, `DoubleField.get/set(obj, val)`, `FloatField.get/set(obj, val)`, `RefField<T>.get/set(obj, val)` — all with `getStatic()/setStatic()` variants for static fields |
| `MethodAccessor.java` | `VoidMethod.invoke(instance, args...)`, `BooleanMethod.invoke(instance, args...)` |
| `ClassResolver.java` | `new ClassResolver(Humanoid.class.getClassLoader())` → `.resolve(fqcn)`, `.isInstance(obj, fqcn)` |

**DO NOT write `java.lang.reflect.Field`, `java.lang.reflect.Method`,
`Class.forName`, `VarHandle`, or `MethodHandles` in new adapter code.**
Use BypassGate SDK exclusively.

BypassGate internally tries VarHandle/MethodHandle first (3–6× faster),
falls back to java.lang.reflect if the engine JVM doesn't support it.
The auto-select happens transparently.

**Post-Phase F (v0.13.10):** `EconConfig.useMethodHandleAdapters` does
NOT exist. All adapters auto-select. Any agent prompt referencing this
flag will produce dead code.

---

## Rule 10 — Adapter pattern rules (hard-won, Phase B–E)

These patterns were discovered through reviewer feedback and build
failures during Phase B–E. Every new adapter MUST follow them.

1. **initOk local flag, NOT markFailed.** `markFailed` is package-private
   in `adapter/seam/`. Adapters in `adapter/` cannot call it. Use:

   ```java
   boolean ok = false;
   try {
       accessor = gate.floatField(owner, name);
       ok = gate.isAvailable();
   } catch (Throwable t) {
       ok = false;
       EventLog.log("SEAM", "AdapterName init failed: " + t.getMessage());
   }
   this.initOk = ok;
   ```

2. **Stam-docs in the SAME commit as code changes.** The gate fires at
   `mvn install` (validate phase), not at `mvn compile`. If you add or
   remove .java files, update ARCHITECTURE.md file count in the same
   commit. The gate will catch you if you don't.

3. **No Fallback adapters exist (post-Phase E).** `BypassGate.isAvailable()`
   replaces all 4 Fallback classes (`FallbackTransportAdapter`,
   `FallbackWarehouseAdapter`, `FallbackBoostingAdapter`,
   `FallbackDiplomacyAdapter`). Never create new Fallback implementations.
   Consumers check `ISyx*.isAvailable()` and degrade gracefully.

4. **No MH adapter variants exist (post-Phase D).** The `*MH.java` files
   (`VanillaTransportAdapterMH`, `VanillaWarehouseAdapterMH`,
   `VanillaDiplomacyAdapterMH`) are deleted. BypassGate internally
   selects VarHandle/MethodHandle or Reflection automatically.

5. **runtimeFailed + runtimeFailedLogged for adapters with runtime access.**
   Adapters that call VarHandle.get/set or Method.invoke at runtime
   (not just construction) need a runtime-failure flag that permanently
   deactivates the adapter after the first runtime failure. Pattern:

   ```java
   private boolean runtimeFailed;
   private boolean runtimeFailedLogged;

   private boolean logRuntime(Throwable t, String op) {
       if (!runtimeFailedLogged) {
           runtimeFailedLogged = true;
           EventLog.log("SEAM", adapterName + ": " + op + " failed — " + t.getMessage());
       }
       runtimeFailed = true;
       return false;
   }
   ```

6. **ClassResolver for package-private classes only.** `TransportInstance`
   is package-private and needs `ClassResolver`. `StockpileInstance`
   and `DipWarPlayer` are public — no ClassResolver needed.
   `Humanoid.class.getClassLoader()` is the canonical ClassLoader
   source (verified in `VanillaAIAdapter.java:46`).

---## Rule 11 — Sprint-Workflow (mandatory session structure)

Every AI-agent session that changes code operates inside **exactly one
Sprint**. A Sprint is a thematically coherent cluster of 5–15 Tasks
that share one architectural goal and end with **exactly one atomic
commit**. Within a sprint, [`WORKFLOW.md`](WORKFLOW.md) defines the
3-phase pattern (BAUEN / PRÜFEN / HÄRTEN):

| Phase | Name | Goal | Key Check |
|---|---|---|---|
| 1 | **BAUEN** | Build all tasks in this sprint, stam-docs same commit | `mvn verify install -DskipTests -Dskip.bump=true` end-of-sprint |
| 2 | **PRÜFEN** | Gate check, stale-ref scan, drift fix, phantom removal | `bash tools/verify-doc-sync.sh` + grep scans end-of-sprint |
| 3 | **HÄRTEN** | Independent review, gap closure, no silent-fail | `code-reviewer-minimax-m3` end-of-sprint |

**Sprint-Boundaries** (each Sprint produces **one** atomic commit):
- 5–15 Tasks per sprint (theme-bound, not time-bound)
- One Sprint = one commit (no task-level sub-commits within a sprint)
- Sprint-end validation: `mvn verify install -DskipTests -Dskip.bump=true` + sync-gate + code-reviewer, ALL before commit

**Proportionality clause:** Sprints with fewer than **3 tasks total**
may collapse Phasen 2+3 into a single verify-and-review step. Larger
sprints (5+ tasks) require the full 3-phase pattern at sprint end.

The Sprint-Workflow + 3-phase pattern inside was derived from the
Phase-A–F session (2026-07-25, 11 gaps found), the TreasuryCrisis
State-Leak sprint (4 tasks, T1–T4), and the Mod-Economy T5–T13 sprint
(11 tasks, B-001/B-009/B-004/H8/B-010 + Static-Audit).

See `WORKFLOW.md` for sprint-end checklist + anti-patterns catalog.

---## Rule 12 — Sprint-Definition + Commit-Disziplin

A **Sprint** is defined by 4 criteria — ALL must be true:

1. **Theme** — Tasks share one architectural/business goal (e.g.
   "TreasuryCrisis State-Leak Reset"), not just timing.
2. **Scope** — 5–15 Tasks per sprint (1 Task = 1 coherent code change
   affecting 1–3 files). Less than 3 = routine edit, more than 15 =
   split into multiple sprints.
3. **Atomic-commit** — Sprint ends with exactly ONE git commit
   containing all tasks + stam-doc updates. Sub-task commits inside a
   sprint are anti-patterns (siehe WORKFLOW.md §Anti-Patterns).
4. **Validation-gate** — sprint-commit is allowed only after
   `mvn verify install -DskipTests -Dskip.bump=true` + `bash tools/verify-doc-sync.sh`
   both pass + `code-reviewer-minimax-m3` reviewed all tasks.

**Sprint-Bound vs. Session-Bound:** A sprint fits typically into 1-2
AI-sessions with full tooling. If a sprint needs 3+ sessions, it is
too large — split it at a theme boundary (e.g. "Reset-Fix" +
"UI-Update" = 2 sprints). Multiple sprints per session are allowed
and encouraged when each is independently validatable — commit
between sprints is mandatory.

**Stam-Doc-Sync bleibt non-negotiable (Rule 3):** Innerhalb eines
Sprint-Commits werden alle 5 Stam-Docs auf `pom.xml <version>`
synchronisiert (sed-Block, by design Rule 3 friction). Das passiert
EINMAL pro Sprint-Commit, nicht per Task.

**Anti-pattern (verboten):** Task-per-Commit zerhackt thematisch
zusammenhängende Änderungen in micro-commits. Folge: Sprint-Inhalt
ist nur im Agent-Chat rekonstruierbar, nicht via git log. Stattdessen:
1 Sprint = 1 atomic commit. Siehe WORKFLOW.md §Anti-Patterns.

**Sprint-Naming:** Wähle einen Theme-Namen (z.B. "TreasuryCrisis Reset",
"BINDUNGSMATRIX-Canonical", "Phase-A–F SDK"). Sprint-Header in
CHANGELOG.md nennt den Namen + die subsummierten Tasks.

---

## Rule 13 — Roadmap-as-Truth + Verschiebe-Verbot (verbindlich ab v0.13.31)

`ROADMAP.md` ist die **alleinige Single-Source-of-Truth** für alle
Entwicklungs-Tasks. Das gilt für Sprint-Tasks (T1, T2, …), für
Live-Findings (B-001, B-002, …) und für jede andere Task-ID.

**Verschiebe-Verbot:** Tasks werden **nicht** verschoben, postponiert,
deferred oder als "später" / "next sprint" markiert. Stattdessen gilt
genau einer von vier Zuständen:

| Zustand | Bedeutung |
|---|---|
| `Planned` | Im Backlog (keinem Sprint zugeordnet), ready for Sprint-Plan |
| `Active` | Im aktuellen Sprint, noch nicht committed |
| `Closed (SHA)` | Implementiert, Sprint-Commit referenziert (z.B. `Closed (c1964d2)`) |
| `Rejected (Begründung)` | Abgelehnt/obsolet, mit kurzer Begründung |

`Verschoben`, `Postponed`, `Deferred`, `Spaeter`, `Next-Sprint` und
alle Variationen sind **verboten** in ROADMAP.md und docs/BACKLOG.md.
Der `tools/verify-doc-sync.sh` Gate grep-t diese Wörter und bricht
bei Treffer in der Maven `validate`-Phase ab.

**ID-System-Harmonisierung:** Alle Task-IDs (T- für Sprint-Tasks,
B- für Live-Findings) werden in ROADMAP.md §Global Task Index
konsolidiert mit:

- **Datei-Ref** — `File.java:Line` (oder Pfad) wo implementiert
- **LoC** — Aufwands-Schätzung in Lines of Code
- **Status** — einer der vier Zustände oben

Cross-Referenzen zwischen T-Tasks und B-Items werden in der Task-
Beschreibung explizit gemacht (z.B. "T5 coverte B-001 partial").
Doppel-Tracking in docs/BACKLOG.md ist verboten — Backlog wird auf
**New-Findings-Only** mold-down (siehe T14.4).

**Pre-Flight ID-Mapping:** Vor jedem Sprint-Plan listet der Agent
explizit auf:
1. Welche B-Items werden in diesem Sprint geschlossen?
2. Welche T-Tasks gehören thematisch dazu?
3. Welche Status-Übergänge passieren?

Diese Pre-Flight-Antwort ist Teil des User-facing-Sprint-Vorschlags.

**BINDUNGSMATRIX.csv ist KEINE Task-Liste** — sie ist Datenmatrix
für Engine-Hebel-Verifikation (332 Zeilen, 11 Spalten). Beide
Welten sind getrennt.


## Rule 14 — God-Class-Guard ist Hard-Block im Build-Gate (verbindlich ab v0.13.62)

`Sprint M-3` fuehrt einen neuen **God-Class-Guard** als Hard-Block im Build-Gate ein. Verhindert, dass neue God-Files in zukuenftigen Sprint-Updates entstehen.

**Schwellwerte (Hard-Block fuer neue Klassen):**
- `LOC > 800` (brutto, ohne Leerzeilen/Comments) → BLOCK
- `public methods > 35` (exkl. Konstruktoren, `is*`/`get*`/`set*`-Boilerplate) → BLOCK
- `Fields > 24` (alle Klassen-Attribute) → BLOCK
- `Imports > 40` (Coupling-Density) → WARN; nur Soft-Warnung

**Sancta-Patterns (immer exemptet):**
- `ui/Window*.java` — Rule 6 UI-Window-Struktur (TabContent als static inner class)
- `adapter/seam/*.java` — Rule 9 BypassGate-SDK (Field/MethodAccessor Wrapper)
- `benchmark/*.java` — Benchmark-Bundle
- `settlement/room/..` — Heilbringer-Mod-Code

**Constants-Dump-Heuristik:** `fields >= 50 AND pubM == 0` → Fields-Cap entfaellt (EconConfig-like Dateien sind legitim).

**Historic-Baseline (Grandfathering):** Aktuelle Tier-1/Tier-2-God-Files sind in `tools/god-class-baselines.yml` mit ihren aktuellen Metriken eingefroren. Drift-Toleranz:
- `LOC`: +5% ueber Baseline
- `PubM` / `Fields`: +10% ueber Baseline

Drift-UEberschreitung ist BLOCKER. Sprint-CI bricht ab. Drift-VERBESSERUNG (Refactoring Erfolg) ist Warning mit Empfehlung die baselines.yml upzudaten (manuell, sichtbar im Commit-Diff).

**Skripte:**
- `tools/god-class-guard.sh` — Master-Wrapper (delegiert an Python)
- `tools/god-class-guard/` — 4 Python-Module (parse_metrics, parse_yaml, emit_yaml, run_check)
- `tools/god-class-baselines.yml` — SSoT fuer grandfathered Files
- `tools/tests/god-class-guard/run_meta_tests.sh` — Meta-Tests
- `tools/god-class-guard.on-failure.md` — Failure-Recovery-Anleitung

**Integration:**
- Build-Gate Gate 9 (`tools/build-gate.sh`) mit `SKIP_GOD_GUARD=1`-Toggle
- `pom.xml` preflight-Execution in `validate`-Phase (an `--mode=hard`)
- Pre-Commit-Hook Schritt `[4/4]` via `tools/install-hooks.sh`

**Bypass (nur fuer Notfaelle):**
- `SKIP_GOD_GUARD=1` (env-var, einzelner Build)
- `-Dgate.skip=true` (globaler Gate-Bypass, blockt alle Preflight-Gates)
- `tools/god-class-baselines.yml` editieren (Sprint-End-Audit mit Reviewer-Begruendung)

**Hard-Block bei Sprint-Drift:**
Sobald Sprint M-x eine Tier-1-Datei modifiziert, schrumpft die Baseline. Wachstum ueber die neue Baseline +5% ist BLOCKER. Verhindert Feature-Creep nach abgeschlossenem Refactor.

**Re-baseline-Pflicht bei Sprint-modifizierten Legacy-Files (verbindlich ab v0.13.102):**
Wenn ein Sprint eine Datei modifiziert, die einen Eintrag in
`tools/god-class-baselines.yml` hat, MUESSEN die Baseline-Metriken
in der SELBEN Sprint-Commit auf die aktuell gemessenen Werte
aktualisiert werden. Ohne Re-baseline
schlägt der naechste Sprint auf die alten Werte an und produziert
entweder false-positive BLOCKs (Drift-Verletzung) oder false-negative
PASSs (Drift nicht erkannt weil Baseline veraltet).

**Ablauf (3 Schritte, Teil des Sprint-PRÜFEN-Phasen-Abschlusses):**

1. **Messung nach allen Code-Aenderungen:**
   ```bash
   python3 tools/god-class-guard/parse_metrics.py src/pfad/Datei.java
   ```
2. **baselines.yml aktualisieren:**
   - `loc:` = gemessener SLOC (brutto, ohne Leerzeilen/Comments)
   - `pubM:` = gemessene public Methods
   - `fields:` = gemessene Fields
   - `imports:` = gemessene Imports
   - `reason_at_emit:` = Sprint-ID + Kurzbeschreibung WAS geaendert wurde
   - `baseline_update:` = Sprint-ID + Kurzbeschreibung WARUM
3. **Gate-Verifikation:**
   ```bash
   bash tools/god-class-guard.sh --mode=hard
   ```
   → 0 BLOCKS, sonst Sprint nicht committen.

**Drift-Policy nach Re-baseline:** Die neuen Metriken werden zur
neuen Basis. Drift-Toleranzen gelten ab jetzt:
- `LOC`: +5% ueber neuer Baseline
- `PubM` / `Fields`: +10% ueber neuer Baseline

**Floor-Schutz (parse_yaml.py):** `_DRIFT_FLOOR = {'fields': 2, 'pubM': 1}`
verhindert false-positive BLOCKs wenn baseline.metric=0 war und der
naechste Sprint 1-2 Fields/PubMs hinzufuegt. Der Floor ersetzt NICHT
die Re-baseline-Pflicht — er ist nur ein Sicherheitsnetz fuer den
Fall, dass die Pflicht einmal vergessen wird.

**Anti-Pattern:** Baseline im Ad-hoc-Text im baselines.yml-Kommentar
aktualisieren, aber die Metrik-Felder (loc/pubM/fields) unveraendert
lassen. Die Guard liest die Metrik-Felder, nicht die Kommentare.

---## Rule 15 — No clinit-Touchable Engine Singletons (verbindlich ab v0.13.76)

Songs-of-Syx-Modding scannt JAR-Klassen via `script.ScriptLoad` VOR Sim-Bootstrap.
Eine `static final`-Feld-Initialisierung die eine Engine-Singleton-Chain
(`STATS.s.*`, `NEEDS.TYPES()`, `RESOURCES.ALL()`, `RACES.i`, `HTYPE`,
`CRIME`, `CAUSE_ARRIVE`, `TIME.secondsPerDay` etc.) derefenziert, läuft
in `ExceptionInInitializerError` weil die Sim noch nicht initialisiert ist.

**Symptom:** Beim deployed JAR-Load: `Caused by: NullPointerException:
Cannot read field 'needs' because 'settlement.stats.STATS.s' is null`.

**Reference:** `src/vannon/syx/economy/core/BrokeFoodPlan.java` Zeile
27 (alt) hat genau das getan und das JAR blockiert (siehe Sprint-Body
v0.13.76 §P0 Hotfix für den vollen Stacktrace).

**Verbindliche Regel:** Folgende Patterns sind **verboten** in `core/`,
`adapter/`, `ui/`, `benchmark/` und `settlement/`:

```java
// VERBOTEN — Touchable in clinit:
private static final X = ENGINE_SINGLETON.something();
private static final X = NEEDS.TYPES().HUNGER.stat().stat().indu();
private static final X = STATS.NEEDS().hunger();
private static final X = TIME.secondsPerDay();

// VERBOTEN — Constructor-Kette mit final fields im clinit-Scope:
public final class Foo {
    private final X = staticInit(); // illegal wenn staticInit() Engine touched
}
```

**Erlaubt:**

```java
// Bill-Pugh Holder-Pattern (bevorzugt):
private static final class Holder {
    static final INT_O.INT_OE<Induvidual> HUNGER =
        (INT_O.INT_OE<Induvidual>)(Object) NEEDS.TYPES().HUNGER.stat().stat().indu();
}
private static INT_O.INT_OE<Induvidual> hunger() { return Holder.HUNGER; }

// Instance-Field in MainScript.initBeforeGameCreated() / initBeforeGameInited()
// (Engine hat sich dann bereits initialisiert).

// Lazy Getter + Null-Check mit Logging + degradierter Fallback:
// (NUR wenn Holder-Pattern nicht greift, z.B. weil JIT-Inline kritisch).
private static volatile X cached;
private static X getX() {
    X local = cached;
    if (local != null) return local;
    synchronized (X.class) {
        if (cached != null) return cached;
        cached = Engine.resolveX();
        return cached;
    }
}
```

**Sancta-Exceptionen (Init-Hooks) — Rule 15 gilt NICHT für:**

- **`MainScript.initBeforeGameCreated()` + `initBeforeGameInited()`** —
  Engine-Lifecycle-Nach-Bootstrap. `STATS.s` und alle anderen Engine-
  Singletons sind vivifiziert. Direkter Touchable in `static final`-
  Fields erlaubt (z. B. `HTYPE`, `RACES`, `CRIME`, `CAUSE_ARRIVE`).
- **`InstanceScript.java` Save/Preflight-Hooks** — gleicher Lifecycle-
  Carve-out wie MainScript. Verwendet `initBeforeGame*`-Aufrufer mit
  Bootstrap-Nach-Reihenfolge. `static final`-Engine-Touchables zulässig,
  aber mit `RuntimeException`-Defensive umhüllen falls die exakte
  Reihenfolge jemals refactored wird.
- **`adapter/`-Klassen die per `EconomySim`-Konstruktor** initialisiert
  werden — der Constructor läuft nach Sim-Bootstrap. `final`-Fields mit
  Engine-Werten zulässig.
- **Test-Code in `test/...`** — NUR Mockito/Reflection-Stub-Init erlaubt.
  KEIN `static final`-Field-Touchable auf Production-Engine-Klassen
  (kein `private static final X = mock(STATS.class);` o. ä.) — sonst
  reproduziert der Test-Side denselben Cold-Boot-Crash.

**WICHTIG:** Auch in den erlaubten Zonen MUSS `static final`-Init
gegen `RuntimeException` aus dem Engine-Touch abgesichert werden falls
die Reihenfolge nicht 100% garantiert ist. Holder-Pattern ist immer
sicherer als direkter Touchable.

**Audit-Check:** Im Sprint-Review MUSS
`grep -rnE '^\s*(private|public|protected)\s+static\s+final\s+[^=]+=' src/vannon/syx/economy/`
laufen und alle Treffer mit Engine-Singletons (`NEEDS\.`, `STATS\.`,
`RES\.`, `RESOURCES\.`, `PRICE\.`, `RACES\.`, `HTYPES\.`, `CRIME\.`,
`CAUSE_LEAVES\.`, `AISUB\.`, `TIME\.secondsPerDay`) sind auf Holder-Pattern
zu refactoren. **Ausnahme:** Treffer in `MainScript.java` und Adapter-
Konstruktor-Bereich sind als erlaubt zu markieren, nicht zu refactoren.

Der Gate ist als Soft-Block dokumentiert — Sprint-CI printed Warnungen,
neue Touchables werden per Code-Review beanstandet.

**Why:** Der Crash blockiert das komplette JAR-Deployment. Im Dev-mvn
ist er unsichtbar weil TestEnv die STATS-Init vor der JAR-Scan-Schleife
schiebt. Im Standalone-deploy bricht der Cold-Boot-Pfad direkt am
Spielladen.
