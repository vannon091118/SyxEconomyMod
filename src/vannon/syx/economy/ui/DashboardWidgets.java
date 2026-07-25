package vannon.syx.economy.ui;

import snake2d.util.color.COLOR;
import util.gui.misc.GText;

/**
 * Self-drawn IMGUI primitives for the 3-second dashboard.
 * <p>
 * <b>Master banner:</b> a single, always-visible strip that rolls every
 *   sub-system up into one traffic light. The player can tell in
 *   &lt;1 second whether anything needs attention — no number reading required.
 * <p>
 * <b>Status cards:</b> players care about questions, not data sources.
 *   Each card answers one question. Cards answer them visually: a dot,
 *   a value, and a trend arrow. The "what" (label) and "is it OK" (light)
 *   are read together; the number is the third layer of detail.
 *
 * <p>Implements the briefing's rule "Ampelsystem statt Zahlenwand" and
 * "Ein einziger 'Ist alles okay?'-Indikator" — never a raw number
 * without a colour and never a metric without a status verdict.
 */
public final class DashboardWidgets {

    private DashboardWidgets() {}

    /**
     * Self-drawn header banner with the rolled-up "all systems" indicator.
     *
     * @param ctx render context (the renderer lives inside)
     * @param x left edge
     * @param y top edge
     * @param w total width
     * @param rolledStatus overall status colour (worst-of-all-subsystems)
     * @param title banner title, e.g. "Ist alles in Ordnung?"
     * @param subtitle one-line summary, e.g. "2 Hinweise · Lohn-Run heute"
     */
    public static void masterBanner(EconContext ctx,
                                     int x, int y, int w,
                                     COLOR rolledStatus,
                                     CharSequence title,
                                     CharSequence subtitle) {
        int h = EconStyle.BANNER_H;
        // Solid status colour as the body — drives the eye even from across the room.
        rolledStatus.render(ctx.renderer, x, x + w, y, y + h);
        // Border rim top + bottom for structure
        COLOR.WHITE35.render(ctx.renderer, x, x + w, y, y + 2);
        COLOR.WHITE35.render(ctx.renderer, x, x + w, y + h - 2, y + h);

        // Big dot inside the left edge — visible even when text is missed.
        int dotSize = EconStyle.BANNER_DOT;
        int dotX = x + 16;
        int dotY = y + (h - dotSize) / 2;
        COLOR.WHITE100.render(ctx.renderer, dotX, dotX + dotSize, dotY, dotY + dotSize);

        // Banner title (left, large)
        GText t = EconWidgets.labelText();
        t.clear();
        t.add(title);
        t.color(COLOR.WHITE100);
        t.render(ctx.renderer, x + 16 + dotSize + 14, x + w - 16, y + 16, y + 16 + EconStyle.BANNER_TITLE_H);

        // Banner subtitle (left, smaller, below title)
        t.clear();
        t.add(subtitle);
        t.color(COLOR.WHITE100);
        t.render(ctx.renderer, x + 16 + dotSize + 14, x + w - 16, y + 16 + EconStyle.BANNER_TITLE_H + 4,
                 y + h - 8);
    }

    /**
     * Single status card. Each card is self-drawn, no {@code GButt} / {@code GPanel}.
     * Visual hierarchy: title + status-dot (top), value + trend-arrow (middle),
     * caption (bottom).
     */
    public static void card(EconContext ctx,
                             int x, int y, int w, int h,
                             COLOR status,
                             CharSequence title,
                             CharSequence valueBig,
                             CharSequence caption,
                             String trendGlyph,
                             COLOR trendColor,
                             CharSequence trendDelta) {
        // Card background + border
        EconStyle.CARD_BG.render(ctx.renderer, x, x + w, y, y + h);
        EconStyle.CARD_BORDER_C.render(ctx.renderer, x, x + w, y, y + 2);
        EconStyle.CARD_BORDER_C.render(ctx.renderer, x, x + w, y + h - 2, y + h);
        EconStyle.CARD_BORDER_C.render(ctx.renderer, x, x + 2, y, y + h);
        EconStyle.CARD_BORDER_C.render(ctx.renderer, x + w - 2, x + w, y, y + h);

        // Status dot — top-right corner
        int ds = EconStyle.DOT_SIZE;
        int dx = x + w - EconStyle.CARD_PAD - ds;
        int dy = y + EconStyle.CARD_PAD;
        status.render(ctx.renderer, dx, dx + ds, dy, dy + ds);

        // Title — left, just below the top padding
        GText t = EconWidgets.labelText();
        int titleY = y + EconStyle.CARD_PAD;
        t.clear();
        t.add(title);
        t.color(EconStyle.LABEL_C);
        t.render(ctx.renderer,
            x + EconStyle.CARD_PAD,
            x + w - EconStyle.CARD_PAD - ds - 6, // don't overrun the dot
            titleY,
            titleY + EconStyle.LABEL_H);

        // Big value — middle
        int valueY = titleY + EconStyle.LABEL_H + 6;
        int valueXEnd = x + w - EconStyle.CARD_PAD;
        t.clear();
        t.add(valueBig);
        t.color(EconStyle.NUMBER_C);
        t.render(ctx.renderer,
            x + EconStyle.CARD_PAD,
            valueXEnd,
            valueY,
            valueY + EconStyle.NUMBER_H);

        // Trend arrow + delta — right side, vertically aligned with value
        if (trendGlyph != null && trendDelta != null) {
            t.clear();
            t.add(trendGlyph).add(" ").add(trendDelta);
            t.color(trendColor);
            t.render(ctx.renderer,
                valueXEnd - EconStyle.ARROW_W - t.width() - 4,
                valueXEnd,
                valueY,
                valueY + EconStyle.NUMBER_H);
        } else if (trendGlyph != null) {
            t.clear();
            t.add(trendGlyph);
            t.color(trendColor);
            t.render(ctx.renderer,
                valueXEnd - EconStyle.ARROW_W - t.width(),
                valueXEnd,
                valueY,
                valueY + EconStyle.NUMBER_H);
        }

        // Caption — bottom
        if (caption != null) {
            int capY = y + h - EconStyle.CARD_PAD - EconStyle.LABEL_H;
            t.clear();
            t.add(caption);
            t.color(EconStyle.MUTED_C);
            t.render(ctx.renderer,
                x + EconStyle.CARD_PAD,
                x + w - EconStyle.CARD_PAD,
                capY,
                capY + EconStyle.LABEL_H);
        }
    }

    /**
     * Trend verdict from two history samples, with hysteresis-style null return
     * when the delta is too small to bother the player.
     *
     * @return {@link EconStyle#ARROW_FLAT} for "no meaningful change",
     *         {@link EconStyle#ARROW_UP} for "improving" (or {@code ARROW_DOWN}
     *         if {@code invert} is true and higher number = worse, e.g. evictions).
     */
    public static String trendFor(double recent, double older, double minDelta, boolean invert) {
        if (Double.isNaN(recent) || Double.isNaN(older)) return EconStyle.ARROW_FLAT;
        double delta = recent - older;
        if (Math.abs(delta) < minDelta) return EconStyle.ARROW_FLAT;
        if (delta > 0) return invert ? EconStyle.ARROW_DOWN : EconStyle.ARROW_UP;
        return invert ? EconStyle.ARROW_UP : EconStyle.ARROW_DOWN;
    }

    /** Map trend glyph → colour. */
    public static COLOR trendColor(String glyph) {
        if (glyph == null || EconStyle.ARROW_FLAT.equals(glyph)) return EconStyle.TREND_FLAT_C;
        if (EconStyle.ARROW_UP.equals(glyph)) return EconStyle.TREND_UP_C;
        return EconStyle.TREND_DOWN_C;
    }
}
