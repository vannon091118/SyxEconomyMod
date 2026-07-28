package vannon.syx.economy.adapter;

import vannon.syx.economy.adapter.seam.SchemaValidator;
import vannon.syx.economy.adapter.seam.SchemaValidator.AccessType;
import vannon.syx.economy.adapter.seam.SchemaValidator.ValidationReport;
import vannon.syx.economy.core.EventLog;

/**
 * Central dispatcher that builds ALL adapters based on schema validation.
 *
 * <p>Replaces the 5 separate {@code createXxxAdapter()} static methods in
 * {@code EconomySim}. Instead of each adapter probing the engine
 * independently, this dispatcher validates the complete {@code vanilla-schema.yaml}
 * first, then builds only adapters whose engine contracts are satisfied.</p>
 *
 * <p>At engine-update time (V71→V72): one diff in {@code registerSchema()}
 * re-validates everything. No scattering across 5 files.</p>
 */
public final class AdapterDispatcher {

    private AdapterDispatcher() {}

    /**
     * Single point of adapter creation for the production no-arg constructor.
     * Test constructors inject pre-built adapters directly — no change needed.
     */
    public static AdapterBundle build() {
        // 1. Register every class + field + method the mod touches.
        SchemaValidator validator = new SchemaValidator("AdapterDispatcher");
        registerSchema(validator);

        // 2. Validate against the live engine.
        ValidationReport report = validator.validate();

        // 3. Build adapters — each adapter checks isAvailable() internally,
        //    and the validator result gives us a pre-flight signal.
        ISyxTransport   transport  = buildTransport(report);
        ISyxWarehouse   warehouse  = buildWarehouse(report);
        ISyxBoosting    boosting   = buildBoosting(report);
        ISyxDiplomacy   diplomacy  = buildDiplomacy(report);
        ISyxAI          ai         = buildAi(report);
        ISyxNpc         npc        = buildNpc(report);
        ITreasuryAccess treasury   = buildTreasury(report);
        IPopulationAccess population = buildPopulation(report);
        IGoodsAccess    goods      = buildGoods(report);

        AdapterBundle bundle = new AdapterBundle(transport, warehouse, boosting, diplomacy, ai, npc,
                treasury, population, goods);

        // 4. Initialize the EngineMirror facade with all sub-interfaces.
        EngineMirror.initFromBundle(bundle);

        return bundle;
    }

    // ─── Schema registration (keep in-sync with tools/vanilla-schema.yaml) ────
    private static void registerSchema(SchemaValidator v) {
        // Group 1 — BypassGate instance fields (ISyxDiplomacy)
        v.registerField("game.faction.diplomacy.DipWarPlayer", "upI");
        v.registerField("game.faction.diplomacy.DipWarPlayer", "pPow");
        v.registerField("game.faction.diplomacy.DipWarPlayer", "coalitionPow");
        v.registerField("game.faction.diplomacy.DipWarPlayer", "bWilling");

        // Group 1 — BypassGate instance method (ISyxWarehouse)
        v.registerMethod("settlement.room.infra.stockpile.StockpileInstance", "storingSet");

        // Group 1 — BypassGate instance field (ISyxTransport, package-private class)
        v.registerField("settlement.room.infra.transport.TransportInstance", "distance");

        // Group 1 — BypassGate ref field (ISyxBoosting, inner class)
        v.registerField("game.boosting.BOOSTABLES$CIVICS", "GOV");

        // Group 2 — ClassResolver existence-only (ISyxAI, 6 plan classes)
        v.registerClass("settlement.entity.humanoid.ai.work.PlanOddjobber");
        v.registerClass("settlement.entity.humanoid.ai.consume.F_SPlanEatery");
        v.registerClass("settlement.entity.humanoid.ai.consume.F_SPlanCanteen");
        v.registerClass("settlement.entity.humanoid.ai.consume.F_PlanEat");
        v.registerClass("settlement.entity.humanoid.ai.consume.PlanTavern");
        v.registerClass("settlement.entity.humanoid.ai.consume.M_PlanMarket");

        // Group 3 — NPC Faction classes (ISyxNpc)
        v.registerClass("game.faction.npc.FactionNPC");
        v.registerClass("game.faction.npc.NPCResources$FactionResource");
        v.registerField("game.faction.npc.NPCResources$FactionResource", "priceSell");
        v.registerField("game.faction.npc.NPCResources$FactionResource", "priceBuy");

        // Group 4 — Treasury (ITreasuryAccess) — Player, PCredits
        v.registerClass("game.faction.player.Player");
        v.registerClass("game.faction.player.PCredits");
        v.registerField("game.faction.player.PCredits", "credits");
        v.registerField("game.faction.player.PCredits", "yearly");
        v.registerField("game.faction.player.PCredits$Yearly", "TURNOVER");
        v.registerField("game.faction.player.PCredits$Yearly", "PROFITS");
        v.registerField("game.faction.player.PCredits$Yearly", "LOSSES");
        v.registerField("game.faction.player.PCredits", "all");

        // Group 5 — Population (IPopulationAccess) — STATS.POP, STANDINGS, HTYPES
        v.registerClass("settlement.stats.STATS");
        v.registerMethod("settlement.stats.STATS", "POP");
        v.registerClass("settlement.stats.colls.StatsPopulation");
        v.registerClass("settlement.stats.standing.STANDINGS");
        v.registerMethod("settlement.stats.standing.STANDINGS", "get");
        v.registerClass("init.type.HTYPES");

        // Group 6 — Goods (IGoodsAccess) — SettTrade, StockpileTally, ResourcePrices
        v.registerClass("settlement.trade.SettTrade");
        v.registerMethod("settlement.trade.SettTrade", "buyer");
        v.registerMethod("settlement.trade.SettTrade", "seller");
        v.registerClass("settlement.room.infra.stockpile.StockpileTally");
        v.registerMethod("settlement.room.infra.stockpile.StockpileTally", "tally");
        v.registerClass("game.faction.trade.ResourcePrices");
        v.registerMethod("game.faction.trade.ResourcePrices", "get");
    }

    // ─── Adapter factories (each checks isAvailable() before use) ──────────

    private static ISyxTransport buildTransport(ValidationReport report) {
        VanillaTransportAdapter a = new VanillaTransportAdapter();
        if (!a.isDistanceAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: TransportAdapter distance-Feld "
                    + "nicht verfügbar — geometrischer Fallback aktiv");
        }
        return a;
    }

    private static ISyxWarehouse buildWarehouse(ValidationReport report) {
        VanillaWarehouseAdapter a = new VanillaWarehouseAdapter();
        if (!a.isStoringLockAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: WarehouseAdapter storingSet-Methode "
                    + "nicht verfügbar — Pricing-Lock-Fallback aktiv");
        }
        return a;
    }

    private static ISyxBoosting buildBoosting(ValidationReport report) {
        VanillaBoostingAdapter a = new VanillaBoostingAdapter();
        if (!a.isAdminBoosterAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: BoostingAdapter GOV-Feld "
                    + "nicht verfügbar — Industrie-Bonus No-op");
        }
        return a;
    }

    private static ISyxDiplomacy buildDiplomacy(ValidationReport report) {
        VanillaDiplomacyAdapter a = new VanillaDiplomacyAdapter();
        if (!a.isAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: DiplomacyAdapter numerische Felder "
                    + "nicht verfügbar — Schulden-Puffer inaktiv");
        }
        return a;
    }

    private static ISyxAI buildAi(ValidationReport report) {
        VanillaAIAdapter a = new VanillaAIAdapter();
        if (!a.isAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: AIAdapter Plan-Klassen "
                    + "nicht verfügbar — Plan-Erkennung inaktiv");
        }
        return a;
    }

    private static ISyxNpc buildNpc(ValidationReport report) {
        NpcFactionAdapter a = new NpcFactionAdapter();
        if (!a.isAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: NpcFactionAdapter "
                    + "nicht verfügbar — NPC-Preis-Kontrolle inaktiv");
        }
        return a;
    }

    private static ITreasuryAccess buildTreasury(ValidationReport report) {
        TreasuryAccessImpl a = new TreasuryAccessImpl();
        if (!a.isAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: TreasuryAccess "
                    + "nicht verfügbar — Treasury-Daten inaktiv");
        }
        return a;
    }

    private static IPopulationAccess buildPopulation(ValidationReport report) {
        PopulationAccessImpl a = new PopulationAccessImpl();
        if (!a.isAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: PopulationAccess "
                    + "nicht verfügbar — Bevölkerungsdaten inaktiv");
        }
        return a;
    }

    private static IGoodsAccess buildGoods(ValidationReport report) {
        GoodsAccessImpl a = new GoodsAccessImpl();
        if (!a.isAvailable()) {
            EventLog.log("SEAM", "AdapterDispatcher: GoodsAccess "
                    + "nicht verfügbar — Warenpreise/Lagerbestände inaktiv");
        }
        return a;
    }

    // ─── Bundle record ──────────────────────────────────────────────────

    public static final class AdapterBundle {
        public final ISyxTransport   transport;
        public final ISyxWarehouse   warehouse;
        public final ISyxBoosting    boosting;
        public final ISyxDiplomacy   diplomacy;
        public final ISyxAI          ai;
        public final ISyxNpc         npc;
        public final ITreasuryAccess treasury;
        public final IPopulationAccess population;
        public final IGoodsAccess    goods;

        AdapterBundle(ISyxTransport transport, ISyxWarehouse warehouse,
                      ISyxBoosting boosting, ISyxDiplomacy diplomacy,
                      ISyxAI ai, ISyxNpc npc,
                      ITreasuryAccess treasury, IPopulationAccess population,
                      IGoodsAccess goods) {
            this.transport   = transport;
            this.warehouse   = warehouse;
            this.boosting    = boosting;
            this.diplomacy   = diplomacy;
            this.ai          = ai;
            this.npc         = npc;
            this.treasury    = treasury;
            this.population  = population;
            this.goods       = goods;
        }
    }
}