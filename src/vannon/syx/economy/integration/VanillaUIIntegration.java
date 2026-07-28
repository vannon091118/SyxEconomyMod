package vannon.syx.economy.integration;

import init.resources.RESOURCE;
import init.trade.TRADABLE;
import init.type.HCLASS;
import init.race.Race;
import snake2d.util.color.COLOR;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.gui.renderable.RENDEROBJ;
import snake2d.util.sprite.SPRITE;
import util.colors.GCOLOR;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import util.info.GFORMAT;
import init.sprite.UI.UI;
import vannon.syx.economy.adapter.EngineMirror;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.LoggingAdapter;
import vannon.syx.economy.ui.WindowOverview;

import java.util.List;

/**
 * Vanilla-UI-Integration — provides economy data injection points for vanilla windows.
 *
 * <p>Integration-Punkte (V71.44):</p>
 * <ul>
 *   <li>Top-Panel Button — via hotkey (Numpad+) since UIPanelTopSett is not accessible</li>
 *   <li>UITreasury Extension — injectTreasuryHoverInfo() for hover data</li>
 *   <li>UICitizens Extension — injectCitizensHoverInfo() for wallet/firms/gini/loyalty/housing</li>
 *   <li>UIGoods Extension — injectGoodsHoverInfo() for flow-prices/scarcity/stockpiles/import-export</li>
 * </ul>
 *
 * <p>All data flows through {@link EngineMirror} — no duplicate data sources.</p>
 */
public final class VanillaUIIntegration {

    private VanillaUIIntegration() {}

    private static boolean initialized = false;

    /**
     * Initializes the vanilla UI integration.
     * Called once from {@link vannon.syx.economy.core.MainScript#initBeforeGameInited()}.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // Top-panel button is not feasible — UIPanelTopSett is not accessible.
        // Hotkey (Numpad+) is handled by InstanceScript.pollHotkeys() instead.
        LoggingAdapter.csvTrace("INTEGRATION", "INIT", LoggingAdapter.Severity.INFO,
                "vanilla_ui_init", "complete", "hotkey-only-mode");
    }

    // ─── Treasury Hover Injection ────────────────────────────────────────

    /**
     * Injects economy data into UITreasury.hoverInfoGet at runtime.
     * Call from vanilla window's hoverInfoGet via reflection hook.
     */
    public static void injectTreasuryHoverInfo(GUI_BOX box) {
        GBox b = (GBox) box;
        var api = EngineMirror.api();
        if (api == null || api.treasury() == null || !api.treasury().isAvailable()) return;

        var treasury = api.treasury();
        long credits = (long) treasury.getPlayerCredits();
        double income = treasury.getPlayerDailyIncome();
        double expenses = treasury.getPlayerDailyExpenses();
        double net = income - expenses;

        b.text("\u2500 Econ-Mod \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        b.NL();
        b.textLL("Staatskasse:");
        b.tab(6);
        b.add(GFORMAT.i(b.text(), credits));
        b.NL();

        b.textLL("T\u00e4gliches Einkommen:");
        b.tab(6);
        b.add(GFORMAT.i(b.text(), (long) income));
        b.add(UI.icons().s.arrowUp, GCOLOR.UI().GOOD.normal);
        b.NL();

        b.textLL("T\u00e4gliche Ausgaben:");
        b.tab(6);
        b.add(GFORMAT.i(b.text(), (long) expenses));
        b.add(UI.icons().s.arrowDown, GCOLOR.UI().BAD.normal);
        b.NL();

        COLOR netColor = net >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal;
        b.textLL("Nettosaldo/Tag:");
        b.tab(6);
        b.add(GFORMAT.i(b.text(), (long) net));
        b.add(UI.icons().l.coin, netColor);
        b.NL();

        // Tax info
        double taxRev = treasury.getLastTaxRevenue();
        if (taxRev > 0) {
            b.textLL("Steuereinnahmen (letztes):");
            b.tab(6);
            b.add(GFORMAT.i(b.text(), (long) taxRev));
            b.NL();
        }

        // Crisis tier
        int crisisTier = treasury.getCrisisTier();
        if (crisisTier > 0) {
            b.sep();
            b.text("KRISIENSTUFE " + crisisTier + "/5");
            if (crisisTier >= 3) {
                b.add(UI.icons().s.cancel, GCOLOR.UI().BAD.normal);
            } else {
                b.add(UI.icons().s.arrow_right, GCOLOR.UI().SOSO.normal);
            }
            b.NL(2);
        }
    }

    // ─── Citizens Hover Injection ────────────────────────────────────────

    /**
     * Injects economy data into UICitizens hoverInfoGet at runtime.
     */
    public static void injectCitizensHoverInfo(GUI_BOX box, HCLASS cl) {
        GBox b = (GBox) box;
        var api = EngineMirror.api();
        if (api == null || api.population() == null || !api.population().isAvailable()) return;

        var pop = api.population();

        b.text("\u2500 Econ-Mod \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        b.NL();

        // Wallet stats per class
        if (cl != null) {
            double avgWallet = pop.getAvgWallet(cl, null);
            double medianWallet = pop.getMedianWallet(cl, null);
            double gini = pop.getGini(cl, null);

            if (avgWallet > 0 || medianWallet > 0) {
                b.textLL("\u2300 Geldb\u00f6rse:");
                b.tab(6);
                b.add(GFORMAT.i(b.text(), (long) avgWallet));
                b.add(UI.icons().l.coin);
                b.NL();

                b.textLL("Median Geldb\u00f6rse:");
                b.tab(6);
                b.add(GFORMAT.i(b.text(), (long) medianWallet));
                b.add(UI.icons().l.coin);
                b.NL();

                b.textLL("Gini-Koeffizient:");
                b.tab(6);
                GText gt = b.text();
                gt.set(String.format("%.3f", gini));
                if (gini > 0.40) gt.color(GCOLOR.UI().BAD.normal);
                else if (gini > 0.35) gt.color(GCOLOR.UI().SOSO.normal);
                else gt.color(GCOLOR.UI().GOOD.normal);
                b.NL();
            }
        }

        // Housing
        int housingCap = pop.getHousingCapacity(cl);
        int housingUsed = pop.getHousingUsed(cl);
        int housingFree = pop.getHousingFree(cl);
        int homeless = pop.getHomeless(cl, null);

        if (housingCap > 0) {
            b.sep();
            b.textLL("Wohnraum:");
            b.NL();
            b.text("  Kapazit\u00e4t: " + housingCap);
            b.NL();
            b.text("  Belegt: " + housingUsed);
            b.NL();
            b.text("  Frei: " + housingFree);
            b.NL();
            if (homeless > 0) {
                b.text("  Obdachlos: " + homeless);
                b.add(UI.icons().s.cancel, GCOLOR.UI().BAD.normal);
                b.NL();
            }
        }

        // Loyalty
        if (cl != null) {
            double loyalty = pop.getLoyalty(cl);
            double targetLoyalty = pop.getTargetLoyalty(cl);
            b.sep();
            b.textLL("Loyalit\u00e4t:");
            b.tab(6);
            GText lt = b.text();
            lt.set(String.format("%.1f%%", loyalty * 100));
            if (loyalty < 0.3) lt.color(GCOLOR.UI().BAD.normal);
            else if (loyalty < 0.5) lt.color(GCOLOR.UI().SOSO.normal);
            else lt.color(GCOLOR.UI().GOOD.normal);
            b.NL();
            b.textLL("Ziel-Loyalit\u00e4t:");
            b.tab(6);
            b.add(GFORMAT.perc(b.text(), targetLoyalty));
            b.NL();
        }

        // Firms (Mod-specific via EconomySim)
        var sim = EconomySim.active();
        if (sim != null && sim.firmLedger() != null && sim.firmLedger().firmFinancialSnapshots() != null) {
            List<FirmLedger.FirmFinancialSnapshot> firms = sim.firmLedger().firmFinancialSnapshots();
            int firmCount = firms.size();
            if (firmCount > 0) {
                b.sep();
                b.textLL("Produktionsbetriebe: " + firmCount);
                b.NL();
                long profitable = firms.stream()
                        .filter(f -> f.profitPerDay() > 0)
                        .count();
                b.text("  Davon profitabel: " + profitable);
                b.NL();
            }
        }
    }

    // ─── Goods Hover Injection ───────────────────────────────────────────

    /**
     * Injects economy data into UIGoods.hoverInfoGet at runtime.
     */
    public static void injectGoodsHoverInfo(GUI_BOX box, TRADABLE tradable) {
        GBox b = (GBox) box;
        var api = EngineMirror.api();
        if (api == null || api.goods() == null || !api.goods().isAvailable()) return;

        var goods = api.goods();
        if (tradable == null) return;

        b.text("\u2500 Econ-Mod \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
        b.NL();

        // Prices
        int worldPrice = goods.getWorldPrice(tradable);
        double localPrice = goods.getLocalPrice(tradable);
        int anchorPrice = goods.getAnchorPrice(tradable);
        double scarcity = goods.getScarcityMultiplier(tradable);
        double coverage = goods.getEffectiveCoverage(tradable);
        boolean capped = goods.isPriceCapped(tradable);

        b.textLL("Weltmarktpreis:");
        b.tab(6);
        b.add(GFORMAT.i(b.text(), worldPrice));
        b.add(UI.icons().l.coin);
        b.NL();

        if (localPrice != worldPrice) {
            b.textLL("Ortspreis:");
            b.tab(6);
            b.add(GFORMAT.f(b.text(), localPrice));
            b.add(UI.icons().l.coin);
            b.NL();
        }

        b.textLL("Ankerpreis:");
        b.tab(6);
        b.add(GFORMAT.i(b.text(), anchorPrice));
        b.add(UI.icons().l.coin);
        b.NL();

        // Scarcity with color
        b.textLL("Knappheit:");
        b.tab(6);
        GText sc = b.text();
        sc.set(String.format("%.0f%%", scarcity * 100));
        if (scarcity > 0.7) sc.color(GCOLOR.UI().BAD.normal);
        else if (scarcity > 0.3) sc.color(GCOLOR.UI().SOSO.normal);
        else sc.color(GCOLOR.UI().GOOD.normal);
        b.NL();

        // Coverage
        b.textLL("Versorgungsgrad:");
        b.tab(6);
        GText cov = b.text();
        cov.set(String.format("%.0f%%", coverage * 100));
        if (coverage < 0.5) cov.color(GCOLOR.UI().BAD.normal);
        else if (coverage < 0.8) cov.color(GCOLOR.UI().SOSO.normal);
        else cov.color(GCOLOR.UI().GOOD.normal);
        b.NL();

        if (capped) {
            b.textLL("Preisdeckel AKTIV");
            b.add(UI.icons().s.cancel, GCOLOR.UI().BAD.normal);
            b.NL();
        }

        // Production/Consumption
        double prod = goods.getDailyProduction(tradable);
        double cons = goods.getDailyConsumption(tradable);
        double netFlow = prod - cons;

        b.sep();
        b.textLL("Produktion/Tag:");
        b.tab(6);
        b.add(GFORMAT.f(b.text(), prod));
        b.NL();

        b.textLL("Verbrauch/Tag:");
        b.tab(6);
        b.add(GFORMAT.f(b.text(), cons));
        b.NL();

        COLOR netColor = netFlow >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal;
        b.textLL("Netto-Fluss:");
        b.tab(6);
        GText netT = b.text();
        netT.set((netFlow >= 0 ? "+" : "") + GFORMAT.f(new GText(UI.FONT().S, 64), netFlow));
        netT.color(netColor);
        b.NL();

        // Stockpiles — check if the tradable is a RESOURCE by name matching
        // (Cannot use instanceof due to TRADABLE/RESOURCE type hierarchy in V71)
        RESOURCE res = asResource(tradable);
        if (res != null) {
            long stock = goods.getTotalStockpileAmount(res);
            long playerStock = goods.getPlayerStockpileAmount(tradable);
            b.sep();
            b.textLL("Gesamtlager:");
            b.tab(6);
            b.add(GFORMAT.i(b.text(), stock));
            b.NL();
            b.textLL("Staatslager:");
            b.tab(6);
            b.add(GFORMAT.i(b.text(), playerStock));
            b.NL();
        }

        // Import/Export
        if (goods.isImporting(tradable)) {
            b.sep();
            b.textLL("Import AKTIV");
            b.add(UI.icons().s.arrow_right, GCOLOR.UI().GOOD.normal);
            b.NL();
            int impLimit = goods.getImportLimit(tradable);
            if (impLimit > 0) {
                b.text("Limit: " + impLimit);
                b.NL();
            }
        }
        if (goods.isExporting(tradable)) {
            b.sep();
            b.textLL("Export AKTIV");
            b.add(UI.icons().s.arrow_left, GCOLOR.UI().SOSO.normal);
            b.NL();
            int expLimit = goods.getExportLimit(tradable);
            if (expLimit > 0) {
                b.text("Limit: " + expLimit);
                b.NL();
            }
        }
    }

    /**
     * Attempts to cast a TRADABLE to RESOURCE via engine API lookup.
     * Returns null if the tradable is not a resource type.
     */
    private static RESOURCE asResource(TRADABLE tradable) {
        if (tradable == null) return null;
        try {
            // Check if the tradable's key matches any known RESOURCE
            String key = tradable.key();
            if (key == null) return null;
            for (int i = 0; i < init.resources.RESOURCES.ALL().size(); i++) {
                RESOURCE r = init.resources.RESOURCES.ALL().get(i);
                if (key.equals(r.key)) return r;
            }
        } catch (Throwable t) {
            // Graceful degradation
        }
        return null;
    }
}
