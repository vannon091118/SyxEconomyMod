package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import util.colors.GCOLOR;
import util.gui.misc.GButt;
import util.gui.misc.GText;
import util.gui.panel.GPanel;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.StateWarehouses;
import snake2d.util.color.COLOR;

public final class WindowState extends EconWindowBase {

    private static final TabContent[] TABS = {
        new WarehousesTab(),
        new FiscalTab(),
        new PublicWorksTab(),
        new SocialTab(),
        new FaithTab()
    };

    public WindowState(EconomySim sim) {
        super(sim);
    }

    @Override protected CharSequence title() { return "Staat"; }
    @Override protected TabContent[] tabs() {
        if (EconConfig.debugLoggingEnabled) {
            return new TabContent[]{new WarehousesTab(), new FiscalTab(), new PublicWorksTab(), new SocialTab(), new DebugTab()};
        }
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

            GText modeHeader = new GText(UI.FONT().M, 256);
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
                @Override public void exe() { wh.setTradeMode(StateWarehouses.TradeMode.NORMAL); }
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
                @Override public void exe() { wh.setTradeMode(StateWarehouses.TradeMode.BUY_ONLY); }
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
                @Override public void exe() { wh.setTradeMode(StateWarehouses.TradeMode.SELL_ONLY); }
            });
            content.add(sell, x + 320, y);
            y += 40;

            GButt.ButtPanel std = new GButt.ButtPanel("Standardisieren (80%/110%)", 220);
            std.clickActionSet(new ACTION() {
                @Override public void exe() { wh.standardizeAllPrices(sim.flowPrices()); }
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
                @Override public void exe() { wh.setAllLiquidating(!wh.allLiquidating()); }
            });
            content.add(liquidate, x + 240, y);
            y += 40;

            GText statsHeader = new GText(UI.FONT().M, 256);
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

            GText wageHeader = new GText(UI.FONT().M, 256);
            wageHeader.set("--- Lagerlöhne ---");
            wageHeader.lablify();
            content.add(wageHeader, x, y);
            y += 24;

            addSlider(content, x, y, "Lohn/Arbeiter", wh::wage, 0, EconConfig.wageMax, EconConfig.wageStep,
                new ACTION() { @Override public void exe() { wh.setWage(wh.wage() + EconConfig.wageStep); } },
                new ACTION() { @Override public void exe() { wh.setWage(Math.max(0, wh.wage() - EconConfig.wageStep)); } });

            addKpi(content, x + 380, y, UI.icons().s.human, "Bezahlt",
                String.valueOf(wh.lastWorkersPaid()), GCOLOR.UI().GOOD.normal);
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
            GText taxHeader = new GText(UI.FONT().M, 256);
            taxHeader.set("--- Steuern & Abgaben ---");
            taxHeader.lablify();
            content.add(taxHeader, x, y);
            y += 28;

            // Kopfsteuer slider (replaces static KPI)
            addSlider(content, x, y, "Kopfsteuer/Saison", () -> EconConfig.perHeadTax, 0, 500, 5,
                new ACTION() { @Override public void exe() { EconConfig.perHeadTax = Math.min(500, EconConfig.perHeadTax + 5); } },
                new ACTION() { @Override public void exe() { EconConfig.perHeadTax = Math.max(0, EconConfig.perHeadTax - 5); } });
            addKpi(content, x + 380, y, UI.icons().s.shield, "Freigrenze",
                EconConfig.perHeadTaxExemptionThreshold + " D", GCOLOR.T().NORMAL);
            y += 38;

            addKpi(content, x, y, UI.icons().s.trade, "Marktsteuer",
                String.format("%.1f%%", EconConfig.marketTaxRate * 100), GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().s.storage, "Rücklagen",
                EconConfig.warehouseTaxPercent + "%", GCOLOR.T().NORMAL);
            y += 40;

            GText collHeader = new GText(UI.FONT().M, 256);
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

            GText toggleHeader = new GText(UI.FONT().M, 256);
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
            GText swHeader = new GText(UI.FONT().M, 256);
            swHeader.set("--- Staatsgehälter ---");
            swHeader.lablify();
            content.add(swHeader, x, y);
            y += 28;

            addKpi(content, x, y, UI.icons().m.pickaxe, "Staatslöhne",
                CompactNumber.format(sim.wagesPaid()) + " D", GCOLOR.T().NORMAL);
            addKpi(content, x + 380, y, UI.icons().m.citizen, "Bevölkerung",
                String.valueOf(sim.roster().size()), GCOLOR.T().NORMAL);
            y += 40;

            GText corveeHeader = new GText(UI.FONT().M, 256);
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

            GText oddHeader = new GText(UI.FONT().M, 256);
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

            GText txHeader = new GText(UI.FONT().M, 256);
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

            GText gdHeader = new GText(UI.FONT().M, 256);
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
            GText header = new GText(UI.FONT().M, 256);
            header.set("--- Religion & Liturgie ---");
            header.lablify();
            content.add(header, x, y);
            y += 28;

            addCheckbox(content, x, y, "Religion-Steuer", EconConfig.religionTaxEnabled,
                b -> EconConfig.religionTaxEnabled = b);
            GText religionInfo = new GText(UI.FONT().S, 256);
            religionInfo.set("Treibt Geld fuer Tempel und Glaube ein.");
            religionInfo.color(GCOLOR.T().INACTIVE);
            content.add(religionInfo, x + 20, y + 16);
            y += 36;

            addCheckbox(content, x, y, "Liturgie abhalten", EconConfig.liturgyEnabled,
                b -> EconConfig.liturgyEnabled = b);
            GText liturgyInfo = new GText(UI.FONT().S, 256);
            liturgyInfo.set("Sammelt Spenden — Stimmung der Buerger steigt.");
            liturgyInfo.color(GCOLOR.T().INACTIVE);
            content.add(liturgyInfo, x + 20, y + 16);
            y += 50;

            GText collHeader = new GText(UI.FONT().M, 256);
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
            GText totalText = new GText(UI.FONT().M, 256);
            totalText.set("Gesamte Sammlungen heute: " + CompactNumber.format(total) + " D");
            totalText.color(total > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(totalText, x, y);
        }
    }

    // ─── Tab 5: Debug ────────────────────────────────────────────────

    private static final class DebugTab implements TabContent {
        @Override public CharSequence title() { return "Debug"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText header = new GText(UI.FONT().M, 256);
            header.set("--- Debug & Diagnose ---");
            header.lablify();
            content.add(header, x, y);
            y += 28;

            GText info = new GText(UI.FONT().S, 512);
            info.set("Opt-in/out für Logger und Diagnose. Nur bei Bedarf aktivieren.");
            info.color(GCOLOR.T().INACTIVE);
            content.add(info, x, y);
            y += 22;

            addCheckbox(content, x, y, "Debug-Logging", EconConfig.debugLoggingEnabled,
                b -> EconConfig.debugLoggingEnabled = b);
            addCheckbox(content, x + 380, y, "Debug-Tracing", EconConfig.debugTracing,
                b -> EconConfig.debugTracing = b);
            y += 24;

            addCheckbox(content, x, y, "Preis-Logging", EconConfig.debugPriceLogging,
                b -> EconConfig.debugPriceLogging = b);
            addCheckbox(content, x + 380, y, "Diagnostik-Export", EconConfig.diagnosticsExportEnabled,
                b -> EconConfig.diagnosticsExportEnabled = b);
            y += 24;

            addCheckbox(content, x, y, "Möbel-Dump", EconConfig.debugFurnitureDump,
                b -> EconConfig.debugFurnitureDump = b);
            addCheckbox(content, x + 380, y, "Konservierung", EconConfig.checkConservation,
                b -> EconConfig.checkConservation = b);
            y += 30;

            GText diHeader = new GText(UI.FONT().M, 256);
            diHeader.set("--- Dump-Intervall ---");
            diHeader.lablify();
            content.add(diHeader, x, y);
            y += 22;

            addKpi(content, x, y, "Dump alle N Tage",
                String.format("%.1f", EconConfig.dumpIntervalDays), GCOLOR.T().NORMAL);
            y += 30;

            GText traceInfo = new GText(UI.FONT().S, 512);
            traceInfo.set("Debug-Tracing speichert 8192 Events im Ring-Buffer. Dump via Numpad / (Division).");
            traceInfo.color(GCOLOR.T().INACTIVE);
            content.add(traceInfo, x, y);
            y += 16;

            GText exportInfo = new GText(UI.FONT().S, 512);
            exportInfo.set("Diagnostik-Export schreibt CSV-Dateien pro Tag fuer Offline-Analyse.");
            exportInfo.color(GCOLOR.T().INACTIVE);
            content.add(exportInfo, x, y);
            y += 20;

            GText pathHeader = new GText(UI.FONT().M, 256);
            pathHeader.set("--- Speicherort ---");
            pathHeader.lablify();
            content.add(pathHeader, x, y);
            y += 20;

            GText pathInfo = new GText(UI.FONT().S, 512);
            pathInfo.set("~/.local/share/songsofsyx/mods/SyxEconomyMod/diagnostics/");
            pathInfo.color(GCOLOR.T().NORMAL);
            content.add(pathInfo, x, y);
            y += 16;

            GText pathNote = new GText(UI.FONT().S, 512);
            pathNote.set("CSVs: macro, resources, firms. Im Dateimanager öffnen.");
            pathNote.color(GCOLOR.T().INACTIVE);
            content.add(pathNote, x, y);
        }
    }

    // ─── Tab 5: Faith ────────────────────────────────────────────────

    private static final class FaithTab implements TabContent {
        @Override public CharSequence title() { return "Glaube"; }

        @Override
        public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
            GText header = new GText(UI.FONT().M, 256);
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
            GText totalText = new GText(UI.FONT().M, 256);
            totalText.set("Gesamte Sammlungen heute: " + CompactNumber.format(total) + " D");
            totalText.color(total > 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.T().INACTIVE);
            content.add(totalText, x, y);
            y += 36;

            GText toggleHdr = new GText(UI.FONT().M, 256);
            toggleHdr.set("--- Schalter ---");
            toggleHdr.lablify();
            content.add(toggleHdr, x, y);
            y += 22;

            addCheckbox(content, x, y, "Religionssteuer aktiv", EconConfig.religionTaxEnabled,
                b -> EconConfig.religionTaxEnabled = b);
            GText relInfo = new GText(UI.FONT().S, 256);
            relInfo.set("Treibt Geld fuer Tempel und Glaube ein.");
            relInfo.color(GCOLOR.T().INACTIVE);
            content.add(relInfo, x + 20, y + 16);
            y += 36;

            addCheckbox(content, x, y, "Liturgie abhalten", EconConfig.liturgyEnabled,
                b -> EconConfig.liturgyEnabled = b);
            GText litInfo = new GText(UI.FONT().S, 256);
            litInfo.set("Sammelt Spenden — Stimmung der Buerger steigt.");
            litInfo.color(GCOLOR.T().INACTIVE);
            content.add(litInfo, x + 20, y + 16);
            y += 36;

            // Liturgy interval display only (no toggle — controlled by EconConfig)
            GText litInt = new GText(UI.FONT().S, 256);
            litInt.set("Liturgie-Turnus: alle " + EconConfig.liturgyIntervalSeasons + " Saison(en)");
            litInt.color(GCOLOR.T().INACTIVE);
            content.add(litInt, x, y);
            y += 28;

            GText infoHdr = new GText(UI.FONT().M, 256);
            infoHdr.set("--- Info ---");
            infoHdr.lablify();
            content.add(infoHdr, x, y);
            y += 18;

            GText info = new GText(UI.FONT().S, 512);
            info.set("Religionssteuer: Pro-Kopf-Abgabe an Tempel. Liturgie: Freiwillige Spenden sammeln. Beide verbessern Stimmung und Loyalitaet.");
            info.color(GCOLOR.T().INACTIVE);
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
                setter.accept(next);
                cb.selectedSet(next);
            }
        });
        section.add(cb, x, y);
    }
}
