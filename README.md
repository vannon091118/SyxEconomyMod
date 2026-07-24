# SyxEconomyMod

> **A full-stack economy simulation mod for Songs of Syx V71.**
> Replaces the vanilla resource-exchange model with a complete economic engine:
> wages, firm profit-maximization, market pricing, taxation, treasury crises,
> citizen wallets, property markets, and more — all running inside the game engine.

---

## Overview

SyxEconomyMod transforms Songs of Syx from a resource-management game into
an economy simulator. Every citizen earns wages, pays taxes, buys food, rents
housing, and saves wealth. Firms hire workers, optimize output, and distribute
profits. The state manages treasury, sets policy, and faces escalating crisis
tiers when spending outruns revenue.

**Current version:** v0.1.4 (Phase 4.7 stabilization)

### Core subsystems

| System | File | Purpose |
|--------|------|---------|
| Firm Economy | `FirmLedger.java` | Firm profit-max, hiring, sizing, guild surplus, cold-start guard |
| Labor Market | `LaborMarket.java` | Worker allocation, wage competition, priority bidding |
| Flow Pricing | `FlowPrices.java` | Supply/demand price signals per resource |
| Citizen Wallets | `Wallets.java`, `SubjectWallet.java` | Wealth, spending, saving, inheritance |
| Treasury Crisis | `TreasuryCrisis.java` | 5-tier escalating fiscal crisis + Hard Floor |
| Property Market | `PropertyLedger.java`, `HousingMarket.java` | Home purchases, firm shares, dividends |
| Food System | `FoodPlanController.java`, `AffordabilityGate.java` | Meal planning, affordability gates, grain dole |
| Taxes | `Taxes.java`, `Fiscal.java` | Per-head tax, market tax, religion tax, liturgy |
| State Industry | `StateWarehouses.java`, `WarehouseMarket.java` | State-run storage, pricing, auto-procurement |
| Diagnostics | `EconomyWindow.java`, `ChartPanel.java` | In-game economy dashboard, live charts |
| Persistence | `EconomySim.java`, `ChunkedSave.java` | Save/load, chunked serialization, audit trails |

### v0.1.4 New Classes

| Class | LOC | Extracted From | Purpose |
|-------|-----|---------------|---------|
| `RoomOperatingModeController.java` | 79 | `FirmLedger.java` | Per-room op-mode map + cost scaling |
| `PropertyMarketController.java` | 179 | `EconomySim.java` | Home purchase, share trading, dividend cycle |
| `CrisisDispatch.java` | 27 | `EconomySim.java` | Thin `TreasuryCrisis.update()` wrapper |

### Architecture

```
src/vannon/syx/economy/
├── core/           ← 96 files, ~21.400 LOC (EconomySim: 1.442)
├── adapter/        ← Vanilla API abstraction layer (17 files)
└── settlement/     ← Bridge classes for SoS room/service hooks
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full system map,
[`docs/API_REFERENCE.md`](docs/API_REFERENCE.md) for the vanilla API usage guide,
and [`docs/ROADMAP.md`](docs/ROADMAP.md) for the prioritized development TODO.

---


### Build

```bash
# Requires: JDK 21+, Maven 3.8+
mvn compile
```

### Install (local testing)

```bash
# Build JAR
mvn package

# Copy to Songs of Syx mod directory
cp target/SyxEconomyMod.jar ~/.local/share/songsofsyx/mods/SyxEconomyMod/V71/script/
```

### Run diagnostics

```bash
# CI gate — verifies Phase 4.7 blockers are closed
./tools/phase47-shield.sh

# Truth consistency check — verifies docs match code
./tools/docs-truth-consistency.sh

# Food dole cheat check — verifies equity drift < 5%
./tools/food-dole-cheat-check.sh

# Live notes consolidation — turns playtest brain-dumps into backlog entries
./tools/consolidate-live-notes.sh
```

---

## Documentation

| Document | Content |
|----------|---------|
| [`CHANGELOG.md`](CHANGELOG.md) | **Kanonisch** — vollständige Release-Historie |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Full system architecture, data flow, class map |
| [`docs/API_REFERENCE.md`](docs/API_REFERENCE.md) | Vanilla engine hooks we use, adapter patterns |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | **Master TODO** — priorisierte Entwicklungsschritte |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | Live-Test-Funde, Bugs, Papercuts |
| [`docs/GLOSSARY.md`](docs/GLOSSARY.md) | Terminology: economic concepts, SoS engine terms |
| [`docs/BALANCE_LEVERS.md`](docs/BALANCE_LEVERS.md) | All tunable constants and their effects |
| [`docs/live-notes/`](docs/live-notes/) | Raw playtest observations (funnel into backlog weekly) |
| [`docs/archive/`](docs/archive/) | Frozen historical snapshots + reports |

---

## Development

### Tooling

```bash
tools/
├── build-gate.sh              # Pre-build validation
├── bump-version.sh            # Version management (pom.xml + CHANGELOG + _Info.txt + git tag)
├── code-audit.sh              # Static analysis: IdentityHashMap count, catch(Throwable), printStackTrace
├── consolidate-live-notes.sh  # Weekly playtest-note funnel
├── docs-truth-consistency.sh  # Doc-vs-code drift detector
├── food-dole-cheat-check.sh   # Equity drift monitor
├── install-hooks.sh           # Git hook installer
├── phase47-shield.sh          # CI gate for Phase 4.7 blockers
├── truth-stamp.sh             # Post-commit doc timestamp updater
├── verify-version-consistency.sh  # Version sync checker
└── vanilla-api-inventory.md   # Vanilla engine hook catalog
```

### Live Notes Funnel

During playtests, write raw observations to `docs/live-notes/YYYY-MM-DD-title.md`
using the template. Tag with: `cover:plan-task-N`, `gap:net-new`, `reject:scope-out`,
`ux:papercut`, `balance:drift`, `bug:silent`.

Run `./tools/consolidate-live-notes.sh` weekly to categorize and integrate.

---

## License

This project is a mod for Songs of Syx. All original game assets and APIs
are property of Gamatron AB. The economic simulation engine is original work.

---

## Credits

- **Original mod:** TiredGirl4's Economy Mod (Steam Workshop)
- **Decompilation & re-architecture:** vannon091118
- **Phase 4–5 engineering:** Freebuff-assisted development
- **4-Perspective Audit system:** Self-audit methodology for solo-dev QA

