package vannon.syx.economy.core;

import init.constant.C;
import init.resources.RESOURCE;
import init.resources.RESOURCES;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import init.sprite.UI.Icon;
import init.sprite.UI.UI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import settlement.entity.humanoid.Humanoid;
import settlement.main.SETT;
import settlement.room.infra.stockpile.StockpileInstance;
import settlement.room.main.RoomBlueprintImp;
import settlement.room.main.RoomBlueprintIns;
import settlement.room.main.RoomInstance;
import snake2d.CORE;
import snake2d.MButt;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import snake2d.util.datatypes.COORDINATE;
import snake2d.util.datatypes.Rec;
import snake2d.util.sets.LIST;
import snake2d.util.sprite.text.StringInputSprite;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.CorveeController;
import vannon.syx.economy.core.DebtBondage;
import vannon.syx.economy.core.EconConfig;
import vannon.syx.economy.core.EconIndicators;
import vannon.syx.economy.core.EconSnapshot;
import vannon.syx.economy.core.EconomicRoles;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.EngineSeams;
import vannon.syx.economy.core.FirmLedger;
import vannon.syx.economy.core.Fiscal;
import vannon.syx.economy.core.FlowMeter;
import vannon.syx.economy.core.FlowPrices;
import vannon.syx.economy.core.ForeignTradeLedger;
import vannon.syx.economy.core.GrainDole;
import vannon.syx.economy.core.Liturgy;
import vannon.syx.economy.core.LocalPrices;
import vannon.syx.economy.core.OddjobMarket;
import vannon.syx.economy.core.ProductionSubsidies;
import vannon.syx.economy.core.Purchases;
import vannon.syx.economy.core.ReligionMarket;
import vannon.syx.economy.core.StateWageMarket;
import vannon.syx.economy.core.StateWarehouses;
import vannon.syx.economy.core.Taxes;
import vannon.syx.economy.core.TransportMarket;
import vannon.syx.economy.core.WealthStats;
import util.gui.misc.GBox;
import util.gui.misc.GChart;
import util.gui.misc.GText;
import view.interrupter.Interrupter;
import view.main.VIEW;

public final class EconomyWindow {
    private static final int BTN_W = 92;
    private static final int BTN_H = 30;
    private static final int BTN_Y = 6;
    private static final int BTN_GAP_FROM_CENTRE = 150;
    private static final int WIN_X = 12;
    private static final int WIN_Y = 60;
    private static final int WIN_W_MAX = 1280;
    private static final int WIN_H_MAX = 1160;
    private static final int WIN_W_MIN = 900;
    private static final int WIN_H_MIN = 640;
    private static final int WIN_MARGIN_BOTTOM = 84;
    private static final int PAD = 18;
    private static final int TAB_H = 30;
    private static final int ROW_H = 30;
    private static final int SLIDER_W = 260;
    private static final int SLIDER_H = 10;
    private static final int CHART_H = 150;

    // Status indicator thresholds
    private static final long TREASURY_LOW_THRESHOLD = 1000L;
    private static final long TREASURY_HIGH_THRESHOLD = 10000L;
    private static final double GINI_GOOD_THRESHOLD = 0.25;
    private static final double GINI_BAD_THRESHOLD = 0.40;
    private static final int TAX_THRESHOLD_MAX = 50000;
    private static final String[] SEASON_NAMES = new String[]{EconTexts.¤¤seasonSpring, EconTexts.¤¤seasonSummer, EconTexts.¤¤seasonAutumn, EconTexts.¤¤seasonWinter};
    private boolean open = false;
    private Tab tab = Tab.DASHBOARD;
    private Menu menu = Menu.OVERVIEW;
    private final Rec btn = new Rec();
    private final Rec win = new Rec(12.0, 912.0, 60.0, 700.0);
    private final InputBlocker inputBlocker = new InputBlocker();
    private final ChartPanel treasuryChart = new ChartPanel();
    private final ChartPanel giniChart = new ChartPanel();
    private final GText label;
    private final GText line;
    private int mouseX;
    private int mouseY;
    private Object grabbed;
    private int grabX1;
    private int grabX2;
    private int wageScroll;
    private int stateWageScroll;
    private int priceScroll;
    private int subsidyScroll;
    private int stateScroll;
    private int marketScroll;
    private int granaryScroll;
    private int pendingScroll;
    private int advisorScroll = 0;
    private int firmScroll = 0;
    private int flowsScroll = 0;
    private int foreignTradeScroll = 0;

    private boolean showZeroRows = false;
    private EconomySim lastDashboardSim = null;
    private final List<RESOURCE> visibleResources = new ArrayList<>();
    private final Rec scratch;
    private final Rec ownerScratch;
    private final StringInputSprite input;
    private Object editing;
    private Setter pendingSet;
    private int pendingMin;
    private int pendingMax;
    private boolean leftWasDown;
    private boolean leftClicked;
    private boolean clickedAField;
    private final Rec editBox;
    private final Rec toggleBox;

    public EconomyWindow() {
        this.label = new GText(UI.FONT().S, 40);
        this.line = new GText(UI.FONT().S, 110);
        this.mouseX = -1;
        this.mouseY = -1;
        this.grabbed = null;
        this.grabX1 = 0;
        this.grabX2 = 0;
        this.wageScroll = 0;
        this.stateWageScroll = 0;
        this.priceScroll = 0;
        this.subsidyScroll = 0;
        this.stateScroll = 0;
        this.marketScroll = 0;
        this.granaryScroll = 0;
        this.pendingScroll = 0;
        this.scratch = new Rec();
        this.ownerScratch = new Rec();
        this.input = new StringInputSprite(9, UI.FONT().S){

            protected void enter() {
                EconomyWindow.this.commit();
            }
        };
        this.editing = null;
        this.pendingSet = null;
        this.pendingMin = 0;
        this.pendingMax = 0;
        this.leftWasDown = false;
        this.leftClicked = false;
        this.clickedAField = false;
        this.editBox = new Rec();
        this.toggleBox = new Rec();
    }

    public void hover(COORDINATE mCoo) {
        // Maus-Koordinaten kommen vom Engine-Interrupter und sind bereits
        // im richtigen UI-Koordinatensystem (wichtig bei Zoom).
        this.mouseX = mCoo.x();
        this.mouseY = mCoo.y();
        if (!EconConfig.windowEnabled || !this.open) {
            return;
        }
        this.placeWindow();
        if (!this.hit(this.win)) {
            return;
        }
        float wheel = MButt.peekWheel();
        if (wheel != 0.0f) {
            MButt.clearWheelSpin();
            this.pendingScroll -= (int)Math.signum(wheel);
        }
    }

    private int takeScroll() {
        return this.takeScroll(true);
    }

    private int takeScroll(boolean claims) {
        if (!claims) {
            return 0;
        }
        int notches = this.pendingScroll;
        this.pendingScroll = 0;
        return notches;
    }

    public boolean click(MButt button) {
        if (!EconConfig.windowEnabled) {
            return false;
        }
        if (this.open && button == MButt.RIGHT) {
            if (this.editing != null) {
                this.cancelEdit();
                return true;
            }
            this.open = false;
            return true;
        }
        this.placeButton();
        this.placeWindow();
        if (button == MButt.LEFT && this.hit(this.btn)) {
            this.open = !this.open;
            return true;
        }
        if (!this.open || !this.hit(this.win)) {
            return false;
        }
        if (button == MButt.LEFT) {
            // Main menu click
            for (Menu m : Menu.values()) {
                if (!this.hit(this.menuRec(m))) continue;
                if (this.menu != m) {
                    this.menu = m;
                    for (Tab t : Tab.values()) {
                        if (t.menu == m) {
                            this.tab = t;
                            break;
                        }
                    }
                }
                return true;
            }
            // Sub-tab click (only visible tabs of the current menu)
            for (Tab t : Tab.values()) {
                if (t.menu != this.menu) continue;
                if (!this.hit(this.tabRec(t))) continue;
                this.tab = t;
                return true;
            }
        }
        return true;
    }

    /** Toggle window open/closed via hotkey (default: KEY_E). */
    public void toggle() {
        if (!EconConfig.windowEnabled) {
            return;
        }
        if (this.open && this.editing != null) {
            this.cancelEdit();
        }
        this.open = !this.open;
    }

    private boolean hit(Rec r) {
        return this.mouseX >= r.x1() && this.mouseX <= r.x2() && this.mouseY >= r.y1() && this.mouseY <= r.y2();
    }

    private void scrollbar(Renderer r, int y1, int y2, int scroll, int visible, int total) {
        if (total <= visible) {
            return;
        }
        int x2 = this.win.x2() - 5;
        int x1 = x2 - 4;
        COLOR.WHITE25.render((SPRITE_RENDERER)r, x1, x2, y1, y2);
        int track = y2 - y1;
        int thumb = Math.max(16, track * visible / total);
        int top = y1 + (track - thumb) * scroll / Math.max(1, total - visible);
        COLOR.WHITE100.render((SPRITE_RENDERER)r, x1, x2, top, top + thumb);
    }

    private void placeButton() {
        this.btn.setDim(92.0, 30.0);
        this.btn.moveX1Y1((double)(C.WIDTH() / 2 - 150 - 92), 6.0);
    }

    private void placeWindow() {
        int w = EconomyWindow.clamp(C.WIDTH() - 24, 900, 1280);
        int h = EconomyWindow.clamp(C.HEIGHT() - 60 - 84, 640, 1160);
        this.win.setDim((double)w, (double)h);
        this.win.moveX1Y1(12.0, 60.0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int winW() {
        return this.win.x2() - this.win.x1();
    }

    private Rec menuRec(Menu m) {
        int gap = 6;
        int count = Menu.values().length;
        int w = (this.winW() - 36 - gap * (count - 1)) / count;
        int x = this.win.x1() + 18 + m.ordinal() * (w + gap);
        this.scratch.setDim((double)w, 30.0);
        this.scratch.moveX1Y1((double)x, (double)(this.win.y1() + 42));
        return this.scratch;
    }

    private Rec tabRec(Tab t) {
        int gap = 6;
        int subTabs = 0;
        int index = 0;
        for (Tab candidate : Tab.values()) {
            if (candidate.menu != t.menu) continue;
            if (candidate == t) index = subTabs;
            subTabs++;
        }
        int w = (this.winW() - 36 - gap * (subTabs - 1)) / subTabs;
        int x = this.win.x1() + 18 + index * (w + gap);
        this.scratch.setDim((double)w, 30.0);
        this.scratch.moveX1Y1((double)x, (double)(this.win.y1() + 42 + 30 + 6));
        return this.scratch;
    }

    public void render(Renderer r, float ds) {
        boolean down;
        if (!EconConfig.windowEnabled) {
            return;
        }
        // Fallback: Vor dem ersten hover()-Event haben wir noch keine Koordinaten.
        // Danach kommen die Werte aus hover() / InputBlocker.hover() und bleiben
        // im richtigen UI-Koordinatensystem (wichtig bei Zoom).
        if (this.mouseX == -1 && this.mouseY == -1) {
            COORDINATE mCoo = CORE.getInput().getMouse().getCoo();
            this.mouseX = mCoo.x();
            this.mouseY = mCoo.y();
        }
        this.inputBlocker.ensureShown();
        EconomySim sim = EconomySim.active();
        if (sim == null) {
            return;
        }
        this.placeButton();
        this.placeWindow();
        this.renderButton(r);
        if (!this.open) {
            this.grabbed = null;
            if (this.editing != null) {
                this.cancelEdit();
            }
            return;
        }
        if (!MButt.LEFT.isDown()) {
            this.grabbed = null;
        }
        this.leftClicked = (down = MButt.LEFT.isDown()) && !this.leftWasDown;
        this.leftWasDown = down;
        this.clickedAField = false;
        this.frame(r);
        this.renderTabs(r);
        this.renderStatusIndicators(r, sim);
        int y = this.win.y1() + 42 + 30 + 6 + 30 + 18;
        switch (this.tab) {
            case DASHBOARD -> this.renderDashboard(r, y, sim);
            case DISTRIBUTION -> this.renderDistribution(r, y, sim);
            case CITIZENS -> this.renderCitizens(r, y, sim);
            case PRICES -> this.renderPrices(r, y, sim);
            case WAGES -> this.renderWages(r, y, sim);
            case SUBSIDIES -> this.renderSubsidies(r, y, sim);
            case GRANARY -> this.renderStateWarehouses(r, y, sim);
            case MARKET -> this.renderCrownMarket(r, y, sim);
            case TAXES -> this.renderTaxes(r, y, sim);
            case RELIGION -> this.renderReligion(r, y, sim);
            case CORVEE -> this.renderCorvee(r, y, sim);
            case RELIEF -> this.renderRelief(r, y, sim);
            case FOREIGN_TRADE -> this.renderForeignTrade(r, y, sim);
            case BOOKS -> this.renderBooks(r, y, sim);
            case ADVISOR -> this.renderAdvisor(r, y, sim);
            case FIRMS -> this.renderFirms(r, y, sim);
            case FLOWS -> this.renderFlows(r, y, sim);
            case DEBUG -> this.renderDebug(r, y, sim);
        }
        this.pendingScroll = 0;
        if (this.leftClicked && this.editing != null && !this.clickedAField) {
            this.commit();
        }
    }

    private void renderButton(Renderer r) {
        boolean hot = this.hit(this.btn);
        (hot || this.open ? COLOR.WHITE50 : COLOR.WHITE25).render((SPRITE_RENDERER)r, this.btn.x1(), this.btn.x2(), this.btn.y1(), this.btn.y2());
        this.label.clear().add(EconTexts.¤¤windowTitle);
        this.label.color(hot || this.open ? COLOR.WHITE200 : COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, this.btn.x1() + 10, this.btn.x2(), this.btn.y1() + 6, this.btn.y2());
    }

    private void frame(Renderer r) {
        COLOR.WHITE15.render((SPRITE_RENDERER)r, this.win.x1(), this.win.x2(), this.win.y1(), this.win.y2());
        COLOR.WHITE35.render((SPRITE_RENDERER)r, this.win.x1(), this.win.x2(), this.win.y1(), this.win.y1() + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, this.win.x1(), this.win.x2(), this.win.y2() - 1, this.win.y2());
        COLOR.WHITE35.render((SPRITE_RENDERER)r, this.win.x1(), this.win.x1() + 1, this.win.y1(), this.win.y2());
        COLOR.WHITE35.render((SPRITE_RENDERER)r, this.win.x2() - 1, this.win.x2(), this.win.y1(), this.win.y2());
    }

    private void renderStatusIndicators(Renderer r, EconomySim sim) {
        int h = 28;
        int gap = 8;
        int x = this.win.x2() - 18;
        int y = this.win.y1() + 6;

        String treasury = CompactNumber.format(sim.treasury());
        String gini = String.format("%.2f", sim.stats().gini);
        String stage = sim.progression().stage.displayName;

        int[] widths = {
            this.indicatorWidth(EconTexts.¤¤indTreasury, treasury),
            this.indicatorWidth(EconTexts.¤¤indGini, gini),
            this.indicatorWidth(EconTexts.¤¤indStage, stage)
        };

        // Treasury indicator
        x -= widths[0];
        this.drawIndicator(r, x, y, widths[0], h, EconTexts.¤¤indTreasury, treasury, treasuryColor(sim));
        x -= gap;

        // Gini indicator
        x -= widths[1];
        this.drawIndicator(r, x, y, widths[1], h, EconTexts.¤¤indGini, gini, giniColor(sim));
        x -= gap;

        // Stage indicator
        x -= widths[2];
        this.drawIndicator(r, x, y, widths[2], h, EconTexts.¤¤indStage, stage, stageColor(sim));
    }

    private int indicatorWidth(String label, String value) {
        this.label.clear().add(label);
        this.line.clear().add(value);
        return Math.max(70, Math.max(this.label.width(), this.line.width()) + 16);
    }

    private void drawIndicator(Renderer r, int x, int y, int w, int h, String topText, String botText, COLOR color) {
        color.render((SPRITE_RENDERER)r, x, x + w, y, y + h);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y, y + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y + h - 1, y + h);
        this.label.clear().add(topText);
        this.label.color(COLOR.WHITE200);
        this.label.render((SPRITE_RENDERER)r, x + 6, x + w, y + 2, y + h / 2);
        this.line.clear().add(botText);
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x + 6, x + w, y + h / 2 + 1, y + h - 2);
    }

    private COLOR treasuryColor(EconomySim sim) {
        long t = sim.treasury();
        if (t < TREASURY_LOW_THRESHOLD) return COLOR.REDISH;
        if (t < TREASURY_HIGH_THRESHOLD) return COLOR.YELLOW100;
        return COLOR.GREENISH;
    }

    private COLOR giniColor(EconomySim sim) {
        double g = sim.stats().gini;
        if (g < GINI_GOOD_THRESHOLD) return COLOR.GREENISH;
        if (g < GINI_BAD_THRESHOLD) return COLOR.YELLOW100;
        return COLOR.REDISH;
    }

    private COLOR stageColor(EconomySim sim) {
        return switch (sim.progression().stage) {
            case SUBSISTENZ -> COLOR.WHITE25;
            case HANDEL -> COLOR.WHITE50;
            case INDUSTRIE -> COLOR.YELLOW100;
            case WOHLSTAND -> COLOR.GREENISH;
            case IMPERIUM -> COLOR.REDISH;
        };
    }

    private void renderTabs(Renderer r) {
        // Main menu row
        for (Menu m : Menu.values()) {
            Rec b = this.menuRec(m);
            boolean sel = m == this.menu;
            boolean hot = this.hit(b);
            (sel ? COLOR.WHITE50 : (hot ? COLOR.WHITE35 : COLOR.WHITE25)).render((SPRITE_RENDERER)r, b.x1(), b.x2(), b.y1(), b.y2());
            if (m.icon != null) {
                m.icon.render((SPRITE_RENDERER)r, b.x1() + 8, b.y1() + 7);
            }
            this.label.clear().add(switch (m) {
                case OVERVIEW -> EconTexts.¤¤menuOverview;
                case ECONOMY -> EconTexts.¤¤menuEconomy;
                case STATE -> EconTexts.¤¤menuStateAndSocial;
            });
            this.label.color(sel ? COLOR.WHITE200 : COLOR.WHITE120);
            this.label.render((SPRITE_RENDERER)r, b.x1() + 28, b.x2(), b.y1() + 5, b.y2());
        }

        // Sub-tab row for the active menu
        for (Tab t : Tab.values()) {
            if (t.menu != this.menu) continue;
            Rec b = this.tabRec(t);
            boolean sel = t == this.tab;
            boolean hot = this.hit(b);
            (sel ? COLOR.WHITE50 : (hot ? COLOR.WHITE35 : COLOR.WHITE25)).render((SPRITE_RENDERER)r, b.x1(), b.x2(), b.y1(), b.y2());
            if (t.icon != null) {
                t.icon.render((SPRITE_RENDERER)r, b.x1() + 8, b.y1() + 7);
            }
            this.label.clear().add((CharSequence)t.label);
            this.label.color(sel ? COLOR.WHITE200 : COLOR.WHITE120);
            this.label.render((SPRITE_RENDERER)r, b.x1() + 28, b.x2(), b.y1() + 5, b.y2());
        }
    }

    private void renderPrices(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        FlowPrices prices = sim.flowPrices();
        FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
        this.line.clear().add(EconTexts.¤¤pricesHeader);
        this.line.color(prices.ready() ? COLOR.WHITE150 : COLOR.REDISH);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        this.line.clear().add(EconTexts.¤¤pricesCoverageHint);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 16, y + 12);
        y += 20;
        int maxAvailable = (this.win.x2() - 18) - x;
        int resourceW = 180;
        int colW = Math.max(60, (maxAvailable - resourceW) / 6);
        int localX = x + resourceW;
        int anchorX = localX + colW;
        int multipleX = anchorX + colW;
        int coverageX = multipleX + colW;
        int stockX = coverageX + colW;
        int flowX = stockX + colW;
        this.line.clear().add(EconTexts.¤¤pricesColumnResource);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, localX - 8, y, y + 12);
        this.line.clear().add(EconTexts.¤¤pricesColumnLocal);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, localX, anchorX - 8, y, y + 12);
        this.line.clear().add(EconTexts.¤¤pricesColumnAnchor);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, anchorX, multipleX - 8, y, y + 12);
        this.line.clear().add(EconTexts.¤¤pricesColumnMultiple);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, multipleX, coverageX - 8, y, y + 12);
        this.line.clear().add(EconTexts.¤¤pricesColumnCoverage);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, coverageX, stockX - 8, y, y + 12);
        this.line.clear().add(EconTexts.¤¤pricesColumnStock);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, stockX, flowX - 8, y, y + 12);
        this.line.clear().add(EconTexts.¤¤pricesColumnSupplyDemand);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, flowX, this.win.x2() - 18, y, y + 12);
        y += 14;
        boolean prevShowZeroRows = this.showZeroRows;
        this.showZeroRows = this.toggle(r, x, y, 180, 22, this.showZeroRows, EconTexts.¤¤uiShowZeroRows);
        if (this.showZeroRows != prevShowZeroRows) {
            this.priceScroll = 0;
        }
        int headerY = y;
        this.visibleResources.clear();
        List<RESOURCE> allResources = sim.cachedAllResources();
        for (RESOURCE res : allResources) {
            int idx = res.index();
            boolean hasFlow = idx < flow.size() && (flow.supplyPerDay(idx) > 0 || flow.demandPerDay(idx) > 0 || flow.stock(idx) > 0);
            double anchorRaw = prices.anchor(idx);
            double multiple = anchorRaw > 0.0 ? prices.price(idx) / anchorRaw : 0.0;
            double coverage = prices.coverage(idx);
            boolean active = hasFlow || multiple >= 2.0 || coverage < 1.0;
            if (this.showZeroRows || active) {
                this.visibleResources.add(res);
            }
        }
        List<RESOURCE> resources = this.visibleResources;
        int total = resources.size();
        int listTop = y += 28;
        int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
        int maxScroll = Math.max(0, total - visible);
        this.priceScroll = Math.max(0, Math.min(maxScroll, this.priceScroll + this.takeScroll()));
        this.scrollbar(r, listTop, this.win.y2() - 18, this.priceScroll, visible, total);
        this.line.clear().add((CharSequence)("" + (this.priceScroll + 1))).add((CharSequence)EconTexts.¤¤uiRange).add((CharSequence)("" + Math.min(total, this.priceScroll + visible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + total));
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, headerY, headerY + 12);
        for (int i = this.priceScroll; i < total && y + 30 < this.win.y2() - 18; ++i) {
            RESOURCE resource = resources.get(i);
            int index = resource.index();
            int local = prices.priceRoundedUp(index);
            double anchorRaw = prices.anchor(index);
            int anchor = anchorRaw >= 2.147483647E9 ? Integer.MAX_VALUE : (int)Math.ceil(Math.max(0.0, anchorRaw));
            double multiple = anchorRaw > 0.0 ? prices.price(index) / anchorRaw : 0.0;
            double coverage = prices.coverage(index);
            COLOR priceColor = multiple >= 10.0 ? COLOR.REDISH : (multiple >= 2.0 ? COLOR.WHITE200 : COLOR.WHITE150);
            this.label.clear().add(resource.name);
            this.label.color(COLOR.WHITE150);
            this.label.render((SPRITE_RENDERER)r, x, localX - 8, y + 8, y + 22);
            this.line.clear().add((CharSequence)CompactNumber.format(local));
            this.line.color(priceColor);
            this.line.render((SPRITE_RENDERER)r, localX, anchorX - 8, y + 8, y + 22);
            this.line.clear().add((CharSequence)CompactNumber.format(anchor));
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, anchorX, multipleX - 8, y + 8, y + 22);
            this.line.clear().add((CharSequence)EconTexts.¤¤uiMultiple).add((CharSequence)CompactNumber.format(multiple));
            this.line.color(priceColor);
            this.line.render((SPRITE_RENDERER)r, multipleX, coverageX - 8, y + 8, y + 22);
            this.line.clear().add((CharSequence)CompactNumber.format(coverage));
            this.line.color(coverage < 1.0 ? priceColor : COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, coverageX, stockX - 8, y + 8, y + 22);
            long stock = index < flow.size() ? Math.round(flow.stock(index)) : 0L;
            this.line.clear().add((CharSequence)CompactNumber.format(stock));
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, stockX, flowX - 8, y + 8, y + 22);
            double supply = index < flow.size() ? flow.supplyPerDay(index) : 0.0;
            double demand = index < flow.size() ? flow.demandPerDay(index) : 0.0;
            this.line.clear().add((CharSequence)CompactNumber.format(supply)).add((CharSequence)EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(demand));
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, flowX, this.win.x2() - 18 - 12, y + 8, y + 22);
            y += 30;
        }
    }

    private void renderSubsidies(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        int maxAvailable = (this.win.x2() - 18) - x;
        int resourceW = 180;
        int sliderW = 260;
        int fieldW = 150;
        int gap = 16;
        int outputW = Math.max(80, maxAvailable - resourceW - sliderW - fieldW - gap * 2);
        int outputX = x + resourceW;
        int sliderX = outputX + outputW + gap;
        int fieldX = sliderX + sliderW + gap;
        ProductionSubsidies subsidies = sim.productionSubsidies();
        this.line.clear().add(EconTexts.¤¤subSeasonProd).add((CharSequence)CompactNumber.format(subsidies.seasonUnits())).add(EconTexts.¤¤subUnitsDue).add((CharSequence)CompactNumber.format(subsidies.seasonDue())).add(EconTexts.¤¤subPaid).add((CharSequence)CompactNumber.format(subsidies.seasonPaid()));
        this.line.color(subsidies.seasonPaid() < subsidies.seasonDue() ? COLOR.REDISH : COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        this.line.clear().add(EconTexts.¤¤subColumns);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 16, y + 12);
        int headerY = y;
        boolean prevShowZeroRows = this.showZeroRows;
        this.showZeroRows = this.toggle(r, x, y, 180, 22, this.showZeroRows, EconTexts.¤¤uiShowZeroRows);
        if (this.showZeroRows != prevShowZeroRows) {
            this.subsidyScroll = 0;
        }
        FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
        this.visibleResources.clear();
        List<RESOURCE> allResources = sim.cachedAllResources();
        for (RESOURCE res : allResources) {
            int idx = res.index();
            int output = idx < flow.size() ? (int)Math.round(flow.supplyPerDay(idx)) : 0;
            boolean active = output > 0 || subsidies.bounty(res) > 0;
            boolean isEdited = Objects.equals(this.editing, "f_sub_" + res.key) || Objects.equals(this.grabbed, "sub_" + res.key);
            if (this.showZeroRows || active || isEdited) {
                this.visibleResources.add(res);
            }
        }
        List<RESOURCE> resources = this.visibleResources;
        int total = resources.size();
        int listTop = y += 28;
        int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
        int maxScroll = Math.max(0, total - visible);
        this.subsidyScroll = Math.max(0, Math.min(maxScroll, this.subsidyScroll + this.takeScroll()));
        this.scrollbar(r, listTop, this.win.y2() - 18, this.subsidyScroll, visible, total);
        this.line.clear().add((CharSequence)("" + (this.subsidyScroll + 1))).add((CharSequence)EconTexts.¤¤uiRange).add((CharSequence)("" + Math.min(total, this.subsidyScroll + visible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + total));
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, headerY, headerY + 12);
        for (int i = this.subsidyScroll; i < total && y + 30 < this.win.y2() - 18; ++i) {
            RESOURCE resource = resources.get(i);
            this.label.clear().add(resource.name);
            this.label.color(COLOR.WHITE150);
            this.label.render((SPRITE_RENDERER)r, x, x + resourceW - 8, y + 8, y + 22);
            int output = resource.index() < flow.size() ? (int)Math.round(flow.supplyPerDay(resource.index())) : 0;
            this.line.clear().add((CharSequence)CompactNumber.format(output));
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, outputX, sliderX - 8, y + 8, y + 22);
            int value = subsidies.bounty(resource);
            int next = this.slider(r, "sub_" + resource.key, sliderX, y, value, 0, EconConfig.productionSubsidyMax, 1);
            if (next != value) {
                subsidies.setBounty(resource, next);
            }
            this.valueField(r, "f_sub_" + resource.key, fieldX, y, fieldW, subsidies.bounty(resource), 0, EconConfig.productionSubsidyMax, v -> subsidies.setBounty(resource, v), EconTexts.¤¤uiPerUnit, subsidies.bounty(resource) > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
            y += 30;
        }
    }

    private void renderCitizens(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        HousingMarket housing = sim.housingMarket();

        // Title
        this.line.clear().add("BÜRGER & MIETMARKT / IMMOBILIEN");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 14);
        y += 22;

        // Stats summary line
        this.line.clear().add(EconTexts.¤¤housingRentCollected).add((CharSequence)CompactNumber.format(housing.lastRentCollected())).add(EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(housing.lastRentDue())).add(EconTexts.¤¤housingEvictions).add((CharSequence)CompactNumber.format(housing.lastEvictions()));
        this.line.color(housing.lastEvictions() > 0 ? COLOR.REDISH : COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 16;

        this.line.clear().add("Immobilien-Verkäufe (Staat): ").add((CharSequence)CompactNumber.format(sim.propertySalesCollected())).add(EconTexts.¤¤uiDenari).add("   Gilden-Dividenden: ").add((CharSequence)CompactNumber.format(sim.propertyDividendsPaid())).add(EconTexts.¤¤uiDenari);
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 22;

        // Housing Toggles
        EconConfig.housingMarketEnabled = this.toggle(r, x, y, 160, 22, EconConfig.housingMarketEnabled, EconConfig.housingMarketEnabled ? "Miete einziehen" : "Keine Miete");
        EconConfig.propertyMarketEnabled = this.toggle(r, x + 180, y, 180, 22, EconConfig.propertyMarketEnabled, EconConfig.propertyMarketEnabled ? "Immobilienmarkt AN" : "Immobilienmarkt AUS");
        EconConfig.homePurchaseEnabled = this.toggle(r, x + 380, y, 180, 22, EconConfig.homePurchaseEnabled, EconConfig.homePurchaseEnabled ? "Hauskauf erlaubt" : "Kein Hauskauf");
        y += 28;

        // Base rent slider
        this.label.clear().add("Miete/Kachel/Saison:");
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 160, y + 4, y + 18);
        EconConfig.housingBaseRentPerTile = this.slider(r, "h_rent", x + 160, y, EconConfig.housingBaseRentPerTile, 0, 20, 1);
        this.valueField(r, "f_h_rent", x + 160 + 260 + 18, y, 80, EconConfig.housingBaseRentPerTile, 0, 20, v -> EconConfig.housingBaseRentPerTile = v, EconTexts.¤¤uiDenari, COLOR.WHITE200);
        y += 26;

        // Eviction threshold slider
        this.label.clear().add("Räumung bei Schulden:");
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 160, y + 4, y + 18);
        EconConfig.housingEvictionDebtThreshold = this.slider(r, "h_thresh", x + 160, y, EconConfig.housingEvictionDebtThreshold, 10, 1000, 10);
        this.valueField(r, "f_h_thresh", x + 160 + 260 + 18, y, 80, EconConfig.housingEvictionDebtThreshold, 10, 1000, v -> EconConfig.housingEvictionDebtThreshold = v, EconTexts.¤¤uiDenari, COLOR.WHITE200);
        y += 26;

        // Grace seasons slider
        this.label.clear().add("Schonfrist (Saisons):");
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 160, y + 4, y + 18);
        EconConfig.housingGraceDays = this.slider(r, "h_grace", x + 160, y, EconConfig.housingGraceDays, 0, 10, 1);
        this.valueField(r, "f_h_grace", x + 160 + 260 + 18, y, 80, EconConfig.housingGraceDays, 0, 10, v -> EconConfig.housingGraceDays = v, " Saisons", COLOR.WHITE200);
        y += 32;

        // Richest Citizen & Wealth Stats Section
        this.line.clear().add("BÜRGER-VERMÖGEN & UNGLEICHHEIT");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 14);
        y += 20;

        Humanoid richest = sim.cachedRichestCitizen();
        if (richest == null) {
            this.line.clear().add(EconTexts.¤¤citNobodyMoney);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            return;
        }
        int money = sim.wallets().get(richest);
        int median = sim.stats().median;
        this.line.clear().add(EconTexts.¤¤citRichestHolds).add((CharSequence)CompactNumber.format(money)).add(EconTexts.¤¤uiDenari);
        if (median > 0) {
            this.line.add((CharSequence)EconTexts.¤¤citMedianPrefix).add((CharSequence)CompactNumber.format(money / Math.max(1, median))).add(EconTexts.¤¤citTimesMedian);
        }
        this.line.color(COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 16;

        this.line.clear().add(EconTexts.¤¤citMedian).add((CharSequence)CompactNumber.format(median)).add(EconTexts.¤¤citMean).add((CharSequence)CompactNumber.format(sim.stats().mean)).add(EconTexts.¤¤citGini).add((CharSequence)String.format("%.2f", sim.stats().gini));
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 24;

        if (this.button(r, x, y, 260, 30, EconTexts.¤¤citBtnJump)) {
            VIEW.s().ui.subjects.show(richest);
            VIEW.s().getWindow().centerAtTile(richest.tc().x(), richest.tc().y());
        }
        y += 36;
        this.line.clear().add(EconTexts.¤¤citJumpHint);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
    }

    private void renderStateWarehouses(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        StateWarehouses state = sim.stateWarehouses();
        this.line.clear().add(EconTexts.¤¤granBought).add((CharSequence)CompactNumber.format(state.lastUnitsBought())).add(EconTexts.¤¤granUnitsFor).add((CharSequence)CompactNumber.format(state.lastBought())).add(EconTexts.¤¤granSold).add((CharSequence)CompactNumber.format(state.lastUnitsSold())).add(EconTexts.¤¤granFor).add((CharSequence)CompactNumber.format(state.lastSold()));
        this.line.color(COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 16;
        this.line.clear().add(EconTexts.¤¤granClerksPaid).add((CharSequence)CompactNumber.format(state.lastWorkersPaid())).add(EconTexts.¤¤granWages).add((CharSequence)CompactNumber.format(state.lastWagesPaid()));
        if (state.lastWorkersUnpaid() > 0) {
            this.line.add(EconTexts.¤¤granUnpaid).add((CharSequence)CompactNumber.format(state.lastWorkersUnpaid()));
        }
        this.line.color(state.lastWorkersUnpaid() > 0 ? COLOR.REDISH : COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        this.label.clear().add(EconTexts.¤¤granSalary);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 240, (y += 18) + 8, y + 22);
        int wage = state.wage();
        int nextWage = this.slider(r, "state_wage", x + 250, y, wage, 0, EconConfig.wageMax, EconConfig.wageStep);
        if (nextWage != wage) {
            state.setWage(nextWage);
        }
        this.valueField(r, "f_state_wage", x + 250 + 260 + 18, y, 150, state.wage(), 0, EconConfig.wageMax, state::setWage, EconTexts.¤¤uiDenari, state.wage() > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
        y += 34;
        int owned = state.ownedCount();
        this.line.clear().add(EconTexts.¤¤granStateCount).add((CharSequence)CompactNumber.format(owned)).add(EconTexts.¤¤uiOf).add((CharSequence)CompactNumber.format(EconProgression.reliableStockpileCount())).add(EconTexts.¤¤granWarning);
        this.line.color(owned == 0 ? COLOR.REDISH : COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 14;
        if (owned > 0) {
            boolean allLiq = state.allLiquidating();
            boolean nextAll = this.toggle(r, x, y, 200, 26, allLiq, EconTexts.¤¤granBtnLiqAll);
            if (nextAll != allLiq) {
                state.setAllLiquidating(nextAll);
            }
            y += 30;
        }
        List<StockpileInstance> ordered = sim.cachedStateWarehouses();
        int listCount = ordered.size();
        int listHeight = Math.min(4, Math.max(1, listCount)) * 30;
        int ownerTop = y;
        int ownerVisible = Math.max(1, listHeight / 30);
        int ownerMax = Math.max(0, listCount - ownerVisible);
        this.granaryScroll = Math.max(0, Math.min(ownerMax, this.granaryScroll + this.takeScroll(this.hit(this.ownerRec(ownerTop, listHeight)))));
        this.scrollbar(r, ownerTop, ownerTop + listHeight, this.granaryScroll, ownerVisible, listCount);
        for (int idx = this.granaryScroll; idx < listCount && y + 30 <= ownerTop + listHeight; ++idx) {
            StockpileInstance warehouse = (StockpileInstance)ordered.get(idx);
            boolean owns = state.isStateOwned((RoomInstance)warehouse);
            boolean liquid = state.isLiquidating((RoomInstance)warehouse);
            boolean nextLiq;
            this.label.clear().add((CharSequence)warehouse.name());
            this.label.color(owns ? COLOR.WHITE200 : COLOR.WHITE120);
            this.label.render((SPRITE_RENDERER)r, x, x + 240, y + 8, y + 22);
            boolean next = this.toggle(r, x + 250, y + 4, 110, 22, owns, owns ? EconTexts.¤¤granBtnState : EconTexts.¤¤granBtnPrivate);
            if (next != owns) {
                state.setStateOwned((RoomInstance)warehouse, next);
            }
            if (owns && (nextLiq = this.toggle(r, x + 370, y + 4, 150, 22, liquid, liquid ? EconTexts.¤¤granBtnLiq : EconTexts.¤¤granBtnHoard)) != liquid) {
                state.setLiquidating((RoomInstance)warehouse, nextLiq);
            }
            y += 30;
        }
        int headerY = y = ownerTop + listHeight + 6;
        boolean prevShowZeroRows = this.showZeroRows;
        this.showZeroRows = this.toggle(r, x, y, 180, 22, this.showZeroRows, EconTexts.¤¤uiShowZeroRows);
        if (this.showZeroRows != prevShowZeroRows) {
            this.stateScroll = 0;
        }
        int maxAvailable = (this.win.x2() - 18) - x;
        int resourceW = 180;
        int fieldW = 160;
        int gap = 10;
        int buyX = x + resourceW;
        int sellX = buyX + fieldW + gap;
        int infoX = sellX + fieldW + gap;
        this.visibleResources.clear();
        List<RESOURCE> allResources = sim.cachedAllResources();
        for (RESOURCE res : allResources) {
            boolean active = state.buyPrice(res) > 0 || state.sellPrice(res) > 0 || sim.warehouseMarket().stateStock(res) > 0;
            boolean isEdited = Objects.equals(this.editing, "f_buy_" + res.key) || Objects.equals(this.editing, "f_sell_" + res.key);
            if (this.showZeroRows || active || isEdited) {
                this.visibleResources.add(res);
            }
        }
        List<RESOURCE> resources = this.visibleResources;
        int total = resources.size();
        int listTop = y += 28;
        int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
        int maxScroll = Math.max(0, total - visible);
        this.stateScroll = Math.max(0, Math.min(maxScroll, this.stateScroll + this.takeScroll(this.hit(this.win))));
        this.scrollbar(r, listTop, this.win.y2() - 18, this.stateScroll, visible, total);
        this.line.clear().add((CharSequence)("" + (this.stateScroll + 1))).add((CharSequence)EconTexts.¤¤uiRange).add((CharSequence)("" + Math.min(total, this.stateScroll + visible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + total));
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, headerY, headerY + 12);
        for (int i2 = this.stateScroll; i2 < total && y + 30 < this.win.y2() - 18; ++i2) {
            RESOURCE resource = resources.get(i2);
            int buy = state.buyPrice(resource);
            int sell = state.sellPrice(resource);
            boolean active = buy > 0 || sell > 0;
            this.label.clear().add(resource.name);
            this.label.color(active ? COLOR.WHITE200 : COLOR.WHITE100);
            this.label.render((SPRITE_RENDERER)r, x, x + resourceW - 8, y + 8, y + 22);
            this.valueField(r, "f_buy_" + resource.key, buyX, y, fieldW, buy, 0, EconConfig.statePriceMax, v -> state.setBuyPrice(resource, v), EconTexts.¤¤granBuyAt, "", buy > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
            this.valueField(r, "f_sell_" + resource.key, sellX, y, fieldW, sell, 0, EconConfig.statePriceMax, v -> state.setSellPrice(resource, v), EconTexts.¤¤granSellAt, "", sell > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
            int stock = sim.warehouseMarket().stateStock(resource);
            this.line.clear().add(EconTexts.¤¤granStores).add((CharSequence)CompactNumber.format(stock)).add(EconTexts.¤¤granInStateHands);
            this.line.color(stock > 0 ? COLOR.WHITE150 : COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, infoX, this.win.x2() - 18 - 12, y + 8, y + 22);
            y += 30;
        }
    }

    private void renderCrownMarket(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        int maxAvailable = (this.win.x2() - 18) - x;
        int resourceW = 180;
        int sliderW = 260;
        int fieldW = 130;
        int gapResourceSlider = 10;
        int gapSliderValue = 16;
        int gapValueInfo = 14;
        int infoW = Math.max(120, maxAvailable - resourceW - sliderW - fieldW - gapResourceSlider - gapSliderValue - gapValueInfo);
        int sliderX = x + resourceW + gapResourceSlider;
        int valueX = sliderX + sliderW + gapSliderValue;
        int infoX = valueX + fieldW + gapValueInfo;
        StateWarehouses state = sim.stateWarehouses();
        this.line.clear().add(EconTexts.¤¤mrkSold).add((CharSequence)CompactNumber.format(state.lastCrownMarketUnitsSold())).add(EconTexts.¤¤granUnitsFor).add((CharSequence)CompactNumber.format(state.lastCrownMarketSold()));
        this.line.color(COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        this.line.clear().add(EconTexts.¤¤mrkHint);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 16, y + 12);
        boolean prevShowZeroRows = this.showZeroRows;
        this.showZeroRows = this.toggle(r, x, y, 180, 22, this.showZeroRows, EconTexts.¤¤uiShowZeroRows);
        if (this.showZeroRows != prevShowZeroRows) {
            this.marketScroll = 0;
        }
        this.visibleResources.clear();
        List<RESOURCE> allResources = sim.cachedAllResources();
        for (RESOURCE res : allResources) {
            boolean active = state.crownMarketPrice(res) > 0 || sim.warehouseMarket().crownUnits(res) > 0;
            boolean isEdited = Objects.equals(this.editing, "f_crown_market_" + res.key) || Objects.equals(this.grabbed, "crown_market_" + res.key);
            if (this.showZeroRows || active || isEdited) {
                this.visibleResources.add(res);
            }
        }
        List<RESOURCE> resources = this.visibleResources;
        int total = resources.size();
        int listTop = y += 28;
        int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
        int maxScroll = Math.max(0, total - visible);
        this.marketScroll = Math.max(0, Math.min(maxScroll, this.marketScroll + this.takeScroll()));
        this.scrollbar(r, listTop, this.win.y2() - 18, this.marketScroll, visible, total);
        for (int i = this.marketScroll; i < total && y + 30 < this.win.y2() - 18; ++i) {
            RESOURCE resource = resources.get(i);
            int posted = state.crownMarketPrice(resource);
            this.label.clear().add(resource.name);
            this.label.color(COLOR.WHITE150);
            this.label.render((SPRITE_RENDERER)r, x, x + resourceW - 8, y + 8, y + 22);
            int next = this.logSlider(r, "crown_market_" + resource.key, sliderX, y, posted, 0, EconConfig.statePriceMax);
            if (next != posted) {
                state.setCrownMarketPrice(resource, next);
            }
            this.valueField(r, "f_crown_market_" + resource.key, valueX, y, fieldW, posted, 0, EconConfig.statePriceMax, v -> state.setCrownMarketPrice(resource, v), "", EconTexts.¤¤uiPerUnitShort, posted == 75 ? COLOR.WHITE150 : COLOR.WHITE200);
            int live = sim.flowPrices().priceRoundedUp(resource.index());
            long crown = sim.warehouseMarket().crownUnits(resource);
            this.line.clear().add(EconTexts.¤¤mrkLiveMarket).add((CharSequence)CompactNumber.format(live)).add(EconTexts.¤¤mrkCrownUnits).add((CharSequence)CompactNumber.format(crown));
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, infoX, this.win.x2() - 18 - 12, y + 8, y + 22);
            y += 30;
        }
    }

    private Rec ownerRec(int top, int height) {
        this.ownerScratch.setDim((double)(this.win.x2() - this.win.x1()), (double)height);
        this.ownerScratch.moveX1Y1((double)this.win.x1(), (double)top);
        return this.ownerScratch;
    }

    private void beginEdit(Object id, int value, int min, int max, Setter set) {
        this.editing = id;
        this.pendingSet = set;
        this.pendingMin = min;
        this.pendingMax = max;
        this.input.del();
        this.input.placeHolder((CharSequence)("" + value));
        this.input.listen();
    }

    private void commit() {
        if (this.pendingSet != null) {
            try {
                String typed = this.input.text().toString().trim();
                if (typed.isEmpty()) {
                    this.cancelEdit();
                    return;
                }
                int v = Integer.parseInt(typed);
                if (v < this.pendingMin) {
                    v = this.pendingMin;
                }
                if (v > this.pendingMax) {
                    v = this.pendingMax;
                }
                this.pendingSet.set(v);
            }
            catch (NumberFormatException numberFormatException) {
                // intentionally empty: invalid user input keeps old value
            }
        }
        this.cancelEdit();
    }

    private void cancelEdit() {
        this.editing = null;
        this.pendingSet = null;
        CORE.getInput().clearAllInput();
    }

    private void valueField(Renderer r, Object id, int x, int y, int w, int value, int min, int max, Setter set, String suffix, COLOR col) {
        this.valueField(r, id, x, y, w, value, min, max, set, "", suffix, col);
    }

    private void valueField(Renderer r, Object id, int x, int y, int w, int value, int min, int max, Setter set, String prefix, String suffix, COLOR col) {
        this.editBox.setDim((double)w, 24.0);
        this.editBox.moveX1Y1((double)x, (double)(y + 3));
        boolean over = this.hit(this.editBox);
        boolean active = Objects.equals(this.editing, id);
        if (this.leftClicked && over && !active) {
            if (this.editing != null) {
                this.commit();
            }
            this.beginEdit(id, value, min, max, set);
            this.clickedAField = true;
        }
        if (active) {
            this.clickedAField |= over;
        }
        if (active) {
            COLOR.WHITE35.render((SPRITE_RENDERER)r, this.editBox.x1(), this.editBox.x2(), this.editBox.y1(), this.editBox.y2());
            COLOR.WHITE120.render((SPRITE_RENDERER)r, this.editBox.x1(), this.editBox.x2(), this.editBox.y1(), this.editBox.y1() + 1);
            COLOR.WHITE120.render((SPRITE_RENDERER)r, this.editBox.x1(), this.editBox.x2(), this.editBox.y2() - 1, this.editBox.y2());
            this.input.listen();
            this.input.render((SPRITE_RENDERER)r, this.editBox.x1() + 6, this.editBox.y1() + 4);
            return;
        }
        if (over) {
            COLOR.WHITE25.render((SPRITE_RENDERER)r, this.editBox.x1(), this.editBox.x2(), this.editBox.y1(), this.editBox.y2());
        }
        this.line.clear().add((CharSequence)prefix).add((CharSequence)("" + value)).add((CharSequence)suffix);
        this.line.color(over ? COLOR.WHITE200 : col);
        this.line.render((SPRITE_RENDERER)r, this.editBox.x1() + 6, this.editBox.x2(), this.editBox.y1() + 4, this.editBox.y2());
    }

    private int stateWageRow(Renderer r, int x, int y, StateWageMarket.Entry e) {
        this.label.clear().add((CharSequence)e.name);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 150, y + 8, y + 22);
        int w = this.slider(r, "sw_" + e.name, x + 160, y, e.wage(), 0, EconConfig.wageMax, EconConfig.wageStep);
        if (w != e.wage()) {
            e.setWage(w);
        }
        this.valueField(r, "f_sw_" + e.name, x + 160 + 260 + 18, y, 100, e.wage(), 0, EconConfig.wageMax, e::setWage, EconTexts.¤¤uiPerDay, e.wage() > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add(EconTexts.¤¤wageRowLast).add((CharSequence)CompactNumber.format(e.lastPaid())).add(EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(e.lastDue())).add(EconTexts.¤¤wageRowDueTo).add((CharSequence)CompactNumber.format(e.lastWorkers())).add(EconTexts.¤¤wageRowWorkers).add((CharSequence)(e.treasuryBlocked() ? EconTexts.¤¤wageRowTreasuryShort : ""));
        this.line.color(e.treasuryBlocked() ? COLOR.REDISH : COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x + 160 + 260 + 130, this.win.x2(), y + 8, y + 22);
        return y + 30 + 2;
    }

    private int slider(Renderer r, Object id, int x, int y, int value, int min, int max, int step) {
        double frac;
        int fill;
        boolean over;
        int x2 = x + 260;
        int cy = y + 15 - 5;
        COLOR.WHITE25.render((SPRITE_RENDERER)r, x, x2, cy, cy + 10);
        boolean bl = over = this.mouseX >= x - 4 && this.mouseX <= x2 + 4 && this.mouseY >= y && this.mouseY <= y + 30;
        if (over && MButt.LEFT.isDown() && this.grabbed == null) {
            this.grabbed = id;
            this.grabX1 = x;
            this.grabX2 = x2;
        }
        if (Objects.equals(this.grabbed, id) && MButt.LEFT.isDown()) {
            double t = (double)(this.mouseX - this.grabX1) / (double)(this.grabX2 - this.grabX1);
            int v = (int)Math.round((double)min + (t = Math.max(0.0, Math.min(1.0, t))) * (double)(max - min));
            value = v / step * step;
            if (value < min) {
                value = min;
            }
            if (value > max) {
                value = max;
            }
        }
        if ((fill = (int)((double)x + (frac = max > min ? (double)(value - min) / (double)(max - min) : 0.0) * 260.0)) > x) {
            COLOR.WHITE120.render((SPRITE_RENDERER)r, x, fill, cy, cy + 10);
        }
        int knob = Math.max(x, Math.min(x2 - 10, fill - 5));
        boolean active = Objects.equals(this.grabbed, id) || over;
        (active ? COLOR.GREENISH : COLOR.WHITE150).render((SPRITE_RENDERER)r, knob, knob + 10, cy - 3, cy + 10 + 3);
        return value;
    }

    private int logSlider(Renderer r, Object id, int x, int y, int value, int max) {
        return this.logSlider(r, id, x, y, value, 0, max);
    }

    private int logSlider(Renderer r, Object id, int x, int y, int value, int min, int max) {
        double frac;
        int fill;
        boolean over;
        int x2 = x + 260;
        int cy = y + 15 - 5;
        if (max <= min) {
            return min;
        }
        double span = Math.log1p(max - min);
        COLOR.WHITE25.render((SPRITE_RENDERER)r, x, x2, cy, cy + 10);
        boolean bl = over = this.mouseX >= x - 4 && this.mouseX <= x2 + 4 && this.mouseY >= y && this.mouseY <= y + 30;
        if (over && MButt.LEFT.isDown() && this.grabbed == null) {
            this.grabbed = id;
            this.grabX1 = x;
            this.grabX2 = x2;
        }
        if (Objects.equals(this.grabbed, id) && MButt.LEFT.isDown()) {
            double t = (double)(this.mouseX - this.grabX1) / (double)(this.grabX2 - this.grabX1);
            value = min + (int)Math.round(Math.expm1((t = Math.max(0.0, Math.min(1.0, t))) * span));
            if (value < min) {
                value = min;
            }
            if (value > max) {
                value = max;
            }
        }
        if ((fill = (int)((double)x + (frac = span > 0.0 ? Math.log1p(Math.max(0, value - min)) / span : 0.0) * 260.0)) > x) {
            COLOR.WHITE120.render((SPRITE_RENDERER)r, x, fill, cy, cy + 10);
        }
        int knob = Math.max(x, Math.min(x2 - 10, fill - 5));
        boolean active = Objects.equals(this.grabbed, id) || over;
        (active ? COLOR.GREENISH : COLOR.WHITE150).render((SPRITE_RENDERER)r, knob, knob + 10, cy - 3, cy + 10 + 3);
        return value;
    }

    private boolean button(Renderer r, int x, int y, int w, int h, CharSequence text) {
        boolean clicked;
        this.toggleBox.setDim((double)w, (double)h);
        this.toggleBox.moveX1Y1((double)x, (double)y);
        boolean over = this.hit(this.toggleBox);
        boolean bl = clicked = this.leftClicked && over;
        if (clicked) {
            this.clickedAField = true;
        }
        (clicked ? COLOR.WHITE100 : (over ? COLOR.WHITE50 : COLOR.WHITE25)).render((SPRITE_RENDERER)r, x, x + w, y, y + h);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y, y + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y + h - 1, y + h);
        this.label.clear().add(text);
        this.label.color(over ? COLOR.WHITE200 : COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x + 8, x + w, y + (h - 12) / 2, y + h);
        return clicked;
    }

    private boolean toggle(Renderer r, int x, int y, int w, int h, boolean on, CharSequence text) {
        this.toggleBox.setDim((double)w, (double)h);
        this.toggleBox.moveX1Y1((double)x, (double)y);
        boolean over = this.hit(this.toggleBox);
        if (this.leftClicked && over) {
            on = !on;
            this.clickedAField = true;
        }
        (on ? COLOR.WHITE150 : (over ? COLOR.WHITE35 : COLOR.WHITE20)).render((SPRITE_RENDERER)r, x, x + w, y, y + h);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y, y + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y + h - 1, y + h);
        if (text != null && text.length() > 0) {
            this.label.clear().add(text);
            this.label.color(on ? COLOR.BLACK : (over ? COLOR.WHITE150 : COLOR.WHITE120));
            this.label.render((SPRITE_RENDERER)r, x + 6, x + w, y + (h - 12) / 2, y + h);
        }
        return on;
    }

    private void renderDashboard(Renderer r, int y, EconomySim sim) {
        if (sim != this.lastDashboardSim) {
            this.treasuryChart.clear();
            this.treasuryChart.add(sim.treasuryHistory(), 1.0, COLOR.GREENISH, EconTexts.¤¤dashboardTreasury);
            this.treasuryChart.title(EconTexts.¤¤dashboardTreasuryChart);
            this.giniChart.clear();
            this.giniChart.add(sim.giniHistory(), 1.0, COLOR.YELLOW100, EconTexts.¤¤dashboardGini);
            this.giniChart.title(EconTexts.¤¤dashboardGiniChart);
            this.lastDashboardSim = sim;
        }
        int x = this.win.x1() + 18;
        int w = this.winW() - 36;
        WealthStats s = sim.stats();

        // Section title
        this.line.clear().add(EconTexts.¤¤dashboardHeader);
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 14);
        y += 22;

        // KPI tiles: [Kasse] [Gini] [Bürger] [Wachstum]
        int tileCount = 4;
        int tileGap = 12;
        int tileW = Math.min(200, (w - (tileCount - 1) * tileGap) / tileCount);
        int tileH = 70;
        String[] kpiLabels = {EconTexts.¤¤dashboardTreasury, EconTexts.¤¤dashboardGini, EconTexts.¤¤dashboardCitizens, EconTexts.¤¤dashboardStage};
        String[] kpiValues = {
            CompactNumber.format(sim.treasury()),
            String.format("%.2f", s.gini),
            CompactNumber.format(sim.roster().size()),
            sim.progression().stage.displayName
        };
        COLOR[] kpiColors = {COLOR.GREENISH, COLOR.YELLOW100, COLOR.WHITE150, COLOR.WHITE150};
        for (int i = 0; i < tileCount; ++i) {
            int tx = x + i * (tileW + tileGap);
            COLOR.WHITE20.render((SPRITE_RENDERER)r, tx, tx + tileW, y, y + tileH);
            COLOR.WHITE35.render((SPRITE_RENDERER)r, tx, tx + tileW, y, y + 1);
            COLOR.WHITE35.render((SPRITE_RENDERER)r, tx, tx + tileW, y + tileH - 1, y + tileH);
            this.label.clear().add(kpiLabels[i]);
            this.label.color(COLOR.WHITE120);
            this.label.render((SPRITE_RENDERER)r, tx + 8, tx + tileW - 8, y + 8, y + 22);
            this.line.clear().add(kpiValues[i]);
            this.line.color(kpiColors[i]);
            this.line.render((SPRITE_RENDERER)r, tx + 8, tx + tileW - 8, y + 32, y + 58);
        }
        y += tileH + 22;

        // Charts
        int chartW = (w - 24) / 2;
        int chartH = Math.max(160, (this.win.y2() - 18 - y - 24) / 2);

        this.treasuryChart.body().setDim(chartW, chartH);
        this.treasuryChart.body().moveX1Y1(x, y);
        this.treasuryChart.render((SPRITE_RENDERER)r, 0.0f, false);

        this.giniChart.body().setDim(chartW, chartH);
        this.giniChart.body().moveX1Y1(x + chartW + 24, y);
        this.giniChart.render((SPRITE_RENDERER)r, 0.0f, false);
    }

    private void renderDistribution(Renderer r, int y, EconomySim sim) {
        WealthStats s = sim.stats();
        int x = this.win.x1() + 18;
        int cw = this.winW() - 36;
        if (s.people == 0) {
            this.line.clear().add(EconTexts.¤¤wealthNoSettlers);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
            return;
        }

        // Title & Summary line
        this.line.clear().add("VERMÖGENS-VERTEILUNG & SOZIALSTRUKTUR");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 14);
        y += 22;

        this.line.clear()
            .add((CharSequence)CompactNumber.format(s.people)).add(EconTexts.¤¤wealthSettlersMean).add((CharSequence)CompactNumber.format(s.mean))
            .add(EconTexts.¤¤wealthMedian).add((CharSequence)CompactNumber.format(s.median))
            .add(EconTexts.¤¤wealthRichest).add((CharSequence)CompactNumber.format(s.max))
            .add(EconTexts.¤¤wealthGini).add((CharSequence)EconomyWindow.fmt2(s.gini));
        this.line.color(COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        y += 24;

        // 4 Social Class Cards (Unterschicht, Mittelstand, Wohlhabend, Elite)
        int cardGap = 10;
        int cardW = (cw - 3 * cardGap) / 4;
        int cardH = 54;
        int median = Math.max(1, s.median);

        int poorCount = 0, midCount = 0, richCount = 0, eliteCount = 0;
        for (int i = 0; i < sim.roster().size(); i++) {
                Humanoid h = sim.roster().get(i);
                int w = sim.wallets().get(h);
                if (w < median / 2) poorCount++;
                else if (w <= median * 2) midCount++;
                else if (w <= median * 5) richCount++;
                else eliteCount++;
            }

        int cx = x;
        this.kpiBox(r, cx, y, cardW, cardH, "UNTERSCHICHT (<0.5x Med)", CompactNumber.format(poorCount) + " Siedler");
        cx += cardW + cardGap;
        this.kpiBox(r, cx, y, cardW, cardH, "MITTELSTAND (0.5-2x Med)", CompactNumber.format(midCount) + " Siedler");
        cx += cardW + cardGap;
        this.kpiBox(r, cx, y, cardW, cardH, "WOHLHABEND (2-5x Med)", CompactNumber.format(richCount) + " Siedler");
        cx += cardW + cardGap;
        this.kpiBox(r, cx, y, cardW, cardH, "ELITE (>5x Median)", CompactNumber.format(eliteCount) + " Siedler");
        y += cardH + 20;

        // Histogram Chart
        this.label.clear().add("VERMÖGENS-HISTOGRAMM (Siedler pro Vermögensklasse)");
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + cw, y, y + 12);
        y += 18;

        this.chart(r, x, y, cw, 160, s);

        // —— CitizenClass: echte 6-Klassen-Verteilung (nicht nur Vermögens-Brackets) ——
        int classY = y + 174;
        if (classY + 12 < this.win.y2() - 18 && sim != null) {
            this.line.clear().add(EconTexts.¤¤classHeader);
            this.line.color(COLOR.WHITE150);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, classY, classY + 12);
            classY += 18;

            int[] cc = new int[CitizenClass.values().length];
            long[] cwSum = new long[CitizenClass.values().length];
            Roster rstr = sim.roster();
            Wallets wts = sim.wallets();
            for (int i = 0; i < rstr.size(); ++i) {
                CitizenClass cl = wts.classOf(rstr.get(i));
                int idx = cl.ordinal();
                cc[idx]++;
                cwSum[idx] += wts.get(rstr.get(i));
            }

            int clGap = 10;
            int perRow = 3;
            int clW = (cw - clGap * (perRow - 1)) / perRow;
            int clH = 46;
            CitizenClass[] allC = CitizenClass.values();
            for (int i = 0; i < allC.length; ++i) {
                CitizenClass cl = allC[i];
                if (cl == CitizenClass.UNCLASSIFIED) continue;
                int col = i % perRow;
                int row = i / perRow;
                int clX = x + col * (clW + clGap);
                int clY = classY + row * (clH + 6);
                int cnt = cc[cl.ordinal()];
                long totalW = cwSum[cl.ordinal()];
                String val = CompactNumber.format(cnt) + " Bgr";
                if (cnt > 0) {
                    val = val + " · Ø" + CompactNumber.format(totalW / (long)Math.max(1, cnt));
                }
                kpiBox(r, clX, clY, clW, clH, cl.displayName, val);
            }
        }
    }

    private void chart(Renderer r, int x, int y, int w, int h, WealthStats s) {
        int base = y + h;
        COLOR.WHITE25.render((SPRITE_RENDERER)r, x, x + w, base, base + 1);
        int n = 16;
        double bw = (double)w / (double)n;
        for (int i = 0; i < n; ++i) {
            int c = s.histogram[i];
            int x2 = (int)((double)x + (double)(i + 1) * bw) - 1;
            int x1 = (int)((double)x + (double)i * bw) + 1;
            if (x2 <= x1) {
                x2 = x1 + 1;
            }
            if (c > 0) {
                int bh = (int)Math.round((double)c / (double)s.tallest * (double)(h - 2));
                if (bh < 1) {
                    bh = 1;
                }
                COLOR col = i >= n * 3 / 4 ? COLOR.WHITE200 : COLOR.WHITE100;
                col.render((SPRITE_RENDERER)r, x1, x2, base - bh, base);
            }
            if (this.mouseX < x1 || this.mouseX > x2 || this.mouseY < y || this.mouseY > base) continue;
            COLOR.WHITE50.render((SPRITE_RENDERER)r, x1, x2, y, base);
            this.line.clear().add((CharSequence)CompactNumber.format((long)i * (long)s.bucketWidth)).add((CharSequence)EconTexts.¤¤uiRange).add((CharSequence)CompactNumber.format((long)(i + 1) * (long)s.bucketWidth)).add((CharSequence)EconTexts.¤¤uiColon).add((CharSequence)CompactNumber.format(c));
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, x + w, y - 14, y - 2);
        }
        this.line.clear().add(EconTexts.¤¤wealthZeroDenari);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, x + 80, base + 4, base + 16);
        this.line.clear().add((CharSequence)CompactNumber.format((long)s.bucketWidth * 16L));
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x + w - 60, x + w, base + 4, base + 16);
    }

    private void statusLight(Renderer r, int x, int y, boolean ok) {
        COLOR col = ok ? COLOR.WHITE200 : COLOR.REDISH;
        col.render((SPRITE_RENDERER)r, x, x + 10, y, y + 10);
    }

    private void sparkline(Renderer r, int x, int y, int w, int h, double[] values, int count) {
        if (count < 2) {
            return;
        }
        double min = values[0];
        double max = values[0];
        for (int i = 1; i < count; ++i) {
            if (values[i] < min) min = values[i];
            if (values[i] > max) max = values[i];
        }
        if (max <= min) {
            max = min + 1.0;
        }
        int base = y + h;
        int barW = Math.max(2, w / count);
        for (int i = 0; i < count; ++i) {
            int barH = (int)Math.round((values[i] - min) / (max - min) * (double)(h - 2));
            if (barH < 1) {
                barH = 1;
            }
            int bx = x + i * barW;
            COLOR.WHITE100.render((SPRITE_RENDERER)r, bx, bx + barW - 1, base - barH, base);
        }
    }

    private void renderWages(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        FirmLedger ledger = sim.firmLedger();
        List<RoomBlueprintImp> jobs = sim.cachedWorkplaces();
        this.line.clear().add(EconTexts.¤¤wageSurplusDue).add((CharSequence)CompactNumber.format(ledger.lastIncomeDue())).add(EconTexts.¤¤wagePaid).add((CharSequence)CompactNumber.format(ledger.lastIncomePaid())).add(EconTexts.¤¤wageMeanMarginal).add((CharSequence)CompactNumber.format(ledger.meanPositiveMarginal())).add("  B-Lohn ").add((CharSequence)CompactNumber.format((long)sim.laborMarket().meanWage()));
this.line.color(ledger.lastWorkersUnpaid() > 0 ? COLOR.REDISH : COLOR.WHITE150);
this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
y += 14;
this.line.clear().add("Echter Bürgerlohn (Ø letzte Saison): ").add((CharSequence)CompactNumber.format(sim.wages().lastWorkersPaid() > 0 ? (long)(sim.wages().lastPayrollPaid() / sim.wages().lastWorkersPaid()) : 0L)).add("/Tag");
this.line.color(COLOR.WHITE120);
this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
y += 14;
        if (ledger.lastWorkersUnpaid() > 0) {
            this.line.clear().add(EconTexts.¤¤wageInsolvent).add((CharSequence)CompactNumber.format(ledger.lastWorkersUnpaid())).add(EconTexts.¤¤wageInsolHint);
            this.line.color(COLOR.REDISH);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        }
        y += 16;
        if (jobs.isEmpty()) {
            this.line.clear().add(EconTexts.¤¤wageNoWorkplaces);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
            return;
        }
        int listTop = y;
        int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
        int maxScroll = Math.max(0, jobs.size() - visible);
        this.wageScroll = Math.max(0, Math.min(maxScroll, this.wageScroll + this.takeScroll()));
        this.scrollbar(r, listTop, this.win.y2() - 18, this.wageScroll, visible, jobs.size());
        for (int i = this.wageScroll; i < jobs.size() && y + 30 < this.win.y2() - 18; ++i) {
            RoomBlueprintImp b = (RoomBlueprintImp)jobs.get(i);
            this.label.clear().add(b.info.name);
            this.label.color(COLOR.WHITE150);
            this.label.render((SPRITE_RENDERER)r, x, x + 200, y + 8, y + 22);
            if (EconomicRoles.stateFundedMilitary(b)) {
                int prio = sim.laborMarket().derivedPriority(b);
                this.line.clear().add(EconTexts.¤¤wageStateFunded).add((CharSequence)CompactNumber.format(b.employment().employed())).add(EconTexts.¤¤wageSalary).add((CharSequence)CompactNumber.format(sim.militaryPayroll().wage())).add(EconTexts.¤¤uiPerSeason).add((CharSequence)(prio >= 0 ? EconTexts.¤¤wagePrio + prio : ""));
                this.line.color(COLOR.WHITE120);
                this.line.render((SPRITE_RENDERER)r, x + 220, this.win.x2() - 18, y + 8, y + 22);
                y += 30;
                continue;
            }
            double profit = ledger.profitPerDay(b);
            double marginal = ledger.marginalSurplus(b);
            int prio = sim.laborMarket().derivedPriority(b);
            int target = 0;
            if (b instanceof RoomBlueprintIns) {
                RoomBlueprintIns workplace = (RoomBlueprintIns)b;
                for (int ri = 0; ri < workplace.instancesSize(); ++ri) {
                    target += workplace.getInstance(ri).employees().needed();
                }
            }
            this.line.clear().add(EconTexts.¤¤wageProfitDay).add((CharSequence)CompactNumber.format(profit)).add(EconTexts.¤¤wageMarginal).add((CharSequence)CompactNumber.format(marginal)).add(EconTexts.¤¤wageWorkers).add((CharSequence)CompactNumber.format(b.employment().employed())).add((CharSequence)EconTexts.¤¤uiSlashShort).add((CharSequence)CompactNumber.format(target));
            if (EconConfig.laborMarketEnabled && prio >= 0) {
                this.line.add(EconTexts.¤¤wagePrio).add((CharSequence)("" + prio));
            }
            this.line.color(profit < 0.0 ? COLOR.REDISH : COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x + 220, this.win.x2() - 18, y + 8, y + 22);
            y += 30;
        }

        // ── LABOR PRIORITIES (Ticket 1 v1.7.2) ──
        // Show derived priority + scarcity boost per workplace.
        // This is the LaborMarket engine that was previously invisible to the player.
        if (EconConfig.laborMarketEnabled && jobs != null && !jobs.isEmpty()) {
            y += 8;
            this.line.clear().add("-- Arbeitsprioritäten (LaborMarket) --");
            this.line.color(COLOR.WHITE120);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 18;
            for (RoomBlueprintImp b : jobs) {
                if (!(b instanceof RoomBlueprintIns)) continue;
                int prio = sim.laborMarket().derivedPriority(b);
                double scarcity = sim.laborMarket().scarcityForBlueprint(b);
                // Only show if there's meaningful data
                if (prio < 0 && scarcity <= 0.0) continue;
                this.line.clear();
                this.line.add(b.key);
                if (prio >= 0) {
                    this.line.add("  Priorität ").add((CharSequence)("" + prio));
                }
                if (scarcity > 0.0) {
                    this.line.add("  Knappheit ").add((CharSequence)String.format("%.0f%%", scarcity * 100.0));
                }
                this.line.color(scarcity > 0.05 ? COLOR.WHITE200 : COLOR.WHITE100);
                this.line.render((SPRITE_RENDERER)r, x + 10, this.win.x2() - 18, y, y + 12);
                y += 14;
            }
        }
    }

    private void renderTaxes(Renderer r, int y, EconomySim sim) {
        int thr;
        int lint;
        int lcount;
        int x = this.win.x1() + 18;
        Fiscal fiscal = sim.fiscal();
        this.label.clear().add(EconTexts.¤¤taxPerAdult);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, y + 8, y + 22);
        int head = EconConfig.perHeadTax;
        int newHead = this.logSlider(r, "headtax", x + 200, y, head, 0, 50000);
        if (newHead != head) {
            EconConfig.perHeadTax = newHead;
        }
        this.valueField(r, "f_headtax", x + 200 + 260 + 18, y, 130, EconConfig.perHeadTax, 0, 50000, v -> {
            EconConfig.perHeadTax = v;
        }, EconTexts.¤¤uiDenari, EconConfig.perHeadTax > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
        this.label.clear().add(EconTexts.¤¤taxMarketSkim);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        int percent = (int)Math.round(EconConfig.marketTaxRate * 100.0);
        int newPercent = this.slider(r, "marketskim", x + 200, y, percent, 0, 100, 1);
        if (newPercent != percent) {
            EconConfig.marketTaxRate = (double)newPercent / 100.0;
        }
        this.valueField(r, "f_marketskim", x + 200 + 260 + 18, y, 90, newPercent, 0, 100, v -> {
            EconConfig.marketTaxRate = (double)v / 100.0;
        }, "%", newPercent > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
        this.label.clear().add(EconTexts.¤¤taxWarehouseStock);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        int stockTax = EconConfig.warehouseTaxPercent;
        int newStockTax = this.slider(r, "warehousetax", x + 200, y, stockTax, 0, 100, 1);
        if (newStockTax != stockTax) {
            EconConfig.warehouseTaxPercent = newStockTax;
        }
        this.valueField(r, "f_warehousetax", x + 200 + 260 + 18, y, 90, EconConfig.warehouseTaxPercent, 0, 100, v -> {
            EconConfig.warehouseTaxPercent = v;
        }, "%", EconConfig.warehouseTaxPercent > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add(EconTexts.¤¤taxCollectedHead).add((CharSequence)CompactNumber.format(fiscal.headTaxCollected())).add(EconTexts.¤¤taxCollectedMarket).add((CharSequence)CompactNumber.format(fiscal.marketReceipts())).add(EconTexts.¤¤taxCollectedWarehouse).add((CharSequence)CompactNumber.format(sim.warehouseMarket().lastTaxed())).add(EconTexts.¤¤taxCollectedFrom).add((CharSequence)CompactNumber.format(sim.warehouseMarket().lastTaxPayers())).add(EconTexts.¤¤taxCollectedMerchants);
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
        this.line.clear().add(EconTexts.¤¤taxPaidOut).add((CharSequence)CompactNumber.format(fiscal.rationOut())).add(EconTexts.¤¤taxPaidProducer).add((CharSequence)CompactNumber.format(fiscal.producerIncome()));
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 18, y + 12);
        Taxes taxes = sim.taxes();
        EconConfig.taxesEnabled = this.toggle(r, x, y += 26, 150, 22, EconConfig.taxesEnabled, EconConfig.taxesEnabled ? EconTexts.¤¤taxWealthOn : EconTexts.¤¤taxWealthOff);
        this.label.clear().add(EconTexts.¤¤taxExemptBelow);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        int floor = this.logSlider(r, "wtfloor", x + 200, y, taxes.floor(), 0, 50000);
        if (floor != taxes.floor()) {
            taxes.setFloor(floor);
        }
        this.valueField(r, "f_wtfloor", x + 200 + 260 + 18, y, 130, taxes.floor(), 0, 50000, taxes::setFloor, EconTexts.¤¤uiDenari, EconConfig.taxesEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.label.clear().add(EconTexts.¤¤taxRateAboveFloor);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        int wrate = this.slider(r, "wtrate", x + 200, y, taxes.rate(), 0, 100, 1);
        if (wrate != taxes.rate()) {
            taxes.setRate(wrate);
        }
        this.valueField(r, "f_wtrate", x + 200 + 260 + 18, y, 90, taxes.rate(), 0, 100, taxes::setRate, "%", EconConfig.taxesEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add(EconTexts.¤¤taxLastWealthTake).add((CharSequence)CompactNumber.format(taxes.lastCollected())).add(EconTexts.¤¤taxLastWealthPayers).add((CharSequence)CompactNumber.format(taxes.lastPayers())).add(EconTexts.¤¤taxLastWealthPayersSuffix);
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
        Liturgy lit = sim.liturgy();
        EconConfig.liturgyEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.liturgyEnabled, EconConfig.liturgyEnabled ? EconTexts.¤¤taxLiturgyOn : EconTexts.¤¤taxLiturgyOff);
        this.label.clear().add(EconTexts.¤¤taxRichestTaxed);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        EconConfig.liturgyHeadcount = lcount = this.slider(r, "litcount", x + 200, y, EconConfig.liturgyHeadcount, 1, 50, 1);
        this.valueField(r, "f_litcount", x + 200 + 260 + 18, y, 90, EconConfig.liturgyHeadcount, 1, 50, v -> {
            EconConfig.liturgyHeadcount = v;
        }, "", EconConfig.liturgyEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.label.clear().add(EconTexts.¤¤taxShareWealth);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        int lpct = (int)Math.round(EconConfig.liturgyRate * 100.0);
        int nlpct = this.slider(r, "litrate", x + 200, y, lpct, 0, 100, 1);
        if (nlpct != lpct) {
            EconConfig.liturgyRate = (double)nlpct / 100.0;
        }
        this.valueField(r, "f_litrate", x + 200 + 260 + 18, y, 90, nlpct, 0, 100, v -> {
            EconConfig.liturgyRate = (double)v / 100.0;
        }, "%", EconConfig.liturgyEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.label.clear().add(EconTexts.¤¤taxEveryNSeasons);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        EconConfig.liturgyIntervalSeasons = lint = this.slider(r, "litint", x + 200, y, EconConfig.liturgyIntervalSeasons, 1, 16, 1);
        this.valueField(r, "f_litint", x + 200 + 260 + 18, y, 90, EconConfig.liturgyIntervalSeasons, 1, 16, v -> {
            EconConfig.liturgyIntervalSeasons = v;
        }, "", EconConfig.liturgyEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add(EconTexts.¤¤taxLastLiturgy).add((CharSequence)CompactNumber.format(lit.lastLevied())).add(EconTexts.¤¤taxLastLiturgyNamed).add((CharSequence)CompactNumber.format(lit.lastNamed())).add(EconTexts.¤¤taxLastLiturgyNamedSuffix);
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
        DebtBondage bondage = sim.debtBondage();
        EconConfig.debtSlaveryEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.debtSlaveryEnabled, EconConfig.debtSlaveryEnabled ? EconTexts.¤¤taxDebtBondageOn : EconTexts.¤¤taxDebtBondageOff);
        this.label.clear().add(EconTexts.¤¤taxEnslaveAtDebt);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        EconConfig.debtSlaveThreshold = thr = this.logSlider(r, "dsthr", x + 200, y, EconConfig.debtSlaveThreshold, 1, 50000);
        this.valueField(r, "f_dsthr", x + 200 + 260 + 18, y, 130, EconConfig.debtSlaveThreshold, 1, 50000, v -> {
            EconConfig.debtSlaveThreshold = Math.max(1, v);
        }, " denari", EconConfig.debtSlaveryEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add(EconTexts.¤¤taxOutstandingDebt).add((CharSequence)CompactNumber.format(bondage.lastOutstanding())).add(EconTexts.¤¤taxEnslavedLastSeason).add((CharSequence)CompactNumber.format(bondage.lastEnslaved()));
        this.line.color(bondage.lastEnslaved() > 0 ? COLOR.WHITE200 : COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
    }

    private void renderReligion(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        ReligionMarket.ensureSized();
        EconConfig.religionTaxEnabled = this.toggle(r, x, y, 150, 22, EconConfig.religionTaxEnabled, EconConfig.religionTaxEnabled ? EconTexts.¤¤faithJizyaOn : EconTexts.¤¤faithJizyaOff);
        y += 34;
        int n = EngineSeams.religionCount();
        for (int i = 0; i < n && i < EconConfig.religionHeadTax.length; ++i) {
            this.label.clear().add(EngineSeams.religionName(i));
            this.label.color(COLOR.WHITE200);
            this.label.render((SPRITE_RENDERER)r, x, x + 190, y + 8, y + 22);
            int idx = i;
            int rate = EconConfig.religionHeadTax[i];
            int nr = this.logSlider(r, "rel" + i, x + 200, y, rate, 0, 50000);
            if (nr != rate) {
                EconConfig.religionHeadTax[i] = nr;
            }
            this.valueField(r, "f_rel" + i, x + 200 + 260 + 18, y, 130, EconConfig.religionHeadTax[i], 0, 50000, v -> {
                EconConfig.religionHeadTax[idx] = v;
            }, EconTexts.¤¤uiDenari, EconConfig.religionHeadTax[i] > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
            y += 30;
        }
        this.line.clear().add(EconTexts.¤¤faithCollected).add((CharSequence)CompactNumber.format(sim.religionTaxCollected())).add(EconTexts.¤¤faithApostasies).add((CharSequence)CompactNumber.format(sim.religionMarket().lastConversions())).add(EconTexts.¤¤faithNewDebtors).add((CharSequence)CompactNumber.format(sim.religionMarket().lastDebtors()));
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 12, y + 12);
    }

    private void renderCorvee(Renderer r, int y, EconomySim sim) {
        int trate;
        int owage;
        int cap;
        int pct;
        int x = this.win.x1() + 18;
        CorveeController.ensureSized();
        EconConfig.corveeEnabled = this.toggle(r, x, y, 150, 22, EconConfig.corveeEnabled, EconConfig.corveeEnabled ? EconTexts.¤¤corveeOn : EconTexts.¤¤corveeOff);
        y += 36;
        int seasons = CorveeController.seasonsPerYear();
        int dps = CorveeController.daysPerSeason();
        int current = CorveeController.currentCell();
        int cellW = 54;
        int cellH = 26;
        int gap = 4;
        int labelW = 70;
        for (int s = 0; s < seasons; ++s) {
            this.label.clear().add((CharSequence)(s < SEASON_NAMES.length ? SEASON_NAMES[s] : "S" + (s + 1)));
            this.label.color(COLOR.WHITE150);
            this.label.render((SPRITE_RENDERER)r, x, x + labelW, y + 6, y + cellH);
            for (int d = 0; d < dps; ++d) {
                boolean on;
                int cell = s * dps + d;
                if (cell >= EconConfig.corveeDays.length) continue;
                int cx = x + labelW + d * (cellW + gap);
                EconConfig.corveeDays[cell] = on = this.toggle(r, cx, y, cellW, cellH, EconConfig.corveeDays[cell], "" + (d + 1));
                if (cell != current) continue;
                COLOR.WHITE200.render((SPRITE_RENDERER)r, cx, cx + cellW, y - 2, y - 1);
            }
            y += cellH + gap;
        }
        this.label.clear().add(EconTexts.¤¤corveeDraftUpTo);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 10) + 8, y + 22);
        EconConfig.corveeDraftPercent = pct = this.slider(r, "corvpct", x + 200, y, EconConfig.corveeDraftPercent, 0, 100, 1);
        this.valueField(r, "f_corvpct", x + 200 + 260 + 18, y, 90, EconConfig.corveeDraftPercent, 0, 100, v -> {
            EconConfig.corveeDraftPercent = v;
        }, "%", COLOR.WHITE200);
        this.label.clear().add(EconTexts.¤¤corveeButNoMoreThan);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        EconConfig.corveeDraftMax = cap = this.logSlider(r, "corvcap", x + 200, y, EconConfig.corveeDraftMax, 0, 20000);
        this.valueField(r, "f_corvcap", x + 200 + 260 + 18, y, 110, EconConfig.corveeDraftMax, 0, 20000, v -> {
            EconConfig.corveeDraftMax = v;
        }, EconTexts.¤¤corveePeople, COLOR.WHITE200);
        this.line.clear().add((CharSequence)(CorveeController.isCorveeToday() ? "TODAY IS A CORVEE DAY - drafting ~" + Math.round(sim.corveeDraftFractionLast() * 100.0) + "%" : EconTexts.¤¤corveeOrdinaryDay));
        this.line.color(CorveeController.isCorveeToday() ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 12);
        OddjobMarket odd = sim.oddjobMarket();
        EconConfig.oddjobWageEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.oddjobWageEnabled, EconConfig.oddjobWageEnabled ? EconTexts.¤¤corveeOddjobOn : EconTexts.¤¤corveeOddjobOff);
        this.label.clear().add(EconTexts.¤¤corveeDenariPerTask);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
        owage = this.slider(r, "oddwage", x + 200, y, EconConfig.oddjobWagePerTask, 0, 250, 1);
        EconConfig.setOddjobWage(owage);
        this.valueField(r, "f_oddwage", x + 200 + 260 + 18, y, 110, EconConfig.oddjobWagePerTask, 0, 250, v -> {
            EconConfig.setOddjobWage(v);
        }, EconTexts.¤¤uiDenari, EconConfig.oddjobWageEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add(EconTexts.¤¤corveeThisSeason).add((CharSequence)CompactNumber.format(odd.currentPaid())).add(EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(odd.currentTasks())).add(EconTexts.¤¤corveeTasks).add(EconTexts.¤¤corveeLast).add((CharSequence)CompactNumber.format(odd.lastPaid())).add(EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(odd.lastTasks())).add(EconTexts.¤¤corveeTasks).add(EconTexts.¤¤corveeWorkingNow).add((CharSequence)CompactNumber.format(odd.activeWorkersNow())).add(EconTexts.¤¤corveeCycle).add((long)odd.cycleProgressPercent()).add((CharSequence)"%").add((CharSequence)(odd.treasuryBlocked() ? EconTexts.¤¤corveeBlocked : ""));
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 12);
        TransportMarket tm = sim.transportMarket();
        EconConfig.transportFeeEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.transportFeeEnabled, EconConfig.transportFeeEnabled ? EconTexts.¤¤corveeHaulageOn : EconTexts.¤¤corveeHaulageOff);
        this.line.clear().add(EconTexts.¤¤corveeHaulageDesc);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 14);
        this.label.clear().add(EconTexts.¤¤corveeHaulageRate);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 22) + 8, y + 22);
        EconConfig.transportFeePer100TileDay = trate = this.slider(r, "trate", x + 200, y, EconConfig.transportFeePer100TileDay, 0, 100, 1);
        this.valueField(r, "f_trate", x + 200 + 260 + 18, y, 130, EconConfig.transportFeePer100TileDay, 0, 100, v -> {
            EconConfig.transportFeePer100TileDay = v;
        }, EconTexts.¤¤corveeHaulageSuffix, EconConfig.transportFeeEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add(EconTexts.¤¤corveeLastTick).add((CharSequence)CompactNumber.format(tm.lastPaid())).add(EconTexts.¤¤uiDenari).add(EconTexts.¤¤corveeActiveStations).add((CharSequence)CompactNumber.format(tm.lastActiveStations())).add(EconTexts.¤¤corveeMeanHaul).add((long)((int)tm.lastMeanDistance())).add(EconTexts.¤¤corveeTiles).add((CharSequence)(tm.lastUsedReflection() ? "" : EconTexts.¤¤corveeGeoEstimate));
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 12);
        int stateWageHeaderY = y += 28;
        this.label.clear().add(EconTexts.¤¤corveeStateWages);
        this.label.color(COLOR.WHITE200);
        this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 16);
        this.line.clear().add(EconTexts.¤¤corveeStateWagesDesc);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 20, y + 14);
        StateWageMarket.Entry[] stateWages = sim.stateWages().laborEntries();
        int stateWageTotal = stateWages.length;
        int stateWageListTop = y += 22;
        int stateWageListBottom = this.win.y2() - 18;
        int stateWageVisible = Math.max(1, (stateWageListBottom - stateWageListTop) / 32);
        int stateWageMaxScroll = Math.max(0, stateWageTotal - stateWageVisible);
        this.stateWageScroll = Math.max(0, Math.min(stateWageMaxScroll, this.stateWageScroll + this.takeScroll()));
        this.scrollbar(r, stateWageListTop, stateWageListBottom, this.stateWageScroll, stateWageVisible, stateWageTotal);
        this.line.clear().add((CharSequence)("" + (this.stateWageScroll + 1))).add((CharSequence)EconTexts.¤¤uiRange).add((CharSequence)("" + Math.min(stateWageTotal, this.stateWageScroll + stateWageVisible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + stateWageTotal));
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, stateWageHeaderY, stateWageHeaderY + 12);
        for (int i = this.stateWageScroll; i < stateWageTotal && i < this.stateWageScroll + stateWageVisible && y + 30 <= stateWageListBottom; ++i) {
            y = this.stateWageRow(r, x, y, stateWages[i]);
        }
    }

    private void renderRelief(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        GrainDole dole = sim.grainDole();
        this.label.clear().add(EconTexts.¤¤reliefTitle);
        this.label.color(COLOR.WHITE200);
        this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 16);
        this.line.clear().add(EconTexts.¤¤reliefDesc);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 24, y + 14);
        this.label.clear().add(EconTexts.¤¤reliefFreeIfWorthUnder);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 200, (y += 26) + 8, y + 22);
        int th = EconConfig.doleWealthThreshold;
        int nth = this.logSlider(r, "gdt", x + 210, y, th, 50000);
        if (nth != th) {
            EconConfig.doleWealthThreshold = nth;
        }
        this.valueField(r, "f_gdt", x + 210 + 260 + 18, y, 130, EconConfig.doleWealthThreshold, 0, 50000, v -> {
            EconConfig.doleWealthThreshold = v;
        }, EconTexts.¤¤uiDenari, EconConfig.doleWealthThreshold > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
        this.label.clear().add(EconTexts.¤¤reliefGrainRollHolds);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 200, (y += 30) + 8, y + 22);
        int cap = EconConfig.doleHeadcap;
        int ncap = this.slider(r, "gdn", x + 210, y, cap, 0, 500, 5);
        if (ncap != cap) {
            EconConfig.doleHeadcap = ncap;
        }
        int eligible = dole.eligibleCount(sim.roster(), sim.wallets());
        this.valueField(r, "f_gdn", x + 210 + 260 + 18, y, 90, EconConfig.doleHeadcap, 0, 5000, v -> {
            EconConfig.doleHeadcap = v;
        }, "", EconConfig.doleHeadcap > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
        this.line.clear().add((CharSequence)EconTexts.¤¤uiOpenParen).add((CharSequence)CompactNumber.format(eligible)).add((CharSequence)EconTexts.¤¤uiCloseParen);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x + 210 + 260 + 118, this.win.x2() - 18, y + 8, y + 22);
        this.line.clear().add(EconTexts.¤¤reliefOnRoll).add((CharSequence)CompactNumber.format(dole.rollSize())).add(EconTexts.¤¤reliefFreeMeals).add((CharSequence)CompactNumber.format(dole.mealsDoled())).add(EconTexts.¤¤reliefRevenueForegone).add((CharSequence)CompactNumber.format(dole.revenueForegone()));
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 14);
        y += 20;
        if (eligible > EconConfig.doleHeadcap && EconConfig.doleHeadcap > 0) {
            this.line.clear().add((CharSequence)CompactNumber.format(eligible - EconConfig.doleHeadcap)).add(EconTexts.¤¤reliefQualifyButNotOnRoll);
            this.line.color(COLOR.REDISH);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 14);
        }
        this.line.clear().add(EconTexts.¤¤reliefAutoRations).add((CharSequence)CompactNumber.format(dole.compulsoryRations()));
        this.line.color(COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 14);
    }

    private void renderForeignTrade(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        ForeignTradeLedger ledger = sim.foreignTradeLedger();
        if (ledger == null) {
            return;
        }
        this.label.clear().add(EconTexts.¤¤foreignTitle);
        this.label.color(COLOR.WHITE200);
        this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 16);
        this.line.clear().add(EconTexts.¤¤foreignSubtitle);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 24, y + 14);

        // Headline metrics: today's inflow + active NPC count
        this.label.clear().add(EconTexts.¤¤foreignInflowToday);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 220, y += 30, y + 16);
        this.line.clear().add((CharSequence)CompactNumber.format(ledger.todaysInflow())).add((CharSequence)EconTexts.¤¤uiDenari);
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x + 220, x + 380, y, y + 16);

        this.label.clear().add(EconTexts.¤¤foreignActiveCount);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, x + 220, y += 22, y + 16);
        this.line.clear().add("" + ledger.activeFactionCount());
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x + 220, x + 380, y, y + 16);

        // Per-faction credit snapshot (diagnostic) — show up to 8 entries
        Map<String, Long> snap = ledger.snapshotDebug();
        this.label.clear().add(EconTexts.¤¤foreignSnapshot);
        this.label.color(COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 16);
        if (snap.isEmpty()) {
            this.line.clear().add(EconTexts.¤¤foreignNoData);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x + 12, this.win.x2() - 18, y += 20, y + 14);
            return;
        }
        int capacity = 8;
        int start = Math.max(0, Math.min(this.foreignTradeScroll, snap.size() - capacity));
        int end = Math.min(snap.size(), start + capacity);
        int idx = 0;
        for (Map.Entry<String, Long> e : snap.entrySet()) {
            if (idx < start) {
                ++idx;
                continue;
            }
            if (idx >= end) break;
            this.line.clear().add(e.getKey()).add((CharSequence)EconTexts.¤¤uiColon).add((CharSequence)CompactNumber.format(e.getValue()));
            this.line.color(COLOR.WHITE120);
            this.line.render((SPRITE_RENDERER)r, x + 12, this.win.x2() - 18, y += 18, y + 14);
            ++idx;
        }
        if (snap.size() > capacity) {
            int rem = snap.size() - end;
            this.line.clear().add("... und " + rem + " weitere");
            this.line.color(COLOR.WHITE50);
            this.line.render((SPRITE_RENDERER)r, x + 12, this.win.x2() - 18, y += 18, y + 14);
        }
    }

    private void renderBooks(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        int col = x + 420;
        this.label.clear().add(EconTexts.¤¤booksTitle);
        this.label.color(COLOR.WHITE200);
        this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 14);
        y += 20;
        y = this.book(r, x, col, y, EconTexts.¤¤booksFoundingStock, sim.seedSupply(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksImported, sim.imported(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksTreasuryIncome, sim.guildIncomePaid(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksAnnona, sim.rationOut(), false);
        y += 4;
        Purchases p = sim.purchases();
        y = this.book(r, x, col, y, EconTexts.¤¤booksBuyerFood + CompactNumber.format(p.meals()) + EconTexts.¤¤booksMeals, p.spentOnFood(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksBuyerDrink + CompactNumber.format(p.drinks()) + EconTexts.¤¤booksRounds, p.spentOnDrink(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksBuyerGoods + CompactNumber.format(p.goodsBought()) + EconTexts.¤¤booksPurchases, p.spentOnGoods(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksTransfers, 0L, false);
        y += 4;
        y = this.book(r, x, col, y, EconTexts.¤¤booksGrainDoleMeals, sim.grainDole().mealsDoled(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksGrainDoleRevenue, sim.grainDole().revenueForegone(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksWealthTax, sim.taxesCollected(), true);
        y = this.book(r, x, col, y, EconTexts.¤¤booksHeadTax, sim.headTaxCollected(), true);
        y = this.book(r, x, col, y, EconTexts.¤¤booksReligionTax, sim.religionTaxCollected(), true);
        y = this.book(r, x, col, y, EconTexts.¤¤booksLiturgy, sim.liturgyCollected(), true);
        y = this.book(r, x, col, y, EconTexts.¤¤booksMarketSkim, sim.marketReceipts(), true);
        if (sim.spent() > 0L) {
            y = this.book(r, x, col, y, EconTexts.¤¤booksLegacy, sim.spent(), true);
        }
        y = this.book(r, x, col, y, EconTexts.¤¤booksRoundingDrift, sim.roundingDrift(), false);
        y = this.book(r, x, col, y, EconTexts.¤¤booksExported, sim.exported(), true);
        y = this.book(r, x, col, y, EconTexts.¤¤booksHeirless, sim.escheated(), true);
        y += 4;
        long living = sim.stats().total;
        this.line.clear().add(EconTexts.¤¤booksInCirculation).add((CharSequence)CompactNumber.format(living)).add(EconTexts.¤¤uiDenari);
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        y += 16;
        long delta = sim.auditDelta();
        if (delta != 0L) {
            this.line.clear().add(EconTexts.¤¤booksDoNotBalance).add((CharSequence)((delta > 0L ? "+" : "") + CompactNumber.format(delta))).add(EconTexts.¤¤booksUnaccounted);
            this.line.color(COLOR.REDISH);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        } else {
            this.line.clear().add(EconTexts.¤¤booksBalance);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        }
        y += 18;
        DebtBondage bondage = sim.debtBondage();
        this.line.clear().add(EconTexts.¤¤booksDebtors).add((CharSequence)CompactNumber.format(bondage.debtorCount())).add(EconTexts.¤¤booksArrears).add((CharSequence)CompactNumber.format(bondage.lastOutstanding())).add(EconTexts.¤¤uiDenari).add(EconTexts.¤¤booksSoldBondage).add((CharSequence)CompactNumber.format(bondage.totalEnslaved()));
        this.line.color(bondage.debtorCount() > 0 ? COLOR.WHITE200 : COLOR.WHITE120);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        y += 22;
        int pop = sim.roster().size();
        long stock = LocalPrices.foodStock(sim.ticks());
        int meal = LocalPrices.mealPrice(pop, sim.ticks());
        double days = LocalPrices.foodDays();
        this.line.clear().add(EconTexts.¤¤booksFoodStock).add((CharSequence)CompactNumber.format(stock)).add((CharSequence)EconTexts.¤¤uiEquals).add((CharSequence)("" + (int)days)).add(EconTexts.¤¤booksDaysOfFood).add(EconTexts.¤¤booksTarget).add((CharSequence)("" + (int)EconConfig.targetFoodDays)).add((CharSequence)EconTexts.¤¤uiCloseParen);
        this.line.color(days < EconConfig.targetFoodDays * 0.3 ? COLOR.REDISH : COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        y += 16;
        int localFoodBasket = LocalPrices.flowFoodBasketPrice();
        int anchorFoodBasket = LocalPrices.foodBasketPrice(sim.ticks());
        this.line.clear().add(EconTexts.¤¤booksLocalFoodBasket).add((CharSequence)CompactNumber.format(localFoodBasket)).add(EconTexts.¤¤booksTradeAnchorBasket).add((CharSequence)CompactNumber.format(anchorFoodBasket)).add((CharSequence)EconTexts.¤¤uiCloseParen).add(EconTexts.¤¤booksReferenceFoodUnit).add((CharSequence)CompactNumber.format(meal));
        this.line.color((double)localFoodBasket > (double)anchorFoodBasket * 1.5 ? COLOR.REDISH : ((double)localFoodBasket < (double)anchorFoodBasket * 0.8 ? COLOR.WHITE200 : COLOR.WHITE150));
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        this.line.clear().add(EconTexts.¤¤booksLastOptimizedMeal).add((CharSequence)CompactNumber.format(sim.affordabilityGate().lastFoodBundleQuote())).add(EconTexts.¤¤booksDenariFor).add((CharSequence)CompactNumber.format(sim.affordabilityGate().lastFoodBundleUnits())).add(EconTexts.¤¤booksFoodUnits);
        this.line.color(COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 16, y + 12);
        y += 16;
        double dDays = LocalPrices.drinkDays(sim.ticks());
        int localDrinkBasket = LocalPrices.flowDrinkBasketPrice();
        int anchorDrinkBasket = LocalPrices.drinkBasketPrice(sim.ticks());
        this.line.clear().add(EconTexts.¤¤booksDrinkReserve).add((CharSequence)("" + (int)dDays)).add(EconTexts.¤¤booksDays).add(EconTexts.¤¤booksLocalDrinkBasket).add((CharSequence)CompactNumber.format(localDrinkBasket)).add(EconTexts.¤¤booksAnchor).add((CharSequence)CompactNumber.format(anchorDrinkBasket)).add((CharSequence)EconTexts.¤¤uiCloseParen);
        this.line.color((double)localDrinkBasket > (double)anchorDrinkBasket * 1.5 ? COLOR.REDISH : COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        y += 24;

        // Live Chronicle Section
        this.line.clear().add(EconTexts.¤¤historyHeader);
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 14);
        y += 20;

        List<EventLog.EventEntry> events = EventLog.getRecentEvents();
        if (events.isEmpty()) {
            this.line.clear().add(EconTexts.¤¤historyNoEvents);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
        } else {
            int startIdx = Math.max(0, events.size() - 15);
            for (int i = events.size() - 1; i >= startIdx; i--) {
                EventLog.EventEntry entry = events.get(i);
                COLOR catColor = switch (entry.category) {
                    case "HOUSING" -> COLOR.REDISH;
                    case "DEBT" -> COLOR.REDISH;
                    case "PROPERTY" -> COLOR.WHITE200;
                    case "CONSUMPTION" -> COLOR.WHITE150;
                    case "LATENT_DEMAND" -> COLOR.WHITE120;
                    default -> COLOR.WHITE100;
                };
                this.line.clear().add("[" + entry.timestamp + "] [" + entry.category + "] " + entry.message);
                this.line.color(catColor);
                if (y + 12 < this.win.y2() - 18) {
                    this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
                    y += 16;
                }
            }
        }
    }

    private int book(Renderer r, int x, int col, int y, String name, long v, boolean out) {
        this.line.clear().add((CharSequence)name);
        this.line.color(COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, col, y, y + 12);
        this.line.clear().add((CharSequence)CompactNumber.format(v));
        this.line.color(v == 0L ? COLOR.WHITE100 : (out ? COLOR.WHITE150 : COLOR.WHITE200));
        this.line.render((SPRITE_RENDERER)r, col, this.win.x2() - 18, y, y + 12);
        return y + 16;
    }

    private static String fmt2(double d) {
        return String.format("%.2f", d);
    }

    private static enum Menu {
        OVERVIEW(UI.icons().s.eye),
        ECONOMY(UI.icons().s.trade),
        STATE(UI.icons().s.capitol);

        final Icon icon;

        Menu(Icon icon) {
            this.icon = icon;
        }
    }

    private static enum Tab {
        DASHBOARD(Menu.OVERVIEW, EconTexts.¤¤tabDashboard, UI.icons().s.money),
        DISTRIBUTION(Menu.OVERVIEW, EconTexts.¤¤tabWealth, UI.icons().s.money),
        CITIZENS(Menu.OVERVIEW, EconTexts.¤¤tabCitizens, UI.icons().s.citizen),
        BOOKS(Menu.OVERVIEW, EconTexts.¤¤tabBooks, UI.icons().s.book),
        ADVISOR(Menu.OVERVIEW, EconTexts.¤¤tabAdvisor, UI.icons().s.question),
        PRICES(Menu.ECONOMY, EconTexts.¤¤tabPrices, UI.icons().s.trade),
        WAGES(Menu.ECONOMY, EconTexts.¤¤tabWages, UI.icons().s.money),
        SUBSIDIES(Menu.ECONOMY, EconTexts.¤¤tabSubsidies, UI.icons().s.gift),
        GRANARY(Menu.ECONOMY, EconTexts.¤¤tabGranary, UI.icons().s.storage),
        MARKET(Menu.ECONOMY, EconTexts.¤¤tabMarket, UI.icons().s.trade),
        FIRMS(Menu.ECONOMY, EconTexts.¤¤tabFirms, UI.icons().s.hammer),
        FLOWS(Menu.ECONOMY, EconTexts.¤¤tabFlows, UI.icons().s.pickaxe),
        DEBUG(Menu.OVERVIEW, EconTexts.¤¤tabDebug, UI.icons().s.question),
        TAXES(Menu.STATE, EconTexts.¤¤tabTaxes, UI.icons().s.money),
        RELIGION(Menu.STATE, EconTexts.¤¤tabFaith, UI.icons().s.temple),
        CORVEE(Menu.STATE, EconTexts.¤¤tabCorvee, UI.icons().s.pickaxe),
        RELIEF(Menu.STATE, EconTexts.¤¤tabRelief, UI.icons().s.heart),
        FOREIGN_TRADE(Menu.STATE, EconTexts.¤¤tabForeignTrade, UI.icons().s.gift);

        final Menu menu;
        final String label;
        final Icon icon;

        Tab(Menu menu, String label, Icon icon) {
            this.menu = menu;
            this.label = label;
            this.icon = icon;
        }
    }

    private static final class FirmIO {
        final Map<RESOURCE, Double> outputs = new HashMap<>();
        final Map<RESOURCE, Double> inputs = new HashMap<>();

        static void add(Map<RESOURCE, Double> map, RESOURCE res, double rate) {
            if (res == null || !(rate > 0.0) || !Double.isFinite(rate)) return;
            map.merge(res, rate, Double::sum);
        }

        void addOutput(RESOURCE res, double rate) {
            FirmIO.add(this.outputs, res, rate);
        }

        void addInput(RESOURCE res, double rate) {
            FirmIO.add(this.inputs, res, rate);
        }

        static String format(Map<RESOURCE, Double> map) {
            if (map.isEmpty()) return "-";
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<RESOURCE, Double> e : map.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey().name).append(" ").append(CompactNumber.format(e.getValue().longValue()));
            }
            return sb.toString();
        }

        String formatOutputs() {
            return FirmIO.format(this.outputs);
        }

        String formatInputs() {
            return FirmIO.format(this.inputs);
        }
    }

    private final class InputBlocker
    extends Interrupter {
        private InputBlocker() {
        }

        void ensureShown() {
            if (this.manager() != VIEW.current().uiManager) {
                if (this.isActivated()) {
                    this.hide();
                }
                this.show(VIEW.current().uiManager);
            }
        }

        protected boolean hover(COORDINATE mCoo, boolean mouseHasMoved) {
            if (!EconConfig.windowEnabled) {
                return false;
            }
            EconomyWindow.this.mouseX = mCoo.x();
            EconomyWindow.this.mouseY = mCoo.y();
            EconomyWindow.this.placeButton();
            EconomyWindow.this.placeWindow();
            return this.contains(EconomyWindow.this.btn, mCoo) || EconomyWindow.this.open && this.contains(EconomyWindow.this.win, mCoo);
        }

        private boolean contains(Rec area, COORDINATE point) {
            return point.x() >= area.x1() && point.x() <= area.x2() && point.y() >= area.y1() && point.y() <= area.y2();
        }

        protected void mouseClick(MButt button) {
            EconomyWindow.this.click(button);
        }

        protected void hoverTimer(GBox text) {
        }

        protected boolean render(Renderer r, float ds) {
            return true;
        }

        protected boolean update(float ds) {
            return true;
        }
    }

    private static interface Setter {
        public void set(int var1);
    }
    private void renderFirms(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        FirmLedger ledger = sim.firmLedger();
        PropertyLedger propLedger = sim.housingMarket().ledger();
        List<FlowMeter.FirmSnapshot> snaps = sim.flowMeter().firmSnapshots();

        this.line.clear().add(EconTexts.¤¤firmsHeader);
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 20;

        Map<RoomBlueprintImp, FirmIO> ioMap = new HashMap<>();
        for (FlowMeter.FirmSnapshot snap : snaps) {
            RoomInstance room = snap.room();
            if (room == null) continue;
            Object blueprint = room.blueprintI();
            if (!(blueprint instanceof RoomBlueprintImp)) continue;
            RoomBlueprintImp bp = (RoomBlueprintImp)blueprint;
            FirmIO io = ioMap.computeIfAbsent(bp, ignored -> new FirmIO());

            for (int i = 0; i < snap.outputCount(); i++) {
                io.addOutput(snap.outputResource(i), snap.outputPerDay(i));
            }
            for (int i = 0; i < snap.inputCount(); i++) {
                io.addInput(snap.inputResource(i), snap.inputPerDay(i));
            }
        }

        List<RoomBlueprintImp> sortedBps = new ArrayList<>(ioMap.keySet());
        if (sortedBps.isEmpty()) {
            this.line.clear().add(EconTexts.¤¤wageNoWorkplaces);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            return;
        }

        sortedBps.sort((a, b) -> a.info.name.toString().compareTo(b.info.name.toString()));

        int total = sortedBps.size();
        int listTop = y;
        int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
        int maxScroll = Math.max(0, total - visible);
        this.firmScroll = Math.max(0, Math.min(maxScroll, this.firmScroll + this.takeScroll()));
        this.scrollbar(r, listTop, this.win.y2() - 18, this.firmScroll, visible, total);

        for (int i = this.firmScroll; i < total && y + 30 <= this.win.y2() - 18; i++) {
            RoomBlueprintImp bp = sortedBps.get(i);
            FirmIO io = ioMap.get(bp);
            double profitD = ledger.profitPerDay(bp);
            double marginalD = ledger.marginalSurplus(bp);
            int profit = (int)Math.round(profitD);
            int marginal = (int)Math.round(marginalD);

            // Aggregate ownership across instances of this blueprint.
            int privateShares = 0, totalInstances = 0;
            long totalDividends = 0L;
            if (bp instanceof RoomBlueprintIns) {
                RoomBlueprintIns ins = (RoomBlueprintIns) bp;
                totalInstances = ins.instancesSize();
                for (int j = 0; j < totalInstances; ++j) {
                    RoomInstance room = (RoomInstance) ins.getInstance(j);
                    if (room == null || !room.exists()) continue;
                    PropertyLedger.Entry e = propLedger.get(room);
                    if (e != null && e.shares() > 0 && !e.isStateOwned()) {
                        privateShares += e.shares();
                        totalDividends += e.dividendPool();
                    }
                }
            }
            int avgShares = totalInstances > 0 ? privateShares / totalInstances : 0;

            this.label.clear().add(bp.info.name);
            this.label.color(COLOR.WHITE150);
            this.label.render((SPRITE_RENDERER)r, x, x + 180, y + 4, y + 18);

            this.line.clear()
                .add(EconTexts.¤¤firmsProfit).add((CharSequence)CompactNumber.format(profit)).add((CharSequence)"  ")
                .add(EconTexts.¤¤firmsMargin).add((CharSequence)CompactNumber.format(marginal)).add((CharSequence)"  ")
                .add(EconTexts.¤¤firmsInstances).add((CharSequence)CompactNumber.format(totalInstances));
            // Ownership snippet.
            if (avgShares > 0) {
                this.line.add((CharSequence)"  ·  ").add(EconTexts.¤¤firmsOwnership)
                    .add((CharSequence)CompactNumber.format(avgShares)).add((CharSequence)"% privat");
                if (totalDividends > 0L) {
                    this.line.add((CharSequence)"  ").add(EconTexts.¤¤firmsDividend)
                        .add((CharSequence)CompactNumber.format(totalDividends));
                }
            } else {
                this.line.add((CharSequence)"  ·  ").add(EconTexts.¤¤firmsOwnership)
                    .add(EconTexts.¤¤firmsStaat);
            }
            this.line.color(profit < 0 ? COLOR.REDISH : (profit > 0 ? COLOR.WHITE200 : COLOR.WHITE120));
            this.line.render((SPRITE_RENDERER)r, x + 190, this.win.x2() - 25, y + 4, y + 18);

            this.line.clear()
                .add(EconTexts.¤¤firmsInputs).add((CharSequence)io.formatInputs()).add((CharSequence)"  ")
                .add(EconTexts.¤¤firmsOutputs).add((CharSequence)io.formatOutputs());
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x + 470, this.win.x2() - 25, y + 20, y + 34);

            y += 30;
        }
    }

        private void renderAdvisor(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        int cw = this.winW() - 36;          // content width (PAD=18 on each side)
        int contentTop = y;
        int contentBottom = this.win.y2() - 18;
        int visibleRows = Math.max(1, (contentBottom - contentTop) / 16);
        int totalRows = 80;
        int maxScroll = Math.max(0, totalRows - visibleRows);
        this.advisorScroll = Math.max(0, Math.min(maxScroll, this.advisorScroll + this.takeScroll()));
        y -= this.advisorScroll * 16;
        int clipTop = contentTop;
        int clipBot = contentBottom;
        this.scrollbar(r, contentTop, contentBottom, this.advisorScroll, visibleRows, totalRows);

        this.scrollbar(r, contentTop, contentBottom, this.advisorScroll, visibleRows, totalRows);

        // ── TITLE ──
        if (y + 14 > clipTop && y < clipBot) {
            this.line.clear().add(EconTexts.¤¤advTitle);
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
        y += 22;

        // ── 3-LINE SUMMARY (One-Screen-Story: 5-Sekunden-Verständnis) ──
        EconProgression prog = sim.progression();
        EconIndicators ind = sim.econIndicators();
        if (y + 36 > clipTop && y < clipBot) {
            // Line 1: Siedlung + Siedler
            this.line.clear().add("Deine Siedlung: ").add(prog.stage.displayName)
                .add("  —  ").add(CompactNumber.format(sim.roster().size())).add(" Siedler");
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 14;
            // Line 2: Staatskasse + Trend
            long treasuryNow = sim.wallets().circulating();
            this.line.clear().add("Geld im Umlauf: ").add(CompactNumber.format(treasuryNow)).add(" Denari");
            this.line.color(COLOR.WHITE150);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 14;
            // Line 3: Status-Satz
            this.line.clear().add("Zustand: ");
            if (ind.isTreasuryDeclining() || sim.stats().gini > EconIndicators.GINI_WARNING) {
                this.line.add("kritisch — Handlungsbedarf");
                this.line.color(COLOR.REDISH);
            } else if (prog.stage.level >= EconProgression.Stage.INDUSTRIE.level) {
                this.line.add("wachsend — alles im grünen Bereich");
                this.line.color(COLOR.GREEN100);
            } else {
                this.line.add("stabil — keine Warnungen aktiv");
                this.line.color(COLOR.WHITE200);
            }
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
        y += 18;

        EconSnapshot latest = sim.econIndicators().latest();
        EconSnapshot prev = sim.econIndicators().count() >= 2
            ? sim.econIndicators().get(sim.econIndicators().count() - 2) : null;

        // ── KPI DASHBOARD ──
        if (y + 12 > clipTop && y < clipBot) {
            this.line.clear().add("KPI DASHBOARD");
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
        y += 18;

        // 3 columns, 2 rows of KPI boxes
        int boxGap = 10;
        int boxW = (cw - 2 * boxGap) / 3;
        int boxH = 60;

        if (y + boxH > clipTop && y < clipBot) {
            // Row 1: Population | Circulating Money | Gini
            int bx = x;
            COLOR giniCol = sim.stats().gini > 0.35 ? COLOR.REDISH
                : (sim.stats().gini > 0.25 ? COLOR.WHITE200 : COLOR.WHITE200);
            this.kpiBox(r, bx, y, boxW, boxH, EconTexts.¤¤kpiPeople,
                CompactNumber.format(sim.roster().size()));
            bx += boxW + boxGap;
            this.kpiBox(r, bx, y, boxW, boxH, EconTexts.¤¤kpiMoney,
                CompactNumber.format(sim.wallets().circulating()) + " D");
            bx += boxW + boxGap;
            this.kpiBox(r, bx, y, boxW, boxH, EconTexts.¤¤kpiGini,
                String.format("%.3f", sim.stats().gini) + " (Warn. >" + String.format("%.2f", EconIndicators.GINI_WARNING) + ")");
        }
        y += boxH + boxGap;

        if (y + boxH > clipTop && y < clipBot) {
            // Row 2: Mean Wage | Unpaid Workers | Food Price
            int bx = x;
            int unpaid = sim.firmLedger().lastWorkersUnpaid();
            this.kpiBox(r, bx, y, boxW, boxH, EconTexts.¤¤kpiWage,
                CompactNumber.format((long)sim.laborMarket().meanWage()) + "/Tag");
            bx += boxW + boxGap;
            this.kpiBox(r, bx, y, boxW, boxH, EconTexts.¤¤kpiUnpaid,
                CompactNumber.format(unpaid));
            bx += boxW + boxGap;
            int foodPrc = LocalPrices.flowFoodBasketPrice();
            if (foodPrc <= 0) foodPrc = LocalPrices.foodBasketPrice(sim.ticks());
            this.kpiBox(r, bx, y, boxW, boxH, EconTexts.¤¤kpiFood,
                CompactNumber.format(foodPrc) + " D");
        }
        y += boxH + 14;

        // ── 5 STATUS-AMPELN (One-Screen-Story: 1-Klick → Detail-Tab) ──
        // Compute status flags (reused for ampel bars)
        boolean moneyOk = latest != null && prev != null ? latest.totalMoney > prev.totalMoney * 0.99 : true;
        boolean prodOk  = sim.firmLedger().lastWorkersUnpaid() == 0;
        double foodDays = LocalPrices.foodDays();
        boolean basicsOk   = foodDays >= EconConfig.targetFoodDays * 0.8;
        boolean welfareOk  = sim.stats().gini < EconIndicators.GINI_WARNING;
        boolean treasuryOk = latest != null ? (latest.headTax + latest.marketReceipts) >= latest.rationOut : true;

        // Fractions for each ampel
        double moneyFrac = (latest != null && prev != null && prev.totalMoney > 0)
            ? Math.min(1.0, (double)latest.totalMoney / (double)prev.totalMoney) : 1.0;
        double prodFrac = prodOk ? 1.0 : 0.3;
        double tfd = EconConfig.targetFoodDays > 0 ? EconConfig.targetFoodDays : 7.0;
        double basicsFrac = Math.min(1.0, foodDays / tfd);
        double welfareFrac = Math.min(1.0, 1.0 - Math.min(1.0, sim.stats().gini / 0.5));
        double tresFrac = (latest != null && latest.rationOut > 0)
            ? Math.min(1.0, (double)(latest.headTax + latest.marketReceipts) / (double)latest.rationOut) : 1.0;
        double growthFrac = Math.min(1.0, (double)prog.stage.level / 4.0);

        // Status texts
        int unpaid = sim.firmLedger().lastWorkersUnpaid();
        String finanzText = treasuryOk ? (moneyOk ? "Stabil" : "Leicht ruecklaeufig") : "Defizit";
        String arbeitText = prodOk ? "Alle bezahlt" : (unpaid + " unbezahlt");
        String versorgText = basicsOk ? ("Nahrung " + String.format("%.1f", foodDays) + "/" + String.format("%.0f", tfd) + "T") : "Kritisch";
        String gleichText = welfareOk ? ("Gini " + String.format("%.2f", sim.stats().gini) + " (Warn. >" + String.format("%.2f", EconIndicators.GINI_WARNING) + ")") : "Ungleichheit hoch";
        String wachsText = prog.stage.displayName + " (Stufe " + (prog.stage.level + 1) + "/5)";

        // Render 5 clickable ampel boxes in a row
        int ampelGap = 8;
        int ampelW = (cw - 4 * ampelGap) / 5;
        int ampelH = 62;

        if (y + ampelH > clipTop && y < clipBot) {
            int ax = x;
            this.ampelBox(r, ax, y, ampelW, ampelH, "FINANZEN", tresFrac, treasuryOk, finanzText, Tab.TAXES);
            ax += ampelW + ampelGap;
            this.ampelBox(r, ax, y, ampelW, ampelH, "ARBEIT", prodFrac, prodOk, arbeitText, Tab.WAGES);
            ax += ampelW + ampelGap;
            this.ampelBox(r, ax, y, ampelW, ampelH, "VERSORGUNG", basicsFrac, basicsOk, versorgText, Tab.PRICES);
            ax += ampelW + ampelGap;
            this.ampelBox(r, ax, y, ampelW, ampelH, "GLEICHHEIT", welfareFrac, welfareOk, gleichText, Tab.DISTRIBUTION);
            ax += ampelW + ampelGap;
            this.ampelBox(r, ax, y, ampelW, ampelH, "WACHSTUM", growthFrac, prog.stage.level >= 1, wachsText, Tab.ADVISOR);
        }
        y += ampelH + 12;

        // ── PREDICTIVE TEXT ──
        if (latest != null) {
            long treasury = latest.treasuryCurrent;
            // Burn rate misst den tatsaechlichen Bestandsrueckgang, nicht Einnahmen.
            // Harmoniert mit der daysLeft-Berechnung: beide nutzen treasuryCurrent.
            double burnRate = prev != null ? (double)(prev.treasuryCurrent - treasury) : 0.0;
            int daysLeft = burnRate > 0 ? (int)((double)treasury / burnRate) : -1;
            this.line.clear();
            if (daysLeft > 0 && daysLeft < 365) {
                this.line.add("Bei aktuellem Trend: Staatskasse leer in ~").add((CharSequence)("" + daysLeft)).add(" Tagen");
                this.line.color(COLOR.REDISH);
            } else if (burnRate > 0) {
                // Treasury-Balance schrumpft, aber noch nicht kritisch.
                this.line.add("Staatskasse schrumpft — Ausgaben senken oder Einnahmen erhöhen");
                this.line.color(COLOR.WHITE100);
            } else {
                this.line.add("Staatskasse stabil");
                this.line.color(COLOR.GREEN100);
            }
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 16;
        }

        // ── MAKRO-TRENDS (wide sparklines 2x2) ──
        if (y + 12 > clipTop && y < clipBot) {
            this.line.clear().add(EconTexts.¤¤advTrends);
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
        y += 16;

        int snapCount = sim.econIndicators().count();
        if (snapCount >= 2) {
            double[] food = new double[snapCount];
            double[] wages = new double[snapCount];
            double[] giniArr = new double[snapCount];
            double[] wageShare = new double[snapCount];
            for (int i = 0; i < snapCount; i++) {
                EconSnapshot s = sim.econIndicators().get(i);
                food[i]      = s.foodBasketPrice;
                wages[i]     = s.meanWage;
                giniArr[i]   = s.gini;
                wageShare[i] = s.wageShare;
            }
            // Wide sparklines: full half-width each, 55px tall
            int half     = cw / 2 - 8;
            int sparkH   = 55;
            int sparkGap = 16;

            if (y + sparkH + sparkGap > clipTop && y < clipBot) {
                // Row 1
                this.label.clear().add(EconTexts.¤¤trendFood);
                this.label.color(COLOR.WHITE150);
                this.label.render((SPRITE_RENDERER)r, x, x + half, y, y + 12);
                this.sparkline(r, x, y + 14, half, sparkH, food, snapCount);

                int sx2 = x + half + 16;
                this.label.clear().add(EconTexts.¤¤trendGini);
                this.label.color(sim.stats().gini > 0.35 ? COLOR.REDISH : COLOR.WHITE150);
                this.label.render((SPRITE_RENDERER)r, sx2, sx2 + half, y, y + 12);
                this.sparkline(r, sx2, y + 14, half, sparkH, giniArr, snapCount);
            }
            y += sparkH + sparkGap + 14;

            if (y + sparkH + sparkGap > clipTop && y < clipBot) {
                // Row 2
                this.label.clear().add(EconTexts.¤¤trendWage);
                this.label.color(COLOR.WHITE150);
                this.label.render((SPRITE_RENDERER)r, x, x + half, y, y + 12);
                this.sparkline(r, x, y + 14, half, sparkH, wages, snapCount);

                int sx2 = x + half + 16;
                this.label.clear().add(EconTexts.¤¤trendWageShare);
                this.label.color(COLOR.WHITE150);
                this.label.render((SPRITE_RENDERER)r, sx2, sx2 + half, y, y + 12);
                this.sparkline(r, sx2, y + 14, half, sparkH, wageShare, snapCount);
            }
            y += sparkH + sparkGap + 14;
        } else {
            if (y + 12 > clipTop && y < clipBot) {
                this.line.clear().add(EconTexts.¤¤advTrendsMissing);
                this.line.color(COLOR.WHITE100);
                this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            }
            y += 16;
        }

        // ── WARNKETTEN ──
        if (y + 12 > clipTop && y < clipBot) {
            this.line.clear().add(EconTexts.¤¤advChains);
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
        y += 16;

        boolean giniCritical  = sim.stats().gini > EconIndicators.GINI_WARNING;
        boolean giniRising    = sim.econIndicators().isInequalityRising();
        boolean emigrationSpike = sim.econIndicators().isEmigrationSpike();
        boolean hasChain      = false;

        FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
        boolean scarcity = false;
        for (int i = 0; i < flow.size(); i++) {
            if (flow.supplyPerDay(i) < flow.demandPerDay(i) && flow.stock(i) <= 0) {
                scarcity = true; break;
            }
        }
        if (scarcity && y + 12 > clipTop && y < clipBot) {
            this.line.clear().add((CharSequence)EconTexts.¤¤chainScarcity);
            this.line.color(COLOR.REDISH);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 16; hasChain = true;
        }
        if (sim.firmLedger().lastWorkersUnpaid() > 0 && y + 12 > clipTop && y < clipBot) {
            this.line.clear().add((CharSequence)EconTexts.¤¤chainInsolvency);
            this.line.color(COLOR.REDISH);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 16; hasChain = true;
        }
        if ((giniCritical || giniRising) && y + 12 > clipTop && y < clipBot) {
            this.line.clear().add((CharSequence)EconTexts.¤¤chainInequality);
            this.line.color(COLOR.REDISH);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 16; hasChain = true;
        }
        if (emigrationSpike && y + 12 > clipTop && y < clipBot) {
            this.line.clear().add((CharSequence)EconTexts.¤¤chainEmigration);
            this.line.color(COLOR.REDISH);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 16; hasChain = true;
        }
        if (!hasChain && y + 12 > clipTop && y < clipBot) {
            this.line.clear().add((CharSequence)EconTexts.¤¤chainAllClear);
            this.line.color(COLOR.WHITE150);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 16;
        }
        y += 8;

        // ── WARNUNGEN ──
        if (y + 12 > clipTop && y < clipBot) {
            this.line.clear().add(EconTexts.¤¤advWarnings);
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
        y += 16;
        boolean hasWarnings = false;
        if (giniCritical || giniRising) {
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advWarnInequality); this.line.color(COLOR.REDISH); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14;
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advRecInequality); this.line.color(COLOR.WHITE100); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14; hasWarnings = true;
        }
        if (sim.firmLedger().lastWorkersUnpaid() > 0) {
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advWarnInsolvent); this.line.color(COLOR.REDISH); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14;
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advRecInsolvent); this.line.color(COLOR.WHITE100); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14; hasWarnings = true;
        }
        if (emigrationSpike) {
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advWarnEmigration); this.line.color(COLOR.REDISH); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14;
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advRecEmigration); this.line.color(COLOR.WHITE100); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14; hasWarnings = true;
        }
        if (scarcity) {
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advWarnScarcity); this.line.color(COLOR.REDISH); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14;
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advRecScarcity); this.line.color(COLOR.WHITE100); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14; hasWarnings = true;
        }
        if (!hasWarnings && y + 12 > clipTop && y < clipBot) {
            this.line.clear().add(EconTexts.¤¤advAllClear);
            this.line.color(COLOR.WHITE150);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12);
            y += 14;
        }
        y += 14;

        // ── DEBT DIPLOMACY STATUS ──
        DebtDiplomacyBuffer debtBuf = sim.debtDiplomacyBuffer();
        if (debtBuf != null && debtBuf.isActive() && y + 12 > clipTop && y < clipBot) {
            int deterred = debtBuf.deterredCount();
            if (deterred > 0) {
                this.line.clear().add("Diplomatie-Puffer: ").add((CharSequence)CompactNumber.format(deterred))
                    .add((CharSequence)" Fraktionen durch Staatsreserven abgeschreckt");
                this.line.color(COLOR.WHITE150);
            } else {
                this.line.clear().add("Diplomatie-Puffer: aktiv, keine akute Abschreckung");
                this.line.color(COLOR.WHITE100);
            }
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            y += 14;
        }

        // ── POLICY SLIDERS ──
        if (y + 12 > clipTop && y < clipBot) {
            this.line.clear().add(EconTexts.¤¤advPolicy);
            this.line.color(COLOR.WHITE200);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
        y += 16;
        if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advHeadTaxLabel); this.line.color(COLOR.WHITE150); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
        y += 14;
        sim.fiscal().setHeadTax(this.slider(r, "adv_htax", x, y, sim.fiscal().headTax(), 0, 200, 5));
        y += 24;
        if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advMarketSkimLabel); this.line.color(COLOR.WHITE150); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
        y += 14;
        int mlPct2 = (int)(sim.fiscal().marketLevy() * 100);
        int mlNext2 = this.slider(r, "adv_mlevy", x, y, mlPct2, 0, 20, 1);
        if (mlNext2 != mlPct2) sim.fiscal().setMarketLevy(mlNext2 / 100.0);
        y += 24;
        if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advOddjobLabel); this.line.color(COLOR.WHITE150); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
        y += 14;
        sim.oddjobMarket().setPay(this.slider(r, "adv_oddjob", x, y, sim.oddjobMarket().pay(), 0, 20, 1));
        y += 24;

        // ── STUFE & MEILENSTEINE ──
        y += 8;
        if (y + 12 > clipTop && y < clipBot) {
            this.line.clear().add(EconTexts.¤¤advStage).add((CharSequence)prog.stage.displayName);
            this.line.color(COLOR.GREEN100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12);
        }
        y += 16;
        // ── ADMIN-BOOST STATUS (Ticket 3 v1.7.2) ──
        if (prog.stage.level >= EconProgression.Stage.INDUSTRIE.level) {
            if (y + 12 > clipTop && y < clipBot) {
                boolean active = EconProgression.adminBoostActive;
                this.line.clear().add(active ? "Industrie-Admin-Bonus: Aktiv (+20%)" : "Industrie-Admin-Bonus: Inaktiv (Feld nicht gefunden)");
                this.line.color(active ? COLOR.WHITE200 : COLOR.REDISH);
                this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12);
            }
            y += 14;
        }
        if (prog.stage != EconProgression.Stage.IMPERIUM) {
            if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advNextStage); this.line.color(COLOR.WHITE120); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
            y += 14;
            if (prog.stage == EconProgression.Stage.SUBSISTENZ) {
                boolean popOk   = sim.roster().size() >= 50;
                boolean storeOk = prog.msFirstStockpile;
                boolean fOk     = LocalPrices.foodDays() >= 3.0;
                boolean tOk     = prog.stageDays >= 30;
                renderProgressLine(r, x, y, clipTop, popOk,   "50 Siedler",     sim.roster().size()+"/50");        y += 14;
                renderProgressLine(r, x, y, clipTop, storeOk, "Lagerhaus",      storeOk?"Ja":"Nein");               y += 14;
                renderProgressLine(r, x, y, clipTop, fOk,     "Nahrung 3 Tage", String.format("%.1f",LocalPrices.foodDays())+"/3.0"); y += 14;
                renderProgressLine(r, x, y, clipTop, tOk,     "30 Tage",        prog.stageDays+"/30");              y += 14;
            } else if (prog.stage == EconProgression.Stage.HANDEL) {
                boolean popOk  = sim.roster().size() >= 100;
                boolean expOk  = prog.msFirstExport;
                boolean wageOk = sim.wages().lastWorkersPaid() > 0 && (double)sim.wages().lastPayrollPaid() / (double)Math.max(1, sim.wages().lastWorkersPaid()) > 50.0;
                boolean svcOk  = prog.msFirstTavern || prog.msFirstMarket;
                renderProgressLine(r, x, y, clipTop, popOk,  "100 Siedler",   sim.roster().size()+"/100");       y += 14;
                renderProgressLine(r, x, y, clipTop, expOk,  "Erster Export", expOk?"Ja":"Nein");                 y += 14;
                renderProgressLine(r, x, y, clipTop, wageOk, "Lohn > 50",     wageOk?"Ja":"Nein");                y += 14;
                renderProgressLine(r, x, y, clipTop, svcOk,  "Taverne/Markt", svcOk?"Ja":"Nein");                y += 14;
            } else if (prog.stage == EconProgression.Stage.INDUSTRIE) {
                boolean popOk = sim.roster().size() >= 150;
                boolean labOk = prog.msFirstLaboratory;
                boolean libOk = prog.cachedLibraryCount > 0;
                boolean milOk = prog.msFirstMilitary;
                boolean tOk   = prog.stageDays >= 30;
                renderProgressLine(r, x, y, clipTop, popOk, "150 Siedler",  sim.roster().size()+"/150");         y += 14;
                renderProgressLine(r, x, y, clipTop, labOk, "Forschung",    labOk?"Ja":"Nein");                   y += 14;
                renderProgressLine(r, x, y, clipTop, libOk, "Bibliothek",   libOk?"Ja":"Nein");                   y += 14;
                renderProgressLine(r, x, y, clipTop, milOk, "Militaer",     milOk?"Ja":"Nein");                   y += 14;
                renderProgressLine(r, x, y, clipTop, tOk,   "30 Tage",      prog.stageDays+"/30");                y += 14;
            }
        }
        if (y + 12 > clipTop && y < clipBot) { this.line.clear().add(EconTexts.¤¤advMilestones); this.line.color(COLOR.WHITE120); this.line.render((SPRITE_RENDERER)r, x, this.win.x2()-18, y, y+12); }
        y += 14;
        renderMilestone(r, x, y, clipTop, prog.msFirstStockpile,  EconTexts.¤¤advMsWarehouse);       y += 14;
        renderMilestone(r, x, y, clipTop, prog.msFirstExport,     EconTexts.¤¤advMsFirstExport);      y += 14;
        renderMilestone(r, x, y, clipTop, prog.msFirstTavern || prog.msFirstMarket, EconTexts.¤¤advMsFirstService); y += 14;
        renderMilestone(r, x, y, clipTop, prog.msFirstTemple,     EconTexts.¤¤advMsFirstTemple);     y += 14;
        renderMilestone(r, x, y, clipTop, prog.msFirstEmbassy,    EconTexts.¤¤advMsFirstEmbassy);    y += 14;
        renderMilestone(r, x, y, clipTop, prog.msStableWages,     EconTexts.¤¤advMsStableWages);      y += 14;
        renderMilestone(r, x, y, clipTop, prog.statusLowInequality, EconTexts.¤¤advMsLowInequality);    y += 14;
        renderMilestone(r, x, y, clipTop, prog.msFirstLaboratory, EconTexts.¤¤advMsFirstLab);          y += 14;
        renderMilestone(r, x, y, clipTop, prog.msFirstMilitary,   EconTexts.¤¤advMsFirstMilitary);    y += 14;
    }

    private void renderProgressLine(Renderer r, int x, int y, int clipTop,
                                     boolean done, String label, String value) {
        if (y < clipTop) return;
        this.line.clear().add((CharSequence)(done ? "+ " : "- ")).add((CharSequence)label).add((CharSequence)(": ")).add((CharSequence)value);
        // D: GREENISH pulsiert sichtbar "erreicht" -- WHITE100 bleibt fuer noch-nicht-erreichte Ziele.
        this.line.color(done ? COLOR.GREENISH : COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
    }

    private void renderMilestone(Renderer r, int x, int y, int clipTop,
                                  boolean done, String label) {
        if (y < clipTop) return;
        this.line.clear().add((CharSequence)(done ? "* " : "o ")).add((CharSequence)label);
        // D: Erreichte Meilensteine pulsieren in GREENISH statt statisch WHITE200.
        this.line.color(done ? COLOR.GREENISH : COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
    }

    // ══════════════════════════════════════════════════════
    // FLOWS TAB — Ressourcen Angebot/Nachfrage/Bestand
    // ══════════════════════════════════════════════════════
    private void renderFlows(Renderer r, int y, EconomySim sim) {
        int x = this.win.x1() + 18;
        int cw = this.winW() - 36;
        int contentTop    = y;
        int contentBottom = this.win.y2() - 18;

        FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
        int n = flow.size();

        // Header
        this.line.clear().add(EconTexts.¤¤flowsHeader);
        this.line.color(COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 16;

        // Toggle zero rows
        boolean prevZ = this.showZeroRows;
        this.showZeroRows = this.toggle(r, x, y, 180, 22, this.showZeroRows, EconTexts.¤¤uiShowZeroRows);
        if (this.showZeroRows != prevZ) this.flowsScroll = 0;
        y += 26;

        // Column header
        int nameW  = 160;
        int numW   = 100;
        int barW   = cw - nameW - numW - 14;

        this.label.clear().add("Ressource");
        this.label.color(COLOR.WHITE100);
        this.label.render((SPRITE_RENDERER)r, x, x + nameW - 4, y, y + 12);
        this.label.clear().add(EconTexts.¤¤flowsColBar);
        this.label.color(COLOR.WHITE100);
        this.label.render((SPRITE_RENDERER)r, x + nameW, x + nameW + barW, y, y + 12);
        this.label.clear().add(EconTexts.¤¤flowsColStock);
        this.label.color(COLOR.WHITE100);
        this.label.render((SPRITE_RENDERER)r, x + nameW + barW + 6, x + cw, y, y + 12);
        y += 16;

        // Build visible list
        List<RESOURCE> allRes = sim.cachedAllResources();
        this.visibleResources.clear();
        for (RESOURCE res : allRes) {
            int idx = res.index();
            if (idx >= n) continue;
            boolean active = flow.supplyPerDay(idx) > 0 || flow.demandPerDay(idx) > 0 || flow.stock(idx) > 0;
            if (this.showZeroRows || active) this.visibleResources.add(res);
        }

        int ROW_H   = 36;
        int listTop = y;
        int total   = this.visibleResources.size();
        int visible = Math.max(1, (contentBottom - listTop) / ROW_H);
        int maxSc   = Math.max(0, total - visible);
        this.flowsScroll = Math.max(0, Math.min(maxSc, this.flowsScroll + this.takeScroll()));
        this.scrollbar(r, listTop, contentBottom, this.flowsScroll, visible, total);

        for (int i = this.flowsScroll; i < total && y + ROW_H <= contentBottom; i++) {
            RESOURCE res = this.visibleResources.get(i);
            int idx = res.index();
            double supply = idx < n ? flow.supplyPerDay(idx) : 0.0;
            double demand = idx < n ? flow.demandPerDay(idx) : 0.0;
            double stock  = idx < n ? flow.stock(idx)        : 0.0;

            // Row background (alternating)
            if (i % 2 == 0) {
                COLOR.WHITE10.render((SPRITE_RENDERER)r, x, x + cw, y, y + ROW_H - 1);
            }

            // Resource name
            this.label.clear().add(res.name);
            this.label.color(COLOR.WHITE150);
            this.label.render((SPRITE_RENDERER)r, x + 2, x + nameW - 4, y + 4, y + ROW_H - 4);

            // Supply/demand bar (top 16px of row)
            this.supplyDemandBar(r, x + nameW, y + 4, barW, 16, supply, demand, stock);

            // Stock-change indicator bar (lower 10px)
            if (idx < n) {
                double chg = flow.stockChangePerDay(idx);
                int scx  = x + nameW;
                int scy  = y + 22;
                int scw  = barW;
                int sch  = 8;
                COLOR.WHITE10.render((SPRITE_RENDERER)r, scx, scx + scw, scy, scy + sch);
                if (chg > 0) {
                    int px = Math.min(scw, (int)(Math.min(1.0, chg / Math.max(1.0, supply)) * scw));
                    if (px > 0) COLOR.WHITE50.render((SPRITE_RENDERER)r, scx, scx + px, scy + 1, scy + sch - 1);
                } else if (chg < 0) {
                    int px = Math.min(scw, (int)(Math.min(1.0, -chg / Math.max(1.0, demand)) * scw));
                    if (px > 0) COLOR.REDISH.render((SPRITE_RENDERER)r, scx + scw - px, scx + scw, scy + 1, scy + sch - 1);
                }
            }

            // Numbers column
            int nx = x + nameW + barW + 6;
            double daysStock = (demand > 0) ? stock / demand : (stock > 0 ? 99 : 0);
            this.label.clear()
                .add((CharSequence)CompactNumber.format((long)supply))
                .add((CharSequence)"/")
                .add((CharSequence)CompactNumber.format((long)demand));
            this.label.color(supply >= demand ? COLOR.WHITE150 : COLOR.REDISH);
            this.label.render((SPRITE_RENDERER)r, nx, x + cw - 2, y + 4, y + 16);
            this.label.clear().add((CharSequence)(String.format("%.0f", Math.min(99, daysStock)) + "d"));
            this.label.color(daysStock >= 3 ? COLOR.WHITE100 : (daysStock >= 1 ? COLOR.WHITE150 : COLOR.REDISH));
            this.label.render((SPRITE_RENDERER)r, nx, x + cw - 2, y + 22, y + ROW_H - 4);

            y += ROW_H;
        }

        if (total == 0) {
            this.line.clear().add(EconTexts.¤¤flowsNoGoods);
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        }
    }

    // ══════════════════════════════════════════════════════
    // UI HELPER: KPI Box (label + value, framed)
    // ══════════════════════════════════════════════════════
    private void kpiBox(Renderer r, int x, int y, int w, int h, String lbl, String val) {
        // Background
        COLOR.WHITE15.render((SPRITE_RENDERER)r, x, x + w, y, y + h);
        // Border lines (manual 1px frame)
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x,         x + w, y,         y + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x,         x + w, y + h - 1, y + h);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x,         x + 1, y,         y + h);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x + w - 1, x + w, y,         y + h);
        // Value text (upper area, bright)
        this.line.clear().add((CharSequence)val);
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x + 8, x + w - 4, y + 6, y + h - 22);
        // Label text (lower area, dim)
        this.label.clear().add((CharSequence)lbl);
        this.label.color(COLOR.WHITE100);
        this.label.render((SPRITE_RENDERER)r, x + 8, x + w - 4, y + h - 18, y + h - 4);
    }

    // ══════════════════════════════════════════════════════
    // DEBUG TAB: Live-Diagnostik-Snapshot (read-only)
    // ══════════════════════════════════════════════════════
    private void renderDebug(Renderer r, int yy, EconomySim sim) {
        int x = this.win.x1() + 18;
        int y = yy;
        boolean exportOn = EconConfig.diagnosticsExportEnabled;

        // ── Header ────────────────────────────────────────────────────
        this.line.clear().add("DIAGNOSTIK / CSV-EXPORT (Live-Snapshot)");
        this.line.color(exportOn ? COLOR.GREENISH : COLOR.WHITE150);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 14);
        y += 18;

        // ── CSV-Export Toggle ────────────────────────────────────────
        boolean prev = exportOn;
        EconConfig.diagnosticsExportEnabled = this.toggle(r, x, y, 200, 22, exportOn,
                exportOn ? "CSV-Export AKTIV" : "CSV-Export AUS");
        exportOn = EconConfig.diagnosticsExportEnabled;
        y += 26;

        // Pfad-Anzeige + Öffnen-Button
        String diagPath = DiagnosticExporter.diagnosticDirectory();
        this.line.clear().add("Pfad: ").add(diagPath);
        this.line.color(COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 14;

        if (this.button(r, x, y, 220, 26, "Ordner öffnen")) {
            this.openCsvFolder(diagPath);
        }
        y += 30;

        if (!exportOn) {
            this.line.clear().add("CSV-Export ist deaktiviert. Aktiviere oben für Live-Daten.");
            this.line.color(COLOR.WHITE100);
            this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
            return;
        }

        // ── Makro-Daten ──────────────────────────────────────────────
        y += 8;
        this.line.clear().add("═══ MAKRO ═══");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 18;

        int pop = sim.roster().size();
        double gini = sim.stats().gini;
        long treasury = sim.treasury();
        long totalMoney = sim.wallets().circulating();
        long auditDelta = sim.auditDelta();
        double meanWage = sim.laborMarket().meanWage();
        int foodBasket = LocalPrices.flowFoodBasketPrice();
        double foodDays = LocalPrices.foodDays();
        long rentCollected = sim.housingMarket().lastRentCollected();
        long rentDue = sim.housingMarket().lastRentDue();

        int colW = (this.win.x2() - 18 - x) / 2;
        int col2 = x + colW;

        this.debugInfoLine(r, x, y, "Population", CompactNumber.format(pop), false);
        this.debugInfoLine(r, col2, y, "Gini", String.format("%.3f", gini), gini > 0.4);
        y += 20;

        this.debugInfoLine(r, x, y, "Treasury", CompactNumber.format(treasury), treasury < 1000L);
        this.debugInfoLine(r, col2, y, "Geldumlauf", CompactNumber.format(totalMoney), false);
        y += 20;

        this.debugInfoLine(r, x, y, "Audit Δ", CompactNumber.format(auditDelta), auditDelta < 0L);
        this.debugInfoLine(r, col2, y, "Lohn (Ø)", CompactNumber.format((int)meanWage), meanWage < 10.0);
        y += 20;

        this.debugInfoLine(r, x, y, "Food-Basket", "" + foodBasket + " D", foodBasket < 5);
        this.debugInfoLine(r, col2, y, "Food-Tage", String.format("%.1f", foodDays), foodDays < 3.0);
        y += 20;

        this.debugInfoLine(r, x, y, "Miete (eingezogen)", CompactNumber.format(rentCollected), false);
        this.debugInfoLine(r, col2, y, "Miete (fällig)", CompactNumber.format(rentDue), rentDue > rentCollected && rentDue > 0L);
        y += 24;

        // ── Balance-Levers (v0.1.1) ──────────────────────────────────
        this.line.clear().add("═══ BALANCE-LEVERS ═══");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 18;

        this.debugInfoLine(r, x, y, "min. Worker/Firma", "" + EconConfig.minimumWorkersPerWorkplace, EconConfig.minimumWorkersPerWorkplace <= 0);
        this.debugInfoLine(r, col2, y, "Gewinnshare Worker", String.format("%.2f", EconConfig.guildSurplusShare), EconConfig.guildSurplusShare <= 0.0);
        y += 20;
        this.debugInfoLine(r, x, y, "Steuer-Freigrenze", CompactNumber.format(EconConfig.perHeadTaxExemptionThreshold), EconConfig.perHeadTaxExemptionThreshold <= 0);
        this.debugInfoLine(r, col2, y, "foodAffordability", EconConfig.foodAffordabilityGateEnabled ? "AN" : "AUS", !EconConfig.foodAffordabilityGateEnabled);
        y += 20;
        this.debugInfoLine(r, x, y, "Min. Profit/Arbeiter", CompactNumber.format((int) EconConfig.guildSurplusMinProfitPerWorker), EconConfig.guildSurplusMinProfitPerWorker <= 0.0);
        y += 24;

        // ── Firmen-Daten ─────────────────────────────────────────────
        this.line.clear().add("═══ FIRMEN ═══");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 18;

        FirmLedger ledger = sim.firmLedger();
        if (ledger != null) {
            java.util.List<FirmLedger.FirmFinancialSnapshot> firms = ledger.firmFinancialSnapshots();
            int firmCount = firms.size();
            int unprofitable = 0;
            int unpaid = 0;
            double totalProfit = 0.0;
            for (FirmLedger.FirmFinancialSnapshot f : firms) {
                if (f.profitPerDay() <= 0.0) unprofitable++;
                if (f.workersUnpaid() > 0) unpaid++;
                totalProfit += f.profitPerDay();
            }
            this.debugInfoLine(r, x, y, "Firmen (aktiv)", "" + firmCount, false);
            this.debugInfoLine(r, col2, y, "Unprofitabel", "" + unprofitable, unprofitable > firmCount / 2);
            y += 20;
            this.debugInfoLine(r, x, y, "∑ Profit/Tag", String.format("%.1f", totalProfit), totalProfit < 0.0);
            this.debugInfoLine(r, col2, y, "Unbez. Arbeiter", "" + unpaid, unpaid > 0);
            y += 24;
        }

        // ── Ressourcen-Daten ─────────────────────────────────────────
        this.line.clear().add("═══ RESSOURCEN ═══");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 18;

        FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
        int resCount = flow.size();
        int starvingCount = 0;
        int lowSupplyCount = 0;
        for (int i = 0; i < resCount; ++i) {
            double demand = flow.demandPerDay(i);
            double stock = flow.stock(i);
            if (demand > 0.0 && stock / demand < 3.0) starvingCount++;
            if (demand > 0.0 && stock / demand < 7.0) lowSupplyCount++;
        }
        this.debugInfoLine(r, x, y, "Ressourcen", "" + resCount, false);
        this.debugInfoLine(r, col2, y, "Starving (<3d)", "" + starvingCount, starvingCount > 0);
        y += 20;
        this.debugInfoLine(r, x, y, "Knapp (<7d)", "" + lowSupplyCount, lowSupplyCount > resCount / 4);
        this.debugInfoLine(r, col2, y, "Preise bereit", sim.flowPrices().ready() ? "Ja" : "Nein", !sim.flowPrices().ready());
        y += 24;

        // ── CSV-Dateien ───────────────────────────────────────────────
        this.line.clear().add("═══ DATEIEN ═══");
        this.line.color(COLOR.WHITE200);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
        y += 18;

        java.io.File dir = new java.io.File(diagPath);
        String[] csvFiles = dir.exists() ? dir.list((d, name) -> name.endsWith(".csv")) : new String[0];
        if (csvFiles == null) csvFiles = new String[0];
        java.util.Arrays.sort(csvFiles, java.util.Comparator.reverseOrder());

        int fileCount = csvFiles.length;
        this.debugInfoLine(r, x, y, "CSV-Dateien", "" + fileCount, fileCount == 0);
        y += 20;

        // Zeige die ersten 6 Dateinamen
        int showCount = Math.min(6, csvFiles.length);
        for (int i = 0; i < showCount; ++i) {
            String fileName = csvFiles[i];
            // Kurzform: 60 Zeichen + "..."
            if (fileName.length() > 63) fileName = fileName.substring(0, 60) + "...";
            this.line.clear().add(fileName);
            this.line.color(fileName.contains("macro") ? COLOR.WHITE150 :
                           (fileName.contains("resource") ? COLOR.WHITE120 : COLOR.WHITE100));
            this.line.render((SPRITE_RENDERER)r, x + 12, this.win.x2() - 18, y, y + 12);
            y += 16;
        }

        if (csvFiles.length > 6) {
            this.line.clear().add("... und " + (csvFiles.length - 6) + " weitere");
            this.line.color(COLOR.WHITE50);
            this.line.render((SPRITE_RENDERER)r, x + 12, this.win.x2() - 18, y, y + 12);
        }
    }

    /** Zeile mit Label und Wert im Debug-Tab. */
    private void debugInfoLine(Renderer r, int x, int y, String label, String value, boolean alert) {
        this.line.clear().add(label).add(": ").add(value);
        this.line.color(alert ? COLOR.REDISH : COLOR.WHITE100);
        this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
    }

    /** Öffnet den CSV-Ordner im Dateimanager. Erstellt das Verzeichnis falls nötig. */
    private void openCsvFolder(String path) {
        if (path == null || path.isEmpty()) {
            EventLog.log("DEBUG", "openCsvFolder: Pfad ist null/leer — Abbruch.");
            return;
        }
        java.io.File dir = new java.io.File(path);
        if (!dir.exists()) {
            try {
                java.nio.file.Files.createDirectories(dir.toPath());
            } catch (IOException | RuntimeException e) {
                EventLog.log("DEBUG", "openCsvFolder: Verzeichnis nicht erstellbar: " + e.getMessage());
                return;
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
                return;
            }
        } catch (IOException | IllegalArgumentException e) {
            EventLog.log("DEBUG", "Desktop.open() fehlgeschlagen: " + e.getMessage() + " — versuche xdg-open");
        }
        // Fallback: xdg-open (Linux), open (macOS), start (Windows)
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("linux")) {
                Runtime.getRuntime().exec(new String[]{"xdg-open", path});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", path});
            } else {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", path});
            }
        } catch (IOException | RuntimeException e2) {
            EventLog.log("DEBUG", "Ordner nicht zu öffnen: " + e2.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    // UI HELPER: Ampel Box (One-Screen-Story — klickbare Status-Box mit Gauge)
    // ══════════════════════════════════════════════════════
    private boolean ampelBox(Renderer r, int x, int y, int w, int h,
                             String title, double frac, boolean ok,
                             String statusText, Tab target) {
        this.toggleBox.setDim((double)w, (double)h);
        this.toggleBox.moveX1Y1((double)x, (double)y);
        boolean over = this.hit(this.toggleBox);
        boolean clicked = this.leftClicked && over;
        if (clicked) {
            this.clickedAField = true;
            this.menu = target.menu;
            this.tab = target;
            return true;
        }
        // Background
        (over ? COLOR.WHITE35 : COLOR.WHITE20).render((SPRITE_RENDERER)r, x, x + w, y, y + h);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y, y + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER)r, x, x + w, y + h - 1, y + h);
        // Title
        this.label.clear().add((CharSequence)title);
        this.label.color(over ? COLOR.WHITE200 : COLOR.WHITE150);
        this.label.render((SPRITE_RENDERER)r, x + 6, x + w - 6, y + 4, y + 16);
        // Gauge bar
        int gx = x + 6;
        int gw = w - 12;
        int gy = y + 21;
        int gh = 12;
        COLOR.WHITE15.render((SPRITE_RENDERER)r, gx, gx + gw, gy, gy + gh);
        int fill = (int)Math.max(0, Math.min(gw, frac * gw));
        if (fill > 0) {
            (ok ? COLOR.GREEN100 : COLOR.REDISH).render((SPRITE_RENDERER)r, gx, gx + fill, gy + 1, gy + gh - 1);
        }
        COLOR.WHITE25.render((SPRITE_RENDERER)r, gx, gx + gw, gy, gy + 1);
        COLOR.WHITE25.render((SPRITE_RENDERER)r, gx, gx + gw, gy + gh - 1, gy + gh);
        // Status text
        this.label.clear().add((CharSequence)statusText);
        this.label.color(ok ? COLOR.GREEN100 : COLOR.REDISH);
        this.label.render((SPRITE_RENDERER)r, x + 6, x + w - 6, y + 38, y + 54);
        return clicked;
    }

    // ══════════════════════════════════════════════════════
    // UI HELPER: Gauge bar (horizontal fill, green/red)
    // ══════════════════════════════════════════════════════
    private void gaugeBar(Renderer r, int x, int y, int w, int h, double frac, boolean ok) {
        // Background
        COLOR.WHITE15.render((SPRITE_RENDERER)r, x, x + w, y, y + h);
        // Fill: D-Fix -- GREENISH pulsiert fuer positive Zustände, REDISH fuer Probleme.
        int fill = (int)Math.max(0, Math.min(w, frac * w));
        if (fill > 0) {
            (ok ? COLOR.GREEN100 : COLOR.REDISH).render((SPRITE_RENDERER)r, x, x + fill, y + 1, y + h - 1);
        }
        // Top/bottom border
        COLOR.WHITE25.render((SPRITE_RENDERER)r, x, x + w, y,         y + 1);
        COLOR.WHITE25.render((SPRITE_RENDERER)r, x, x + w, y + h - 1, y + h);
    }

    // ══════════════════════════════════════════════════════
    // UI HELPER: Supply/Demand bar with stock overlay
    // ══════════════════════════════════════════════════════
    private void supplyDemandBar(Renderer r, int x, int y, int w, int h,
                                  double supply, double demand, double stock) {
        double maxVal = Math.max(supply, demand);
        // Background
        COLOR.WHITE10.render((SPRITE_RENDERER)r, x, x + w, y, y + h);
        if (maxVal <= 0) return;
        double scale = maxVal * 1.3;

        // Supply bar (green if surplus, red if deficit)
        boolean surplus = supply >= demand;
        int supplyPx = (int)Math.min(w, supply / scale * w);
        if (supplyPx > 0) {
            (surplus ? COLOR.WHITE50 : COLOR.REDISH)
                .render((SPRITE_RENDERER)r, x, x + supplyPx, y + 1, y + h - 1);
        }

        // Demand marker (1px white line)
        int demandPx = (int)Math.min(w - 1, demand / scale * w);
        COLOR.WHITE150.render((SPRITE_RENDERER)r, x + demandPx, x + demandPx + 1, y, y + h);

        // Stock overlay: last 30px shows days-of-stock (0-14 days scale)
        if (demand > 0 && stock >= 0) {
            double days   = stock / demand;
            int dsW = Math.min(30, w / 4);
            int dsX = x + w - dsW - 1;
            COLOR.WHITE25.render((SPRITE_RENDERER)r, dsX, dsX + dsW, y + 2, y + h - 2);
            int dsFill = (int)Math.min(dsW, (days / 14.0) * dsW);
            if (dsFill > 0) {
                (days >= 3 ? COLOR.WHITE100 : (days >= 1 ? COLOR.WHITE50 : COLOR.REDISH))
                    .render((SPRITE_RENDERER)r, dsX, dsX + dsFill, y + 2, y + h - 2);
            }
        }
    }
}

