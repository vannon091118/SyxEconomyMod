# Fullscan: TiredGirl4's Economy Mod vs. Vanilla Songs of Syx

> ⚠️ **HISTORISCH** — Scan-Datum: 2026-07-21. Superseded durch COVERAGE_AUDIT.md (2026-07-23).

## Scan Parameters
- Vanilla JAR: `/home/vannon/snap/steam/common/.local/share/Steam/steamapps/common/Songs of Syx/SongsOfSyx.jar`
- Mod source: `/home/vannon/Schreibtisch/TiredGirl4_EconomyMod_Decompiled/`
- Scan date: 2026-07-21
- Vanilla classes total: 10,992
- Unique external imports in mod: 183

## Executive Summary
The mod is heavily coupled to vanilla internals. Almost every subsystem it touches (`settlement.*`, `game.*`, `init.*`, `snake2d.*`, `view.*`, `world.*`) uses concrete vanilla classes rather than interfaces. This creates high maintenance risk if the base game updates. On the other hand, the core economic math and ledger logic have no vanilla dependencies and can be cleanly isolated.

## Coupling Heat Map

| Package area | Import count | Risk level | Notes |
|--------------|--------------|------------|-------|
| `settlement.*` | ~40% | Critical | AI plans, humanoids, rooms, stats, path finding |
| `game.*` | ~20% | High | Factions, credits, diplomacy, time, boosting |
| `init.*` | ~18% | High | Resources, races, needs, constants, sprites |
| `snake2d.*` | ~15% | Medium | Rendering, UI, input, utility classes |
| `view.*` | ~4% | Medium | UI windows, interrupts |
| `util.*` | ~3% | Low-Medium | GUI helpers, statistics |
| `java.*` | ~10% | Low | Standard library |

## Top-Level Vanilla Coupling

### AI & Needs (`settlement.entity.humanoid.ai.main`, `init.type.NEEDS`)
- `AIManager`, `AIPLAN`, `AISUB`, `AISTATE`, `AI`, `HAI`
- `NEEDS.TYPES().HUNGER`, `NEEDS.TYPES().THIRST`
- These are used to overwrite/set AI plans for paying for food/drink/services.
- **Risk:** Any change to AI plan lifecycle or need constants breaks the mod.

### Humanoids & Entities (`settlement.entity`, `settlement.stats`)
- `Humanoid`, `ENTITY`, `ENTETIES`
- `Induvidual`, `STATS`, `WearableResource`
- Mod uses entity IDs for wallet slots.
- **Risk:** Entity ID bitmasks and max entity count are hard-coded.

### Rooms & Buildings (`settlement.room`)
- `RoomInstance`, `RoomBlueprintIns`, `RoomBlueprintImp`, `Room`
- Concrete room classes: `ROOM_CANTEEN`, `ROOM_EATERY`, `ROOM_TAVERN`, `ROOM_MARKET`, `ROOM_EXPORT`, `ROOM_TEMPLE`, etc.
- **Risk:** `EconomicRoles.java` uses `instanceof` against specific rooms. New rooms or room renames break classification.

### Faction & Economy (`game.faction`, `game.time`)
- `FACTIONS`, `FCredits`, `FResources`, `Faction`, `TIME`
- Player/NPC faction access and treasury manipulation.
- **Risk:** Treasury APIs are stable but not guaranteed across updates.

### Rendering & UI (`snake2d.*`, `view.*`, `util.gui`)
- `Renderer`, `SPRITE_RENDERER`, `MButt`, `CORE`, `KEYS`
- `GBox`, `GText`, `Interrupter`, `VIEW`
- **Risk:** UI framework changes require manual rework.

### World & Diplomacy (`world.*`, `game.faction.diplomacy`)
- `WORLD`, `Region`, `RD`, `AD`, `DIP`, `DipWarPlayer`
- Used in `DebtDiplomacyBuffer`.
- **Risk:** Low surface area but deep coupling.

## Missing / Not Found in Vanilla (Potential Risks)

Generated report did not identify any imports that are definitively outside the vanilla JAR, but some names are reflection-based or may be obfuscated in release builds.

## Recommendations for Decoupling

1. **Introduce adapter interfaces** for `ISyxAgent`, `ISyxRoom`, `ISyxFaction`.
2. **Replace `instanceof` room checks** in `EconomicRoles.java` with a capability/tag system.
3. **Move economic math** (`FlowPrices`, `FlowMeter`, `ExchangeKernel`, `FirmLedger`) into a `core` package with zero vanilla imports.
4. **Use an event bus** so vanilla-side adapters emit events; core modules react without knowing vanilla.
5. **Centralize reflection** (`DebtDiplomacyBuffer`) behind a `DiplomacyAdapter` with fallbacks.

## Files by Coupling Severity

### Severely Coupled (need adapters first)
- `EngineSeams.java`
- `EconomicRoles.java`
- `FoodPlanController.java`
- `DrinkTransactionPlan.java`
- `ServicePlanController.java`
- `BrokeFoodPlan.java`
- `BrokeServicePlan.java`

### Moderately Coupled (vanilla needed only for I/O)
- `EconomyWindow.java`
- `InstanceScript.java`
- `MainScript.java`
- `Wallets.java`

### Loosely Coupled (mostly pure logic)
- `FlowPrices.java`
- `FlowMeter.java`
- `ExchangeKernel.java`
- `Histogram.java`
- `AuditKernel.java`

## Next Steps
See the refactoring roadmap in `../../README.md` or choose a concrete action:
1. Build adapter interfaces for the severely coupled files.
2. Extract pure core logic into a new package.
3. Add automated coupling checks in CI.
