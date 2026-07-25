package vannon.syx.economy.ui;

import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import util.colors.GCOLOR;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.FlowPrices;
import snake2d.util.color.COLOR;

/**
 * Wirtschaft-Fenster: Maerkte, Preise, Betriebe.
 */
public final class WindowEconomy extends EconWindowBase {

    private static final TabContent[] TABS = {
        new MarketsTab(),
        new PricesTab(),
        new FirmsTab(),
        new WagesTab(),
        new SubsidiesTab()
    };

    public WindowEconomy(EconomySim sim) {
        super(sim);
    }

    @Override
    protected CharSequence title() {
        return "Wirtschaft";
    }

    @Override
    protected int panelWidth() { return 780; }

    @Override
    protected TabContent[] tabs() { return TABS; }

    // ─── Tab 1: Markets ──────────────────────────────────────────────

    private static final class MarketsTab implements TabContent {
        @Override public CharSequence title() { return "Markt"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            boolean hasPop = sim.stats().people > 0;

            // Labor market overview
            GText laborHeader = new GText(UI.FONT().M, 256);
            laborHeader.set("--- Arbeitsmarkt ---");
            laborHeader.lablify();
            content.add(laborHeader, x, y);
            y += 28;

            addKpi(content, x, y, "Ø-Lohn",
                CompactNumber.format((long)sim.laborMarket().meanWage()) + " D/Tag",
                !hasPop ? GCOLOR.T().INACTIVE : GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Marginal",
                CompactNumber.format((long)sim.firmLedger().meanPositiveMarginal()) + " D", GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Lohnsumme/Tag",
                CompactNumber.format(sim.wagesPaid()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Unbezahlte",
                String.valueOf(sim.firmLedger().lastWorkersUnpaid()),
                sim.firmLedger().lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
            y += 50;

            // Treasury & Fiscal
            GText fiscalHeader = new GText(UI.FONT().M, 256);
            fiscalHeader.set("--- Finanzen ---");
            fiscalHeader.lablify();
            content.add(fiscalHeader, x, y);
            y += 28;

            addKpi(content, x, y, "Staatskasse",
                CompactNumber.format(sim.treasury()) + " D",
                sim.treasury() >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            addKpi(content, x + 380, y, "Umlauf",
                CompactNumber.format(sim.wallets().circulating()) + " D", GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Kopfsteuer",
                CompactNumber.format(sim.fiscal().headTaxCollected()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Marktsteuer",
                CompactNumber.format(sim.fiscal().marketReceipts()) + " D", GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Rationen",
                CompactNumber.format(sim.fiscal().rationOut()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Lagersteuer",
                CompactNumber.format(sim.warehouseMarket().lastTaxed()) + " D", GCOLOR.T().NORMAL);
            y += 50;

            // Market activity
            GText marketHeader = new GText(UI.FONT().M, 256);
            marketHeader.set("--- Marktaktivität ---");
            marketHeader.lablify();
            content.add(marketHeader, x, y);
            y += 28;

            addKpi(content, x, y, "Gekauft",
                sim.warehouseMarket().lastUnitsBought() + " Einh. / " + CompactNumber.format(sim.warehouseMarket().lastBought()) + " D", GCOLOR.T().NORMAL);
            y += 30;
            addKpi(content, x, y, "Verkauft",
                sim.warehouseMarket().lastUnitsSold() + " Einh. / " + CompactNumber.format(sim.warehouseMarket().lastSold()) + " D", GCOLOR.T().NORMAL);
            y += 30;
            addKpi(content, x, y, "Baustoffe",
                CompactNumber.format(sim.warehouseMarket().lastConstructionPaid()) + " D", GCOLOR.T().NORMAL);
            y += 30;
            addKpi(content, x, y, "Export",
                CompactNumber.format(sim.warehouseMarket().lastExportBought()) + " D", GCOLOR.T().NORMAL);
        }
    }

    // ─── Tab 2: Prices ───────────────────────────────────────────────

    private static final class PricesTab implements TabContent {
        @Override public CharSequence title() { return "Preise"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            FlowPrices fp = sim.flowPrices();

            GText header = new GText(UI.FONT().M, 512);
            header.set("Lokale Verrechnungspreise. Deckung: 1.0 = Ziel, <1 = Mangel, >1 = Überschuss.");
            header.color(GCOLOR.T().INACTIVE);
            content.add(header, x, y);
            y += 24;

            addColHeader(content, x, y, "Ressource", 120);
            addColHeader(content, x + 130, y, "Lokal/E", 80);
            addColHeader(content, x + 220, y, "Anker", 80);
            addColHeader(content, x + 310, y, "Faktor", 60);
            addColHeader(content, x + 380, y, "Deckung", 70);
            addColHeader(content, x + 460, y, "Status", 80);
            y += 20;

            // Resource rows
            if (fp.ready()) {
                int rows = Math.min(RESOURCES.ALL().size(), (h - 60) / 16);
                for (int i = 0; i < rows; i++) {
                    RESOURCE r = (RESOURCE) RESOURCES.ALL().get(i);
                    double local = fp.price(i);
                    double anchor = fp.anchor(i);
                    double coverage = fp.coverage(i);
                    double factor = anchor > 0 ? local / anchor : 0;

                    // Resource name
                    GText name = new GText(UI.FONT().S, 128);
                    name.set(r.key);
                    name.color(GCOLOR.T().NORMAL);
                    content.add(name, x, y);

                    GText localT = new GText(UI.FONT().S, 64);
                    localT.set(String.format("%.1f", local));
                    localT.color(GCOLOR.T().NORMAL);
                    content.add(localT, x + 130, y);

                    GText anchorT = new GText(UI.FONT().S, 64);
                    anchorT.set(String.format("%.1f", anchor));
                    anchorT.color(GCOLOR.T().NORMAL);
                    content.add(anchorT, x + 220, y);

                    GText factorT = new GText(UI.FONT().S, 64);
                    factorT.set(String.format("%.1fx", factor));
                    factorT.color(factor > 10 ? GCOLOR.UI().BAD.normal : factor > 3 ? GCOLOR.UI().SOSO.normal : GCOLOR.T().NORMAL);
                    content.add(factorT, x + 310, y);

                    // Coverage
                    GText covT = new GText(UI.FONT().S, 64);
                    covT.set(String.format("%.2f", coverage));
                    covT.color(coverage < 0.5 ? GCOLOR.UI().BAD.normal : coverage < 1.0 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
                    content.add(covT, x + 380, y);

                    // Status indicator
                    String status;
                    COLOR statusColor;
                    if (coverage < 0.3) { status = "MANGL"; statusColor = GCOLOR.UI().BAD.normal; }
                    else if (coverage < 0.7) { status = "knapp"; statusColor = GCOLOR.UI().SOSO.normal; }
                    else if (coverage > 3.0) { status = "UEBERSCH."; statusColor = GCOLOR.UI().GOOD.normal; }
                    else { status = "ok"; statusColor = GCOLOR.UI().GOOD.normal; }

                    GText statusT = new GText(UI.FONT().S, 64);
                    statusT.set(status);
                    statusT.color(statusColor);
                    content.add(statusT, x + 460, y);

                    y += 16;
                }
            } else {
                GText noData = new GText(UI.FONT().M, 128);
                noData.set("Preise noch nicht initialisiert — erster Tag abwarten.");
                noData.color(GCOLOR.T().INACTIVE);
                content.add(noData, x, y);
                y += 24;

                GText noDataInfo = new GText(UI.FONT().S, 256);
                noDataInfo.set("Dieser Tab wird ab Spieltag 2 automatisch befuellt.");
                noDataInfo.color(GCOLOR.T().INACTIVE);
                content.add(noDataInfo, x, y);
            }
        }
    }

    // ─── Tab 3: Firms ────────────────────────────────────────────────

    private static final class FirmsTab implements TabContent {
        @Override public CharSequence title() { return "Firmen"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            FirmLedger ledger = sim.firmLedger();

            GText header = new GText(UI.FONT().M, 512);
            header.set("--- Betriebsgewinn & Input/Output ---");
            header.lablify();
            content.add(header, x, y);
            y += 28;

            addKpi(content, x, y, "Einnahmen",
                CompactNumber.format(ledger.lastIncomeDue()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Bezahlt",
                CompactNumber.format(ledger.lastIncomePaid()) + " D", GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Arbeiter bezahlt",
                String.valueOf(ledger.lastWorkersPaid()), GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 380, y, "Unbezahlt",
                String.valueOf(ledger.lastWorkersUnpaid()),
                ledger.lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
            y += 50;

            addColHeader(content, x, y, "Betrieb", 140);
            addColHeader(content, x + 150, y, "Arb", 40);
            addColHeader(content, x + 200, y, "Ziel", 40);
            addColHeader(content, x + 250, y, "Profit/d", 80);
            addColHeader(content, x + 340, y, "Marginal", 80);
            addColHeader(content, x + 430, y, "Unpaid", 50);
            y += 20;

            // Firm rows
            java.util.List<FirmLedger.FirmFinancialSnapshot> firms = ledger.firmFinancialSnapshots();
            int maxRows = Math.min(firms.size(), (h - 120) / 16);
            for (int i = 0; i < maxRows; i++) {
                FirmLedger.FirmFinancialSnapshot f = firms.get(i);

                // Blueprint key (truncate)
                GText key = new GText(UI.FONT().S, 128);
                String keyStr = f.blueprint();
                if (keyStr != null && keyStr.length() > 18) keyStr = keyStr.substring(0, 18);
                key.set(keyStr);
                key.color(GCOLOR.T().NORMAL);
                content.add(key, x, y);

                GText emp = new GText(UI.FONT().S, 32);
                emp.set(String.valueOf(f.employees()));
                emp.color(GCOLOR.T().NORMAL);
                content.add(emp, x + 150, y);

                GText tgt = new GText(UI.FONT().S, 32);
                tgt.set(String.valueOf(f.employedTarget()));
                tgt.color(GCOLOR.T().NORMAL);
                content.add(tgt, x + 200, y);

                GText profit = new GText(UI.FONT().S, 64);
                profit.set(String.format("%.1f", f.profitPerDay()));
                profit.color(f.profitPerDay() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
                content.add(profit, x + 250, y);

                GText marginal = new GText(UI.FONT().S, 64);
                marginal.set(String.format("%.1f", f.marginalPerWorker()));
                marginal.color(f.marginalPerWorker() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
                content.add(marginal, x + 340, y);

                GText unpaid = new GText(UI.FONT().S, 32);
                unpaid.set(String.valueOf(f.workersUnpaid()));
                unpaid.color(f.workersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
                content.add(unpaid, x + 430, y);

                y += 16;
            }

            if (firms.isEmpty()) {
                GText noFirms = new GText(UI.FONT().M, 128);
                noFirms.set("Keine Betriebe erfasst.");
                noFirms.color(GCOLOR.T().INACTIVE);
                content.add(noFirms, x, y);
            }
        }
    }

    // ─── Tab 4: Wages ────────────────────────────────────────────────

    private static final class WagesTab implements TabContent {
        @Override public CharSequence title() { return "Lohn"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            FirmLedger ledger = sim.firmLedger();

            GText header = new GText(UI.FONT().M, 256);
            header.set("--- Arbeiterbezahlung ---");
            header.lablify();
            content.add(header, x, y);
            y += 28;

            addKpi(content, x, y, "Bezahlt",
                String.valueOf(ledger.lastWorkersPaid()), GCOLOR.UI().GOOD.normal);
            addKpi(content, x + 380, y, "Unbezahlt",
                String.valueOf(ledger.lastWorkersUnpaid()),
                ledger.lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
            y += 40;

            addKpi(content, x, y, "Tagelohn",
                CompactNumber.format((long)sim.laborMarket().meanWage()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Lohnsumme/Tag",
                CompactNumber.format(sim.wagesPaid()) + " D", GCOLOR.T().NORMAL);
            y += 50;

            GText finHeader = new GText(UI.FONT().M, 256);
            finHeader.set("--- Firmen-Einkommen ---");
            finHeader.lablify();
            content.add(finHeader, x, y);
            y += 28;

            addKpi(content, x, y, "Einnahmen",
                CompactNumber.format(ledger.lastIncomeDue()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Bezahlt",
                CompactNumber.format(ledger.lastIncomePaid()) + " D",
                ledger.lastIncomePaid() >= ledger.lastIncomeDue() ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().SOSO.normal);
        }
    }

    // ─── Tab 5: Subsidies ────────────────────────────────────────────

    private static final class SubsidiesTab implements TabContent {
        @Override public CharSequence title() { return "Hilfen"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText header = new GText(UI.FONT().M, 512);
            header.set("Subventionen: Der Staat zahlt pro produzierter Einheit.");
            header.color(GCOLOR.T().INACTIVE);
            content.add(header, x, y);
            y += 24;

            int subsidized = 0;
            int maxRows = Math.min(RESOURCES.ALL().size(), (h - 40) / 14);
            for (int i = 0; i < maxRows; i++) {
                RESOURCE r = (RESOURCE) RESOURCES.ALL().get(i);
                int bounty = sim.productionSubsidies().bounty(r);
                if (bounty > 0) subsidized++;

                GText name = new GText(UI.FONT().S, 128);
                name.set(r.key);
                name.color(bounty > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                content.add(name, x, y);

                GText bountyText = new GText(UI.FONT().S, 64);
                bountyText.set(CompactNumber.format(bounty) + " D/Einheit");
                bountyText.color(bounty > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                content.add(bountyText, x + 160, y);

                GText status = new GText(UI.FONT().S, 64);
                status.set(bounty > 0 ? "subventioniert" : "—");
                status.color(bounty > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                content.add(status, x + 350, y);

                y += 14;
            }

            y += 10;
            GText summary = new GText(UI.FONT().M, 256);
            summary.set(subsidized + " von " + RESOURCES.ALL().size() + " Ressourcen subventioniert.");
            summary.color(subsidized > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(summary, x, y);
        }
    }
}
