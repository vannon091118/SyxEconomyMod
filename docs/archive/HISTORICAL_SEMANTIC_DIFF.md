# Semantic Diff: TiredGirl4 vs SyxEconomyMod Workspace

> ⚠️ **HISTORISCH — ARCHIVIERT** — Stand: 2026-07-21 (Pre-Phase-4 / Pre-v0.1.0).
> Dieses Dokument ist ein eingefrorener Snapshot zum damaligen Vergleichszeitpunkt und
> wird nicht mehr gepflegt. Für aktuelle Aussagen siehe:
> - Architektur: [../ARCHITECTURE.md](../ARCHITECTURE.md)
> - Klassen-Übersicht: [../GLOSSARY.md](../GLOSSARY.md)
> - Release-Historie: [../../CHANGELOG.md](../../CHANGELOG.md)

Generated: Di 21. Jul 21:36:52 CEST 2026

## BrokeFoodPlan.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.464240577 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.467240537 +0200
@@ -25,7 +25,7 @@
 
 public final class BrokeFoodPlan
 extends AIPLAN.PLANRES {
-    private final AIPLAN.PLANRES.Resumer starving = new AIPLAN.PLANRES.Resumer(this, "starving"){
+    private final AIPLAN.PLANRES.Resumer starving = new AIPLAN.PLANRES.Resumer("starving"){
 
         protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
             BrokeFoodPlan.markStarvedIfLethal(humanoid);
```

## DebtDiplomacyBuffer.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.517239877 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.520239837 +0200
@@ -91,7 +91,8 @@
                 }
                 LIST vanillaPotential = war.potential();
                 boolean[] potential = new boolean[FACTIONS.MAX()];
-                for (FactionNPC faction : vanillaPotential) {
+                for (Object factionObj : vanillaPotential) {
+                    FactionNPC faction = (FactionNPC) factionObj;
                     if (faction == null || !faction.isActive()) continue;
                     potential[faction.index()] = true;
                 }
@@ -164,7 +165,7 @@
         double power = AD.power().get((Faction)player);
         power += DebtDiplomacyBuffer.availableMercenaryPower(DebtDiplomacyBuffer.effectiveCredits(player.credits().getD(), EconConfig.diplomacyDebtThreshold));
         if (player.capitolRegion() != null) {
-            power += RD.MILITARY().power.getD((Object)player.capitolRegion());
+            power += RD.MILITARY().power.getD(player.capitolRegion());
         }
         return Math.max(power -= SETT.INVADOR().invadingPower(), 0.0);
     }
```

## DrinkTransactionPlan.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.526239758 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.529239718 +0200
@@ -55,14 +55,14 @@
 extends AIPLAN.PLANRES {
     private final AffordabilityGate gate;
     private final IdentityHashMap<Induvidual, PendingRound> pending = new IdentityHashMap();
-    private final AISUB animation = new AISUB.Simple(this, "ECON_DRINKING"){
+    private final AISUB animation = new AISUB.Simple("ECON_DRINKING"){
 
         protected AISTATE resume(Humanoid h, AIManager d) {
             d.subByte = (byte)(d.subByte + 1);
             return d.subByte < 4 ? AI.STATES().STAND.activate(h, d, (double)(1.5f + RND.rFloat((double)2.0))) : null;
         }
     };
-    private final AIPLAN.PLANRES.Resumer walk = new AIPLAN.PLANRES.Resumer("walking to an affordable tavern"){
+    private final AIPLAN.PLANRES.Resumer walk = new Resumer("walking to an affordable tavern"){
 
         protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
             boolean unaffordable = false;
@@ -99,7 +99,7 @@
             DrinkTransactionPlan.this.release(h, d);
         }
     };
-    private final AIPLAN.PLANRES.Resumer serve = new AIPLAN.PLANRES.Resumer("drinking"){
+    private final AIPLAN.PLANRES.Resumer serve = new Resumer("drinking"){
 
         protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
             FSERVICE s = DrinkTransactionPlan.service(d);
```

## EconomySim.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.548239467 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.550239441 +0200
@@ -24,9 +24,16 @@
 import init.resources.RESOURCE;
 import init.resources.RESOURCES;
 import java.io.IOException;
+import java.util.ArrayList;
+import java.util.Collections;
+import java.util.List;
 import settlement.entity.humanoid.Humanoid;
 import settlement.main.SETT;
+import settlement.room.infra.stockpile.StockpileInstance;
+import settlement.room.main.RoomBlueprintImp;
+import settlement.room.main.RoomBlueprintIns;
 import settlement.room.main.RoomInstance;
+import snake2d.util.sets.LIST;
 import settlement.stats.Induvidual;
 import settlement.stats.STATS;
 import snake2d.util.file.FileGetter;
@@ -115,6 +122,10 @@
     private final ServiceMarket serviceMarket = new ServiceMarket();
     private final ServicePlanController servicePlanController = new ServicePlanController(this.serviceMarket, this.fiscal, this.firmLedger);
     private static volatile EconomySim active = null;
+    private volatile Humanoid cachedRichestCitizen;
+    private volatile List<StockpileInstance> cachedStateWarehouses = Collections.emptyList();
+    private volatile List<RoomBlueprintImp> cachedWorkplaces = Collections.emptyList();
+    private volatile List<RESOURCE> cachedAllResources = Collections.emptyList();
     private int ticks = 0;
     private double encounterCarry = 0.0;
     private long seedSupply = 0L;
@@ -282,6 +293,22 @@
         return this.wallets;
     }
 
+    public Humanoid cachedRichestCitizen() {
+        return this.cachedRichestCitizen;
+    }
+
+    public List<StockpileInstance> cachedStateWarehouses() {
+        return this.cachedStateWarehouses;
+    }
+
+    public List<RoomBlueprintImp> cachedWorkplaces() {
+        return this.cachedWorkplaces;
+    }
+
+    public List<RESOURCE> cachedAllResources() {
+        return this.cachedAllResources;
+    }
+
     public static EconomySim active() {
         return active;
     }
@@ -314,6 +341,7 @@
         }
         this.roster.rebuild();
         if (this.roster.size() < 2) {
+            this.updateRenderCaches();
             return;
         }
         ++this.ticks;
@@ -407,12 +435,76 @@
             this.histogram.dump(this.roster, this.wallets, this.ticks);
             this.logLedger();
         }
+        this.updateRenderCaches();
     }
 
     public WealthStats stats() {
         return this.stats;
     }
 
+    private void updateRenderCaches() {
+        // richest citizen
+        Humanoid best = null;
+        int most = -1;
+        for (int i = 0; i < this.roster.size(); ++i) {
+            Humanoid h = this.roster.get(i);
+            int money = this.wallets.get(h);
+            if (money > most) {
+                most = money;
+                best = h;
+            }
+        }
+        this.cachedRichestCitizen = most > 0 ? best : null;
+
+        // all resources (static, but cache reference to avoid repeated engine calls)
+        LIST<RESOURCE> allResources = RESOURCES.ALL();
+        ArrayList<RESOURCE> resourcesList = new ArrayList<>(allResources.size());
+        for (RESOURCE resource : allResources) {
+            resourcesList.add(resource);
+        }
+        this.cachedAllResources = resourcesList;
+
+        // state-owned warehouses (state-owned first, then private)
+        if (SETT.ROOMS() != null && SETT.ROOMS().STOCKPILE != null) {
+            int stockpiles = SETT.ROOMS().STOCKPILE.instancesSize();
+            ArrayList<StockpileInstance> ordered = new ArrayList<>(stockpiles);
+            for (int i = 0; i < stockpiles; ++i) {
+                StockpileInstance w = (StockpileInstance) SETT.ROOMS().STOCKPILE.getInstance(i);
+                if (w != null && this.stateWarehouses.isStateOwned((RoomInstance) w)) {
+                    ordered.add(w);
+                }
+            }
+            for (int i = 0; i < stockpiles; ++i) {
+                StockpileInstance w = (StockpileInstance) SETT.ROOMS().STOCKPILE.getInstance(i);
+                if (w != null && !this.stateWarehouses.isStateOwned((RoomInstance) w)) {
+                    ordered.add(w);
+                }
+            }
+            this.cachedStateWarehouses = Collections.unmodifiableList(ordered);
+        } else {
+            this.cachedStateWarehouses = Collections.emptyList();
+        }
+
+        // workplaces with employment
+        if (SETT.ROOMS() != null) {
+            LIST all = SETT.ROOMS().imps();
+            ArrayList<RoomBlueprintImp> jobs = new ArrayList<>();
+            for (int i = 0; i < all.size(); ++i) {
+                RoomBlueprintImp b = (RoomBlueprintImp) all.get(i);
+                if (b.employment() == null || !(b instanceof RoomBlueprintIns)) {
+                    continue;
+                }
+                RoomBlueprintIns workplace = (RoomBlueprintIns) b;
+                if (workplace.instancesSize() > 0) {
+                    jobs.add(b);
+                }
+            }
+            this.cachedWorkplaces = Collections.unmodifiableList(jobs);
+        } else {
+            this.cachedWorkplaces = Collections.emptyList();
+        }
+    }
+
     private void refreshFlowPrices() {
         int goods = RESOURCES.ALL().size();
         double[] anchors = new double[goods];
```

## EconomyWindow.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.557239349 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.560239309 +0200
@@ -32,7 +32,7 @@
 import init.resources.RESOURCE;
 import init.resources.RESOURCES;
 import init.sprite.UI.UI;
-import java.util.ArrayList;
+import java.util.List;
 import java.util.Objects;
 import settlement.entity.humanoid.Humanoid;
 import settlement.main.SETT;
@@ -67,7 +67,6 @@
 import NORMALIZED.ProductionSubsidies;
 import NORMALIZED.Purchases;
 import NORMALIZED.ReligionMarket;
-import NORMALIZED.Roster;
 import NORMALIZED.StateWageMarket;
 import NORMALIZED.StateWarehouses;
 import NORMALIZED.Taxes;
@@ -97,6 +96,7 @@
     private static final int SLIDER_H = 10;
     private static final int CHART_H = 150;
     private static final int TAX_THRESHOLD_MAX = 50000;
+    private static final String[] SEASON_NAMES = new String[]{"Spring", "Summer", "Autumn", "Winter"};
     private boolean open = false;
     private Tab tab = Tab.DISTRIBUTION;
     private final Rec btn = new Rec();
@@ -360,7 +360,7 @@
     private void renderButton(Renderer r) {
         boolean hot = this.hit(this.btn);
         (hot || this.open ? COLOR.WHITE50 : COLOR.WHITE25).render((SPRITE_RENDERER)r, this.btn.x1(), this.btn.x2(), this.btn.y1(), this.btn.y2());
-        this.label.clear().add((CharSequence)"ECONOMY");
+        this.label.clear().add(EconTexts.¤¤windowTitle);
         this.label.color(hot || this.open ? COLOR.WHITE200 : COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, this.btn.x1() + 10, this.btn.x2(), this.btn.y1() + 6, this.btn.y2());
     }
@@ -382,18 +382,18 @@
             GText gText = this.label.clear();
             gText.add((CharSequence)(switch (t.ordinal()) {
                 default -> throw new MatchException(null, null);
-                case 0 -> "WEALTH";
-                case 1 -> "CITIZENS";
-                case 2 -> "PRICES";
-                case 3 -> "GUILDS";
-                case 4 -> "SUBSIDIES";
-                case 5 -> "GRANARY";
-                case 6 -> "MARKET";
-                case 7 -> "TAXES";
-                case 8 -> "FAITH";
-                case 9 -> "LABOR";
-                case 10 -> "RELIEF";
-                case 11 -> "BOOKS";
+                case 0 -> EconTexts.¤¤tabWealth;
+                case 1 -> EconTexts.¤¤tabCitizens;
+                case 2 -> EconTexts.¤¤tabPrices;
+                case 3 -> EconTexts.¤¤tabGuilds;
+                case 4 -> EconTexts.¤¤tabSubsidies;
+                case 5 -> EconTexts.¤¤tabGranary;
+                case 6 -> EconTexts.¤¤tabMarket;
+                case 7 -> EconTexts.¤¤tabTaxes;
+                case 8 -> EconTexts.¤¤tabFaith;
+                case 9 -> EconTexts.¤¤tabLabor;
+                case 10 -> EconTexts.¤¤tabRelief;
+                case 11 -> EconTexts.¤¤tabBooks;
             }));
             this.label.color(sel ? COLOR.WHITE200 : COLOR.WHITE120);
             this.label.render((SPRITE_RENDERER)r, b.x1() + 10, b.x2(), b.y1() + 5, b.y2());
@@ -404,10 +404,10 @@
         int x = this.win.x1() + 18;
         FlowPrices prices = sim.flowPrices();
         FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
-        this.line.clear().add((CharSequence)"Local clearing prices used for construction, warehouses and firm inputs.");
+        this.line.clear().add(EconTexts.¤¤pricesHeader);
         this.line.color(prices.ready() ? COLOR.WHITE150 : COLOR.REDISH);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
-        this.line.clear().add((CharSequence)"coverage 1.00 = target stock; below 1 is scarce, above 1 is a glut.  Red = 10x+ anchor.");
+        this.line.clear().add(EconTexts.¤¤pricesCoverageHint);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 16, y + 12);
         y += 20;
@@ -417,39 +417,40 @@
         int coverageX = x + 505;
         int stockX = x + 620;
         int flowX = x + 735;
-        this.line.clear().add((CharSequence)"resource");
+        this.line.clear().add(EconTexts.¤¤pricesColumnResource);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, localX - 8, y, y + 12);
-        this.line.clear().add((CharSequence)"local / unit");
+        this.line.clear().add(EconTexts.¤¤pricesColumnLocal);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, localX, anchorX - 8, y, y + 12);
-        this.line.clear().add((CharSequence)"trade anchor");
+        this.line.clear().add(EconTexts.¤¤pricesColumnAnchor);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, anchorX, multipleX - 8, y, y + 12);
-        this.line.clear().add((CharSequence)"multiple");
+        this.line.clear().add(EconTexts.¤¤pricesColumnMultiple);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, multipleX, coverageX - 8, y, y + 12);
-        this.line.clear().add((CharSequence)"coverage");
+        this.line.clear().add(EconTexts.¤¤pricesColumnCoverage);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, coverageX, stockX - 8, y, y + 12);
-        this.line.clear().add((CharSequence)"stock");
+        this.line.clear().add(EconTexts.¤¤pricesColumnStock);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, stockX, flowX - 8, y, y + 12);
-        this.line.clear().add((CharSequence)"supply / demand per day");
+        this.line.clear().add(EconTexts.¤¤pricesColumnSupplyDemand);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, flowX, this.win.x2() - 18, y, y + 12);
         int headerY = y;
-        int total = RESOURCES.ALL().size();
+        List<RESOURCE> resources = sim.cachedAllResources();
+        int total = resources.size();
         int listTop = y += 14;
         int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
         int maxScroll = Math.max(0, total - visible);
         this.priceScroll = Math.max(0, Math.min(maxScroll, this.priceScroll + this.takeScroll()));
         this.scrollbar(r, listTop, this.win.y2() - 18, this.priceScroll, visible, total);
-        this.line.clear().add((CharSequence)("" + (this.priceScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(total, this.priceScroll + visible))).add((CharSequence)" of ").add((CharSequence)("" + total));
+        this.line.clear().add((CharSequence)("" + (this.priceScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(total, this.priceScroll + visible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + total));
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, headerY, headerY + 12);
         for (int i = this.priceScroll; i < total && y + 30 < this.win.y2() - 18; ++i) {
-            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
+            RESOURCE resource = resources.get(i);
             int index = resource.index();
             int local = prices.priceRoundedUp(index);
             double anchorRaw = prices.anchor(index);
@@ -488,25 +489,26 @@
     private void renderSubsidies(Renderer r, int y, EconomySim sim) {
         int x = this.win.x1() + 18;
         ProductionSubsidies subsidies = sim.productionSubsidies();
-        this.line.clear().add((CharSequence)"production this season: ").add((CharSequence)CompactNumber.format(subsidies.seasonUnits())).add((CharSequence)" units   due ").add((CharSequence)CompactNumber.format(subsidies.seasonDue())).add((CharSequence)"   paid ").add((CharSequence)CompactNumber.format(subsidies.seasonPaid()));
+        this.line.clear().add(EconTexts.¤¤subSeasonProd).add((CharSequence)CompactNumber.format(subsidies.seasonUnits())).add(EconTexts.¤¤subUnitsDue).add((CharSequence)CompactNumber.format(subsidies.seasonDue())).add(EconTexts.¤¤subPaid).add((CharSequence)CompactNumber.format(subsidies.seasonPaid()));
         this.line.color(subsidies.seasonPaid() < subsidies.seasonDue() ? COLOR.REDISH : COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
-        this.line.clear().add((CharSequence)"resource").add((CharSequence)"                   output/day").add((CharSequence)"              denari per new unit");
+        this.line.clear().add(EconTexts.¤¤subColumns);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 16, y + 12);
         int headerY = y;
-        int total = RESOURCES.ALL().size();
+        List<RESOURCE> resources = sim.cachedAllResources();
+        int total = resources.size();
         int listTop = y += 14;
         int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
         int maxScroll = Math.max(0, total - visible);
         this.subsidyScroll = Math.max(0, Math.min(maxScroll, this.subsidyScroll + this.takeScroll()));
         this.scrollbar(r, listTop, this.win.y2() - 18, this.subsidyScroll, visible, total);
-        this.line.clear().add((CharSequence)("" + (this.subsidyScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(total, this.subsidyScroll + visible))).add((CharSequence)" of ").add((CharSequence)("" + total));
+        this.line.clear().add((CharSequence)("" + (this.subsidyScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(total, this.subsidyScroll + visible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + total));
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, headerY, headerY + 12);
         FlowMeter.Snapshot flow = sim.flowMeter().snapshot();
         for (int i = this.subsidyScroll; i < total && y + 30 < this.win.y2() - 18; ++i) {
-            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
+            RESOURCE resource = resources.get(i);
             this.label.clear().add(resource.name);
             this.label.color(COLOR.WHITE150);
             this.label.render((SPRITE_RENDERER)r, x, x + 175, y + 8, y + 22);
@@ -519,69 +521,54 @@
             if (next != value) {
                 subsidies.setBounty(resource, next);
             }
-            this.valueField(r, "f_sub_" + resource.key, x + 310 + 260 + 18, y, 150, subsidies.bounty(resource), 0, EconConfig.productionSubsidyMax, v -> subsidies.setBounty(resource, v), " / unit", subsidies.bounty(resource) > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+            this.valueField(r, "f_sub_" + resource.key, x + 310 + 260 + 18, y, 150, subsidies.bounty(resource), 0, EconConfig.productionSubsidyMax, v -> subsidies.setBounty(resource, v), EconTexts.¤¤uiPerUnit, subsidies.bounty(resource) > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
             y += 30;
         }
     }
 
     private void renderCitizens(Renderer r, int y, EconomySim sim) {
         int x = this.win.x1() + 18;
-        Humanoid richest = EconomyWindow.richest(sim);
+        Humanoid richest = sim.cachedRichestCitizen();
         if (richest == null) {
-            this.line.clear().add((CharSequence)"nobody has any money yet");
+            this.line.clear().add(EconTexts.¤¤citNobodyMoney);
             this.line.color(COLOR.WHITE100);
             this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
             return;
         }
         int money = sim.wallets().get(richest);
         int median = sim.stats().median;
-        this.line.clear().add((CharSequence)"the richest settler holds ").add((CharSequence)CompactNumber.format(money)).add((CharSequence)" denari");
+        this.line.clear().add(EconTexts.¤¤citRichestHolds).add((CharSequence)CompactNumber.format(money)).add(EconTexts.¤¤uiDenari);
         if (median > 0) {
-            this.line.add((CharSequence)"   (").add((CharSequence)CompactNumber.format(money / Math.max(1, median))).add((CharSequence)"x the median)");
+            this.line.add((CharSequence)"   (").add((CharSequence)CompactNumber.format(money / Math.max(1, median))).add(EconTexts.¤¤citTimesMedian);
         }
         this.line.color(COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
-        this.line.clear().add((CharSequence)"median ").add((CharSequence)CompactNumber.format(median)).add((CharSequence)"   mean ").add((CharSequence)CompactNumber.format(sim.stats().mean)).add((CharSequence)"   gini ").add((CharSequence)String.format("%.2f", sim.stats().gini));
+        this.line.clear().add(EconTexts.¤¤citMedian).add((CharSequence)CompactNumber.format(median)).add(EconTexts.¤¤citMean).add((CharSequence)CompactNumber.format(sim.stats().mean)).add(EconTexts.¤¤citGini).add((CharSequence)String.format("%.2f", sim.stats().gini));
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 16, y + 12);
-        if (this.button(r, x, y += 20, 260, 30, "JUMP TO THE RICHEST")) {
+        if (this.button(r, x, y += 20, 260, 30, EconTexts.¤¤citBtnJump)) {
             VIEW.s().ui.subjects.show(richest);
             VIEW.s().getWindow().centerAtTile(richest.tc().x(), richest.tc().y());
         }
-        this.line.clear().add((CharSequence)"opens their panel and moves the camera to them");
+        this.line.clear().add(EconTexts.¤¤citJumpHint);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 36, y + 12);
     }
 
-    private static Humanoid richest(EconomySim sim) {
-        Humanoid best = null;
-        int most = -1;
-        Roster roster = sim.roster();
-        for (int i = 0; i < roster.size(); ++i) {
-            int money = sim.wallets().get(roster.get(i));
-            if (money <= most) continue;
-            most = money;
-            best = roster.get(i);
-        }
-        return most > 0 ? best : null;
-    }
-
     private void renderStateWarehouses(Renderer r, int y, EconomySim sim) {
-        StockpileInstance w;
-        int i;
         int x = this.win.x1() + 18;
         StateWarehouses state = sim.stateWarehouses();
-        this.line.clear().add((CharSequence)"granary: bought ").add((CharSequence)CompactNumber.format(state.lastUnitsBought())).add((CharSequence)" units for ").add((CharSequence)CompactNumber.format(state.lastBought())).add((CharSequence)"   sold ").add((CharSequence)CompactNumber.format(state.lastUnitsSold())).add((CharSequence)" for ").add((CharSequence)CompactNumber.format(state.lastSold()));
+        this.line.clear().add(EconTexts.¤¤granBought).add((CharSequence)CompactNumber.format(state.lastUnitsBought())).add(EconTexts.¤¤granUnitsFor).add((CharSequence)CompactNumber.format(state.lastBought())).add(EconTexts.¤¤granSold).add((CharSequence)CompactNumber.format(state.lastUnitsSold())).add(EconTexts.¤¤granFor).add((CharSequence)CompactNumber.format(state.lastSold()));
         this.line.color(COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
         y += 16;
-        this.line.clear().add((CharSequence)"clerks paid: ").add((CharSequence)CompactNumber.format(state.lastWorkersPaid())).add((CharSequence)"   wages ").add((CharSequence)CompactNumber.format(state.lastWagesPaid()));
+        this.line.clear().add(EconTexts.¤¤granClerksPaid).add((CharSequence)CompactNumber.format(state.lastWorkersPaid())).add(EconTexts.¤¤granWages).add((CharSequence)CompactNumber.format(state.lastWagesPaid()));
         if (state.lastWorkersUnpaid() > 0) {
-            this.line.add((CharSequence)"   UNPAID ").add((CharSequence)CompactNumber.format(state.lastWorkersUnpaid()));
+            this.line.add(EconTexts.¤¤granUnpaid).add((CharSequence)CompactNumber.format(state.lastWorkersUnpaid()));
         }
         this.line.color(state.lastWorkersUnpaid() > 0 ? COLOR.REDISH : COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
-        this.label.clear().add((CharSequence)"clerk salary / season");
+        this.label.clear().add(EconTexts.¤¤granSalary);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 240, (y += 18) + 8, y + 22);
         int wage = state.wage();
@@ -589,33 +576,22 @@
         if (nextWage != wage) {
             state.setWage(nextWage);
         }
-        this.valueField(r, "f_state_wage", x + 250 + 260 + 18, y, 150, state.wage(), 0, EconConfig.wageMax, state::setWage, " denari", state.wage() > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+        this.valueField(r, "f_state_wage", x + 250 + 260 + 18, y, 150, state.wage(), 0, EconConfig.wageMax, state::setWage, EconTexts.¤¤uiDenari, state.wage() > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
         y += 34;
         int owned = state.ownedCount();
-        this.line.clear().add((CharSequence)"state warehouses: ").add((CharSequence)CompactNumber.format(owned)).add((CharSequence)" of ").add((CharSequence)CompactNumber.format(SETT.ROOMS().STOCKPILE.instancesSize())).add((CharSequence)"   (a granary only holds what its crates are set to hold)");
+        this.line.clear().add(EconTexts.¤¤granStateCount).add((CharSequence)CompactNumber.format(owned)).add(EconTexts.¤¤uiOf).add((CharSequence)CompactNumber.format(SETT.ROOMS().STOCKPILE.instancesSize())).add(EconTexts.¤¤granWarning);
         this.line.color(owned == 0 ? COLOR.REDISH : COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
         y += 14;
         if (owned > 0) {
             boolean allLiq = state.allLiquidating();
-            boolean nextAll = this.toggle(r, x, y, 200, 26, allLiq, "LIQUIDATE ALL");
+            boolean nextAll = this.toggle(r, x, y, 200, 26, allLiq, EconTexts.¤¤granBtnLiqAll);
             if (nextAll != allLiq) {
                 state.setAllLiquidating(nextAll);
             }
             y += 30;
         }
-        int stockpiles = SETT.ROOMS().STOCKPILE.instancesSize();
-        ArrayList<StockpileInstance> ordered = new ArrayList<StockpileInstance>(stockpiles);
-        for (i = 0; i < stockpiles; ++i) {
-            w = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
-            if (w == null || !state.isStateOwned((RoomInstance)w)) continue;
-            ordered.add(w);
-        }
-        for (i = 0; i < stockpiles; ++i) {
-            w = (StockpileInstance)SETT.ROOMS().STOCKPILE.getInstance(i);
-            if (w == null || state.isStateOwned((RoomInstance)w)) continue;
-            ordered.add(w);
-        }
+        List<StockpileInstance> ordered = sim.cachedStateWarehouses();
         int listCount = ordered.size();
         int listHeight = Math.min(4, Math.max(1, listCount)) * 30;
         int ownerTop = y;
@@ -624,44 +600,45 @@
         this.granaryScroll = Math.max(0, Math.min(ownerMax, this.granaryScroll + this.takeScroll(this.hit(this.ownerRec(ownerTop, listHeight)))));
         this.scrollbar(r, ownerTop, ownerTop + listHeight, this.granaryScroll, ownerVisible, listCount);
         for (int idx = this.granaryScroll; idx < listCount && y + 30 <= ownerTop + listHeight; ++idx) {
-            boolean liquid;
-            boolean nextLiq;
             StockpileInstance warehouse = (StockpileInstance)ordered.get(idx);
             boolean owns = state.isStateOwned((RoomInstance)warehouse);
+            boolean liquid = state.isLiquidating((RoomInstance)warehouse);
+            boolean nextLiq;
             this.label.clear().add((CharSequence)warehouse.name());
             this.label.color(owns ? COLOR.WHITE200 : COLOR.WHITE120);
             this.label.render((SPRITE_RENDERER)r, x, x + 240, y + 8, y + 22);
-            boolean next = this.toggle(r, x + 250, y + 4, 110, 22, owns, owns ? "STATE" : "private");
+            boolean next = this.toggle(r, x + 250, y + 4, 110, 22, owns, owns ? EconTexts.¤¤granBtnState : EconTexts.¤¤granBtnPrivate);
             if (next != owns) {
                 state.setStateOwned((RoomInstance)warehouse, next);
             }
-            if (owns && (nextLiq = this.toggle(r, x + 370, y + 4, 150, 22, liquid, (liquid = state.isLiquidating((RoomInstance)warehouse)) ? "LIQUIDATE" : "HOARD (held)")) != liquid) {
+            if (owns && (nextLiq = this.toggle(r, x + 370, y + 4, 150, 22, liquid, liquid ? EconTexts.¤¤granBtnLiq : EconTexts.¤¤granBtnHoard)) != liquid) {
                 state.setLiquidating((RoomInstance)warehouse, nextLiq);
             }
             y += 30;
         }
         int headerY = y = ownerTop + listHeight + 6;
-        int total = RESOURCES.ALL().size();
+        List<RESOURCE> resources = sim.cachedAllResources();
+        int total = resources.size();
         int listTop = y += 14;
         int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
         int maxScroll = Math.max(0, total - visible);
         this.stateScroll = Math.max(0, Math.min(maxScroll, this.stateScroll + this.takeScroll(this.hit(this.win))));
         this.scrollbar(r, listTop, this.win.y2() - 18, this.stateScroll, visible, total);
-        this.line.clear().add((CharSequence)("" + (this.stateScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(total, this.stateScroll + visible))).add((CharSequence)" of ").add((CharSequence)("" + total));
+        this.line.clear().add((CharSequence)("" + (this.stateScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(total, this.stateScroll + visible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + total));
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, headerY, headerY + 12);
         for (int i2 = this.stateScroll; i2 < total && y + 30 < this.win.y2() - 18; ++i2) {
-            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i2);
+            RESOURCE resource = resources.get(i2);
             int buy = state.buyPrice(resource);
             int sell = state.sellPrice(resource);
             boolean active = buy > 0 || sell > 0;
             this.label.clear().add(resource.name);
             this.label.color(active ? COLOR.WHITE200 : COLOR.WHITE100);
             this.label.render((SPRITE_RENDERER)r, x, x + 130, y + 8, y + 22);
-            this.valueField(r, "f_buy_" + resource.key, x + 140, y, 150, buy, 0, EconConfig.statePriceMax, v -> state.setBuyPrice(resource, v), "buy at ", "", buy > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
-            this.valueField(r, "f_sell_" + resource.key, x + 300, y, 150, sell, 0, EconConfig.statePriceMax, v -> state.setSellPrice(resource, v), "sell at ", "", sell > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+            this.valueField(r, "f_buy_" + resource.key, x + 140, y, 150, buy, 0, EconConfig.statePriceMax, v -> state.setBuyPrice(resource, v), EconTexts.¤¤granBuyAt, "", buy > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+            this.valueField(r, "f_sell_" + resource.key, x + 300, y, 150, sell, 0, EconConfig.statePriceMax, v -> state.setSellPrice(resource, v), EconTexts.¤¤granSellAt, "", sell > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
             int stock = sim.warehouseMarket().stateStock(resource);
-            this.line.clear().add((CharSequence)"stores ").add((CharSequence)CompactNumber.format(stock)).add((CharSequence)" in state hands");
+            this.line.clear().add(EconTexts.¤¤granStores).add((CharSequence)CompactNumber.format(stock)).add(EconTexts.¤¤granInStateHands);
             this.line.color(stock > 0 ? COLOR.WHITE150 : COLOR.WHITE100);
             this.line.render((SPRITE_RENDERER)r, x + 470, this.win.x2() - 18 - 12, y + 8, y + 22);
             y += 30;
@@ -671,20 +648,21 @@
     private void renderCrownMarket(Renderer r, int y, EconomySim sim) {
         int x = this.win.x1() + 18;
         StateWarehouses state = sim.stateWarehouses();
-        this.line.clear().add((CharSequence)"crown market: sold ").add((CharSequence)CompactNumber.format(state.lastCrownMarketUnitsSold())).add((CharSequence)" units for ").add((CharSequence)CompactNumber.format(state.lastCrownMarketSold()));
+        this.line.clear().add(EconTexts.¤¤mrkSold).add((CharSequence)CompactNumber.format(state.lastCrownMarketUnitsSold())).add(EconTexts.¤¤granUnitsFor).add((CharSequence)CompactNumber.format(state.lastCrownMarketSold()));
         this.line.color(COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y, y + 12);
-        this.line.clear().add((CharSequence)"Felled wood, cleared rock, forage, rubble and similar crown property.  Default 75/unit.");
+        this.line.clear().add(EconTexts.¤¤mrkHint);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2() - 18, y += 16, y + 12);
-        int total = RESOURCES.ALL().size();
+        List<RESOURCE> resources = sim.cachedAllResources();
+        int total = resources.size();
         int listTop = y += 22;
         int visible = Math.max(1, (this.win.y2() - 18 - y) / 30);
         int maxScroll = Math.max(0, total - visible);
         this.marketScroll = Math.max(0, Math.min(maxScroll, this.marketScroll + this.takeScroll()));
         this.scrollbar(r, listTop, this.win.y2() - 18, this.marketScroll, visible, total);
         for (int i = this.marketScroll; i < total && y + 30 < this.win.y2() - 18; ++i) {
-            RESOURCE resource = (RESOURCE)RESOURCES.ALL().get(i);
+            RESOURCE resource = resources.get(i);
             int posted = state.crownMarketPrice(resource);
             this.label.clear().add(resource.name);
             this.label.color(COLOR.WHITE150);
@@ -693,10 +671,10 @@
             if (next != posted) {
                 state.setCrownMarketPrice(resource, next);
             }
-            this.valueField(r, "f_crown_market_" + resource.key, x + 180 + 260 + 16, y, 130, posted, 0, EconConfig.statePriceMax, v -> state.setCrownMarketPrice(resource, v), "", "/unit", posted == 75 ? COLOR.WHITE150 : COLOR.WHITE200);
+            this.valueField(r, "f_crown_market_" + resource.key, x + 180 + 260 + 16, y, 130, posted, 0, EconConfig.statePriceMax, v -> state.setCrownMarketPrice(resource, v), "", EconTexts.¤¤uiPerUnitShort, posted == 75 ? COLOR.WHITE150 : COLOR.WHITE200);
             int live = sim.flowPrices().priceRoundedUp(resource.index());
             long crown = sim.warehouseMarket().crownUnits(resource);
-            this.line.clear().add((CharSequence)"live market ").add((CharSequence)CompactNumber.format(live)).add((CharSequence)"   crown units ").add((CharSequence)CompactNumber.format(crown));
+            this.line.clear().add(EconTexts.¤¤mrkLiveMarket).add((CharSequence)CompactNumber.format(live)).add(EconTexts.¤¤mrkCrownUnits).add((CharSequence)CompactNumber.format(crown));
             this.line.color(COLOR.WHITE100);
             this.line.render((SPRITE_RENDERER)r, x + 180 + 260 + 160, this.win.x2() - 18 - 12, y + 8, y + 22);
             y += 30;
@@ -792,8 +770,8 @@
         if (w != e.wage()) {
             e.setWage(w);
         }
-        this.valueField(r, "f_sw_" + e.name, x + 160 + 260 + 18, y, 100, e.wage(), 0, EconConfig.wageMax, e::setWage, "/day", e.wage() > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"last ").add((CharSequence)CompactNumber.format(e.lastPaid())).add((CharSequence)" / ").add((CharSequence)CompactNumber.format(e.lastDue())).add((CharSequence)" due to ").add((CharSequence)CompactNumber.format(e.lastWorkers())).add((CharSequence)" workers").add((CharSequence)(e.treasuryBlocked() ? "   TREASURY SHORT" : ""));
+        this.valueField(r, "f_sw_" + e.name, x + 160 + 260 + 18, y, 100, e.wage(), 0, EconConfig.wageMax, e::setWage, EconTexts.¤¤uiPerDay, e.wage() > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+        this.line.clear().add(EconTexts.¤¤wageRowLast).add((CharSequence)CompactNumber.format(e.lastPaid())).add(EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(e.lastDue())).add(EconTexts.¤¤wageRowDueTo).add((CharSequence)CompactNumber.format(e.lastWorkers())).add(EconTexts.¤¤wageRowWorkers).add((CharSequence)(e.treasuryBlocked() ? EconTexts.¤¤wageRowTreasuryShort : ""));
         this.line.color(e.treasuryBlocked() ? COLOR.REDISH : COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x + 160 + 260 + 130, this.win.x2(), y + 8, y + 22);
         return y + 30 + 2;
@@ -910,15 +888,15 @@
     private void renderDistribution(Renderer r, int y, WealthStats s) {
         int x = this.win.x1() + 18;
         if (s.people == 0) {
-            this.line.clear().add((CharSequence)"no settlers");
+            this.line.clear().add(EconTexts.¤¤wealthNoSettlers);
             this.line.color(COLOR.WHITE100);
             this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
             return;
         }
-        this.line.clear().add((CharSequence)CompactNumber.format(s.people)).add((CharSequence)" settlers   mean ").add((CharSequence)CompactNumber.format(s.mean)).add((CharSequence)"   median ").add((CharSequence)CompactNumber.format(s.median));
+        this.line.clear().add((CharSequence)CompactNumber.format(s.people)).add(EconTexts.¤¤wealthSettlersMean).add((CharSequence)CompactNumber.format(s.mean)).add(EconTexts.¤¤wealthMedian).add((CharSequence)CompactNumber.format(s.median));
         this.line.color(COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
-        this.line.clear().add((CharSequence)"richest ").add((CharSequence)CompactNumber.format(s.max)).add((CharSequence)"   gini ").add((CharSequence)EconomyWindow.fmt2(s.gini));
+        this.line.clear().add(EconTexts.¤¤wealthRichest).add((CharSequence)CompactNumber.format(s.max)).add(EconTexts.¤¤wealthGini).add((CharSequence)EconomyWindow.fmt2(s.gini));
         this.line.color(COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 16, y + 12);
         this.chart(r, x, y += 22, this.winW() - 36, 150, s);
@@ -950,7 +928,7 @@
             this.line.color(COLOR.WHITE200);
             this.line.render((SPRITE_RENDERER)r, x, x + w, y - 14, y - 2);
         }
-        this.line.clear().add((CharSequence)"0 denari");
+        this.line.clear().add(EconTexts.¤¤wealthZeroDenari);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, x + 80, base + 4, base + 16);
         this.line.clear().add((CharSequence)CompactNumber.format((long)s.bucketWidth * 16L));
@@ -961,29 +939,19 @@
     private void renderWages(Renderer r, int y, EconomySim sim) {
         int x = this.win.x1() + 18;
         FirmLedger ledger = sim.firmLedger();
-        if (SETT.ROOMS() == null) {
-            return;
-        }
-        LIST all = SETT.ROOMS().imps();
-        ArrayList<RoomBlueprintImp> jobs = new ArrayList<RoomBlueprintImp>();
-        for (int i = 0; i < all.size(); ++i) {
-            RoomBlueprintIns workplace;
-            RoomBlueprintImp b = (RoomBlueprintImp)all.get(i);
-            if (b.employment() == null || !(b instanceof RoomBlueprintIns) || (workplace = (RoomBlueprintIns)b).instancesSize() <= 0) continue;
-            jobs.add(b);
-        }
-        this.line.clear().add((CharSequence)"guild surplus income   due ").add((CharSequence)CompactNumber.format(ledger.lastIncomeDue())).add((CharSequence)"   paid ").add((CharSequence)CompactNumber.format(ledger.lastIncomePaid())).add((CharSequence)"   mean marginal ").add((CharSequence)CompactNumber.format(ledger.meanPositiveMarginal()));
+        List<RoomBlueprintImp> jobs = sim.cachedWorkplaces();
+        this.line.clear().add(EconTexts.¤¤wageSurplusDue).add((CharSequence)CompactNumber.format(ledger.lastIncomeDue())).add(EconTexts.¤¤wagePaid).add((CharSequence)CompactNumber.format(ledger.lastIncomePaid())).add(EconTexts.¤¤wageMeanMarginal).add((CharSequence)CompactNumber.format(ledger.meanPositiveMarginal()));
         this.line.color(ledger.lastWorkersUnpaid() > 0 ? COLOR.REDISH : COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
         y += 14;
         if (ledger.lastWorkersUnpaid() > 0) {
-            this.line.clear().add((CharSequence)"INSOLVENT: ").add((CharSequence)CompactNumber.format(ledger.lastWorkersUnpaid())).add((CharSequence)" guild shares could not clear through the treasury");
+            this.line.clear().add(EconTexts.¤¤wageInsolvent).add((CharSequence)CompactNumber.format(ledger.lastWorkersUnpaid())).add(EconTexts.¤¤wageInsolHint);
             this.line.color(COLOR.REDISH);
             this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
         }
         y += 16;
         if (jobs.isEmpty()) {
-            this.line.clear().add((CharSequence)"no workplaces have been built yet");
+            this.line.clear().add(EconTexts.¤¤wageNoWorkplaces);
             this.line.color(COLOR.WHITE100);
             this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
             return;
@@ -1000,7 +968,7 @@
             this.label.render((SPRITE_RENDERER)r, x, x + 200, y + 8, y + 22);
             if (EconomicRoles.stateFundedMilitary(b)) {
                 int prio = sim.laborMarket().derivedPriority(b);
-                this.line.clear().add((CharSequence)"STATE FUNDED   trainees ").add((CharSequence)CompactNumber.format(b.employment().employed())).add((CharSequence)"   salary ").add((CharSequence)CompactNumber.format(sim.militaryPayroll().wage())).add((CharSequence)" / season").add((CharSequence)(prio >= 0 ? "   prio " + prio : ""));
+                this.line.clear().add(EconTexts.¤¤wageStateFunded).add((CharSequence)CompactNumber.format(b.employment().employed())).add(EconTexts.¤¤wageSalary).add((CharSequence)CompactNumber.format(sim.militaryPayroll().wage())).add(EconTexts.¤¤uiPerSeason).add((CharSequence)(prio >= 0 ? EconTexts.¤¤wagePrio + prio : ""));
                 this.line.color(COLOR.WHITE120);
                 this.line.render((SPRITE_RENDERER)r, x + 220, this.win.x2() - 18, y + 8, y + 22);
                 y += 30;
@@ -1016,9 +984,9 @@
                     target += workplace.getInstance(ri).employees().needed();
                 }
             }
-            this.line.clear().add((CharSequence)"profit/day ").add((CharSequence)CompactNumber.format(profit)).add((CharSequence)"   marginal ").add((CharSequence)CompactNumber.format(marginal)).add((CharSequence)"   workers ").add((CharSequence)CompactNumber.format(b.employment().employed())).add((CharSequence)"/").add((CharSequence)CompactNumber.format(target));
+            this.line.clear().add(EconTexts.¤¤wageProfitDay).add((CharSequence)CompactNumber.format(profit)).add(EconTexts.¤¤wageMarginal).add((CharSequence)CompactNumber.format(marginal)).add(EconTexts.¤¤wageWorkers).add((CharSequence)CompactNumber.format(b.employment().employed())).add((CharSequence)"/").add((CharSequence)CompactNumber.format(target));
             if (EconConfig.laborMarketEnabled && prio >= 0) {
-                this.line.add((CharSequence)"   prio ").add((CharSequence)("" + prio));
+                this.line.add(EconTexts.¤¤wagePrio).add((CharSequence)("" + prio));
             }
             this.line.color(profit < 0.0 ? COLOR.REDISH : COLOR.WHITE100);
             this.line.render((SPRITE_RENDERER)r, x + 220, this.win.x2() - 18, y + 8, y + 22);
@@ -1032,7 +1000,7 @@
         int lcount;
         int x = this.win.x1() + 18;
         Fiscal fiscal = sim.fiscal();
-        this.label.clear().add((CharSequence)"per adult / season");
+        this.label.clear().add(EconTexts.¤¤taxPerAdult);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, y + 8, y + 22);
         int head = EconConfig.perHeadTax;
@@ -1042,8 +1010,8 @@
         }
         this.valueField(r, "f_headtax", x + 200 + 260 + 18, y, 130, EconConfig.perHeadTax, 0, 50000, v -> {
             EconConfig.perHeadTax = v;
-        }, " denari", EconConfig.perHeadTax > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.label.clear().add((CharSequence)"market skim");
+        }, EconTexts.¤¤uiDenari, EconConfig.perHeadTax > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+        this.label.clear().add(EconTexts.¤¤taxMarketSkim);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         int percent = (int)Math.round(EconConfig.marketTaxRate * 100.0);
@@ -1054,7 +1022,7 @@
         this.valueField(r, "f_marketskim", x + 200 + 260 + 18, y, 90, newPercent, 0, 100, v -> {
             EconConfig.marketTaxRate = (double)v / 100.0;
         }, "%", newPercent > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.label.clear().add((CharSequence)"warehouse stock / season");
+        this.label.clear().add(EconTexts.¤¤taxWarehouseStock);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         int stockTax = EconConfig.warehouseTaxPercent;
@@ -1065,23 +1033,23 @@
         this.valueField(r, "f_warehousetax", x + 200 + 260 + 18, y, 90, EconConfig.warehouseTaxPercent, 0, 100, v -> {
             EconConfig.warehouseTaxPercent = v;
         }, "%", EconConfig.warehouseTaxPercent > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"collected: head ").add((CharSequence)CompactNumber.format(fiscal.headTaxCollected())).add((CharSequence)"   market ").add((CharSequence)CompactNumber.format(fiscal.marketReceipts())).add((CharSequence)"   warehouse stock ").add((CharSequence)CompactNumber.format(sim.warehouseMarket().lastTaxed())).add((CharSequence)" from ").add((CharSequence)CompactNumber.format(sim.warehouseMarket().lastTaxPayers())).add((CharSequence)" merchants");
+        this.line.clear().add(EconTexts.¤¤taxCollectedHead).add((CharSequence)CompactNumber.format(fiscal.headTaxCollected())).add(EconTexts.¤¤taxCollectedMarket).add((CharSequence)CompactNumber.format(fiscal.marketReceipts())).add(EconTexts.¤¤taxCollectedWarehouse).add((CharSequence)CompactNumber.format(sim.warehouseMarket().lastTaxed())).add(EconTexts.¤¤taxCollectedFrom).add((CharSequence)CompactNumber.format(sim.warehouseMarket().lastTaxPayers())).add(EconTexts.¤¤taxCollectedMerchants);
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
-        this.line.clear().add((CharSequence)"paid out: food procurement ").add((CharSequence)CompactNumber.format(fiscal.rationOut())).add((CharSequence)"   producer income ").add((CharSequence)CompactNumber.format(fiscal.producerIncome()));
+        this.line.clear().add(EconTexts.¤¤taxPaidOut).add((CharSequence)CompactNumber.format(fiscal.rationOut())).add(EconTexts.¤¤taxPaidProducer).add((CharSequence)CompactNumber.format(fiscal.producerIncome()));
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 18, y + 12);
         Taxes taxes = sim.taxes();
-        EconConfig.taxesEnabled = this.toggle(r, x, y += 26, 150, 22, EconConfig.taxesEnabled, EconConfig.taxesEnabled ? "WEALTH TAX ON" : "wealth tax off");
-        this.label.clear().add((CharSequence)"exempt below");
+        EconConfig.taxesEnabled = this.toggle(r, x, y += 26, 150, 22, EconConfig.taxesEnabled, EconConfig.taxesEnabled ? EconTexts.¤¤taxWealthOn : EconTexts.¤¤taxWealthOff);
+        this.label.clear().add(EconTexts.¤¤taxExemptBelow);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         int floor = this.logSlider(r, "wtfloor", x + 200, y, taxes.floor(), 0, 50000);
         if (floor != taxes.floor()) {
             taxes.setFloor(floor);
         }
-        this.valueField(r, "f_wtfloor", x + 200 + 260 + 18, y, 130, taxes.floor(), 0, 50000, taxes::setFloor, " denari", EconConfig.taxesEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.label.clear().add((CharSequence)"rate above floor");
+        this.valueField(r, "f_wtfloor", x + 200 + 260 + 18, y, 130, taxes.floor(), 0, 50000, taxes::setFloor, EconTexts.¤¤uiDenari, EconConfig.taxesEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
+        this.label.clear().add(EconTexts.¤¤taxRateAboveFloor);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         int wrate = this.slider(r, "wtrate", x + 200, y, taxes.rate(), 0, 100, 1);
@@ -1089,19 +1057,19 @@
             taxes.setRate(wrate);
         }
         this.valueField(r, "f_wtrate", x + 200 + 260 + 18, y, 90, taxes.rate(), 0, 100, taxes::setRate, "%", EconConfig.taxesEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"last wealth-tax take: ").add((CharSequence)CompactNumber.format(taxes.lastCollected())).add((CharSequence)" from ").add((CharSequence)CompactNumber.format(taxes.lastPayers())).add((CharSequence)" payers");
+        this.line.clear().add(EconTexts.¤¤taxLastWealthTake).add((CharSequence)CompactNumber.format(taxes.lastCollected())).add(EconTexts.¤¤taxLastWealthPayers).add((CharSequence)CompactNumber.format(taxes.lastPayers())).add(EconTexts.¤¤taxLastWealthPayersSuffix);
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
         Liturgy lit = sim.liturgy();
-        EconConfig.liturgyEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.liturgyEnabled, EconConfig.liturgyEnabled ? "LITURGY ON" : "liturgy off");
-        this.label.clear().add((CharSequence)"richest taxed");
+        EconConfig.liturgyEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.liturgyEnabled, EconConfig.liturgyEnabled ? EconTexts.¤¤taxLiturgyOn : EconTexts.¤¤taxLiturgyOff);
+        this.label.clear().add(EconTexts.¤¤taxRichestTaxed);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         EconConfig.liturgyHeadcount = lcount = this.slider(r, "litcount", x + 200, y, EconConfig.liturgyHeadcount, 1, 50, 1);
         this.valueField(r, "f_litcount", x + 200 + 260 + 18, y, 90, EconConfig.liturgyHeadcount, 1, 50, v -> {
             EconConfig.liturgyHeadcount = v;
         }, "", EconConfig.liturgyEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.label.clear().add((CharSequence)"share of wealth");
+        this.label.clear().add(EconTexts.¤¤taxShareWealth);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         int lpct = (int)Math.round(EconConfig.liturgyRate * 100.0);
@@ -1112,26 +1080,26 @@
         this.valueField(r, "f_litrate", x + 200 + 260 + 18, y, 90, nlpct, 0, 100, v -> {
             EconConfig.liturgyRate = (double)v / 100.0;
         }, "%", EconConfig.liturgyEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.label.clear().add((CharSequence)"every N seasons");
+        this.label.clear().add(EconTexts.¤¤taxEveryNSeasons);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         EconConfig.liturgyIntervalSeasons = lint = this.slider(r, "litint", x + 200, y, EconConfig.liturgyIntervalSeasons, 1, 16, 1);
         this.valueField(r, "f_litint", x + 200 + 260 + 18, y, 90, EconConfig.liturgyIntervalSeasons, 1, 16, v -> {
             EconConfig.liturgyIntervalSeasons = v;
         }, "", EconConfig.liturgyEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"last liturgy: ").add((CharSequence)CompactNumber.format(lit.lastLevied())).add((CharSequence)" from ").add((CharSequence)CompactNumber.format(lit.lastNamed())).add((CharSequence)" named");
+        this.line.clear().add(EconTexts.¤¤taxLastLiturgy).add((CharSequence)CompactNumber.format(lit.lastLevied())).add(EconTexts.¤¤taxLastLiturgyNamed).add((CharSequence)CompactNumber.format(lit.lastNamed())).add(EconTexts.¤¤taxLastLiturgyNamedSuffix);
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
         DebtBondage bondage = sim.debtBondage();
-        EconConfig.debtSlaveryEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.debtSlaveryEnabled, EconConfig.debtSlaveryEnabled ? "DEBT BONDAGE ON" : "debt bondage off");
-        this.label.clear().add((CharSequence)"enslave at debt");
+        EconConfig.debtSlaveryEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.debtSlaveryEnabled, EconConfig.debtSlaveryEnabled ? EconTexts.¤¤taxDebtBondageOn : EconTexts.¤¤taxDebtBondageOff);
+        this.label.clear().add(EconTexts.¤¤taxEnslaveAtDebt);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         EconConfig.debtSlaveThreshold = thr = this.logSlider(r, "dsthr", x + 200, y, EconConfig.debtSlaveThreshold, 1, 50000);
         this.valueField(r, "f_dsthr", x + 200 + 260 + 18, y, 130, EconConfig.debtSlaveThreshold, 1, 50000, v -> {
             EconConfig.debtSlaveThreshold = Math.max(1, v);
         }, " denari", EconConfig.debtSlaveryEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"outstanding tax debt: ").add((CharSequence)CompactNumber.format(bondage.lastOutstanding())).add((CharSequence)"   enslaved last season: ").add((CharSequence)CompactNumber.format(bondage.lastEnslaved()));
+        this.line.clear().add(EconTexts.¤¤taxOutstandingDebt).add((CharSequence)CompactNumber.format(bondage.lastOutstanding())).add(EconTexts.¤¤taxEnslavedLastSeason).add((CharSequence)CompactNumber.format(bondage.lastEnslaved()));
         this.line.color(bondage.lastEnslaved() > 0 ? COLOR.WHITE200 : COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 12);
     }
@@ -1139,7 +1107,7 @@
     private void renderReligion(Renderer r, int y, EconomySim sim) {
         int x = this.win.x1() + 18;
         ReligionMarket.ensureSized();
-        EconConfig.religionTaxEnabled = this.toggle(r, x, y, 150, 22, EconConfig.religionTaxEnabled, EconConfig.religionTaxEnabled ? "JIZYA ON" : "jizya off");
+        EconConfig.religionTaxEnabled = this.toggle(r, x, y, 150, 22, EconConfig.religionTaxEnabled, EconConfig.religionTaxEnabled ? EconTexts.¤¤faithJizyaOn : EconTexts.¤¤faithJizyaOff);
         y += 34;
         int n = EngineSeams.religionCount();
         for (int i = 0; i < n && i < EconConfig.religionHeadTax.length; ++i) {
@@ -1154,10 +1122,10 @@
             }
             this.valueField(r, "f_rel" + i, x + 200 + 260 + 18, y, 130, EconConfig.religionHeadTax[i], 0, 50000, v -> {
                 EconConfig.religionHeadTax[idx] = v;
-            }, " denari", EconConfig.religionHeadTax[i] > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+            }, EconTexts.¤¤uiDenari, EconConfig.religionHeadTax[i] > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
             y += 30;
         }
-        this.line.clear().add((CharSequence)"collected: ").add((CharSequence)CompactNumber.format(sim.religionTaxCollected())).add((CharSequence)"   apostasies: ").add((CharSequence)CompactNumber.format(sim.religionMarket().lastConversions())).add((CharSequence)"   new debtors: ").add((CharSequence)CompactNumber.format(sim.religionMarket().lastDebtors()));
+        this.line.clear().add(EconTexts.¤¤faithCollected).add((CharSequence)CompactNumber.format(sim.religionTaxCollected())).add(EconTexts.¤¤faithApostasies).add((CharSequence)CompactNumber.format(sim.religionMarket().lastConversions())).add(EconTexts.¤¤faithNewDebtors).add((CharSequence)CompactNumber.format(sim.religionMarket().lastDebtors()));
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 12, y + 12);
     }
@@ -1169,7 +1137,7 @@
         int pct;
         int x = this.win.x1() + 18;
         CorveeController.ensureSized();
-        EconConfig.corveeEnabled = this.toggle(r, x, y, 150, 22, EconConfig.corveeEnabled, EconConfig.corveeEnabled ? "CORVEE ON" : "corvee off");
+        EconConfig.corveeEnabled = this.toggle(r, x, y, 150, 22, EconConfig.corveeEnabled, EconConfig.corveeEnabled ? EconTexts.¤¤corveeOn : EconTexts.¤¤corveeOff);
         y += 36;
         int seasons = CorveeController.seasonsPerYear();
         int dps = CorveeController.daysPerSeason();
@@ -1178,9 +1146,8 @@
         int cellH = 26;
         int gap = 4;
         int labelW = 70;
-        String[] seasonNames = new String[]{"Spring", "Summer", "Autumn", "Winter"};
         for (int s = 0; s < seasons; ++s) {
-            this.label.clear().add((CharSequence)(s < seasonNames.length ? seasonNames[s] : "S" + (s + 1)));
+            this.label.clear().add((CharSequence)(s < SEASON_NAMES.length ? SEASON_NAMES[s] : "S" + (s + 1)));
             this.label.color(COLOR.WHITE150);
             this.label.render((SPRITE_RENDERER)r, x, x + labelW, y + 6, y + cellH);
             for (int d = 0; d < dps; ++d) {
@@ -1194,55 +1161,55 @@
             }
             y += cellH + gap;
         }
-        this.label.clear().add((CharSequence)"draft up to");
+        this.label.clear().add(EconTexts.¤¤corveeDraftUpTo);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 10) + 8, y + 22);
         EconConfig.corveeDraftPercent = pct = this.slider(r, "corvpct", x + 200, y, EconConfig.corveeDraftPercent, 0, 100, 1);
         this.valueField(r, "f_corvpct", x + 200 + 260 + 18, y, 90, EconConfig.corveeDraftPercent, 0, 100, v -> {
             EconConfig.corveeDraftPercent = v;
         }, "%", COLOR.WHITE200);
-        this.label.clear().add((CharSequence)"but no more than");
+        this.label.clear().add(EconTexts.¤¤corveeButNoMoreThan);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         EconConfig.corveeDraftMax = cap = this.logSlider(r, "corvcap", x + 200, y, EconConfig.corveeDraftMax, 0, 20000);
         this.valueField(r, "f_corvcap", x + 200 + 260 + 18, y, 110, EconConfig.corveeDraftMax, 0, 20000, v -> {
             EconConfig.corveeDraftMax = v;
-        }, " people", COLOR.WHITE200);
-        this.line.clear().add((CharSequence)(CorveeController.isCorveeToday() ? "TODAY IS A CORVEE DAY - drafting ~" + Math.round(sim.corveeDraftFractionLast() * 100.0) + "%" : "today is an ordinary working day"));
+        }, EconTexts.¤¤corveePeople, COLOR.WHITE200);
+        this.line.clear().add((CharSequence)(CorveeController.isCorveeToday() ? "TODAY IS A CORVEE DAY - drafting ~" + Math.round(sim.corveeDraftFractionLast() * 100.0) + "%" : EconTexts.¤¤corveeOrdinaryDay));
         this.line.color(CorveeController.isCorveeToday() ? COLOR.WHITE200 : COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 12);
         OddjobMarket odd = sim.oddjobMarket();
-        EconConfig.oddjobWageEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.oddjobWageEnabled, EconConfig.oddjobWageEnabled ? "ODDJOB WAGE ON" : "oddjob wage off");
-        this.label.clear().add((CharSequence)"denari / task");
+        EconConfig.oddjobWageEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.oddjobWageEnabled, EconConfig.oddjobWageEnabled ? EconTexts.¤¤corveeOddjobOn : EconTexts.¤¤corveeOddjobOff);
+        this.label.clear().add(EconTexts.¤¤corveeDenariPerTask);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 30) + 8, y + 22);
         EconConfig.oddjobWagePerTask = owage = this.slider(r, "oddwage", x + 200, y, EconConfig.oddjobWagePerTask, 0, 250, 1);
         this.valueField(r, "f_oddwage", x + 200 + 260 + 18, y, 110, EconConfig.oddjobWagePerTask, 0, 250, v -> {
             EconConfig.oddjobWagePerTask = v;
-        }, " denari", EconConfig.oddjobWageEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"this season: ").add((CharSequence)CompactNumber.format(odd.currentPaid())).add((CharSequence)" / ").add((CharSequence)CompactNumber.format(odd.currentTasks())).add((CharSequence)" tasks").add((CharSequence)"   last: ").add((CharSequence)CompactNumber.format(odd.lastPaid())).add((CharSequence)" / ").add((CharSequence)CompactNumber.format(odd.lastTasks())).add((CharSequence)" tasks").add((CharSequence)"   working now: ").add((CharSequence)CompactNumber.format(odd.activeWorkersNow())).add((CharSequence)"   cycle: ").add((long)odd.cycleProgressPercent()).add((CharSequence)"%").add((CharSequence)(odd.treasuryBlocked() ? "   BLOCKED: TREASURY IN DEBT" : ""));
+        }, EconTexts.¤¤uiDenari, EconConfig.oddjobWageEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
+        this.line.clear().add(EconTexts.¤¤corveeThisSeason).add((CharSequence)CompactNumber.format(odd.currentPaid())).add(EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(odd.currentTasks())).add(EconTexts.¤¤corveeTasks).add(EconTexts.¤¤corveeLast).add((CharSequence)CompactNumber.format(odd.lastPaid())).add(EconTexts.¤¤uiSlash).add((CharSequence)CompactNumber.format(odd.lastTasks())).add(EconTexts.¤¤corveeTasks).add(EconTexts.¤¤corveeWorkingNow).add((CharSequence)CompactNumber.format(odd.activeWorkersNow())).add(EconTexts.¤¤corveeCycle).add((long)odd.cycleProgressPercent()).add((CharSequence)"%").add((CharSequence)(odd.treasuryBlocked() ? EconTexts.¤¤corveeBlocked : ""));
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 12);
         TransportMarket tm = sim.transportMarket();
-        EconConfig.transportFeeEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.transportFeeEnabled, EconConfig.transportFeeEnabled ? "HAULAGE FEE ON" : "haulage fee off");
-        this.line.clear().add((CharSequence)"the state pays denari per 100 tiles hauled per day, split among the crews");
+        EconConfig.transportFeeEnabled = this.toggle(r, x, y += 28, 150, 22, EconConfig.transportFeeEnabled, EconConfig.transportFeeEnabled ? EconTexts.¤¤corveeHaulageOn : EconTexts.¤¤corveeHaulageOff);
+        this.line.clear().add(EconTexts.¤¤corveeHaulageDesc);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 14);
-        this.label.clear().add((CharSequence)"fee rate");
+        this.label.clear().add(EconTexts.¤¤corveeHaulageRate);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 190, (y += 22) + 8, y + 22);
         EconConfig.transportFeePer100TileDay = trate = this.slider(r, "trate", x + 200, y, EconConfig.transportFeePer100TileDay, 0, 100, 1);
         this.valueField(r, "f_trate", x + 200 + 260 + 18, y, 130, EconConfig.transportFeePer100TileDay, 0, 100, v -> {
             EconConfig.transportFeePer100TileDay = v;
-        }, " /100t/day", EconConfig.transportFeeEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"last tick: ").add((CharSequence)CompactNumber.format(tm.lastPaid())).add((CharSequence)" denari").add((CharSequence)"   active stations: ").add((CharSequence)CompactNumber.format(tm.lastActiveStations())).add((CharSequence)"   mean haul: ").add((long)((int)tm.lastMeanDistance())).add((CharSequence)" tiles").add((CharSequence)(tm.lastUsedReflection() ? "" : "   (geometric estimate)"));
+        }, EconTexts.¤¤corveeHaulageSuffix, EconConfig.transportFeeEnabled ? COLOR.WHITE200 : COLOR.WHITE100);
+        this.line.clear().add(EconTexts.¤¤corveeLastTick).add((CharSequence)CompactNumber.format(tm.lastPaid())).add(EconTexts.¤¤uiDenari).add(EconTexts.¤¤corveeActiveStations).add((CharSequence)CompactNumber.format(tm.lastActiveStations())).add(EconTexts.¤¤corveeMeanHaul).add((long)((int)tm.lastMeanDistance())).add(EconTexts.¤¤corveeTiles).add((CharSequence)(tm.lastUsedReflection() ? "" : EconTexts.¤¤corveeGeoEstimate));
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 12);
         int stateWageHeaderY = y += 28;
-        this.label.clear().add((CharSequence)"STATE WAGES");
+        this.label.clear().add(EconTexts.¤¤corveeStateWages);
         this.label.color(COLOR.WHITE200);
         this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 16);
-        this.line.clear().add((CharSequence)"treasury-funded wages; an empty treasury pays zero and state workers seek better jobs");
+        this.line.clear().add(EconTexts.¤¤corveeStateWagesDesc);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 20, y + 14);
         StateWageMarket.Entry[] stateWages = sim.stateWages().laborEntries();
@@ -1253,7 +1220,7 @@
         int stateWageMaxScroll = Math.max(0, stateWageTotal - stateWageVisible);
         this.stateWageScroll = Math.max(0, Math.min(stateWageMaxScroll, this.stateWageScroll + this.takeScroll()));
         this.scrollbar(r, stateWageListTop, stateWageListBottom, this.stateWageScroll, stateWageVisible, stateWageTotal);
-        this.line.clear().add((CharSequence)("" + (this.stateWageScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(stateWageTotal, this.stateWageScroll + stateWageVisible))).add((CharSequence)" of ").add((CharSequence)("" + stateWageTotal));
+        this.line.clear().add((CharSequence)("" + (this.stateWageScroll + 1))).add((CharSequence)"-").add((CharSequence)("" + Math.min(stateWageTotal, this.stateWageScroll + stateWageVisible))).add(EconTexts.¤¤uiOf).add((CharSequence)("" + stateWageTotal));
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, this.win.x2() - 18 - 90, this.win.x2() - 18, stateWageHeaderY, stateWageHeaderY + 12);
         for (int i = this.stateWageScroll; i < stateWageTotal && i < this.stateWageScroll + stateWageVisible && y + 30 <= stateWageListBottom; ++i) {
@@ -1264,13 +1231,13 @@
     private void renderRelief(Renderer r, int y, EconomySim sim) {
         int x = this.win.x1() + 18;
         GrainDole dole = sim.grainDole();
-        this.label.clear().add((CharSequence)"THE GRAIN DOLE");
+        this.label.clear().add(EconTexts.¤¤reliefTitle);
         this.label.color(COLOR.WHITE200);
         this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 16);
-        this.line.clear().add((CharSequence)"free bread for the poorest, capped at a fixed roll - as Rome ran it");
+        this.line.clear().add(EconTexts.¤¤reliefDesc);
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 24, y + 14);
-        this.label.clear().add((CharSequence)"free if worth under");
+        this.label.clear().add(EconTexts.¤¤reliefFreeIfWorthUnder);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 200, (y += 26) + 8, y + 22);
         int th = EconConfig.doleWealthThreshold;
@@ -1280,8 +1247,8 @@
         }
         this.valueField(r, "f_gdt", x + 210 + 260 + 18, y, 130, EconConfig.doleWealthThreshold, 0, 50000, v -> {
             EconConfig.doleWealthThreshold = v;
-        }, " denari", EconConfig.doleWealthThreshold > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.label.clear().add((CharSequence)"grain roll holds");
+        }, EconTexts.¤¤uiDenari, EconConfig.doleWealthThreshold > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
+        this.label.clear().add(EconTexts.¤¤reliefGrainRollHolds);
         this.label.color(COLOR.WHITE150);
         this.label.render((SPRITE_RENDERER)r, x, x + 200, (y += 30) + 8, y + 22);
         int cap = EconConfig.doleHeadcap;
@@ -1293,19 +1260,19 @@
         this.valueField(r, "f_gdn", x + 210 + 260 + 18, y, 90, EconConfig.doleHeadcap, 0, 5000, v -> {
             EconConfig.doleHeadcap = v;
         }, "", EconConfig.doleHeadcap > 0 ? COLOR.WHITE200 : COLOR.WHITE100);
-        this.line.clear().add((CharSequence)"(").add((CharSequence)CompactNumber.format(eligible)).add((CharSequence)" qualify)");
+        this.line.clear().add((CharSequence)"(").add((CharSequence)CompactNumber.format(eligible)).add((CharSequence)")");
         this.line.color(COLOR.WHITE100);
         this.line.render((SPRITE_RENDERER)r, x + 210 + 260 + 118, this.win.x2() - 18, y + 8, y + 22);
-        this.line.clear().add((CharSequence)"on the roll: ").add((CharSequence)CompactNumber.format(dole.rollSize())).add((CharSequence)"   free meals served: ").add((CharSequence)CompactNumber.format(dole.mealsDoled())).add((CharSequence)"   revenue foregone: ").add((CharSequence)CompactNumber.format(dole.revenueForegone()));
+        this.line.clear().add(EconTexts.¤¤reliefOnRoll).add((CharSequence)CompactNumber.format(dole.rollSize())).add(EconTexts.¤¤reliefFreeMeals).add((CharSequence)CompactNumber.format(dole.mealsDoled())).add(EconTexts.¤¤reliefRevenueForegone).add((CharSequence)CompactNumber.format(dole.revenueForegone()));
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 34, y + 14);
         y += 20;
         if (eligible > EconConfig.doleHeadcap && EconConfig.doleHeadcap > 0) {
-            this.line.clear().add((CharSequence)CompactNumber.format(eligible - EconConfig.doleHeadcap)).add((CharSequence)" qualify but are NOT on the roll - the poorest are admitted first");
+            this.line.clear().add((CharSequence)CompactNumber.format(eligible - EconConfig.doleHeadcap)).add(EconTexts.¤¤reliefQualifyButNotOnRoll);
             this.line.color(COLOR.REDISH);
             this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 14);
         }
-        this.line.clear().add((CharSequence)"automatic slave/orphan rations served: ").add((CharSequence)CompactNumber.format(dole.compulsoryRations()));
+        this.line.clear().add(EconTexts.¤¤reliefAutoRations).add((CharSequence)CompactNumber.format(dole.compulsoryRations()));
         this.line.color(COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 30, y + 14);
     }
@@ -1313,53 +1280,53 @@
     private void renderBooks(Renderer r, int y, EconomySim sim) {
         int x = this.win.x1() + 18;
         int col = x + 420;
-        this.label.clear().add((CharSequence)"THE MONEY SUPPLY");
+        this.label.clear().add(EconTexts.¤¤booksTitle);
         this.label.color(COLOR.WHITE200);
         this.label.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 14);
         y += 20;
-        y = this.book(r, x, col, y, "founding stock", sim.seedSupply(), false);
-        y = this.book(r, x, col, y, "+ imported by immigrants", sim.imported(), false);
-        y = this.book(r, x, col, y, "+ treasury-funded income", sim.guildIncomePaid(), false);
-        y = this.book(r, x, col, y, "+ annona/ration producer payments", sim.rationOut(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksFoundingStock, sim.seedSupply(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksImported, sim.imported(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksTreasuryIncome, sim.guildIncomePaid(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksAnnona, sim.rationOut(), false);
         y += 4;
         Purchases p = sim.purchases();
-        y = this.book(r, x, col, y, "buyer->guild food  (" + CompactNumber.format(p.meals()) + " meals)", p.spentOnFood(), false);
-        y = this.book(r, x, col, y, "buyer->guild drink (" + CompactNumber.format(p.drinks()) + " rounds)", p.spentOnDrink(), false);
-        y = this.book(r, x, col, y, "buyer->guild goods (" + CompactNumber.format(p.goodsBought()) + " purchases)", p.spentOnGoods(), false);
-        y = this.book(r, x, col, y, "   (transfers within circulation)", 0L, false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksBuyerFood + CompactNumber.format(p.meals()) + EconTexts.¤¤booksMeals, p.spentOnFood(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksBuyerDrink + CompactNumber.format(p.drinks()) + EconTexts.¤¤booksRounds, p.spentOnDrink(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksBuyerGoods + CompactNumber.format(p.goodsBought()) + EconTexts.¤¤booksPurchases, p.spentOnGoods(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksTransfers, 0L, false);
         y += 4;
-        y = this.book(r, x, col, y, "grain dole: free meals served", sim.grainDole().mealsDoled(), false);
-        y = this.book(r, x, col, y, "grain dole: revenue foregone", sim.grainDole().revenueForegone(), false);
-        y = this.book(r, x, col, y, "- wealth tax", sim.taxesCollected(), true);
-        y = this.book(r, x, col, y, "- per-head tax", sim.headTaxCollected(), true);
-        y = this.book(r, x, col, y, "- religious poll tax (jizya)", sim.religionTaxCollected(), true);
-        y = this.book(r, x, col, y, "- liturgy on the rich", sim.liturgyCollected(), true);
-        y = this.book(r, x, col, y, "- market skim/unclaimed sales", sim.marketReceipts(), true);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksGrainDoleMeals, sim.grainDole().mealsDoled(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksGrainDoleRevenue, sim.grainDole().revenueForegone(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksWealthTax, sim.taxesCollected(), true);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksHeadTax, sim.headTaxCollected(), true);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksReligionTax, sim.religionTaxCollected(), true);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksLiturgy, sim.liturgyCollected(), true);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksMarketSkim, sim.marketReceipts(), true);
         if (sim.spent() > 0L) {
-            y = this.book(r, x, col, y, "- legacy gate-disabled consumption", sim.spent(), true);
+            y = this.book(r, x, col, y, EconTexts.¤¤booksLegacy, sim.spent(), true);
         }
-        y = this.book(r, x, col, y, "rounding drift bucket", sim.roundingDrift(), false);
-        y = this.book(r, x, col, y, "- exported by emigrants", sim.exported(), true);
-        y = this.book(r, x, col, y, "- heirless estates seized", sim.escheated(), true);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksRoundingDrift, sim.roundingDrift(), false);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksExported, sim.exported(), true);
+        y = this.book(r, x, col, y, EconTexts.¤¤booksHeirless, sim.escheated(), true);
         y += 4;
         long living = sim.stats().total;
-        this.line.clear().add((CharSequence)"= in circulation: ").add((CharSequence)CompactNumber.format(living)).add((CharSequence)" denari");
+        this.line.clear().add(EconTexts.¤¤booksInCirculation).add((CharSequence)CompactNumber.format(living)).add(EconTexts.¤¤uiDenari);
         this.line.color(COLOR.WHITE200);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
         y += 16;
         long delta = sim.auditDelta();
         if (delta != 0L) {
-            this.line.clear().add((CharSequence)"BOOKS DO NOT BALANCE: ").add((CharSequence)((delta > 0L ? "+" : "") + CompactNumber.format(delta))).add((CharSequence)" denari unaccounted for \u2014 this is a BUG, not a mechanic");
+            this.line.clear().add(EconTexts.¤¤booksDoNotBalance).add((CharSequence)((delta > 0L ? "+" : "") + CompactNumber.format(delta))).add(EconTexts.¤¤booksUnaccounted);
             this.line.color(COLOR.REDISH);
             this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
         } else {
-            this.line.clear().add((CharSequence)"books balance");
+            this.line.clear().add(EconTexts.¤¤booksBalance);
             this.line.color(COLOR.WHITE100);
             this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
         }
         y += 18;
         DebtBondage bondage = sim.debtBondage();
-        this.line.clear().add((CharSequence)"tax debtors: ").add((CharSequence)CompactNumber.format(bondage.debtorCount())).add((CharSequence)"   arrears outstanding: ").add((CharSequence)CompactNumber.format(bondage.lastOutstanding())).add((CharSequence)" denari").add((CharSequence)"   sold to bondage: ").add((CharSequence)CompactNumber.format(bondage.totalEnslaved()));
+        this.line.clear().add(EconTexts.¤¤booksDebtors).add((CharSequence)CompactNumber.format(bondage.debtorCount())).add(EconTexts.¤¤booksArrears).add((CharSequence)CompactNumber.format(bondage.lastOutstanding())).add(EconTexts.¤¤uiDenari).add(EconTexts.¤¤booksSoldBondage).add((CharSequence)CompactNumber.format(bondage.totalEnslaved()));
         this.line.color(bondage.debtorCount() > 0 ? COLOR.WHITE200 : COLOR.WHITE120);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
         y += 22;
@@ -1367,23 +1334,23 @@
         long stock = LocalPrices.foodStock(sim.ticks());
         int meal = LocalPrices.mealPrice(pop, sim.ticks());
         double days = LocalPrices.foodDays();
-        this.line.clear().add((CharSequence)"food stock ").add((CharSequence)CompactNumber.format(stock)).add((CharSequence)"  =  ").add((CharSequence)("" + (int)days)).add((CharSequence)" days of food").add((CharSequence)"   (target ").add((CharSequence)("" + (int)EconConfig.targetFoodDays)).add((CharSequence)")");
+        this.line.clear().add(EconTexts.¤¤booksFoodStock).add((CharSequence)CompactNumber.format(stock)).add((CharSequence)"  =  ").add((CharSequence)("" + (int)days)).add(EconTexts.¤¤booksDaysOfFood).add(EconTexts.¤¤booksTarget).add((CharSequence)("" + (int)EconConfig.targetFoodDays)).add((CharSequence)")");
         this.line.color(days < EconConfig.targetFoodDays * 0.3 ? COLOR.REDISH : COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
         y += 16;
         int localFoodBasket = LocalPrices.flowFoodBasketPrice();
         int anchorFoodBasket = LocalPrices.foodBasketPrice(sim.ticks());
-        this.line.clear().add((CharSequence)"local flow food basket ").add((CharSequence)CompactNumber.format(localFoodBasket)).add((CharSequence)"   (trade anchor basket ").add((CharSequence)CompactNumber.format(anchorFoodBasket)).add((CharSequence)")").add((CharSequence)"   ->  reference food unit ").add((CharSequence)CompactNumber.format(meal));
+        this.line.clear().add(EconTexts.¤¤booksLocalFoodBasket).add((CharSequence)CompactNumber.format(localFoodBasket)).add(EconTexts.¤¤booksTradeAnchorBasket).add((CharSequence)CompactNumber.format(anchorFoodBasket)).add((CharSequence)")").add(EconTexts.¤¤booksReferenceFoodUnit).add((CharSequence)CompactNumber.format(meal));
         this.line.color((double)localFoodBasket > (double)anchorFoodBasket * 1.5 ? COLOR.REDISH : ((double)localFoodBasket < (double)anchorFoodBasket * 0.8 ? COLOR.WHITE200 : COLOR.WHITE150));
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
-        this.line.clear().add((CharSequence)"last optimized meal: ").add((CharSequence)CompactNumber.format(sim.affordabilityGate().lastFoodBundleQuote())).add((CharSequence)" denari for ").add((CharSequence)CompactNumber.format(sim.affordabilityGate().lastFoodBundleUnits())).add((CharSequence)" food units");
+        this.line.clear().add(EconTexts.¤¤booksLastOptimizedMeal).add((CharSequence)CompactNumber.format(sim.affordabilityGate().lastFoodBundleQuote())).add(EconTexts.¤¤booksDenariFor).add((CharSequence)CompactNumber.format(sim.affordabilityGate().lastFoodBundleUnits())).add(EconTexts.¤¤booksFoodUnits);
         this.line.color(COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y += 16, y + 12);
         y += 16;
         double dDays = LocalPrices.drinkDays(sim.ticks());
         int localDrinkBasket = LocalPrices.flowDrinkBasketPrice();
         int anchorDrinkBasket = LocalPrices.drinkBasketPrice(sim.ticks());
-        this.line.clear().add((CharSequence)"drink reserve ").add((CharSequence)("" + (int)dDays)).add((CharSequence)" days").add((CharSequence)"   local flow basket ").add((CharSequence)CompactNumber.format(localDrinkBasket)).add((CharSequence)"   (anchor ").add((CharSequence)CompactNumber.format(anchorDrinkBasket)).add((CharSequence)")");
+        this.line.clear().add(EconTexts.¤¤booksDrinkReserve).add((CharSequence)("" + (int)dDays)).add(EconTexts.¤¤booksDays).add(EconTexts.¤¤booksLocalDrinkBasket).add((CharSequence)CompactNumber.format(localDrinkBasket)).add(EconTexts.¤¤booksAnchor).add((CharSequence)CompactNumber.format(anchorDrinkBasket)).add((CharSequence)")");
         this.line.color((double)localDrinkBasket > (double)anchorDrinkBasket * 1.5 ? COLOR.REDISH : COLOR.WHITE150);
         this.line.render((SPRITE_RENDERER)r, x, this.win.x2(), y, y + 12);
     }
```

## EngineSeams.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.567239217 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.569239190 +0200
@@ -66,11 +66,11 @@
     }
 
     public static int hungerRaw(Humanoid humanoid) {
-        return NEEDS.TYPES().HUNGER.stat().stat().indu().get((Object)humanoid.indu());
+        return NEEDS.TYPES().HUNGER.stat().stat().indu().get(humanoid.indu());
     }
 
     public static void hungerRawSet(Humanoid humanoid, int value) {
-        NEEDS.TYPES().HUNGER.stat().stat().indu().set((Object)humanoid.indu(), value);
+        NEEDS.TYPES().HUNGER.stat().stat().indu().set(humanoid.indu(), value);
     }
 
     public static int eventNeedPriority(Humanoid humanoid, NEED need) {
@@ -82,7 +82,7 @@
     }
 
     public static double serviceFulfilment(Humanoid humanoid, StatService service) {
-        return service.total().indu().getD((Object)humanoid.indu());
+        return service.total().indu().getD(humanoid.indu());
     }
 
     public static ServiceCapacity serviceCapacity(RoomService service) {
@@ -133,7 +133,7 @@
     }
 
     public static int religionIndexOf(Humanoid humanoid) {
-        StatsReligion.StatReligion stat = (StatsReligion.StatReligion)STATS.RELIGION().getter.get((Object)humanoid.indu());
+        StatsReligion.StatReligion stat = (StatsReligion.StatReligion)STATS.RELIGION().getter.get(humanoid.indu());
         return stat == null ? -1 : stat.religion.index();
     }
 
@@ -141,7 +141,7 @@
         LIST all = STATS.RELIGION().ALL;
         for (int i = 0; i < all.size(); ++i) {
             if (((StatsReligion.StatReligion)all.get((int)i)).religion.index() != religionIndex) continue;
-            STATS.RELIGION().getter.set((Object)humanoid.indu(), (Object)((StatsReligion.StatReligion)all.get(i)));
+            STATS.RELIGION().getter.set(humanoid.indu(), (StatsReligion.StatReligion)all.get(i));
             return;
         }
     }
```

## FirmLedger.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.605238714 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.608238675 +0200
@@ -182,7 +182,7 @@
         }
         double window = EconConfig.flowSmoothingDays > 0.0 ? EconConfig.flowSmoothingDays : elapsedDays;
         double blend = 1.0 - Math.exp(-elapsedDays / window);
-        Iterator<Object> iterator = this.firms.entrySet().iterator();
+        Iterator<Map.Entry<RoomInstance, FirmState>> iterator = this.firms.entrySet().iterator();
         while (iterator.hasNext()) {
             Map.Entry<RoomInstance, FirmState> entry = iterator.next();
             RoomInstance roomInstance = entry.getKey();
@@ -213,7 +213,7 @@
             aggregate.marginalNumerator += state.marginal * (double)Math.max(1, roomInstance.employees().employed());
             aggregate.marginalWeight += Math.max(1, roomInstance.employees().employed());
         }
-        for (Map.Entry<Object, Object> entry : this.serviceRevenue.entrySet()) {
+        for (Map.Entry<RoomBlueprintImp, Double> entry : this.serviceRevenue.entrySet()) {
             RoomBlueprintImp roomBlueprintImp = (RoomBlueprintImp)entry.getKey();
             BlueprintState aggregate = this.blueprints.computeIfAbsent(roomBlueprintImp.key, ignored -> new BlueprintState());
             aggregate.profit += ((Double)entry.getValue()).doubleValue();
```

## FlowMeter.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.626238437 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.628238411 +0200
@@ -270,11 +270,11 @@
             int i;
             for (i = 0; i < this.lastOutput.length; ++i) {
                 resource = (IndustryResource)this.industry.outs().get(i);
-                current = resource.day.getD((Object)firm);
-                int produced = resource.year.get((Object)firm);
-                int n = this.producedDelta[i] = this.initialized ? FlowMeter.exactCounterDelta(produced, this.lastProduced[i], resource.yearPrev.get((Object)firm)) : 0;
+                current = resource.day.getD(firm);
+                int produced = resource.year.get(firm);
+                int n = this.producedDelta[i] = this.initialized ? FlowMeter.exactCounterDelta(produced, this.lastProduced[i], resource.yearPrev.get(firm)) : 0;
                 if (this.initialized) {
-                    delta = FlowMeter.counterDelta(current, this.lastOutput[i], resource.dayPrev.get((Object)firm));
+                    delta = FlowMeter.counterDelta(current, this.lastOutput[i], resource.dayPrev.get(firm));
                     this.outputRate[i] = FlowMeter.smooth(this.outputRate[i], delta / elapsedDays, blend);
                 }
                 this.lastOutput[i] = current;
@@ -282,11 +282,11 @@
             }
             for (i = 0; i < this.lastInput.length; ++i) {
                 resource = (IndustryResource)this.industry.ins().get(i);
-                current = resource.day.getD((Object)firm);
-                int consumed = resource.year.get((Object)firm);
-                int n = this.consumedDelta[i] = this.initialized ? FlowMeter.exactCounterDelta(consumed, this.lastConsumed[i], resource.yearPrev.get((Object)firm)) : 0;
+                current = resource.day.getD(firm);
+                int consumed = resource.year.get(firm);
+                int n = this.consumedDelta[i] = this.initialized ? FlowMeter.exactCounterDelta(consumed, this.lastConsumed[i], resource.yearPrev.get(firm)) : 0;
                 if (this.initialized) {
-                    delta = FlowMeter.counterDelta(current, this.lastInput[i], resource.dayPrev.get((Object)firm));
+                    delta = FlowMeter.counterDelta(current, this.lastInput[i], resource.dayPrev.get(firm));
                     this.inputRate[i] = FlowMeter.smooth(this.inputRate[i], delta / elapsedDays, blend);
                 }
                 this.lastInput[i] = current;
```

## FoodRollback.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.659238001 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.662237961 +0200
@@ -138,14 +138,13 @@
     }
 
     public record StallSnapshot(int x, int y, int[] stock) {
-        private final int[] stock;
-
         public StallSnapshot {
-            stock = (int[])stock.clone();
+            stock = stock.clone();
         }
 
+        @Override
         public int[] stock() {
-            return (int[])this.stock.clone();
+            return stock.clone();
         }
     }
 }
```

## FoodTransactionPlan.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.673237816 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.676237777 +0200
@@ -95,7 +95,7 @@
     private final AffordabilityGate gate;
     private final IdentityHashMap<Induvidual, PendingMeal> pending = new IdentityHashMap();
     private final RBIT.RBITImp rawFoodMask = new RBIT.RBITImp();
-    private final SFinderMisc.FinderMiscWithoutDest edibleTerrain = new SFinderMisc.FinderMiscWithoutDest(this, 32){
+    private final SFinderMisc.FinderMiscWithoutDest edibleTerrain = new SFinderMisc.FinderMiscWithoutDest(32){
 
         protected boolean has() {
             return SETT.WEATHER().growthRipe.cropsAreRipe();
@@ -112,7 +112,7 @@
             return terrainTile instanceof TGrowable && (growable = (TGrowable)terrainTile).isEdible(tx, ty) && growable.size.get(tx, ty) > 0;
         }
     };
-    private final SFinderMisc.FinderMiscWithoutDest corpses = new SFinderMisc.FinderMiscWithoutDest(this, 32){
+    private final SFinderMisc.FinderMiscWithoutDest corpses = new SFinderMisc.FinderMiscWithoutDest(32){
 
         protected boolean has() {
             return true;
@@ -122,11 +122,11 @@
             return FoodTransactionPlan.corpse(tx, ty) != null;
         }
     };
-    private final AIPLAN.PLANRES.Resumer denied = new AIPLAN.PLANRES.Resumer("starving"){
+    private final AIPLAN.PLANRES.Resumer denied = new Resumer("starving"){
 
         protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
             BrokeFoodPlan.markStarvedIfLethal(humanoid);
-            if (STATS.FOOD().STARVATION.indu().getD((Object)humanoid.indu()) > 0.0) {
+            if (STATS.FOOD().STARVATION.indu().getD(humanoid.indu()) > 0.0) {
                 if (FoodTransactionPlan.this.edibleTerrain.find(humanoid.physics.tileC(), manager.path)) {
                     manager.planByte4 = (byte)3;
                     return AI.SUBS().walkTo.pathRun(humanoid, manager);
@@ -157,7 +157,7 @@
         public void can(Humanoid humanoid, AIManager manager) {
         }
     };
-    private final AISUB eatAnimation = new AISUB.Simple(this, "ECON_EATING"){
+    private final AISUB eatAnimation = new AISUB.Simple("ECON_EATING"){
 
         protected AISTATE resume(Humanoid humanoid, AIManager manager) {
             manager.subByte = (byte)(manager.subByte + 1);
@@ -168,7 +168,7 @@
             };
         }
     };
-    private final AIPLAN.PLANRES.Resumer walk = new AIPLAN.PLANRES.Resumer("finding affordable food"){
+    private final AIPLAN.PLANRES.Resumer walk = new Resumer("finding affordable food"){
 
         protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
             AISUB.AISubActivation activation = FoodTransactionPlan.this.findService(humanoid, manager, manager.planObject);
@@ -187,12 +187,12 @@
             FoodTransactionPlan.this.releaseAdmission(humanoid, manager);
         }
     };
-    private final AIPLAN.PLANRES.Resumer rawFetch = new AIPLAN.PLANRES.Resumer("finding raw food"){
+    private final AIPLAN.PLANRES.Resumer rawFetch = new Resumer("finding raw food"){
 
         protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
             AISUB.AISubActivation result;
             FoodTransactionPlan.this.rawFoodMask.clearSet(RESOURCES.EDI().mask);
-            if (STATS.FOOD().STARVATION.indu().get((Object)humanoid.indu()) <= 0) {
+            if (STATS.FOOD().STARVATION.indu().get(humanoid.indu()) <= 0) {
                 FoodTransactionPlan.this.rawFoodMask.and(STATS.FOOD().fetchMask(humanoid));
             }
             if ((result = AI.SUBS().walkTo.resource(humanoid, manager, (RBIT)FoodTransactionPlan.this.rawFoodMask, Integer.MAX_VALUE)) == null) {
@@ -237,7 +237,7 @@
             FoodTransactionPlan.this.releaseAdmission(h, d);
         }
     };
-    private final AIPLAN.PLANRES.Resumer rawAnimation = new AIPLAN.PLANRES.Resumer("eating raw food"){
+    private final AIPLAN.PLANRES.Resumer rawAnimation = new Resumer("eating raw food"){
 
         protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
             return FoodTransactionPlan.this.eatAnimation.activate(h, d);
@@ -254,7 +254,7 @@
         public void can(Humanoid h, AIManager d) {
         }
     };
-    private final AIPLAN.PLANRES.Resumer eateryAnimation = new AIPLAN.PLANRES.Resumer("eating"){
+    private final AIPLAN.PLANRES.Resumer eateryAnimation = new Resumer("eating"){
 
         protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
             return FoodTransactionPlan.this.eatAnimation.activate(h, d);
@@ -273,7 +273,7 @@
             FoodTransactionPlan.this.releaseAdmission(humanoid, manager);
         }
     };
-    private final AIPLAN.PLANRES.Resumer chairWalk = new AIPLAN.PLANRES.Resumer("walking to table"){
+    private final AIPLAN.PLANRES.Resumer chairWalk = new Resumer("walking to table"){
 
         protected AISUB.AISubActivation setAction(Humanoid h, AIManager d) {
             return null;
@@ -291,7 +291,7 @@
             FoodTransactionPlan.releaseChair(d);
         }
     };
-    private final AIPLAN.PLANRES.Resumer chairLast = new AIPLAN.PLANRES.Resumer("taking a seat"){
+    private final AIPLAN.PLANRES.Resumer chairLast = new Resumer("taking a seat"){
 
         protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
             DIR direction;
@@ -316,7 +316,7 @@
             FoodTransactionPlan.releaseChair(d);
         }
     };
-    private final AIPLAN.PLANRES.Resumer eatAtTable = new AIPLAN.PLANRES.Resumer("eating at table"){
+    private final AIPLAN.PLANRES.Resumer eatAtTable = new Resumer("eating at table"){
 
         protected AISUB.AISubActivation setAction(Humanoid humanoid, AIManager manager) {
             manager.planByte1 = (byte)(4 + RND.rInt((int)10));
@@ -472,7 +472,7 @@
         if (!service.accessRequest(humanoid) || !service.finder.has(humanoid.tc())) {
             return null;
         }
-        int radius = STATS.FOOD().STARVATION.indu().get((Object)humanoid.indu()) > 0 ? Integer.MAX_VALUE : service.radius;
+        int radius = STATS.FOOD().STARVATION.indu().get(humanoid.indu()) > 0 ? Integer.MAX_VALUE : service.radius;
         AISUB.AISubActivation result = AI.SUBS().walkTo.service(humanoid, manager, (SFinderFindable)service.finder, radius);
         if (result == null) {
             return null;
@@ -659,14 +659,13 @@
     }
 
     private record PendingMeal(int[] bundle, int servings, int preferredServings, AffordabilityGate.Admission admission) {
-        private final int[] bundle;
-
         private PendingMeal {
-            bundle = (int[])bundle.clone();
+            bundle = bundle.clone();
         }
 
+        @Override
         public int[] bundle() {
-            return (int[])this.bundle.clone();
+            return bundle.clone();
         }
     }
 }
```

## Purchases.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.795236205 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.798236165 +0200
@@ -103,7 +103,7 @@
 
     private int chargeMeal(Humanoid h, Wallets wallets, AffordabilityGate gate) {
         Induvidual indu = h.indu();
-        int now = NEEDS.TYPES().HUNGER.stat().stat().indu().get((Object)indu);
+        int now = NEEDS.TYPES().HUNGER.stat().stat().indu().get(indu);
         Integer prevBox = this.lastHunger.put(indu, now);
         int exactMeals = 0;
         int exactPaid = 0;
@@ -149,7 +149,7 @@
     private int chargeDrink(Humanoid h, Wallets wallets) {
         AffordabilityGate gate;
         Induvidual indu = h.indu();
-        int now = STATS.FOOD().DRINK.indu().get((Object)indu);
+        int now = STATS.FOOD().DRINK.indu().get(indu);
         int exact = 0;
         int exactCount = 0;
         EconomySim sim = EconomySim.active();
```

## RationOptimizer.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.816235928 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.819235888 +0200
@@ -69,14 +69,14 @@
     }
 
     public record Result(int[] bundle, int servings, int preferredServings, long cost) {
-        private final int[] bundle;
 
         public Result {
-            bundle = (int[])bundle.clone();
+            bundle = bundle.clone();
         }
 
+        @Override
         public int[] bundle() {
-            return (int[])this.bundle.clone();
+            return bundle.clone();
         }
     }
 
```

## StateWageMarket.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.852235452 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.854235426 +0200
@@ -114,9 +114,10 @@
             RoomInstance room;
             Humanoid worker = roster.get(i);
             if (worker.indu().clas() == HCLASSES.SLAVE() || (room = (RoomInstance)STATS.WORK().EMPLOYED.get(worker.indu())) == null || (entry = this.entryFor((RoomBlueprintImp)(blueprint = room.blueprintI()))) == null) continue;
-            ++byBlueprint.computeIfAbsent(blueprint, (Function<RoomBlueprintImp, Group>)LambdaMetafactory.metafactory(null, null, null, (Ljava/lang/Object;)Ljava/lang/Object;, lambda$update$26(settlement.room.main.RoomBlueprintImp tiredgirl4.economy.econ.StateWageMarket$Entry settlement.room.main.RoomBlueprintImp ), (Lsettlement/room/main/RoomBlueprintImp;)Ltiredgirl4/economy/econ/StateWageMarket$Group;)((RoomBlueprintImp)blueprint, (Entry)entry)).workers;
+            Entry finalEntry = entry;
+            ++byBlueprint.computeIfAbsent(blueprint, k -> new Group((RoomBlueprintImp) k, finalEntry)).workers;
         }
-        ArrayList groups = new ArrayList(byBlueprint.values());
+        ArrayList<Group> groups = new ArrayList<>(byBlueprint.values());
         int[] dues = new int[groups.size()];
         long totalDue = 0L;
         for (int i = 0; i < groups.size(); ++i) {
```

## Wages.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.886235003 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.888234977 +0200
@@ -134,7 +134,7 @@
             RoomBlueprintImp job;
             Humanoid h = roster.get((i + offset) % n);
             Induvidual indu = h.indu();
-            if (!EconConfig.payWagesToSlaves && indu.clas() == HCLASSES.SLAVE() || (job = (RoomBlueprintImp)STATS.WORK().profession.get((Object)indu)) == null) continue;
+            if (!EconConfig.payWagesToSlaves && indu.clas() == HCLASSES.SLAVE() || (job = (RoomBlueprintImp)STATS.WORK().profession.get(indu)) == null) continue;
             int wage = this.wageOf(job);
             int[] rec = this.realized.computeIfAbsent(job.key, k -> new int[2]);
             rec[0] = rec[0] + 1;
@@ -173,10 +173,10 @@
             file.i(entry.getValue().intValue());
         }
         file.i(this.realized.size());
-        for (Map.Entry<String, Integer> entry : this.realized.entrySet()) {
+        for (Map.Entry<String, int[]> entry : this.realized.entrySet()) {
             file.chars((CharSequence)entry.getKey());
-            file.i(((int[])entry.getValue())[0]);
-            file.i(((int[])entry.getValue())[1]);
+            file.i(entry.getValue()[0]);
+            file.i(entry.getValue()[1]);
         }
     }
 
```

## Wallets.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.894234897 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.897234858 +0200
@@ -72,7 +72,7 @@
         if (!this.seeded) {
             return EconConfig.initialWallet;
         }
-        boolean born = STATS.POP().COUNT.arrive.get((Object)h.indu()) == CAUSE_ARRIVES.BORN();
+        boolean born = STATS.POP().COUNT.arrive.get(h.indu()) == CAUSE_ARRIVES.BORN();
         return born ? EconConfig.newbornWallet : EconConfig.immigrantWallet;
     }
 
@@ -144,7 +144,7 @@
         }
         this.seenTick[slot] = tick;
         this.relRef[slot] = STATS.REL().reference(h.indu());
-        this.emigrating[slot] = (byte)(STATS.POP().EMMIGRATING.indu().get((Object)h.indu()) != 0 ? 1 : 0);
+        this.emigrating[slot] = (byte)(STATS.POP().EMMIGRATING.indu().get(h.indu()) != 0 ? 1 : 0);
         return minted;
     }
 
```

## WarehouseMarket.java
```diff
--- /tmp/diff_norm/orig.java	2026-07-21 21:36:52.917234593 +0200
+++ /tmp/diff_norm/new.java	2026-07-21 21:36:52.920234554 +0200
@@ -937,7 +937,7 @@
         if (producer == null || resource == null || units <= 0) {
             return;
         }
-        ArrayList claims = this.directClaims.computeIfAbsent(resource.index(), ignored -> new ArrayList());
+        ArrayList<DirectClaim> claims = this.directClaims.computeIfAbsent(resource.index(), ignored -> new ArrayList<DirectClaim>());
         for (DirectClaim claim : claims) {
             if (claim.producer != producer) continue;
             claim.unitsHeld = WarehouseMarket.safeAdd(claim.unitsHeld, units);
@@ -1387,7 +1387,7 @@
     }
 
     private void lockIntake(StockpileInstance warehouse) {
-        Map locks = this.intakeLocks.computeIfAbsent(warehouse, ignored -> new HashMap());
+        Map<Integer, Integer> locks = this.intakeLocks.computeIfAbsent(warehouse, ignored -> new HashMap<Integer, Integer>());
         for (COORDINATE tile : warehouse.body()) {
             int free;
             TILE_STORAGE crate;
@@ -1471,29 +1471,28 @@
         }
     }
 
-    /*
-     * WARNING - void declaration
-     */
     private void inferCrownFromLoose() {
         this.ensureCrownCapacity();
         long[] loose = new long[this.crownUnits.length];
         for (Object tile : SETT.TILE_BOUNDS) {
-            void var4_5;
-            THINGS.Thing thing = SETT.THINGS().getFirst(tile.x(), tile.y());
-            while (var4_5 != null) {
-                int resource;
-                Object pile;
-                if (var4_5 instanceof ThingsResources.ScatteredResource && !(pile = (ThingsResources.ScatteredResource)var4_5).isRemoved() && (resource = pile.resource().index()) >= 0 && resource < loose.length) {
-                    loose[resource] = Math.min(Long.MAX_VALUE, loose[resource] + (long)Math.max(0, pile.amount()));
+            COORDINATE c = (COORDINATE) tile;
+            THINGS.Thing thing = SETT.THINGS().getFirst(c.x(), c.y());
+            while (thing != null) {
+                ThingsResources.ScatteredResource pile;
+                if (thing instanceof ThingsResources.ScatteredResource && !(pile = (ThingsResources.ScatteredResource) thing).isRemoved()) {
+                    int resource = pile.resource().index();
+                    if (resource >= 0 && resource < loose.length) {
+                        loose[resource] = Math.min(Long.MAX_VALUE, loose[resource] + (long)Math.max(0, pile.amount()));
+                    }
                 }
-                THINGS.Thing thing2 = var4_5.tileNext();
+                thing = thing.tileNext();
             }
         }
         long[] titled = new long[this.crownUnits.length];
-        for (Map.Entry entry : this.directClaims.entrySet()) {
-            if ((Integer)entry.getKey() < 0 || (Integer)entry.getKey() >= titled.length) continue;
-            for (DirectClaim claim : (ArrayList)entry.getValue()) {
-                titled[((Integer)entry.getKey()).intValue()] = Math.min(Long.MAX_VALUE, titled[(Integer)entry.getKey()] + (long)Math.max(0, claim.unitsHeld));
+        for (Map.Entry<Integer, ArrayList<DirectClaim>> entry : this.directClaims.entrySet()) {
+            if (entry.getKey() < 0 || entry.getKey() >= titled.length) continue;
+            for (DirectClaim claim : entry.getValue()) {
+                titled[entry.getKey()] = Math.min(Long.MAX_VALUE, titled[entry.getKey()] + (long)Math.max(0, claim.unitsHeld));
             }
         }
         for (Book[] bookArray : this.books.values()) {
@@ -1537,15 +1536,8 @@
         return null;
     }
 
-    /*
-     * WARNING - void declaration
-     */
     public void save(FilePutter file) {
         RetailBook[] shelf;
-        void var6_23;
-        void var9_41;
-        void var6_21;
-        void var4_11;
         this.resolvePending();
         file.i(7);
         file.i(this.lastTaxSeason);
@@ -1583,23 +1575,23 @@
         file.i(lockCount);
         for (Map.Entry<StockpileInstance, Map<Integer, Integer>> entry : this.intakeLocks.entrySet()) {
             StockpileInstance stockpileInstance = entry.getKey();
-            if (stockpileInstance == null || !stockpileInstance.exists() || ((Map)entry.getValue()).isEmpty()) continue;
+            if (stockpileInstance == null || !stockpileInstance.exists() || entry.getValue().isEmpty()) continue;
             file.i(stockpileInstance.mX());
             file.i(stockpileInstance.mY());
-            file.i(((Map)entry.getValue()).size());
-            for (Map.Entry lock : ((Map)entry.getValue()).entrySet()) {
-                file.i(((Integer)lock.getKey()).intValue());
-                file.i(Math.max(0, (Integer)lock.getValue()));
+            file.i(entry.getValue().size());
+            for (Map.Entry<Integer, Integer> lock : entry.getValue().entrySet()) {
+                file.i(lock.getKey());
+                file.i(Math.max(0, lock.getValue()));
             }
         }
-        boolean bl = false;
-        for (ArrayList arrayList : this.directClaims.values()) {
-            for (DirectClaim claim : arrayList) {
+        int directClaimCount = 0;
+        for (ArrayList<DirectClaim> claims : this.directClaims.values()) {
+            for (DirectClaim claim : claims) {
                 if (claim.producer == null || !claim.producer.exists() || claim.unitsHeld <= 0) continue;
-                ++var4_11;
+                ++directClaimCount;
             }
         }
-        file.i((int)var4_11);
+        file.i(directClaimCount);
         for (Map.Entry<Integer, ArrayList<DirectClaim>> entry : this.directClaims.entrySet()) {
             if (entry.getKey() < 0 || entry.getKey() >= RESOURCES.ALL().size()) continue;
             String resourceKey = ((RESOURCE)RESOURCES.ALL().get((int)entry.getKey().intValue())).key;
@@ -1618,26 +1610,18 @@
             ++crownCount;
         }
         file.i(crownCount);
-        boolean bl2 = false;
-        while (var6_21 < this.crownUnits.length) {
-            if (this.crownUnits[var6_21] > 0L) {
-                file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get((int)var6_21)).key);
-                file.l(this.crownUnits[var6_21]);
-            }
-            ++var6_21;
-        }
-        boolean bl3 = false;
-        long[] resourceKey = this.abandonedUnits;
-        int n = resourceKey.length;
-        boolean bl4 = false;
-        while (var9_41 < n) {
-            long units = resourceKey[var9_41];
-            if (units > 0L) {
-                ++var6_23;
+        for (int i = 0; i < this.crownUnits.length; ++i) {
+            if (this.crownUnits[i] <= 0L) continue;
+            file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get(i)).key);
+            file.l(this.crownUnits[i]);
+        }
+        int abandonedCount = 0;
+        for (long l : this.abandonedUnits) {
+            if (l > 0L) {
+                ++abandonedCount;
             }
-            ++var9_41;
         }
-        file.i((int)var6_23);
+        file.i(abandonedCount);
         for (int resource = 0; resource < this.abandonedUnits.length; ++resource) {
             if (this.abandonedUnits[resource] <= 0L) continue;
             file.chars((CharSequence)((RESOURCE)RESOURCES.ALL().get((int)resource)).key);
@@ -1833,14 +1817,13 @@
     }
 
     public record RetailQuote(int total, int[] byResource) {
-        private final int[] byResource;
-
         public RetailQuote {
             byResource = byResource == null ? new int[]{} : (int[])byResource.clone();
         }
 
+        @Override
         public int[] byResource() {
-            return (int[])this.byResource.clone();
+            return byResource.clone();
         }
     }
 
@@ -1853,14 +1836,13 @@
     }
 
     public record OwnerlessRetailClaims(int waivedValue, int[] payableQuantities) {
-        private final int[] payableQuantities;
-
         public OwnerlessRetailClaims {
             payableQuantities = payableQuantities == null ? new int[]{} : (int[])payableQuantities.clone();
         }
 
+        @Override
         public int[] payableQuantities() {
-            return (int[])this.payableQuantities.clone();
+            return payableQuantities.clone();
         }
     }
 
```

