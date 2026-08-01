package vannon.syx.economy.ui;

import init.constant.C;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import init.sprite.UI.UI;
import snake2d.SPRITE_RENDERER;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.EconSnapshot;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.FlowPrices;
import snake2d.util.color.COLOR;
import vannon.syx.economy.core.EconConfig;

/**
 * Wirtschaft-Fenster: Maerkte, Preise, Betriebe.
 */
public final class WindowEconomy extends EconWindowBase {

    /** Sprint v0.13.104+M-UI-1: TABS jetzt instance-allocated (war static), damit
     *  PricesTab eine per-Instanz rebuild-Referenz (close+toggle) für Filter-Chips
     *  mitbekommt. Single-Window-pro-Session, also keine Extra-Kosten. */
    private final TabContent[] TABS;

    public WindowEconomy(EconomySim sim) {
        super(sim);
        TABS = new TabContent[]{
            new MarketsTab(),
            // PricesTab captures window-ref for filter rebuild (Sprint M-UI-1).
            // Runnable.run() triggert Close+Toggle ohne Return-Wert.
            new PricesTab(() -> { this.close(); this.toggle(); }),
            new FirmsTab(),
            new WagesTab(),
            new SubsidiesTab(),
            new BooksTab()
        };
    }

    @Override
    protected CharSequence title() {
        return "Wirtschaft";
    }

    @Override
    protected int panelWidth() { return 840; }

    @Override
    protected int anchorX() {
        return C.WIDTH() - panelWidth() - 8; // top-right justified horizontal
    }

    @Override
    protected int anchorY() {
        // RES-UI-MINIMAP: anschliessend an typische Songs-of-Syx-Minimap-Zone
        // 252x252 oben rechts. anchorX ist WIDTH-848 (rechtsbuendig), aber
        // der Y-Anker muss unter die Minimap rutschen, sonst verdeckt die
        // Wirtschaft-Tafel die Minimap (U-IM-01).
        return 296; // 252 Minimap + 28 Puffer + 16 Sicherheitsabstand zu UIPanelTop
    }

    @Override
    protected TabContent[] tabs() { return TABS; }

    // ─── Tab 1: Markets ──────────────────────────────────────────────

    private static final class MarketsTab implements TabContent {
        @Override public CharSequence title() { return "Markt"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            boolean hasPop = sim.stats().people > 0;

            // Labor market overview
            GText laborHeader = new GText(UI.FONT().M, FONTW_HDR);
            laborHeader.set("--- Arbeitsmarkt ---");
            laborHeader.lablify();
            content.add(laborHeader, x, y);
            y += 28;

            addKpi(content, x, y, UI.icons().m.pickaxe, "Ø-Lohn",
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
            GText fiscalHeader = new GText(UI.FONT().M, FONTW_HDR);
            fiscalHeader.set("--- Finanzen ---");
            fiscalHeader.lablify();
            content.add(fiscalHeader, x, y);
            y += 28;

            addKpi(content, x, y, UI.icons().m.coins, "Staatskasse",
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
            GText marketHeader = new GText(UI.FONT().M, FONTW_HDR);
            marketHeader.set("--- Marktaktivität ---");
            marketHeader.lablify();
            content.add(marketHeader, x, y);
            y += 28;

            addKpi(content, x, y, "Gekauft",
                sim.warehouseMarket().lastUnitsBought() + " Einh. / " +                CompactNumber.format(sim.warehouseMarket().lastBought()) + " D", GCOLOR.T().NORMAL);
            y += 38;
            addKpi(content, x, y, "Verkauft",
                sim.warehouseMarket().lastUnitsSold() + " Einh. / " +                CompactNumber.format(sim.warehouseMarket().lastSold()) + " D", GCOLOR.T().NORMAL);
            y += 38;
            addKpi(content, x, y, "Baustoffe",
                CompactNumber.format(sim.warehouseMarket().lastConstructionPaid()) + " D", GCOLOR.T().NORMAL);
            y += 38;
            addKpi(content, x, y, "Export",
                CompactNumber.format(sim.warehouseMarket().lastExportBought()) + " D", GCOLOR.T().NORMAL);
        }
    }

    // ─── Tab 2: Prices ───────────────────────────────────────────────

    private static final class PricesTab implements TabContent {
        @Override public CharSequence title() { return "Preise"; }

        // Sprint v0.13.104+M-UI-1 — Severity-Klassifikation + Filter-Modus
        // ----------------
        // Persistent static filter-state: 0=Alle, 1=Mangel+Knapp (default),
        // 2=Überschuss, 3=Nur Mangel. Spieler sieht zuerst die kritischen
        // Ressourcen, kann chip-umschalten, bekommt CSV-Spalten mit Severity.
        private static int currentFilter = 1;
        /** Per-Instance: caller invokes close+toggle rebuild after filter change.
         *  Sprint M-UI-1 Review-Fix: Runnable statt Supplier<Boolean> — der
         *  Boolean-Return war dead (Lambda-Block wirft nichts, Wert wurde verworfen).
         *  Runnable.run() ist semantisch sauberer und spart Boxing. */
        private final Runnable rebuildTrigger;

        PricesTab(Runnable rebuildTrigger) {
            this.rebuildTrigger = rebuildTrigger;
        }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            FlowPrices fp = sim.flowPrices();

            GText header = new GText(UI.FONT().M, FONTW_BODY);
            header.set("Lokale Verrechnungspreise. Deckung: 1.0 = Ziel, <1 = Mangel, >1 = Überschuss.");
            header.color(GCOLOR.T().NORMAL);
            content.add(header, x, y);
            y += 24;

            // ── Empty-State-Kurzschluss (Sprint v0.13.104+M-UI-1) ─────
            if (!fp.ready()) {
                GText noData = new GText(UI.FONT().M, FONTW_KPI);
                noData.set("Preise noch nicht initialisiert — erster Tag abwarten.");
                noData.color(GCOLOR.T().INACTIVE);
                content.add(noData, x, y);
                GText noDataInfo = new GText(UI.FONT().S, FONTW_HDR);
                noDataInfo.set("Dieser Tab wird ab Spieltag 2 automatisch befüllt.");
                noDataInfo.color(GCOLOR.T().INACTIVE);
                content.add(noDataInfo, x, y + 24);
                return;
            }

            // ── Filter-Chip-Bar (Sprint v0.13.104+M-UI-1) ─────────────
            // Sprint M-UI-1 fügt 4 Chips für Severity-Filter hinzu. Default
            // PROBLEM_ONLY zeigt dem Spieler zuerst Mangel+Knapp — kritischste
            // Ressourcen zuerst. Severity-Sort (coverage ASC) gibt die 25-Reihen-
            // Grenze einen spielerischen Sinn: die 25 wichtigsten Ressourcen.
            int chipX = x;
            for (KpiSection.FilterMode mode : KpiSection.FilterMode.values()) {
                final int targetOrdinal = mode.ordinal();
                GButt.ButtPanel chip = new GButt.ButtPanel(mode.chipLabel(), 110) {
                    @Override protected void render(SPRITE_RENDERER r, float ds,
                                                      boolean isActive, boolean isSelected, boolean isHovered) {
                        selectedSet(currentFilter == targetOrdinal);
                        super.render(r, ds, isActive, isSelected, isHovered);
                    }
                };
                chip.clickActionSet(new ACTION() {
                    @Override public void exe() {
                        if (currentFilter == targetOrdinal) return;
                        DiagnosticExporter.logPlayerAction("prices.filter",
                                KpiSection.FilterMode.values()[targetOrdinal].chipLabel());
                        currentFilter = targetOrdinal;
                        if (rebuildTrigger != null) rebuildTrigger.run();
                    }
                });
                content.add(chip, chipX, y);
                chipX += 115;
            }
            y += 28;

            addColHeader(content, x, y, "Ressource", 100);
            addColHeader(content, x + 110, y, "Lokal", 55);
            addColHeader(content, x + 175, y, "Anker", 55);
            addColHeader(content, x + 240, y, "Faktor", 45);
            addColHeader(content, x + 295, y, "Deckung", 50);
            addColHeader(content, x + 355, y, "Bestand", 55);
            addColHeader(content, x + 420, y, "+Tag", 55);
            addColHeader(content, x + 485, y, "-Tag", 55);
            addColHeader(content, x + 550, y, "Status", 55);
            y += 20;

            // ── Severity-Sort + Filter (Sprint v0.13.104+M-UI-1) ───────
            // Coverage-ASC-Sort priorisiert Mangel. Filter-Mode entscheidet
            // welche Severity-Klassen sichtbar sind. Color-For-Severity
            // ersetzt die inline Color-Triples (DRY-Konsolidierung).
            int n = RESOURCES.ALL().size();
            int[] sorted = KpiSection.sortIndicesByCoverageAsc(fp, n);
            EconSnapshot snap = sim.econIndicators().latest();
            KpiSection.FilterMode activeMode = KpiSection.FilterMode.values()[currentFilter];
            int maxRows = Math.min(sorted.length, Math.min(25, (h - 80) / 16));
            int rowCount = 0;
            for (int idx : sorted) {
                if (rowCount >= maxRows) break;
                double coverage = fp.coverage(idx);
                KpiSection.Severity sev = KpiSection.Severity.classify(coverage);
                if (!activeMode.accepts(sev)) continue;
                RESOURCE r = (RESOURCE) RESOURCES.ALL().get(idx);
                double local = fp.price(idx);
                double anchor = fp.anchor(idx);
                double factor = anchor > 0 ? local / anchor : 0;
                double stock = (snap != null && idx < snap.stock.length) ? snap.stock[idx] : 0;
                double supply = (snap != null && idx < snap.supplyPerDay.length) ? snap.supplyPerDay[idx] : 0;
                double demand = (snap != null && idx < snap.demandPerDay.length) ? snap.demandPerDay[idx] : 0;

                GText name = new GText(UI.FONT().S, FONTW_NAME);
                name.set(toDisplayName(r.key));
                name.color(GCOLOR.T().NORMAL);
                content.add(name, x, y);

                GText localT = new GText(UI.FONT().S, FONTW_CNT);
                localT.set(String.format("%.1f", local));
                localT.color(GCOLOR.T().NORMAL);
                content.add(localT, x + 110, y);

                GText anchorT = new GText(UI.FONT().S, FONTW_CNT);
                anchorT.set(String.format("%.1f", anchor));
                anchorT.color(GCOLOR.T().NORMAL);
                content.add(anchorT, x + 175, y);

                GText factorT = new GText(UI.FONT().S, FONTW_CNT);
                factorT.set(String.format("%.1fx", factor));
                factorT.color(factor > 10 ? GCOLOR.UI().BAD.normal : factor > 3 ? GCOLOR.UI().SOSO.normal : GCOLOR.T().NORMAL);
                content.add(factorT, x + 240, y);

                GText covT = new GText(UI.FONT().S, FONTW_CNT);
                covT.set(String.format("%.2f", coverage));
                covT.color(KpiSection.colorForSeverity(sev));
                content.add(covT, x + 295, y);

                GText stockT = new GText(UI.FONT().S, FONTW_CNT);
                stockT.set(CompactNumber.format((long)stock));
                stockT.color(stock > 0 ? GCOLOR.T().NORMAL : GCOLOR.UI().BAD.normal);
                content.add(stockT, x + 355, y);

                GText supplyT = new GText(UI.FONT().S, FONTW_CNT);
                supplyT.set(CompactNumber.format((long)supply));
                supplyT.color(supply > 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
                content.add(supplyT, x + 420, y);

                GText demandT = new GText(UI.FONT().S, FONTW_CNT);
                demandT.set(CompactNumber.format((long)demand));
                demandT.color(GCOLOR.T().NORMAL);
                content.add(demandT, x + 485, y);

                GText statusT = new GText(UI.FONT().S, FONTW_CNT);
                statusT.set(sev.badge());
                statusT.color(KpiSection.colorForSeverity(sev));
                content.add(statusT, x + 550, y);

                y += 16;
                rowCount++;
            }

            // Leerer-Filter-Hinweis unten (Sprint v0.13.104+M-UI-1)
            if (rowCount == 0) {
                GText empty = new GText(UI.FONT().S, FONTW_HDR);
                empty.set("Keine Ressourcen in Filter '" + activeMode.chipLabel() + "'.");
                empty.color(GCOLOR.T().INACTIVE);
                content.add(empty, x, y);
            }
        }
    }

    // ─── Tab 3: Firms ────────────────────────────────────────────────

    private static final class FirmsTab implements TabContent {
        @Override public CharSequence title() { return "Firmen"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            FirmLedger ledger = sim.firmLedger();

            GText header = new GText(UI.FONT().M, FONTW_BODY);
            header.set("--- Betriebsgewinn & Input/Output ---");
            header.lablify();
            content.add(header, x, y);
            y += 28;

            addKpi(content, x, y, UI.icons().m.coins, "Einnahmen",
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

            java.util.List<FirmLedger.FirmFinancialSnapshot> firms = ledger.firmFinancialSnapshots();

            if (!firms.isEmpty()) {
                addColHeader(content, x, y, "Betrieb", 140);
                addColHeader(content, x + 150, y, "Arb", 40);
                addColHeader(content, x + 200, y, "Ziel", 40);
                addColHeader(content, x + 250, y, "Profit/d", 80);
                addColHeader(content, x + 340, y, "Marginal", 80);
                addColHeader(content, x + 430, y, "Unpaid", 50);
                y += 20;

                int maxRows = Math.min(firms.size(), (h - 120) / 16);
                for (int i = 0; i < maxRows; i++) {
                    FirmLedger.FirmFinancialSnapshot f = firms.get(i);

                    GText key = new GText(UI.FONT().S, FONTW_KPI);
                    String keyStr = f.blueprint();
                    if (keyStr != null && keyStr.length() > 18) keyStr = keyStr.substring(0, 18);
                    key.set(keyStr);
                    key.color(GCOLOR.T().NORMAL);
                    content.add(key, x, y);

                    GText emp = new GText(UI.FONT().S, FONTW_CNT);
                    emp.set(String.valueOf(f.employees()));
                    emp.color(GCOLOR.T().NORMAL);
                    content.add(emp, x + 150, y);

                    GText tgt = new GText(UI.FONT().S, FONTW_CNT);
                    tgt.set(String.valueOf(f.employedTarget()));
                    tgt.color(GCOLOR.T().NORMAL);
                    content.add(tgt, x + 200, y);

                    GText profit = new GText(UI.FONT().S, FONTW_LABEL);
                    profit.set(String.format("%.1f", f.profitPerDay()));
                    profit.color(f.profitPerDay() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
                    content.add(profit, x + 250, y);

                    GText marginal = new GText(UI.FONT().S, FONTW_LABEL);
                    marginal.set(String.format("%.1f", f.marginalPerWorker()));
                    marginal.color(f.marginalPerWorker() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
                    content.add(marginal, x + 340, y);

                    GText unpaid = new GText(UI.FONT().S, FONTW_CNT);
                    unpaid.set(String.valueOf(f.workersUnpaid()));
                    unpaid.color(f.workersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
                    content.add(unpaid, x + 430, y);

                    y += 16;
                }
            } else {
                GText noFirms = new GText(UI.FONT().M, FONTW_KPI);
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

            GText header = new GText(UI.FONT().M, FONTW_HDR);
            header.set("--- Arbeiterbezahlung ---");
            header.lablify();
            content.add(header, x, y);
            y += 28;

            addKpi(content, x, y, "Bezahlt",
                String.valueOf(ledger.lastWorkersPaid()),
                ledger.lastWorkersPaid() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            addKpi(content, x + 380, y, "Unbezahlt",
                String.valueOf(ledger.lastWorkersUnpaid()),
                ledger.lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
            y += 40;

            addKpi(content, x, y, UI.icons().m.pickaxe, "Tagelohn",
                CompactNumber.format((long)sim.laborMarket().meanWage()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Lohnsumme/Tag",
                CompactNumber.format(sim.wagesPaid()) + " D", GCOLOR.T().NORMAL);
            y += 50;

            GText finHeader = new GText(UI.FONT().M, FONTW_HDR);
            finHeader.set("--- Firmen-Einkommen ---");
            finHeader.lablify();
            content.add(finHeader, x, y);
            y += 28;

            addKpi(content, x, y, "Einnahmen",
                CompactNumber.format(ledger.lastIncomeDue()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Bezahlt",
                CompactNumber.format(ledger.lastIncomePaid()) + " D",
                ledger.lastIncomePaid() > 0 && ledger.lastIncomePaid() >= ledger.lastIncomeDue()
                    ? GCOLOR.UI().GOOD.normal
                    : ledger.lastIncomePaid() > 0
                        ? GCOLOR.UI().SOSO.normal
                        : GCOLOR.T().INACTIVE);
        }
    }

    // ─── Tab 5: Subsidies ────────────────────────────────────────────

    private static final class SubsidiesTab implements TabContent {
        @Override public CharSequence title() { return "Hilfen"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText header = new GText(UI.FONT().M, FONTW_BODY);
            header.set("Subventionen: Der Staat zahlt pro produzierter Einheit.");
            header.color(GCOLOR.T().NORMAL);
            content.add(header, x, y);
            y += 24;

            int subsidized = 0;
            int maxRows = Math.min(RESOURCES.ALL().size(), (h - 40) / 16);
            for (int i = 0; i < maxRows; i++) {
                RESOURCE r = (RESOURCE) RESOURCES.ALL().get(i);
                int bounty = sim.productionSubsidies().bounty(r);
                if (bounty > 0) subsidized++;

                GText name = new GText(UI.FONT().S, FONTW_KPI);
                name.set(toDisplayName(r.key));  // SK-06: lesbarer Name statt Rohkey
                name.color(bounty > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                content.add(name, x, y);

                GText bountyText = new GText(UI.FONT().S, FONTW_LABEL);
                bountyText.set(CompactNumber.format(bounty) + " D/Einheit");
                bountyText.color(bounty > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                content.add(bountyText, x + 160, y);

                GText status = new GText(UI.FONT().S, FONTW_LABEL);
                status.set(bounty > 0 ? "subventioniert" : "—");
                status.color(bounty > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                content.add(status, x + 350, y);

                y += 16;
            }

            y += 10;
            GText summary = new GText(UI.FONT().M, FONTW_HDR);
            summary.set(subsidized + " von " + RESOURCES.ALL().size() + " Ressourcen subventioniert.");
            summary.color(subsidized > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(summary, x, y);
        }
    }

    // ─── Tab 6: Books (Audit) ────────────────────────────────────────

    private static final class BooksTab implements TabContent {
        @Override public CharSequence title() { return "Bücher"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            // === Geldfluss-Bilanz ===
            GText flowHdr = new GText(UI.FONT().M, FONTW_HDR);
            flowHdr.set("--- Geldfluss-Bilanz (letzte Saison) ---");
            flowHdr.lablify();
            content.add(flowHdr, x, y);
            y += 24;

            long inTotal = sim.fiscal().headTaxCollected() + sim.fiscal().marketReceipts()
                + sim.religionTaxCollected() + sim.liturgyCollected()
                + sim.housingMarket().lastRentCollected() + sim.warehouseMarket().lastSold()
                + sim.propertySalesCollected() + sim.propertyDividendsPaid();
            long outTotal = sim.wagesPaid() + sim.fiscal().rationOut()
                + sim.warehouseMarket().lastBought() + sim.warehouseMarket().lastConstructionPaid();
            long treasury = sim.treasury();

            addKpi(content, x, y, "Einnahmen gesamt", CompactNumber.format(inTotal) + " D",
                inTotal > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            addKpi(content, x + 380, y, "Ausgaben gesamt", CompactNumber.format(outTotal) + " D", GCOLOR.T().NORMAL);
            y += 40;

            addKpi(content, x, y, "Staatskasse", CompactNumber.format(treasury) + " D",
                treasury >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            addKpi(content, x + 380, y, "Saldo", CompactNumber.format(inTotal - outTotal) + " D",
                inTotal >= outTotal ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            y += 50;

            // Detail-Aufschlüsselung
            GText detailHdr = new GText(UI.FONT().M, FONTW_HDR);
            detailHdr.set("--- Einnahmen-Detail ---");
            detailHdr.lablify();
            content.add(detailHdr, x, y);
            y += 22;

            addKpi(content, x, y, "Kopfsteuer", CompactNumber.format(sim.fiscal().headTaxCollected()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Marktsteuer", CompactNumber.format(sim.fiscal().marketReceipts()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Religionssteuer", CompactNumber.format(sim.religionTaxCollected()) + " D", GCOLOR.T().NORMAL);
            y += 38;
            addKpi(content, x, y, "Liturgie", CompactNumber.format(sim.liturgyCollected()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Miete", CompactNumber.format(sim.housingMarket().lastRentCollected()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Lagerverkauf", CompactNumber.format(sim.warehouseMarket().lastSold()) + " D", GCOLOR.T().NORMAL);
            y += 38;
            addKpi(content, x, y, "Immobilien", CompactNumber.format(sim.propertySalesCollected()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Dividenden", CompactNumber.format(sim.propertyDividendsPaid()) + " D", GCOLOR.T().NORMAL);
            y += 50;

            GText outHdr = new GText(UI.FONT().M, FONTW_HDR);
            outHdr.set("--- Ausgaben-Detail ---");
            outHdr.lablify();
            content.add(outHdr, x, y);
            y += 22;

            addKpi(content, x, y, "Löhne gesamt", CompactNumber.format(sim.wagesPaid()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 240, y, "Rationen", CompactNumber.format(sim.fiscal().rationOut()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 480, y, "Lagereinkauf", CompactNumber.format(sim.warehouseMarket().lastBought()) + " D", GCOLOR.T().NORMAL);
            y += 38;
            addKpi(content, x, y, "Baustoffe", CompactNumber.format(sim.warehouseMarket().lastConstructionPaid()) + " D", GCOLOR.T().NORMAL);
            y += 40;

            // Sanity-Check
            GText sanityHdr = new GText(UI.FONT().M, FONTW_HDR);
            sanityHdr.set("--- Bücher stimmen? ---");
            sanityHdr.lablify();
            content.add(sanityHdr, x, y);
            y += 20;

            long circulating = sim.wallets().circulating();
            long pendingEst = sim.wallets().pendingEstates();
            long totalCreated = sim.seedSupply() + sim.imported() - sim.exported();
            long totalNow = treasury + circulating + pendingEst;
            long discrepancy = totalCreated - totalNow;

            addKpi(content, x, y, "Geldmenge erstellt", CompactNumber.format(totalCreated) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Geldmenge heute", CompactNumber.format(totalNow) + " D", GCOLOR.T().NORMAL);
            y += 38;

            GText sanity = new GText(UI.FONT().M, FONTW_BODY);
            sanity.set("Diskrepanz (erstellt - heute) = " + CompactNumber.format(discrepancy) + " D");
            sanity.color(Math.abs(discrepancy) < 1000 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
            content.add(sanity, x, y);
            y += 16;

            // Sprint v0.13.129+U-09 — Detail-Zeile mit neutralen Separatoren statt
            // mathematischem '+', damit "Kasse -1.8M | Umlauf 2.0M" nicht wie
            // eine Addition mit negativem Summand aussieht (widerspricht dann
            // nicht mehr dem positiven Geldmenge-heute-Wert).
            GText detail = new GText(UI.FONT().S, FONTW_BODY);
            detail.set("Komponenten: Kasse " + CompactNumber.format(treasury) + "  |  Umlauf " + CompactNumber.format(circulating)
                + "  |  Pendings " + CompactNumber.format(pendingEst));
            detail.color(GCOLOR.T().INACTIVE);
            content.add(detail, x, y);
            y += 24;

            // Zusätzliche Erklärung wenn Treasury negativ aber Geldmenge positiv
            if (treasury < 0 && totalNow > 0) {
                GText note = new GText(UI.FONT().S, FONTW_BODY);
                note.set("→ Kasse im Minus, aber Umlaufgeld bei Bürgern gleicht aus. Geldmenge insgesamt positiv.");
                note.color(GCOLOR.UI().SOSO.normal);
                content.add(note, x, y);
                y += 16;
            }

            // Event-Chronik
            java.util.List<EventLog.EventEntry> events = EventLog.getRecentEvents();
            if (events != null && !events.isEmpty()) {
                GText chronHdr = new GText(UI.FONT().M, FONTW_HDR);
                chronHdr.set("--- Wirtschafts-Chronik ---");
                chronHdr.lablify();
                content.add(chronHdr, x, y);
                y += 18;

                int shown = Math.min(events.size(), 8);
                for (int i = events.size() - shown; i < events.size() && y < 480; i++) {
                    EventLog.EventEntry e = events.get(i);
                    GText evt = new GText(UI.FONT().S, FONTW_BODY);
                    evt.set("[" + e.category + "] " + e.message + " (t=" + e.timestamp + ")");
                    evt.color(GCOLOR.T().NORMAL);
                    content.add(evt, x, y);
                    y += 16;
                }
            }
        }
    }

    /** SK-06: Wandelt Ressourcen-Rohkey (z.B. ALCO_BEER) in lesbaren Namen um. */
    private static String toDisplayName(String key) {
        if (key == null || key.isEmpty()) return key;
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}