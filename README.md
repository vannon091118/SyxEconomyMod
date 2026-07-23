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

**Current version:** v0.3.2 (Phase 4.7 stabilization + Phase 5e Player-Agency Bundle)

### Core subsystems

| System | File | Purpose |
|--------|------|---------|
| Firm Economy | `FirmLedger.java` | Firm profit-max, hiring, sizing, guild surplus |
| Labor Market | `LaborMarket.java` | Worker allocation, wage competition, priority bidding |
| Flow Pricing | `FlowPrices.java` | Supply/demand price signals per resource |
| Citizen Wallets | `Wallets.java`, `SubjectWallet.java` | Wealth, spending, saving, inheritance |
| Treasury Crisis | `TreasuryCrisis.java` | 6-tier escalating fiscal crisis mechanic |
| Property Market | `PropertyLedger.java`, `HousingMarket.java` | Home purchases, firm shares, dividends |
| Food System | `FoodPlanController.java`, `AffordabilityGate.java` | Meal planning, affordability gates, grain dole |
| Taxes | `Taxes.java`, `Fiscal.java` | Per-head tax, market tax, religion tax, liturgy |
| State Industry | `StateWarehouses.java`, `WarehouseMarket.java` | State-run storage, pricing, auto-procurement |
| Diagnostics | `EconomyWindow.java`, `ChartPanel.java` | In-game economy dashboard, live charts |
| Persistence | `EconomySim.java`, `ChunkedSave.java` | Save/load, chunked serialization, audit trails |

### Architecture

```
src/vannon/syx/economy/
├── core/           ← 80+ files, the economic engine
├── adapter/        ← Vanilla API abstraction layer
└── settlement/     ← Bridge classes for SoS room/service hooks
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full system map,
[`docs/API_REFERENCE.md`](docs/API_REFERENCE.md) for the vanilla API usage guide,
and [`docs/ROADMAP.md`](docs/ROADMAP.md) for the development plan.

---


