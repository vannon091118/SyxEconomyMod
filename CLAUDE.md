# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**SyxEconomyMod** is a comprehensive economic simulation mod for **Songs of Syx V71.44**. It adds a parallel economic layer where every citizen has a wallet, firms track profit/loss, markets set prices via supply/demand, and the state can go bankrupt through a 5-tier crisis cascade.

**Key stats:** 163 Java files, ~31,152 LOC, 402 tests, Java 21, Maven build.

## Build Commands

```bash
# Canonical build (runs all 9 quality gates + install)
mvn verify install -DskipTests

# Build only (skip tests, skip gates for rapid iteration)
mvn compile -Dgate.skip=true

# Run tests only
mvn test

# Install to game mod directory
mvn install -DskipTests

# Skip patch version bump on install
mvn install -DskipTests -Dskip.bump=true
```

## Code Architecture (3-Layer Model)

```
Layer 0: Vanilla Engine (Songs of Syx V71) — untouched
    ↓ Adapter Layer (27 files in adapter/ + adapter/seam/)
Layer 1: EngineMirror SDK + BypassGate SDK + ISyx* adapters
    ↓ Core Economic Logic (126 files in core/)
Layer 2: EconomySim orchestrator (6 engines + 1 facade), subsystems
    ↓ UI Layer (5 files in ui/)
Layer 3: 5 windows (Overview, Economy, State, Quickview, Debug) + 16 inline tabs
```

### Key Entry Points
- **Main entry:** `MainScript.initBeforeGameInited()` → bootstraps config + registers hooks
- **Simulation tick:** `EconomySim.update(deltaSeconds)` — called from `InstanceScript.update()`
- **UI entry:** `InstanceScript.pollHotkeys()` → Numpad hotkeys open 4 windows

## Build Gates (9 Gates, bound to Maven `validate` phase)

Run via `mvn verify install -DskipTests` — all must pass:
1. **Stam-Doku-Sync** — `verify-doc-sync.sh` validates 7 doc anchors match pom.xml version
2. **Code-Audit** — `code-audit.sh` silent failure scan
3. **Version ↔ Changelog** — `verify-version-consistency.sh`
4. **Adapter Signatures** — inline signature validation
5. **Bytecode-Injection Audit** — `audit-bytecode.sh`
6. **Sim-Logic Audit** — `audit-sim-logic.sh`
7. **Schema Validation** — YAML ↔ Adapter cross-check
8. **Balance Regression** — `balance-regression-check.sh` (41 constants)
9. **God-Class Guard** — `god-class-guard.sh` (800 LOC / 35 pubM / 24 fields hard block)

**Never use `-Dgate.skip=true`** except for emergency debugging.

## Version & Doc Sync Protocol (Critical)

`pom.xml <version>` is the **single source of truth**. After any `mvn install` that bumps the version, you MUST manually sync the 7 stam-documents:

```bash
# 1. Get new version from pom.xml (truth source)
NEW_V=$(grep -m1 '<version>' pom.xml | sed 's/.*<version>\([0-9.]*\).*/\1/')

# 2. Sync 6 markdown docs
sed -i "s|> \*\*Version:\*\* v[0-9.]\+|> **Version:** v${NEW_V}|g" README.md ARCHITECTURE.md ROADMAP.md GLOSSARY.md CHANGELOG.md

# 3. Sync CHANGELOG heading
sed -i "0,/^## v[0-9.]\+/{s|^## v[0-9.]\+|## v${NEW_V}|}" CHANGELOG.md

# 4. Sync vanilla-schema.yaml
sed -i "s|SyxEconomyMod v[0-9.]\+|SyxEconomyMod v${NEW_V}|" tools/vanilla-schema.yaml

# 5. Verify
bash tools/verify-doc-sync.sh
```

**Never automate this** — the manual `sed` + `git diff` step is intentional friction.

## Sprint Workflow (Mandatory)

Every AI session = **exactly one Sprint** = **one atomic commit**.

**3 Phases per Sprint:**
1. **BAUEN** — Implement all 5-15 tasks, update stam-docs in same commit
2. **PRÜFEN** — `mvn verify install -DskipTests -Dskip.bump=true` + `verify-doc-sync.sh` + stale-ref scan
3. **HÄRTEN** — `code-reviewer-minimax-m3` review + close all gaps

**Commit message format:** `sprint: <Theme> — <task list>`

**Never** commit mid-sprint. Never push without full validation.

## Adapter Layer Rules (Critical)

**Two bypass layers, never mix:**
- **Runtime** (`adapter/seam/`): `BypassGate` → `ISyx*` adapters → live game objects
- **Init-Time** (`core/`): `MainScript.initBeforeGameCreated/Inited()` → engine defaults

**BypassGate SDK (4 files, use exclusively):**
- `BypassGate` — lookup + typed factories
- `FieldAccessor` — `IntField/DoubleField/FloatField/RefField<T>` with VarHandle primary
- `MethodAccessor` — `VoidMethod/BooleanMethod`
- `ClassResolver` — for package-private classes only

**Adapter patterns (hard-won):**
1. `initOk` local boolean flag, NOT `markFailed()` (package-private in seam)
2. Per-adapter `BypassGate` with own `initOk` → granular degradation
3. No Fallback adapters (deleted Phase E). Consumers check `isAvailable()`.
4. No `*MH.java` variants (deleted Phase D). BypassGate auto-selects VarHandle.
5. Runtime adapters need `runtimeFailed` + `runtimeFailedLogged` flags.

## God-Class Guard (Gate 9, Hard Block)

**Hard block thresholds for NEW classes:**
- >800 LOC (non-blank, non-comment)
- >35 public methods (excl. getters/setters/constructors)
- >24 fields

**Always exempt:**
- `ui/Window*.java` (Rule 6 UI structure)
- `adapter/seam/*.java` (BypassGate SDK)
- `benchmark/*.java`
- `settlement/room/*.java` (bridge files)

**Constants files:** `fields >= 50 AND pubM == 0` → exempt (EconConfig pattern).

## Testing

```bash
# All tests (402 tests)
mvn test

# Single test class
mvn test -Dtest=EconConfigTest

# Single test method
mvn test -Dtest=FlowPricesTest#testEffectiveCoverage
```

**Test structure:** 7 new suites in Sprint 9 (T-COV-6): `EconConfigTest`, `FlowPricesTest`, `EconomySimMockitoTest`, plus existing kernel tests.

## Key Files to Know

| File | Purpose |
|------|---------|
| `EconomySim.java` | Main orchestrator (1,459 LOC), tick orchestration, save/load v33 |
| `EconConfig.java` | 200+ constants, all tunables |
| `EngineMirror.java` | Facade for all Vanilla access (rooms, factions, humanoids, stats) |
| `EngineLevers.java` | 103 config toggles for granular degradation |
| `EconWindowBase.java` | UI base class, 4 windows + 16 tabs as static inner classes |
| `BypassGate.java` | Adapter SDK entry point |
| `EconomySaveLoad.java` | Chunked TLV save/load v33 |
| `tools/build_bindungsmatrix.py` | Canonical BINDUNGSMATRIX.csv generator |

## Hotkeys (Numpad)

| Key | Code | Window |
|-----|------|--------|
| Numpad + | 334 | WindowOverview |
| Numpad − | 333 | WindowEconomy |
| Numpad ∗ | 332 | WindowState |
| Numpad 0 | 320 | WindowQuickview |
| Numpad / | 331 | DebugTracer.dump() |
| ESC | 256 | Close all |

## Critical Rules Summary

1. **Always** run `mvn verify install -DskipTests` before committing
2. **Always** sync 7 stam-docs after version bump (manual sed)
3. **Never** create `EconContext`, `EconTab`, `EconWidgets`, `OverviewTabs`, `EconomyTabs`, `StateTabs` — consolidated away
4. **Never** use raw reflection/VarHandle in adapters — use BypassGate SDK
5. **Never** create Fallback adapters or `*MH.java` variants
6. **Never** use `-Dgate.skip=true` or `-Dskip.bump=true` in normal workflow
7. **Never** mark tasks as "postponed/deferred" — only 4 states: Planned/Active/Closed(SHA)/Rejected(reason)
8. **Always** verify file counts/LOC with actual commands before writing to docs

## Development Workflow

```bash
# 1. Make changes
# 2. Build & test (full gate)
mvn verify install -DskipTests -Dskip.bump=true

# 3. Verify doc sync
bash tools/verify-doc-sync.sh

# 4. Code review
code-reviewer-minimax-m3  # or invoke via agent

# 5. Commit atomically
git add -p
git commit -m "sprint: <Theme> — <tasks>"

# 6. Push
git push
```