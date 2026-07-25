package vannon.syx.economy.ui;

import snake2d.util.color.COLOR;

/**
 * Centralized UI design constants for the economy mod.
 * <p>
 * Replaces magic numbers scattered across EconWindowBase, EconWidgets and
 * OverviewTabs with a single source of truth. Sized for the existing
 * 80% × 80% window (≈ 960 × 540 px on 1280×720 — fits 4 cards wide on most
 * screens, 2 cards wide on minimum 800 px).
 */
public final class EconStyle {

    private EconStyle() {}

    // ──────────── Spacing ────────────
    public static final int PAD = 16;                   // outer container padding
    public static final int GAP_X = 12;                 // horizontal gap between cards
    public static final int GAP_Y = 12;                 // vertical gap between rows
    public static final int CARD_PAD = 12;              // inner card padding

    // ──────────── Cards ────────────
    public static final int CARD_BORDER = 2;            // card border thickness
    public static final int CARD_H = 78;                // standard card height
    public static final int DOT_SIZE = 10;              // traffic-light dot diameter
    public static final int ICON_SIZE = 18;             // leading icon target size
    public static final int ARROW_W = 14;               // arrow column width

    // ──────────── Banner ────────────
    public static final int BANNER_H = 86;              // master banner height
    public static final int BANNER_DOT = 16;            // banner traffic-light dot
    public static final int BANNER_TITLE_H = 22;        // banner title line height
    public static final int BANNER_SUBTITLE_H = 14;     // banner subtitle line height

    // ──────────── Typography ────────────
    public static final int NUMBER_H = 18;
    public static final int LABEL_H = 14;

    // ──────────── Color palette: traffic lights ────────────
    public static final COLOR GOOD = COLOR.GREEN100;
    public static final COLOR OKAY = COLOR.YELLOW100;
    public static final COLOR BAD  = COLOR.RED100;
    public static final COLOR NA   = COLOR.WHITE35;

    // ──────────── Color palette: chrome ────────────
    public static final COLOR CARD_BG      = COLOR.WHITE10;
    public static final COLOR CARD_BORDER_C = COLOR.WHITE15;
    public static final COLOR BANNER_BG    = COLOR.WHITE10;
    public static final COLOR BANNER_RIM   = COLOR.WHITE35;
    public static final COLOR LABEL_C       = COLOR.WHITE100;
    public static final COLOR NUMBER_C      = COLOR.WHITE200;
    public static final COLOR MUTED_C       = COLOR.WHITE100;
    public static final COLOR TREND_UP_C    = COLOR.GREEN100;
    public static final COLOR TREND_DOWN_C  = COLOR.RED100;
    public static final COLOR TREND_FLAT_C  = COLOR.WHITE35;

    // ──────────── Trend glyphs (UTF-8) ────────────
    public static final String ARROW_UP   = "↑";
    public static final String ARROW_DOWN = "↓";
    public static final String ARROW_FLAT = "→";

    // ──────────── Panel container helpers ────────────
    /** Compute card width for N cards across a container. */
    public static int cardWidth(int containerW, int cardsPerRow) {
        int totalGaps = (cardsPerRow - 1) * GAP_X;
        return (containerW - totalGaps) / cardsPerRow;
    }

    /** Compute row Y for row-index inside a card grid starting at topY. */
    public static int rowY(int topY, int row, int cardH) {
        return topY + row * (cardH + GAP_Y);
    }
}
