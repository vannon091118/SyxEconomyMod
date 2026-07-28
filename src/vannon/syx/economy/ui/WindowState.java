package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.DebugTracer;
import vannon.syx.economy.core.DiagnosticExporter;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.EventLog;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.CitizenClass;
import vannon.syx.economy.core.Wallets;
import vannon.syx.economy.core.WealthStats;
import snake2d.util.color.COLOR;

public final class WindowState extends EconWindowBase {

    private static final TabContent[] TABS = {
        new WarehousesTab(),
        new FiscalTab(),
        new PublicWorksTab(),
        new SocialTab(),
        new FaithTab(),
        new DebugTab()
    };

    public WindowState(EconomySim sim) {
        super(sim);
    }

    @Override protected CharSequence title() { return "Staat"; }
    @Override protected TabContent[] tabs() {
        return TABS;
    }

    // ─── Tab 1: Warehouses ───────────────────────────────────────────

    private static final class WarehousesTab implements TabContent {
        @Override public CharSequence title() { return "Lager"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            StateWarehouses wh = sim.stateWarehouses();

            addKpi(content, x, y, UI.icons().m.admin, "Staatslager", String.valueOf(wh.ownedCount()), GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().s.trade, "Modus", wh.tradeMode().name(), GCOLOR.T().NORMAL);
            y += 50;

            GText modeHeader = new GText(UI.FONT().M, FONTW_HDR);
            modeHeader.set("Handelsmodus:");
            modeHeader.lablify();
            content.add(modeHeader, x, y);
            y += 22;

            GButt.ButtPanel normal = new GButt.ButtPanel("Normal handeln", 140) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds,
                                                  boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.NORMAL);
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            normal.clickActionSet(new ACTION() {
                @Override public void exe() { DiagnosticExporter.logPlayerAction("state.trade_mode", "NORMAL"); wh.setTradeMode(StateWarehouses.TradeMode.NORMAL); }
            });
            content.add(normal, x, y);

            GButt.ButtPanel buy = new GButt.ButtPanel("Nur einkaufen", 140) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds,
                                                  boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.BUY_ONLY);
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            buy.clickActionSet(new ACTION() {
                @Override public void exe() { DiagnosticExporter.logPlayerAction("state.trade_mode", "BUY_ONLY"); wh.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY); }
            });
            content.add(buy, x + 160, y);

            GButt.ButtPanel sell = new GButt.ButtPanel("Nur verkaufen", 140) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds,
                                                  boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.tradeMode() == StateWarehouses.TradeMode.SELL_ONLY);
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            sell.clickActionSet(new ACTION() {
                @Override public void exe() { DiagnosticExporter.logPlayerAction("state.trade_mode", "SELL_ONLY"); wh.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY); }
            });
            content.add(sell, x + 320, y);
            y += 40;

            GButt.ButtPanel std = new GButt.ButtPanel("Standardisieren (80%/110%)", 220);
            std.clickActionSet(new ACTION() {
                @Override public void exe() { DiagnosticExporter.logPlayerAction("state.standardize_prices", "80%/110%"); wh.standardizeAllPrices(sim.flowPrices()); }
            });
            content.add(std, x, y);

            GButt.ButtPanel liquidate = new GButt.ButtPanel("Not-Liquidation", 160) {
                @Override protected void render(snake2d.SPRITE_RENDERER r, float ds,
                                                  boolean isActive, boolean isSelected, boolean isHovered) {
                    selectedSet(wh.allLiquidating());
                    super.render(r, ds, isActive, isSelected, isHovered);
                }
            };
            liquidate.hoverInfoSet(wh.allLiquidating()
                ? "Liquidation laeuft — Klick: beenden (Normal-Modus)"
                : "Alle Staatslager sofort zu Geld machen (setzt Modus auf Normal)");
            liquidate.clickActionSet(new ACTION() {
                @Override public void exe() { DiagnosticExporter.logPlayerAction("state.liquidate", wh.allLiquidating() ? "stop" : "start"); wh.setAllLiquidating(!wh.allLiquidating()); }
            });
            content.add(liquidate, x + 240, y);
            y += 40;

            GText statsHeader = new GText(UI.FONT().M, FONTW_HDR);
            statsHeader.set("--- Letzte Saison ---");
            statsHeader.lablify();
            content.add(statsHeader, x, y);
            y += 24;

            addKpi(content, x, y, UI.icons().m.coins, "Gekauft",
                wh.lastUnitsBought() + " Einh. / " + CompactNumber.format(wh.lastBought()) + " D", GCOLOR.T().NORMAL);
            y += 30;
            addKpi(content, x, y, UI.icons().m.coins, "Verkauft",
                wh.lastUnitsSold() + " Einh. / " + CompactNumber.format(wh.lastSold()) + " D", GCOLOR.T().NORMAL);
            y += 30;
            addKpi(content, x, y, UI.icons().s.crown, "Kronmarkt",
                wh.lastCrownMarketUnitsSold() + " Einh. / " + CompactNumber.format(wh.lastCrownMarketSold()) + " D", GCOLOR.T().NORMAL);
            y += 40;

            GText wageHeader = new GText(UI.FONT().M, FONTW_HDR);
            wageHeader.set("--- Lagerlöhne ---");
            wageHeader.lablify();
            content.add(wageHeader, x, y);
            y += 24;

            addSlider(content, x, y, "Lohn/Arbeiter", wh::wage, 0, EconConfig.wageMax, EconConfig.wageStep,
                new ACTION() { @Override public void exe() { wh.setWage(wh.wage() + EconConfig.wageStep); } },
                new ACTION() { @Override public void exe() { wh.setWage(Math.max(0, wh.wage() - EconConfig.wageStep)); } });

            addKpi(content, x + 380, y, UI.icons().s.human, "Bezahlt",
                String.valueOf(wh.lastWorkersPaid()),
                wh.lastWorkersPaid() > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            y += 30;
            addKpi(content, x, y, UI.icons().m.coins, "Lohnsumme",
                CompactNumber.format(wh.lastWagesPaid()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().s.angry, "Unbezahlt",
                String.valueOf(wh.lastWorkersUnpaid()),
                wh.lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
        }
    }

    // ─── Tab 2: Fiscal ───────────────────────────────────────────────

    private static final class FiscalTab implements TabContent {
        @Override public CharSequence title() { return "Steuern"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText taxHeader = new GText(UI.FONT().M, FONTW_HDR);
            taxHeader.set("--- Steuern & Abgaben ---");
            taxHeader.lablify();
            content.add(taxHeader, x, y);
            y += 28;

            // Kopfsteuer slider (replaces static KPI)
            addSlider(content, x, y, "Kopfsteuer/Saison", () -> EconConfig.perHeadTax, 0, 500, 5,
                new ACTION() { @Override public void exe() { int old = EconConfig.perHeadTax; EconConfig.perHeadTax = Math.min(500, EconConfig.perHeadTax + 5); DiagnosticExporter.logConfigChange("perHeadTax", old, EconConfig.perHeadTax); } },
                new ACTION() { @Override public void exe() { int old = EconConfig.perHeadTax; EconConfig.perHeadTax = Math.max(0, EconConfig.perHeadTax - 5); DiagnosticExporter.logConfigChange("perHeadTax", old, EconConfig.perHeadTax); } });
            addKpi(content, x + 380, y, UI.icons().s.shield, "Freigrenze",
                EconConfig.perHeadTaxExemptionThreshold + " D", GCOLOR.T().NORMAL);
            y += 38;

            addKpi(content, x, y, UI.icons().s.trade, "Marktsteuer",
                String.format("%.1f%%", EconConfig.marketTaxRate * 100), GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().s.storage, "Rücklagen",
                EconConfig.warehouseTaxPercent + "%", GCOLOR.T().NORMAL);
            y += 40;

            GText collHeader = new GText(UI.FONT().M, FONTW_HDR);
            collHeader.set("--- Einnahmen ---");
            collHeader.lablify();
            content.add(collHeader, x, y);
            y += 24;

            addKpi(content, x, y, UI.icons().m.law, "Kopfsteuer",
                CompactNumber.format(sim.fiscal().headTaxCollected()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().s.trade, "Marktsteuer",
                CompactNumber.format(sim.fiscal().marketReceipts()) + " D", GCOLOR.T().NORMAL);
            y += 30;

            addKpi(content, x, y, UI.icons().s.temple, "Religionssteuer",
                CompactNumber.format(sim.religionTaxCollected()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().s.shrine, "Liturgie",
                CompactNumber.format(sim.liturgyCollected()) + " D", GCOLOR.T().NORMAL);
            y += 30;

            addKpi(content, x, y, UI.icons().s.plate, "Rationen",
                CompactNumber.format(sim.fiscal().rationOut()) + " D", GCOLOR.T().NORMAL);
            y += 50;

            GText toggleHeader = new GText(UI.FONT().M, FONTW_HDR);
            toggleHeader.set("--- Schalter ---");
            toggleHeader.lablify();
            content.add(toggleHeader, x, y);
            y += 24;

            addCheckbox(content, x, y, "Alle Steuern", EconConfig.taxesEnabled,
                b -> EconConfig.taxesEnabled = b);
            addCheckbox(content, x + 380, y, "Religionssteuer", EconConfig.religionTaxEnabled,
                b -> EconConfig.religionTaxEnabled = b);
            y += 22;
            addCheckbox(content, x, y, "Liturgie", EconConfig.liturgyEnabled,
                b -> EconConfig.liturgyEnabled = b);
            addCheckbox(content, x + 380, y, "Schuldknechtschaft", EconConfig.debtSlaveryEnabled,
                b -> EconConfig.debtSlaveryEnabled = b);
            y += 22;
            addCheckbox(content, x, y, "Oddjob-Lohn", EconConfig.oddjobWageEnabled,
                b -> EconConfig.oddjobWageEnabled = b);
            addCheckbox(content, x + 380, y, "Transport", EconConfig.transportFeeEnabled,
                b -> EconConfig.transportFeeEnabled = b);
            y += 22;
            addCheckbox(content, x, y, "Staatsarbeit", EconConfig.corveeEnabled,
                b -> EconConfig.corveeEnabled = b);
            addCheckbox(content, x + 380, y, "Firm-Sizing", EconConfig.firmSizingEnabled,
                b -> EconConfig.firmSizingEnabled = b);
        }
    }

    // ─── Tab 3: Public Works ─────────────────────────────────────────

    private static final class PublicWorksTab implements TabContent {
        @Override public CharSequence title() { return "Arbeiten"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText swHeader = new GText(UI.FONT().M, FONTW_HDR);
            swHeader.set("--- Staatsgehälter ---");
            swHeader.lablify();
            content.add(swHeader, x, y);
            y += 28;

            addKpi(content, x, y, UI.icons().m.pickaxe, "Staatslöhne",
                CompactNumber.format(sim.wagesPaid()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().m.citizen, "Bevölkerung",
                String.valueOf(sim.roster().size()), GCOLOR.T().NORMAL);
            y += 40;

            GText corveeHeader = new GText(UI.FONT().M, FONTW_HDR);
            corveeHeader.set("--- Staatsarbeit (Corvée) ---");
            corveeHeader.lablify();
            content.add(corveeHeader, x, y);
            y += 24;

            addCheckbox(content, x, y, "Staatsarbeit aktiv", EconConfig.corveeEnabled,
                b -> EconConfig.corveeEnabled = b);
            y += 22;

            // Corvée-Draft slider
            addSlider(content, x, y, "Aushebung %", () -> EconConfig.corveeDraftPercent, 0, 100, 5,
                new ACTION() { @Override public void exe() { EconConfig.corveeDraftPercent = Math.min(100, EconConfig.corveeDraftPercent + 5); } },
                new ACTION() { @Override public void exe() { EconConfig.corveeDraftPercent = Math.max(0, EconConfig.corveeDraftPercent - 5); } }, "%");
            addKpi(content, x + 380, y, UI.icons().s.human, "Max Personen",
                String.valueOf(EconConfig.corveeDraftMax), GCOLOR.T().NORMAL);
            y += 38;
            addKpi(content, x, y, UI.icons().m.gov, "Letzte Fraktion",
                String.format("%.1f%%", sim.corveeDraftFractionLast() * 100), GCOLOR.T().NORMAL);
            y += 40;

            GText oddHeader = new GText(UI.FONT().M, FONTW_HDR);
            oddHeader.set("--- Gelegenheitsarbeit ---");
            oddHeader.lablify();
            content.add(oddHeader, x, y);
            y += 24;

            addCheckbox(content, x, y, "Oddjob aktiv", EconConfig.oddjobWageEnabled,
                b -> EconConfig.oddjobWageEnabled = b);
            y += 22;
            addKpi(content, x, y, UI.icons().m.pickaxe, "Lohn/Aufgabe",
                EconConfig.oddjobWagePerTask + " D", GCOLOR.T().NORMAL);
            y += 40;

            GText txHeader = new GText(UI.FONT().M, FONTW_HDR);
            txHeader.set("--- Transportpauschale ---");
            txHeader.lablify();
            content.add(txHeader, x, y);
            y += 24;

            addCheckbox(content, x, y, "Transport aktiv", EconConfig.transportFeeEnabled,
                b -> EconConfig.transportFeeEnabled = b);
            y += 22;
            addKpi(content, x, y, UI.icons().s.speed, "Pauschale",
                EconConfig.transportFeePer100TileDay + " D / 100t / Tag", GCOLOR.T().NORMAL);
            y += 40;

            GText gdHeader = new GText(UI.FONT().M, FONTW_HDR);
            gdHeader.set("--- Kornspende ---");
            gdHeader.lablify();
            content.add(gdHeader, x, y);
            y += 24;

            addKpi(content, x, y, UI.icons().s.plate, "Mahlzeiten",
                String.valueOf(sim.grainDole().mealsDoled()), GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().s.human, "Auf Liste",
                String.valueOf(sim.grainDole().rollSize()), GCOLOR.T().NORMAL);
        }
    }

    // ─── Tab 4: Social ───────────────────────────────────────────────

    private static final class SocialTab implements TabContent {
        @Override public CharSequence title() { return "Soziales"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText header = new GText(UI.FONT().M, FONTW_HDR);
            header.set("--- Religion & Liturgie ---");
            header.lablify();
            content.add(header, x, y);
            y += 28;

            addCheckbox(content, x, y, "Religion-Steuer", EconConfig.religionTaxEnabled,
                b -> EconConfig.religionTaxEnabled = b);
            y += 22;

            addCheckbox(content, x, y, "Liturgie abhalten", EconConfig.liturgyEnabled,
                b -> EconConfig.liturgyEnabled = b);
            y += 22;

            GText collHeader = new GText(UI.FONT().M, FONTW_HDR);
            collHeader.set("--- Heutige Einnahmen ---");
            collHeader.lablify();
            content.add(collHeader, x, y);
            y += 24;

            long religionToday = sim.religionTaxCollected();
            long liturgyToday = sim.liturgyCollected();

            addKpi(content, x, y, UI.icons().m.heart, "Religionssteuer",
                CompactNumber.format(religionToday) + " D",
                religionToday > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Liturgie",
                CompactNumber.format(liturgyToday) + " D",
                liturgyToday > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().NORMAL);
            y += 40;

            long total = religionToday + liturgyToday;
            GText totalText = new GText(UI.FONT().M, FONTW_HDR);
            totalText.set("Gesamte Sammlungen heute: " + CompactNumber.format(total) + " D");
            totalText.color(total > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(totalText, x, y);
            y += 40;

            // U-03: CitizenClass Verteilung
            if (EconConfig.citizenClassesEnabled) {
                GText classHeader = new GText(UI.FONT().M, FONTW_HDR);
                classHeader.set("--- Bürgerklassen ---");
                classHeader.lablify();
                content.add(classHeader, x, y);
                y += 24;

                Wallets wallets = sim.wallets();
                WealthStats stats = sim.stats();
                int totalPop = sim.roster().size();
                int classified = CitizenClass.classifiedCount(wallets);

                addKpi(content, x, y, UI.icons().s.human, "Klassifiziert",
                    classified + " / " + totalPop, GCOLOR.T().NORMAL);
                y += 28;

                if (classified == 0 && totalPop > 0) {
                    GText pending = new GText(UI.FONT().S, FONTW_BODY);
                    pending.set("Wird berechnet...");
                    pending.color(GCOLOR.T().INACTIVE);
                    content.add(pending, x, y);
                } else {

                // Header row
                GText hdrClass = new GText(UI.FONT().S, FONTW_LABEL);
                hdrClass.set("Klasse");
                hdrClass.lablify();
                content.add(hdrClass, x, y);
                GText hdrCount = new GText(UI.FONT().S, FONTW_CNT);
                hdrCount.set("Anzahl");
                hdrCount.lablify();
                content.add(hdrCount, x + 160, y);
                GText hdrHome = new GText(UI.FONT().S, FONTW_CNT);
                hdrHome.set("Haus×");
                hdrHome.lablify();
                content.add(hdrHome, x + 240, y);
                GText hdrFirm = new GText(UI.FONT().S, FONTW_CNT);
                hdrFirm.set("Firma×");
                hdrFirm.lablify();
                content.add(hdrFirm, x + 310, y);
                y += 18;

                for (CitizenClass cc : CitizenClass.values()) {
                    if (cc == CitizenClass.UNCLASSIFIED) continue;
                    int count = CitizenClass.countByClass(wallets, cc);
                    GText nameText = new GText(UI.FONT().S, FONTW_LABEL);
                    nameText.set(cc.displayName);
                    nameText.color(count > 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
                    content.add(nameText, x, y);

                    GText countText = new GText(UI.FONT().S, FONTW_CNT);
                    countText.set(String.valueOf(count));
                    countText.color(count > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
                    content.add(countText, x + 160, y);

                    GText homeText = new GText(UI.FONT().S, FONTW_CNT);
                    homeText.set(String.format("%.1f", cc.homeBuyMultiplier));
                    content.add(homeText, x + 240, y);

                    GText firmText = new GText(UI.FONT().S, FONTW_CNT);
                    firmText.set(cc.firmBuyThresholdMultiplier >= 999 ? "∞" : String.format("%.1f", cc.firmBuyThresholdMultiplier));
                    content.add(firmText, x + 310, y);
                    y += 16;
                }
                } // else (classified > 0)
            }
        }
    }

    // ─── Tab 5: Debug ────────────────────────────────────────────────

    private static final class DebugTab implements TabContent {
        @Override public CharSequence title() { return "Debug"; }

        // Persist results across tab rebuilds (static = survives close/reopen)
        private static String[] selfTestResults = {};
        private static String cheatStatus = "";

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            // ── Section 1: Opt-in toggles ───────────────────────────
            GText header = new GText(UI.FONT().M, FONTW_HDR);
            header.set("--- Logger & Export ---");
            header.lablify();
            content.add(header, x, y);
            y += 22;

            addCheckbox(content, x, y, "Debug-Logging", EconConfig.debugLoggingEnabled,
                b -> EconConfig.debugLoggingEnabled = b);
            addCheckbox(content, x + 380, y, "Debug-Tracing", EconConfig.debugTracing,
                b -> EconConfig.debugTracing = b);
            y += 20;

            addCheckbox(content, x, y, "Preis-Logging", EconConfig.debugPriceLogging,
                b -> EconConfig.debugPriceLogging = b);
            addCheckbox(content, x + 380, y, "CSV-Export", EconConfig.diagnosticsExportEnabled,
                b -> EconConfig.diagnosticsExportEnabled = b);
            y += 20;

            addCheckbox(content, x, y, "Möbel-Dump", EconConfig.debugFurnitureDump,
                b -> EconConfig.debugFurnitureDump = b);
            addCheckbox(content, x + 380, y, "Konservierung", EconConfig.checkConservation,
                b -> EconConfig.checkConservation = b);
            y += 26;

            // ── Section 2: Persistent logging paths ─────────────────
            GText logHeader = new GText(UI.FONT().M, FONTW_HDR);
            logHeader.set("--- Persistente Logs ---");
            logHeader.lablify();
            content.add(logHeader, x, y);
            y += 20;

            GText eventLog = new GText(UI.FONT().S, FONTW_BODY);
            eventLog.set("EventLog: economy_events.log  [" + (EconConfig.debugPriceLogging ? "AKTIV" : "AUS") + "]");
            eventLog.color(EconConfig.debugPriceLogging ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(eventLog, x, y);
            y += 14;

            GText csvLog = new GText(UI.FONT().S, FONTW_BODY);
            csvLog.set("CSV-Export: " +                    DiagnosticExporter.diagnosticDirectory());
            csvLog.color(EconConfig.diagnosticsExportEnabled ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(csvLog, x, y);
            y += 14;

            GText traceLog = new GText(UI.FONT().S, FONTW_BODY);
            traceLog.set("Trace: 8192 Events, Dump via Numpad /");
            traceLog.color(EconConfig.debugTracing ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(traceLog, x, y);
            y += 22;

            // Force-export + Trace dump buttons
            GButt.ButtPanel exportBtn = new GButt.ButtPanel("Export jetzt", 120);
            exportBtn.clickActionSet(new ACTION() {
                @Override public void exe() {
                    DiagnosticExporter.logPlayerAction("state.export_csv", "forced");
                    sim.forceDiagnosticExport();
                    cheatStatus = "CSV exportiert";
                }
            });
            exportBtn.hoverInfoSet("Erzwingt CSV-Export sofort (ohne Tag-Grenze)");
            content.add(exportBtn, x, y);

            GButt.ButtPanel traceBtn = new GButt.ButtPanel("Trace dump", 120);
            traceBtn.clickActionSet(new ACTION() {
                @Override public void exe() {
                    DiagnosticExporter.logPlayerAction("state.trace_dump", "ring_buffer");
                    DebugTracer.dump();
                    cheatStatus = "Trace gedumpt (siehe Log)";
                }
            });
            traceBtn.hoverInfoSet("Dump Ring-Buffer nach stdout");
            content.add(traceBtn, x + 130, y);

            GButt.ButtPanel boosterBtn = new GButt.ButtPanel("Boosters", 90);
            boosterBtn.clickActionSet(new ACTION() {
                @Override public void exe() {
                    DiagnosticExporter.logPlayerAction("state.boosters_dump", "reflect");
                    try {
                        String info = null;
                        for (String cn : new String[]{"BOOSTING", "BOOSTABLES"}) {
                            try {
                                Class<?> clazz = Class.forName(cn);
                                // Try: BOOSTING.available() (static)
                                try {
                                    java.lang.reflect.Method m = clazz.getMethod("available");
                                    info = (String) m.invoke(null);
                                    break;
                                } catch (NoSuchMethodException ignored) {}
                                // Fallback: BOOSTABLES.BOOSTING() -> available()
                                try {
                                    java.lang.reflect.Method m = clazz.getMethod("BOOSTING");
                                    Object boosting = m.invoke(null);
                                    java.lang.reflect.Method av = boosting.getClass().getMethod("available");
                                    info = (String) av.invoke(null);
                                    break;
                                } catch (NoSuchMethodException ignored) {}
                            } catch (ClassNotFoundException ignored) {}
                        }
                        if (info != null) {
                            EventLog.log("BOOSTERS", info);
                            cheatStatus = "Boosters gedumpt (siehe EventLog)";
                        } else {
                            cheatStatus = "Boosters: BOOSTING-Klasse nicht gefunden";
                        }
                    } catch (ReflectiveOperationException e) {
                        cheatStatus = "Boosters: " + e.getClass().getSimpleName();
                    }
                }
            });
            boosterBtn.hoverInfoSet("Dump aller registrierten Booster (WORLD, CIVIC, PHYSICS etc.) ins EventLog");
            content.add(boosterBtn, x + 260, y);
            y += 32;

            // ── Section 3: BypassGate Status ────────────────────────
            GText gateHeader = new GText(UI.FONT().M, FONTW_HDR);
            gateHeader.set("--- BypassGate Adapter ---");
            gateHeader.lablify();
            content.add(gateHeader, x, y);
            y += 20;

            String[] adapterStatus = sim.debugAdapterStatus();
            for (String status : adapterStatus) {
                boolean ok = status.contains("OK");
                GText line = new GText(UI.FONT().S, FONTW_BODY);
                line.set(status);
                line.color(ok ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
                content.add(line, x + 8, y);
                y += 14;
            }
            y += 8;

            // ── Section 4: Self-Test Buttons ───────────────────────
            GText testHeader = new GText(UI.FONT().M, FONTW_HDR);
            testHeader.set("--- Adapter Self-Test ---");
            testHeader.lablify();
            content.add(testHeader, x, y);
            y += 20;

            GButt.ButtPanel testBtn = new GButt.ButtPanel("Alle testen", 120);
            testBtn.clickActionSet(new ACTION() {
                @Override public void exe() {
                    DiagnosticExporter.logPlayerAction("state.self_test", "all_adapters");
                    selfTestResults = sim.debugSelfTest();
                    cheatStatus = "Self-Test abgeschlossen";
                }
            });
            testBtn.hoverInfoSet("Testet jeden BypassGate-Accessor mit echtem Zugriff");
            content.add(testBtn, x, y);

            // Show last test results
            if (selfTestResults.length > 0) {
                for (int i = 0; i < selfTestResults.length && y < 460; i++) {
                    String r = selfTestResults[i];
                    boolean pass = r.contains("PASS");
                    boolean skip = r.contains("SKIP");
                    GText line = new GText(UI.FONT().S, FONTW_BODY);
                    line.set(r);
                    line.color(pass ? GCOLOR.UI().GOOD.normal : skip ? GCOLOR.T().INACTIVE : GCOLOR.UI().BAD.normal);
                    content.add(line, x + 130, y);
                    y += 14;
                }
            }
            y += 8;

            // ── Section 5: Cheat Buttons ───────────────────────────
            GText cheatHeader = new GText(UI.FONT().M, FONTW_HDR);
            cheatHeader.set("--- Cheat-Tests ---");
            cheatHeader.lablify();
            content.add(cheatHeader, x, y);
            y += 20;

            GButt.ButtPanel mintBtn = new GButt.ButtPanel("+100.000 D", 110);
            mintBtn.clickActionSet(new ACTION() {
                @Override public void exe() {
                    DiagnosticExporter.logPlayerAction("state.cheat_mint", "+100000");
                    sim.mintTreasury(100_000L);
                    cheatStatus = "+100.000 D (Kasse: " + CompactNumber.format(sim.treasury()) + " D)";
                }
            });
            mintBtn.hoverInfoSet("Cheat: 100.000 Denari in die Staatskasse");
            content.add(mintBtn, x, y);

            GButt.ButtPanel auditBtn = new GButt.ButtPanel("Audit", 80);
            auditBtn.clickActionSet(new ACTION() {
                @Override public void exe() {
                    DiagnosticExporter.logPlayerAction("state.audit", "delta_check");
                    sim.logAuditDelta();
                    cheatStatus = "Audit: delta=" + sim.auditDelta();
                }
            });
            auditBtn.hoverInfoSet("Cheat: Geldfluss-Bilanz pruefen");
            content.add(auditBtn, x + 120, y);
            y += 28;

            // Cheat status line
            if (!cheatStatus.isEmpty()) {
                GText statusLine = new GText(UI.FONT().S, FONTW_BODY);
                statusLine.set(cheatStatus);
                statusLine.color(GCOLOR.UI().SOSO.normal);
                content.add(statusLine, x, y);
            }
        }
    }

    // ─── Tab 5: Faith ────────────────────────────────────────────────

    private static final class FaithTab implements TabContent {
        @Override public CharSequence title() { return "Glaube"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText header = new GText(UI.FONT().M, FONTW_HDR);
            header.set("--- Religion & Liturgie ---");
            header.lablify();
            content.add(header, x, y);
            y += 24;

            long religionToday = sim.religionTaxCollected();
            long liturgyToday = sim.liturgyCollected();

            addKpi(content, x, y, UI.icons().m.heart, "Religionssteuer",
                CompactNumber.format(religionToday) + " D",
                religionToday > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, "Liturgie",
                CompactNumber.format(liturgyToday) + " D",
                liturgyToday > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().NORMAL);
            y += 40;

            long total = religionToday + liturgyToday;
            GText totalText = new GText(UI.FONT().M, FONTW_HDR);
            totalText.set("Gesamte Sammlungen heute: " + CompactNumber.format(total) + " D");
            totalText.color(total > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(totalText, x, y);
            y += 36;

            GText toggleHdr = new GText(UI.FONT().M, FONTW_HDR);
            toggleHdr.set("--- Schalter ---");
            toggleHdr.lablify();
            content.add(toggleHdr, x, y);
            y += 22;

            addCheckbox(content, x, y, "Religionssteuer aktiv", EconConfig.religionTaxEnabled,
                b -> EconConfig.religionTaxEnabled = b);
            y += 22;

            addCheckbox(content, x, y, "Liturgie abhalten", EconConfig.liturgyEnabled,
                b -> EconConfig.liturgyEnabled = b);
            y += 22;

            // Liturgy interval display only (no toggle — controlled by EconConfig)
            GText litInt = new GText(UI.FONT().S, FONTW_HDR);
            litInt.set("Liturgie-Turnus: alle " + EconConfig.liturgyIntervalSeasons + " Saison(en)");
            litInt.color(GCOLOR.T().NORMAL);
            content.add(litInt, x, y);
            y += 28;

            GText infoHdr = new GText(UI.FONT().M, FONTW_HDR);
            infoHdr.set("--- Info ---");
            infoHdr.lablify();
            content.add(infoHdr, x, y);
            y += 18;

            GText info = new GText(UI.FONT().S, FONTW_BODY);
            info.set("Religionssteuer: Pro-Kopf-Abgabe an Tempel. Liturgie: Freiwillige Spenden sammeln. Beide verbessern Stimmung und Loyalitaet.");
            info.color(GCOLOR.T().NORMAL);
            content.add(info, x, y);
        }
    }

    // ─── WindowState-specific helpers ────────────────────────────────

    private static void addCheckbox(GuiSection section, int x, int y, String label, boolean initial, java.util.function.Consumer<Boolean> setter) {
        GButt.Checkbox cb = new GButt.Checkbox(label);
        cb.selectedSet(initial);
        cb.clickActionSet(new ACTION() {
            @Override public void exe() {
                boolean next = !cb.selectedIs();
                DiagnosticExporter.logPlayerAction("state.toggle", label);
                setter.accept(next);
                cb.selectedSet(next);
            }
        });
        section.add(cb, x, y);
    }
}
