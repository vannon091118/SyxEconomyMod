package vannon.syx.economy.ui.tabs.Overview;

import init.sprite.UI.UI;
import snake2d.util.gui.GuiSection;
import util.colors.GCOLOR;
import util.gui.misc.GText;
import vannon.syx.economy.core.CompactNumber;
import vannon.syx.economy.core.EconIndicators;
import vannon.syx.economy.core.EconProgression;
import vannon.syx.economy.core.EconSnapshot;
import vannon.syx.economy.core.EconomySim;
import vannon.syx.economy.core.WealthStats;
import vannon.syx.economy.ui.EconWindowBase;

/**
 * Sprint v0.13.106+M-UI-3 — AdvisorTab extrahiert aus WindowOverview.
 *
 * <p>Ehemalige {@code private static final class AdvisorTab} aus der 948-LOC
 * WindowOverview-Datei jetzt eigenes File im Sub-Package
 * {@code vannon.syx.economy.ui.tabs.Overview}. Behavior 1:1 erhalten:</p>
 * <ul>
 *   <li>6-Ampel-Dashboard (Finanzen, Arbeit, Versorgung, Gleichheit, Wachstum, Abwanderung)</li>
 *   <li>Warnketten (Schuldenkrise → Lohnsenkung → Abwanderung etc.)</li>
 *   <li>Trend-Tabelle der letzten 3 Tage (Kasse/Gini/Lohn/Nahrung/Unpaid)</li>
 *   <li>Stufe + Meilensteine (7 msFirst*-Felder als Icons)</li>
 *   <li>"Was soll ich heute tun?" Priority-based Advisor (B-013-aware)</li>
 * </ul>
 *
 * <p>Verwendet {@link OverviewHelpers} (addTrafficLight, addTrendArrow,
 * addMilestoneIcon, buildAdvice, nextStageReqs, buildWarningChains, countLines)
 * + {@link EconWindowBase} (addKpi, addColHeader). Helper-Lookup via
 * same-package.</p>
 *
 * <p>Rule-14 Guard: ~190 SLOC (unter 600 warn). Rule-15 konform.</p>
 */
public final class AdvisorTab implements EconWindowBase.TabContent {

    @Override public CharSequence title() { return "Berater"; }

    @Override
    public void build(EconomySim sim, GuiSection content, int x, int y, int w, int h) {
        EconProgression prog = sim.progression();
        WealthStats stats = sim.stats();
        EconIndicators ind = sim.econIndicators();
        long treasury = sim.treasury();
        boolean hasPop = stats.people > 0;

        // === AMPEL-DASHBOARD (6 Indikatoren) ===
        GText ampelHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        ampelHdr.set("--- Ampel-Status ---");
        ampelHdr.lablify();
        content.add(ampelHdr, x, y);
        y += 22;

        OverviewHelpers.addTrafficLight(content, x, y, "Finanzen",
                !hasPop ? -1 : treasury > 10000 ? 2 : treasury > 0 ? 1 : 0);
        OverviewHelpers.addTrendArrow(content, x + 160, y, ind, "treasuryCurrent");
        OverviewHelpers.addTrafficLight(content, x + 220, y, "Arbeit",
                !hasPop ? -1 : ind.isWagesFalling() ? 0 : sim.firmLedger().lastWorkersUnpaid() == 0 ? 2 : 1);
        OverviewHelpers.addTrafficLight(content, x + 440, y, "Versorgung",
                !hasPop ? -1 : !ind.isFurnishingCrisis() ? 2 : 1);
        y += 24;

        OverviewHelpers.addTrafficLight(content, x, y, "Gleichheit",
                !hasPop ? -1 : stats.gini < 0.30 ? 2 : stats.gini < 0.40 ? 1 : 0);
        OverviewHelpers.addTrendArrow(content, x + 160, y, ind, "gini");
        OverviewHelpers.addTrafficLight(content, x + 220, y, "Wachstum",
                !hasPop ? -1 : !ind.isTreasuryDeclining() ? 2 : treasury > -5000 ? 1 : 0);
        OverviewHelpers.addTrafficLight(content, x + 440, y, "Abwanderung",
                !hasPop ? -1 : ind.isEmigrationSpike() ? 0 : 2);
        y += 32;

        // === WARNKETTEN (kausale Abhängigkeiten) ===
        String chains = OverviewHelpers.buildWarningChains(ind, treasury, stats);
        if (!chains.isEmpty()) {
            GText warnHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
            warnHdr.set("--- Warnketten ---");
            warnHdr.lablify();
            content.add(warnHdr, x, y);
            y += 20;

            GText chainsText = new GText(UI.FONT().S, EconWindowBase.FONTW_BODY);
            chainsText.set(chains);
            chainsText.color(GCOLOR.UI().SOSO.normal);
            content.add(chainsText, x, y);
            y += 20 + OverviewHelpers.countLines(chains) * 14;
        }

        // === TREND-TABELLE (letzte 3 Tage) ===
        if (ind.count() >= 2) {
            GText trendHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
            trendHdr.set("--- Trend (letzte 3 Tage) ---");
            trendHdr.lablify();
            content.add(trendHdr, x, y);
            y += 18;

            EconWindowBase.addColHeader(content, x, y, "Tag", 35);
            EconWindowBase.addColHeader(content, x + 45, y, "Kasse", 75);
            EconWindowBase.addColHeader(content, x + 130, y, "Gini", 55);
            EconWindowBase.addColHeader(content, x + 195, y, "Lohn", 60);
            EconWindowBase.addColHeader(content, x + 265, y, "Nahrung", 55);
            EconWindowBase.addColHeader(content, x + 330, y, "Unpaid", 45);
            y += 16;

            int maxRows = Math.min(ind.count(), 3);
            for (int i = ind.count() - maxRows; i < ind.count(); i++) {
                EconSnapshot s = ind.get(i);
                if (s == null) continue;
                int dayLabel = i - (ind.count() - 1);

                GText dayT = new GText(UI.FONT().S, EconWindowBase.FONTW_TINY);
                dayT.set(dayLabel == 0 ? "Heute" : "D" + dayLabel);
                dayT.color(dayLabel == 0 ? GCOLOR.T().NORMAL : GCOLOR.T().INACTIVE);
                content.add(dayT, x, y);

                GText treasuryT = new GText(UI.FONT().S, EconWindowBase.FONTW_LABEL);
                treasuryT.set(CompactNumber.format(s.treasuryCurrent));
                treasuryT.color(s.treasuryCurrent >= 0 ? GCOLOR.UI().GOOD.normal : GCOLOR.UI().BAD.normal);
                content.add(treasuryT, x + 45, y);

                GText giniT = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                giniT.set(String.format("%.3f", s.gini));
                giniT.color(s.gini > 0.40 ? GCOLOR.UI().BAD.normal : s.gini > 0.35 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().GOOD.normal);
                content.add(giniT, x + 130, y);

                GText wageT = new GText(UI.FONT().S, EconWindowBase.FONTW_MED);
                wageT.set(CompactNumber.format((long) s.actualMeanWage));
                wageT.color(GCOLOR.T().NORMAL);
                content.add(wageT, x + 195, y);

                GText foodT = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                foodT.set(String.format("%.1fd", s.foodDays));
                foodT.color(s.foodDays > 3 ? GCOLOR.UI().GOOD.normal : s.foodDays > 1 ? GCOLOR.UI().SOSO.normal : GCOLOR.UI().BAD.normal);
                content.add(foodT, x + 265, y);

                GText unpaidT = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                unpaidT.set(String.valueOf(s.workersUnpaid));
                unpaidT.color(s.workersUnpaid > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
                content.add(unpaidT, x + 330, y);

                y += 14;
            }
            y += 8;
        }

        // === STUFE & MEILENSTEINE ===
        GText stageHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        stageHdr.set("--- Stufe & Meilensteine ---");
        stageHdr.lablify();
        content.add(stageHdr, x, y);
        y += 22;

        EconWindowBase.addKpi(content, x, y, "Stufe", prog.stage.displayName, GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 380, y, "Tage in Stufe", String.valueOf(prog.stageDays), GCOLOR.T().NORMAL);
        y += 38;

        EconWindowBase.addKpi(content, x, y, "Bevoelkerung", String.valueOf(stats.people), GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 240, y, "Unbezahlte", String.valueOf(sim.firmLedger().lastWorkersUnpaid()),
                sim.firmLedger().lastWorkersUnpaid() > 0 ? GCOLOR.UI().BAD.normal : GCOLOR.UI().GOOD.normal);
        EconWindowBase.addKpi(content, x + 480, y, "Tote", String.valueOf(sim.deaths()), GCOLOR.T().NORMAL);
        y += 38;

        EconWindowBase.addKpi(content, x, y, "Ausgewandert", String.valueOf(sim.emigrations()),
                sim.emigrations() > 0 ? GCOLOR.UI().SOSO.normal : GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 240, y, "Erben", String.valueOf(sim.inherited()), GCOLOR.T().NORMAL);
        EconWindowBase.addKpi(content, x + 480, y, "Erblos", String.valueOf(sim.heirless()), GCOLOR.T().NORMAL);
        y += 38;

        OverviewHelpers.addMilestoneIcon(content, x, y, "Lagerhaus", prog.msFirstStockpile);
        OverviewHelpers.addMilestoneIcon(content, x + 220, y, "Export", prog.msFirstExport);
        OverviewHelpers.addMilestoneIcon(content, x + 440, y, "Taverne/Markt", prog.msFirstTavern || prog.msFirstMarket);
        y += 18;
        OverviewHelpers.addMilestoneIcon(content, x, y, "Stabile Löhne", prog.msStableWages);
        OverviewHelpers.addMilestoneIcon(content, x + 220, y, "Tempel", prog.msFirstTemple);
        OverviewHelpers.addMilestoneIcon(content, x + 440, y, "Labor", prog.msFirstLaboratory);
        y += 18;
        OverviewHelpers.addMilestoneIcon(content, x, y, "Botschaft", prog.msFirstEmbassy);
        y += 22;

        // Nächste Stufe
        String nextReqs = OverviewHelpers.nextStageReqs(prog, stats);
        GText nextText = new GText(UI.FONT().S, EconWindowBase.FONTW_BODY);
        nextText.set("Naechste Stufe: " + nextReqs);
        nextText.color(GCOLOR.UI().SOSO.normal);
        content.add(nextText, x, y);
        y += 24;

        // === WAS SOLL ICH TUN? === Sprint v0.13.105+/M-UI-2 — Triplet-Render
        GText adviceHdr = new GText(UI.FONT().M, EconWindowBase.FONTW_HDR);
        adviceHdr.set("--- Was soll ich heute tun? ---");
        adviceHdr.lablify();
        content.add(adviceHdr, x, y);
        y += 20;

        // Causality-Triplet: {Wahrscheinlichkeit p, Empfehlung A, Alternativen [B, C] mit Trade-off-Tabelle}.
        AdvisorEngine.Advice advice = AdvisorEngine.buildAdvice(sim, stats, ind, treasury);

        // ─── Empfehlung A (prominent) — Sprint v0.13.129+U-06 Multi-Line-Wrap ───
        // Vorher: Einzel-GText mit FONTW_BODY kappte Text mid-sentence ohne
        // Indikator ("kein Scrollindikator, kein Hinweis auf weiteren Inhalt").
        // Jetzt: Wenn Empfehlung > ~60 Zeichen (FONTW_BODY / UI.FONT().S-Char-Breite),
        // wird der Text auf 2 Zeilen verteilt mit einheitlich S-Font. Kurze
        // Empfehlungen (≤ 1 Zeile) behalten M-Font für Prominenz.
        // Gekürzter Text bekommt "…"-Suffix.
        String[] recLines = wrapAdviceText(advice.recommendation(), advice.probability());
        boolean singleLine = recLines.length == 1;
        for (String line : recLines) {
            if (y >= h - 20) break;
            GText recLine = singleLine
                ? new GText(UI.FONT().M, EconWindowBase.FONTW_BODY)
                : new GText(UI.FONT().S, EconWindowBase.FONTW_BODY);
            recLine.set(line);
            recLine.color(GCOLOR.UI().SOSO.normal);
            content.add(recLine, x, y);
            y += singleLine ? 22 : 16;
        }

        // ─── Alternativen-Tabelle (Top-3 mit 4 Trade-off-Spalten) ───
        if (advice.alternatives() != null && !advice.alternatives().isEmpty()) {
            GText altHdr = new GText(UI.FONT().S, EconWindowBase.FONTW_HDR);
            altHdr.set("Alternativen (mit Trade-off):");
            altHdr.lablify();
            content.add(altHdr, x, y);
            y += 18;

            // Column-Header
            EconWindowBase.addColHeader(content, x,        y, "Massnahme", 200);
            EconWindowBase.addColHeader(content, x + 210,  y, "Cash",       60);
            EconWindowBase.addColHeader(content, x + 280,  y, "Loyalty",    55);
            EconWindowBase.addColHeader(content, x + 345,  y, "Produkt.",   55);
            EconWindowBase.addColHeader(content, x + 410,  y, "Risiko",     50);
            y += 16;

            for (AdvisorEngine.Alternative alt : advice.alternatives()) {
                GText actT = new GText(UI.FONT().S, EconWindowBase.FONTW_KPI);
                actT.set(alt.action());
                actT.color(GCOLOR.T().NORMAL);
                content.add(actT, x, y);

                GText cashT = new GText(UI.FONT().S, EconWindowBase.FONTW_LABEL);
                cashT.set(alt.formatCash());
                cashT.color(alt.cashDeltaPerDay() > 0
                    ? GCOLOR.UI().GOOD.normal
                    : alt.cashDeltaPerDay() < 0
                        ? GCOLOR.UI().BAD.normal
                        : GCOLOR.T().NORMAL);
                content.add(cashT, x + 210, y);

                GText loyT = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                loyT.set(alt.formatLoyalty());
                loyT.color(alt.loyaltyDelta() > 0
                    ? GCOLOR.UI().GOOD.normal
                    : alt.loyaltyDelta() < 0
                        ? GCOLOR.UI().BAD.normal
                        : GCOLOR.T().NORMAL);
                content.add(loyT, x + 280, y);

                GText prodT = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                prodT.set(alt.formatProduction());
                prodT.color(alt.productionDelta() > 0
                    ? GCOLOR.UI().GOOD.normal
                    : alt.productionDelta() < 0
                        ? GCOLOR.UI().BAD.normal
                        : GCOLOR.T().NORMAL);
                content.add(prodT, x + 345, y);

                GText riskT = new GText(UI.FONT().S, EconWindowBase.FONTW_CNT);
                riskT.set(alt.formatRisk());
                riskT.color(alt.riskScore() > 50
                    ? GCOLOR.UI().BAD.normal
                    : alt.riskScore() > 25
                        ? GCOLOR.UI().SOSO.normal
                        : GCOLOR.UI().GOOD.normal);
                content.add(riskT, x + 410, y);

                y += 14;
            }
        }
    }

    // ═══ Sprint v0.13.129+U-06: Multi-Line-Wrap für Berater-Text ═══
    // FONTW_BODY = 512 px. Mit UI.FONT().S ≈ 8 px/Zeichen → ~64 Zeichen pro Zeile.
    // Text > 64 Zeichen wird in 2 Zeilen gesplittet (Wort-Grenze). > 128 Zeichen
    // wird auf 2 Zeilen gekürzt mit "…"-Trunkations-Indikator.
    // Einfacher Split-Algorithmus ohne Vanilla-Engine-Touch (Rule-15 safe).

    private static final int CHARS_PER_LINE = 62;

    private static String[] wrapAdviceText(String recommendation, int probability) {
        String confSuffix = "  (" + probability + "% Konfidenz)";
        String full = recommendation + confSuffix;
        if (full.length() <= CHARS_PER_LINE) return new String[] { full };

        // Zwei-Zeilen-Split: finde Wortgrenze nahe der Mitte
        int mid = CHARS_PER_LINE;
        while (mid > CHARS_PER_LINE / 2 && mid < full.length()
                && full.charAt(mid) != ' ') mid++;
        if (mid >= full.length() || mid <= CHARS_PER_LINE / 2) {
            // Keine gute Wortgrenze — hart bei CHARS_PER_LINE kürzen
            mid = CHARS_PER_LINE;
        }

        String line1 = full.substring(0, mid).trim();
        String line2 = full.substring(mid).trim();

        // Wenn line2 noch zu lang, kürzen mit "…"
        if (line2.length() > CHARS_PER_LINE) {
            int cut = CHARS_PER_LINE - 1;
            while (cut > CHARS_PER_LINE - 15 && cut < line2.length()
                    && line2.charAt(cut) != ' ') cut++;
            if (cut >= line2.length()) cut = CHARS_PER_LINE - 1;
            line2 = line2.substring(0, Math.min(cut, line2.length())).trim() + "…";
        }

        return new String[] { line1, line2 };
    }
}
