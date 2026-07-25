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

The 5 stam-documents are:

- `README.md` (root)
- `CHANGELOG.md` (root)
- `ARCHITECTURE.md` (root)
- `ROADMAP.md` (root)
- `GLOSSARY.md` (root)

If `pom.xml <version>` changed, propagate to all 5 stam-doc
anchors by hand using `sed` — there is no auto-tool for this,
by design (see Rule 3 for the reason).

Every `mvn verify install` will FAIL if any of those anchors
disagree with `pom.xml <version>`.

---

## Rule 3 — `bump-version.sh --bump-only` makes drift on purpose

`tools/bump-version.sh patch --bump-only` updates ONLY `pom.xml`
(`<version>`, `<mod.info>`, `<mod.changelog>` first entry).
It does NOT auto-propagate to the 5 stam-documents. This is
**intentional friction** — the next `mvn verify install` will
FAIL with a documented drift list, and you see exactly which
files need the version stamp manually updated.

Resolving the drift — execute these 5 commands in order:

```bash
# 1. mvn clean install triggers the post-install patch bump:
#    pom.xml 0.13.2 → 0.13.3. Stam-docs still 0.13.2. Drift on purpose.
mvn clean install -DskipTests

# 2. Confirm the drift is exactly what you expected:
bash tools/verify-doc-sync.sh
# → shows FAIL with per-file drift list

# 3. Propagate the new pom-version into the 5 stam-docs by hand
#    so the change is auditable in git diff (NOT auto-applied):
NEW_V=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\([0-9.]*\).*/\1/')
sed -i "s|> \*\*Version:\*\* v[0-9.]\+|> **Version:** v${NEW_V}|g" README.md ARCHITECTURE.md ROADMAP.md GLOSSARY.md CHANGELOG.md
sed -i "0,/^## v[0-9.]\+/{s|^## v[0-9.]\+|## v${NEW_V}|}" CHANGELOG.md

# 4. Verify the drift is gone:
bash tools/verify-doc-sync.sh
# → shows PASS

# 5. Atomic commit — the drift resolution is a single visible commit:
git diff                          # human review of the 5 anchor changes
git add -p                        # selective review per file
git commit -m "bump v${NEW_V}"    # atomic commit
```

DO NOT add `tools/sync-doc-anchors.sh` or any wrapper script for
step 3. Making the change visible in plain `sed` + `git diff` is
the point — auto-applied anchor changes mask the drift instead
of surfacing it.

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

---## Rule 11 — Three-Phase Workflow (mandatory session structure)

Every AI-agent session that changes code MUST follow the 3-phase pattern
formalized in [`WORKFLOW.md`](WORKFLOW.md):

| Phase | Name | Goal | Key Check |
|---|---|---|---|
| 1 | **BAUEN** | Build the feature, stam-docs in same commit | `mvn verify install` per commit |
| 2 | **PRÜFEN** | Gate check, stale-ref scan, drift fix, phantom removal | `bash tools/verify-doc-sync.sh` + grep scans |
| 3 | **HÄRTEN** | Independent review, gap closure, no silent-fail | `code-reviewer-deepseek`, all gaps closed |

**Proportionality clause:** For changes touching fewer than **5 lines
in a single file**, Phases 2+3 may be collapsed into a single
verify-and-review step. For everything else: all three phases,
committed and pushed before the next phase begins.

The 3-phase pattern was derived from the Phase-A–F session (2026-07-25),
where two independent reviewers found 11 gaps with zero overlap — proving
that a single pass is never enough.

See `WORKFLOW.md` for the full checklist per phase and the catalog of
anti-patterns discovered in this project.
