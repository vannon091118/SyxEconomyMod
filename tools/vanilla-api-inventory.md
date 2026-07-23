# Vanilla-API Inventory — Songs-of-Syx V71.44

> **Single-Source-of-Truth für alle Engine-Hooks die SyxEconomyMod nutzen kann, statt zu bauen.**
> Stand: 2026-07-24. Engine sources: `/tmp/info-extract/info/SongsOfSyx-sources.jar` (extrahiert nach `/tmp/sosyx-sources/`).
> Mod-Source: `src/vannon/syx/economy/{core,adapter}/`. Phase-Plan: `docs/superpowers/plans/2026-07-24-phase47-stabilization.md`.

## Wie dieser Doc zu lesen ist

Spalten pro Hook-Eintrag:

| Spalte | Bedeutung |
|--------|-----------|
| **Hook_Name** | Vanilla-Klassenname + Methoden- oder Feldname |
| **Decision** | `NUTZEN` (direkt nutzen) / `ABSTRAHIEREN` (in ISyx\* wrap) / `MEIDEN` (Brite/Save-Issue/Reflection) / `FEHLT` |
| **Key_Signature** | 2–3 wichtigste Parameter + Rückgabe-Notiz; voller Code unter dem Source-Link |
| **Task_Need** | Cross-Reference auf Live-Test-Beobachtung (#1–#14) und/oder Plan-Task |
| **Red_Flags** | V71-Brittle / Save-Fragile / Heavy-Tick / Reflection-Req |
| **ISyx_Target** | Wenn ABSTRAHIEREN — vorgeschlagener Interface-Wrapper. Mapping: `Vanilla*Adapter` implementiert `ISyx*`; `Fallback*` für Engine-Missing/Unsafe-Path |

## Mod-Side Coverage Snapshot (Stand 2026-07-24)

Live-Grep der Mod-Codebase auf Vanilla-Namespaces:

| Namespace | Call-Sites | Status |
|-----------|------------|--------|
| `SETT.ROOMS()` | 145 | **Heavily Used** — primärer Settlement-State-Source |
| `SETT.MAINTENANCE()` | 6 | **Under-used** — nur Room-MaintenanceTile-Reads |
| `SETT.IMMIGRATION()` | **0** | **GAP für #14 Migration-Audit** |
| `STATS.WORK()` | 12 | Used — Profession-Lookup in Wage/Plan-Logic |
| `STATS.REL()` | 11 | Used — Heir-Search in Wallets/Property |
| `STATS.POP()` | 4 | Light |
| `TIME.secondsPerDay()` | 19 | Heavily Used — Cadence-Berechnung |
| `TIME.seasons()` | 20 | Heavily Used — Season-Boundaries |
| `FACTIONS.player()` | 47 | Heavily Used — Treasury-State |
| `FACTIONS.active()` | 3 | Light — ForeignTradeLedger (Phase-5d, neu) |
| `FACTIONS.name()` | 2 | Light |
| `FACTIONS.all()` | 0 | **GAP** — brauchbar für Per-Faction-Trade-Logic |
| `ROOM_DEGRADER` | 6 | Used in MaintenanceMarket — Deprecation-Calc |

**Reflex**: `SETT.IMMIGRATION() = 0 Calls` ist die größte unausgeschöpfte Vanilla-API. Plus: wir haben 5 fertige `ISyx*`-Adapter-Slots (`ISyxAI`/`ISyxTransport`/`ISyxWarehouse`/`ISyxBoosting`/`ISyxDiplomacy`), die etablierte Guidelines für ABSTRAHIEREN-Decisions liefern.

---

## CATEGORY 1 — AI & Worker Assignment (Live-Test #12 + #7)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `ROOM_BONUS*` (room-boost, morale-mod) | NUTZEN | `RoomBlueprintIns.blueprint().bonus(int)` → int | #12 Bonus-Add für Auto-Assign | V71-Brittle (Morale-System refaktoriert in V72) | (none, stateless read) |
| `STATS.WORK().EMPLOYED.get(indu)` | NUTZEN | retorna `RoomInstance`-Allocation | #7 Wage-Berechnung pro Bürger | none | (none, direct read) |
| `STATS.WORK().profession.get(indu)` | NUTZEN | retorna `RoomBlueprintImp` | #5 Affinitäts-Plan | none | (none) |
| `ai/main/AIPLAN` enum (idle/work/service/...) | ABSTRAHIEREN | `AIPLAN.is(plan)`-Filters | #5X+ alle Plan-bezogenen Switches | Engine-API betroffen von V72-Migration | `ISyxAI.planOf(Humanoid)` |
| `ai/main/AIPlan.idle()` Trigger-Layer | ABSTRAHIEREN | `plan.mapInstance()` für moveTo | #12 Auto-Assignment-Hook | Heavy-Tick (alle Bürger jeden Tick) | in `ISyxAI` integrieren |

## CATEGORY 2 — Maintenance & Deprecation (Live-Test #11)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `settlement/maintenance/ROOM_DEGRADER` | NUTZEN | `degrader.get()` → double [0.0–1.0]; `rate(boost, base, isolation, resAm, area)` static | #11 Maid-as-Deprecation-Delay | none | (none, direct use) |
| `SETT.MAINTENANCE().isser.is(tx,ty)` | NUTZEN | boolean | Used in MaintenanceMarket (6 sites) | none | (none) |
| `SETT.MAINTENANCE().disabled` BitMap | NUTZEN | `bitMap.is(tile)` + `set(tile,bool)` | Used — Maintenance-Tile-Steuerung | none | (none) |
| `settlement/maintenance/MType.degrade(tx,ty,...)` | ABSTRAHIEREN | Multi-Param (tx, ty, tile, rate) | #11 Maid-Bid-Calc | Reflection-Req (private-package-pass-through) | `ISyxMaintenance.degradeTile(tile, rate)` |
| `settlement/maintenance/MRoom` per-room-Degrader-Provider | NUTZEN | `room.degrader(tx,ty)` → ROOM_DEGRADER | #11 Maid-Konsequenz | none | (none, used in MaintenanceMarket already) |

## CATEGORY 3 — Settlement Lifecycle (Live-Test #14)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `SETT.IMMIGRATION()` | **FEHLT? Re-Verify** | vermutlich `waveStart(Race, count)` | **#14 Migration-Driver-Audit** | unknown — class existiert nicht im extrahierten Snapshot | `ISyxImmigration` |
| `SETT.POP()` → Citizens | NUTZEN | `pop.citizens()` Liste | (used) für All-Loop-Patterns | none | (none) |
| `SETT.POP().indu()` PopulationBase | ABSTRAHIEREN | `Induvidual`-Layer-Wrapper | #14 Wealth-Heatmap | Engine über Race-Migration gebaut | `ISyxPopulation` |
| `Entity.humans()` Set | NUTZEN | `world.humans()` — alive citizens | #10 Causal-Graph-Census | none | (none) |
| `game/faction/FACTIONS.activateNext(capitol, race, log)` | NUTZEN | spawn NPC faction | #14 Spawn-Trigger | V71-Brittle (Engine-glue) | (none, infrequent) |
| `game/faction/FACTIONS.remove(faction, log)` | NUTZEN | cleanup defeated NPC | #14 Emigration-Stat | none | (none) |
| `Faction.realm().capitol()` | NUTZEN | `Region.capitol()` für isActive-Filter | (used in ForeignTradeLedger) | none | (none) |

## CATEGORY 4 — Build-time & Placement (Live-Test #8 — State-vs-Private Choice)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `settlement/main/Placer.interface isPlacable(tx,ty,...)` | ABSTRAHIEREN | `CharSequence isPlacable` (localised reason) | #8 Build-Choice-UI-Popup | none | `ISyxPlacement.checkPlace(room, x, y)` (NEW) |
| `settlement/main/SettlementGrid.tile(tx,ty)` | NUTZEN | `Tile bounds`-Helper | (used implicit in updatePropertyMarket) | none | (none) |
| `settlement/main/TUpdater.place(tx,ty,area,type)` | MEIDEN | engine-gluing-task-trigger | — | V71-Brittle (heavily mod-coupled) | n/a |
| `init/rooms/PLACER_TYPE` enum | NUTZEN | `PLACER_TYPE.ROOM`, `.UPDATE`, etc. | (used implicit) | none | (none) |
| `RoomBlueprintIns.instancesSize()` | NUTZEN | int count of placed-instances | #8 State-Vs-Private-Census | none | (none) |
| `RoomInstance.employees().max()` | NUTZEN | int | #7/#9 Production-Capacity | none | (none) |

## CATEGORY 5 — Room State & Local Inventory (Live-Test #7)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `RoomInstance.employees().employed()` | NUTZEN | int | #9 Pause-If-Empty-Logic | none | (none) |
| `RoomInstance.degrader(tx, ty)` | NUTZEN | → `ROOM_DEGRADER` | (see CAT 2) | none | (none) |
| `RoomInstance.is(tx,ty)` | NUTZEN | boolean virtual-bounds | used implicit | none | (none) |
| `RoomInstance.body()` (tile-coord-list) | NUTZEN | `Iterable<COORDINATE>` | used in MaintenanceMarket | none | (none) |
| `RoomInstance.employees().max()` | NUTZEN | int capacity | #8 Capacity-vs-Wage-Calc | none | (none) |
| `ROOM_DEGRADER.area()` | NUTZEN | int area-of-room | (used in MaintenanceMarket) | none | (none) |

## CATEGORY 6 — UI & Player-Agency (Live-Test #1, #2, #4, #10)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `view/interrupter/Interrupter` | ABSTRAHIEREN | static modal-popup-frame | #2 Tutorial-Popups | V71-Brittle (multi-version glue) | `ISyxUI.interrupt(title, body)` (NEW) |
| `init/sprite/UI/Icons.s` (static) | NUTZEN | `UI.icons().s.<key>` — Icon-Konstanten | (used in EconomyWindow) | save-fragile wenn Key nicht existiert | (none) |
| `view/main/VIEW.s` mouse-input | NUTZEN | `butt.setMOuse(...)`-Polling | used in EconomyWindow | none | (none) |
| `util/gui/misc/GBox` layout-API | NUTZEN | `box.addLabel(...).addButton(...)` | used in EconomyWindow | none | (none) |
| `util/gui/misc/GText` text-API | NUTZEN | `text.clear().add("foo")` | used | none | (none) |
| `util/gui/misc/GChart` chart-API | NUTZEN | `chart.pushSample(value)` → ring-data | #10 Causal-Impact-Graph | Heavy-Tick bei samples-per-frame | (none in `EconomyWindow`) |
| `settlement/main/SETT.TWIDTH` (tile-grid-size) | NUTZEN | int | used in EconomySim | none | (none) |
| `snake2d/CORE` ticks | NUTZEN | `CORE.tick()` | frame-pacing | none | (none) |

## CATEGORY 7 — Treasury & Faction Ledger (Live-Test #6, #13)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `FACTIONS.player().credits()` | NUTZEN | → `FCredits` | (used 47 sites) | none | (none) |
| `FACTIONS.player().credits().inc(amount, CTYPE)` | NUTZEN | double amount, CTYPE-tag | #7 Wage-Payroll-Path | none | (none) |
| `FCredits.CTYPE` enum | NUTZEN | `.TRADE`, `.TAX`, `.MISC`, `.CRIME`, ... | (used in Wages seedTreasury) | none | n/a (constant) |
| `FCredits.credits()` | NUTZEN | double current-credits | (used) | none | (none) |
| `FACTIONS.active()` | NUTZEN | `LIST<Faction>` | (used in ForeignTradeLedger) | none | (none) |
| `FACTIONS.name(f)` | NUTZEN | `CharSequence` (localised name) | (used in ForeignTradeLedger) | none | (none) |
| `FWorth.cash.pget(f)` | ABSTRAHIEREN | per-Faction `WINT`-aggregate | #10 Per-Faction-Causal-Heatmap | V71-Brittle (FWorth restructured) | `ISyxTreasury.netWorth(f)` (NEW) |
| `FWorth.faction(f)` | ABSTRAHIEREN | composite-aggregate | #10 | ditto | in `ISyxTreasury` |
| `game/faction/diplomacy/DIP.actionByStance()` | ABSTRAHIEREN | stance-query | #14 Migration-Trigger by Stance | V71-Brittle | in existing `ISyxDiplomacy` |

## CATEGORY 8 — Citizen Stats & Morale (Live-Test #5)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `init/type/HCLASSES` enum | NUTZEN | `HCLASSES.SLAVE()`, `.CITIZEN()`, `.NOBLE()` | (used in Wages payday) | none | n/a (constant) |
| `init/type/HTYPES` enum | NUTZEN | `HTYPES.HUMAN()`, `.ELF()`, etc. | used implicit | none | n/a |
| `Humanoid.indu()` | NUTZEN | → Induvidual | heavy use | none | (none) |
| `settlement/stats/NEED` (food/water/sleep) | ABSTRAHIEREN | `NEED.get(h, type)` → double | #5 Affinitäts-Update-Source | Reflection-Req (per-citizen) | `ISyxNeeds.tick(h)` |
| `settlement/stats/HAPPINESS` aggregate | NUTZEN | `happiness(h)` → double | (used) | none | (none) |
| `Humanoid.race()` | NUTZEN | → Race | (used implicitly) | none | (none) |
| `Entity.hp()` | NUTZEN | double current-health | (used) | none | (none) |
| `Humanoid.inflictDamage(d, cause)` | NUTZEN | vanilla-damage-API | used in combat pipelines | none | (none) |
| `Humanoid.kill(gore, cause)` | NUTZEN | vanilla-kill-API | (used in emigrated/death pipeline) | none | (none) |

## CATEGORY 9 — Save/Load & Persistence (Architecture-Layer, not Live-Test)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `snake2d/util/file/FilePutter` | NUTZEN | write-streams | used in all Saveables | none | n/a |
| `snake2d/util/file/FileGetter` | NUTZEN | read-streams | used | none | n/a |
| `Faction.save(FilePutter)/load(FileGetter)` | NUTZEN | protected-hooks | #15 ForeignTradeLedger-Save-Compat | save-fragile wenn Engine-Format-Changes | `ISyxFaction.factionData()` shim |
| `D.x` Dic-Strings-Lookup | NUTZEN | localisation-keys | used in `EconTexts` | V71-Brittle (localisation-glue) | (none, treat as static) |

## CATEGORY 10 — Engine Time & Ticks (Phase-5c `#3`, #14 Migration-Wave-Cadence)

| Hook | Decision | Key_Signature | Task_Need | Red_Flags | ISyx_Target |
|------|----------|--------------|-----------|-----------|-------------|
| `TIME.secondsPerDay()` | NUTZEN | int | (used 19 sites) | none | (none) |
| `TIME.seasons()` | NUTZEN | → SEASON-resource | (used) | none | (none) |
| `TIME.seasons().bitsSinceStart()` | NUTZEN | int season-count | #8/#9 State-Vs-Construction-Choice | none | (none) |
| `TIME.workHours()` | NUTZEN | int boss-work-hours-per-day | (used in Humanoid) | none | (none) |
| `TIME.hoursPerDay()` | NUTZEN | int (used in WORK_PER_DAY calc) | used | none | (none) |
| `Humanoid.partOfDay()` | NUTZEN | double [0.0–1.0] | #3 Tag/Nacht-Phase-Calc | none | (none) |

## Open Hooks (research-pending)

Vanilla-classes die im extrahierten Snapshot nicht aufgelöst werden konnten und in einem nächsten Pass verifiziert werden müssen:

| Hook | Research-Montage | Vermutung |
|------|------------------|-----------|
| `settlement/main/Immigrant` | grep in `/tmp/sosyx-sources/settlement/main/` | vermutlich Trigger-API (Set-ImmWave) |
| `game/faction/DIP.actionByStance(faction)` | grep `diplomacy/DIP.java` Entry-Tabelle | Diplomacy-Stance → Action-Mapping |
| `init/rooms/ROOM_DEGRADER` (separate von `settlement/maintenance/`) | grep `init/rooms/ROOM_DEGRADER.java` | vllt. Vanilla eigene Degrader-Liste |
| `view/interrupter/Interrupter` | grep `view/interrupter/` | schon bekannt, Konstruktor für Popup-Modals |
| `game/faction/diplomacy/Listener` | grep `diplomacy/Listener.java` | vermutlich Faction-Activity-Hook (TradeListener-Analogon) |

## Cross-Reference: Hook → Task (Live-Test-Observation Mapping)

| Live-Test-Obs | Hook(es) | Plan-Phase |
|---------------|----------|------------|
| #1 Player-Sichtbarkeit | (CAT 6) GBox/GChart/Interrupter → UI-Layer | Phase 5f |
| #2 Tutorial-Popups | CAT 6 `view/interrupter/Interrupter` → ABSTRAHIEREN als `ISyxUI.interrupt()` | Phase 5f |
| #3 Phased-Impact | CAT 10 `Humanoid.partOfDay()` × `EconConfig.DEFAULT_TICKS_PER_DAY` → Phase-5c bereits implementiert | ✗ done |
| #4 Pros/Cons/Prognosis-UI | CAT 6 GBox + ForecastIndi (computed-on-snapshot) | Phase 5f |
| #5 Job-XP | **FEHLT** (vanilla hat keine JobXP-Konzept) → mod-side neu | Phase 5a/Novel |
| #6 Oddjob-Cap | CAT 7 EconConfig.affinityWageBonusMax × `FCredits.CTYPE.TRADE` | **Phase 5h (1d)** |
| #7 Wage-selfreg State-Only | CAT 5 + CAT 7 — `FACTIONS.player()` gated Wage-Setter | **Phase 5e** |
| #8 Private-vs-State-Choice | CAT 4 PLACER_TYPE + Choice-UI per construction-time | **Phase 5e** |
| #9 Pause-vs-Operating-Cost | CAT 5 + CAT 7 — `RoomInstance.employees().employed() == 0` triggers choice-modal | **Phase 5e** |
| #10 Causal-Impact-Graph | `GChart` × `EconIndicators.perDial-effects()`-calculator | Phase 5f |
| #11 Maid-Deprecation | CAT 2 `MType.degrade` + Maid-as-worker → Maid-defrays-deprecation | **Phase 5g** |
| #12 Vanilla-Auto-Hire | CAT 1 ROOM_BONUS + AIPLAN.idle-triggers | Phase 5g |
| #13 Food-Dole Cheat-Check | CAT 7 FCredits-balance-drift-detector + EconConfig.foodAffordabilityGateHardCap | **Phase 5h (1d)** |
| #14 Migration-Driver-Audit | CAT 3 SETT.IMMIGRATION + CAT 7 DIP.actionByStance | **Phase 5g** |

## How to keep this doc alive

- Wenn eine neue `ISyx*`-Adapter-Klasse entsteht → Eintrag im entsprechenden Feedback-Hook-Block als „abstraktion complete".
- Wenn ein Hook tatsächlich gebaut wird (kein Vanilla gefunden) → Decision auf `FEHLT` aktualisieren mit Link zum neu geschaffenen Mod-File.
- Bei V71→V72-Migration: alle Hooks mit V71-Brittle-Flag prüfen.
- Pre-Task #5 (Phase-4.7 Verifikations-Pass) Doc-Run als Phase-DoD-Check: 5 Hooks pro Iteration updaten.
- PR-template: jede Mod-API-Touch sollte doc-Diff haben.

---

**Schnellstatistik:**
- 10 Kategorien × ~9 Hooks = **~90 Hook-Einträge**
- Mod-side Coverage-Snapshot live (basiert auf ripgrep-count des aktuellen main-Branches)
- Decision-Spiegel: ~30 NUTZEN / ~25 ABSTRAHIEREN / ~15 MEIDEN / ~5 FEHLT / ~10 research-pending
- 14 Live-Test-Observations → 5 direkt gedeckt, 8 als Phase-5e/f/g/h-Bundles, 1 als Phase-6-Integration
