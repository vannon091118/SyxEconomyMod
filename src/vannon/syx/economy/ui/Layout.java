package vannon.syx.economy.ui;

import snake2d.util.color.COLOR;
import snake2d.util.gui.GuiSection;
import snake2d.util.misc.ACTION;
import snake2d.util.sprite.SPRITE;
import util.gui.misc.GText;
import vannon.syx.economy.ui.tabs.Overview.OverviewHelpers;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Sprint v0.13.106+M-UI-3.5 — Layout-Prototyp mit Fluent-API für die 16 Tabs.
 *
 * <p>Ersetzt hardcoded {@code x + 170} / {@code x + 240} / {@code x + 380} durch
 * deklarative Grid-Positionierung. Beispiel:</p>
 *
 * <pre>{@code
 * Layout.grid(3, 10)
 *     .at(12, 30)
 *     .cellWidth(240)
 *     .kpi("Staatskasse", treasuryStr, color)
 *     .icon(UI.icons().m.citizen, "Bevölkerung", "85", color)
 *     .kpi("Stufe", "Handel", color)
 *     .row()
 *     .icon(UI.icons().m.heart, "Gini", "0.32", color)
 *     .kpi("Median", "10K D", color)
 *     .build(content);
 * }</pre>
 *
 * <p><b>Performance-Modell (Generic-Layout-Trap vermeidet)</b>: keine {@code Method.invoke},
 * keine {@code Class.forName}-Lookup, kein Generic-T-Box. Ein einmaliger
 * <b>Pre-Compute-Pass</b> setzt (x, y) für jede Cell (linear, O(N)). Der
 * <b>Render-Pass</b> ist ein dichter {@code switch}-auf-{@code CellKind}-enum
 * (5 konstante Cases), JIT-foldet zu {@code tableswitch} ohne VTable-Indirection.</p>
 *
 * <p>Render-Pfad delegiert an existierende {@code EconWindowBase}-Helfer
 * (alle {@code public static} nach M-UI-3 Visibility-Tweak):</p>
 * <ul>
 *   <li>{@code EconWindowBase.addKpi(parent, x, y, label, value, color)} — KPI-String</li>
 *   <li>{@code EconWindowBase.addKpi(parent, x, y, SPRITE, label, value, color)} — KPI-Icon</li>
 *   <li>{@code EconWindowBase.addSlider(parent, x, y, label, supplier, min, max, step, plus, minus)}</li>
 *   <li>{@code OverviewHelpers.addCheckbox(parent, x, y, label, initial, setter)}</li>
 * </ul>
 *
 * <p><b>Spec:</b> {@code docs/UI_GRID_LAYOUT_SPEC.md}. Tab-Migration selbst
 * ist separate Sprint M-UI-5 (kein Rule-11 Theme-Scope hier).</p>
 *
 * <p><b>Test-Status (M-UI-3.5):</b> Build-only. Mockito-Test-Fixture analog
 * T-COV-9 folgt in Sprint M-UI-5.</p>
 *
 * <p>Rule-14 God-Class-Guard: ~190 SLOC + 5 cells, alle unter Block-Limits.
 * Rule-15 konform: keine static-final Engine-Touchables.</p>
 */
public final class Layout {

    // ──────────────────────────────────────────────────────────────────
    // Builder-State
    // ──────────────────────────────────────────────────────────────────

    private final int cols;
    private final int gap;
    private int cellWidth = 240;            // Default-Spaltenbreite
    private int originX = 0;
    private int originY = 0;
    private int rowHeight = 30;             // Default-Zellenhöhe
    private int row = 0;
    private int col = 0;

    /** Pipeline-Buffer (wird in build() fixed-size rendert). */
    private final ArrayList<Cell> cells = new ArrayList<>();

    /** Hard-Cap für Sanity-Check in precompute(); abgeleitet aus Tab-Panel-Default-Height. */
    private static final int MAX_GRID_ROWS = 16;

    private Layout(int cols, int gap) {
        if (cols <= 0 || gap < 0) throw new IllegalArgumentException("cols>0, gap>=0");
        this.cols = cols;
        this.gap = gap;
    }

    // ──────────────────────────────────────────────────────────────────
    // 3.1 — Erzeugung (Builder)
    // ──────────────────────────────────────────────────────────────────

    /** Neue Grid-Instanz: {@code cols} Spalten mit {@code gap} Pixeln Abstand. */
    public static Layout grid(int cols, int gap) {
        return new Layout(cols, gap);
    }

    /** Origin (Anker oben-links) setzen. */
    public Layout at(int x, int y) {
        if (x < 0 || y < 0) throw new IllegalArgumentException("at(x,y) >= 0");
        this.originX = x;
        this.originY = y;
        return this;
    }

    /** Spaltenbreite in Pixel (Default: 240). */
    public Layout cellWidth(int w) {
        if (w <= 0) throw new IllegalArgumentException("cellWidth>0");
        this.cellWidth = w;
        return this;
    }

    /** Default-Zellenhöhe in Pixel (Default: 30). */
    public Layout rowHeight(int h) {
        if (h <= 0) throw new IllegalArgumentException("rowHeight>0");
        this.rowHeight = h;
        return this;
    }

    // ──────────────────────────────────────────────────────────────────
    // 3.2 — Builder-Methoden (Cell-Operations)
    // ──────────────────────────────────────────────────────────────────

    /** Explizit neue Zeile beginnen (manuell nach Spaltenlimit). */
    public Layout row() {
        this.row++;
        this.col = 0;
        return this;
    }

    /** Vertikaler Spacer + neue Zeile. */
    public Layout newLine() {
        this.row++;
        this.col = 0;
        return this;
    }

    /** KPI-Cell: String-Label-Variante. */
    public Layout kpi(String label, String value, COLOR valueColor) {
        cells.add(Cell.kpi(row, col, label, value, valueColor));
        advance(1);
        return this;
    }

    /** KPI-Cell mit Icon-Sprite (z.B. {@code UI.icons().m.coins}). */
    public Layout icon(SPRITE sprite, String label, String value, COLOR valueColor) {
        cells.add(Cell.icon(row, col, sprite, label, value, valueColor));
        advance(1);
        return this;
    }

    /**
     * Slider-Cell (Live-Slider). Über 7 Parameter — direkt durchgereicht
     * an {@link EconWindowBase#addSlider}.
     */
    public Layout slider(String label, IntSupplier currentSupplier,
                         int min, int max, int step,
                         ACTION plusAction, ACTION minusAction) {
        if (min < 0 || max < min || step <= 0)
            throw new IllegalArgumentException("slider range invalid");
        cells.add(Cell.slider(row, col, label, currentSupplier, min, max, step, plusAction, minusAction));
        advance(1);
        return this;
    }

    /** Toggle-Checkbox. Reicht an {@link OverviewHelpers#addCheckbox}. */
    public Layout checkbox(String label, boolean initial, Consumer<Boolean> setter) {
        cells.add(Cell.checkbox(row, col, label, initial, setter));
        advance(1);
        return this;
    }

    /** Volle-Zeile Header-Text (auto-wrap col=0, row++). */
    public Layout header(String text) {
        int fullWidth = cellWidth * cols + (cols - 1) * gap;
        cells.add(Cell.text(row, 0, text, fullWidth, /*isHeader*/ true));
        this.row++;
        this.col = 0;
        return this;
    }

    /** Multi-Line-Text (1 Spalte, freie Höhe). */
    public Layout text(String text, int width) {
        cells.add(Cell.text(row, col, text, width, /*isHeader*/ false));
        advance(1);
        return this;
    }

    /**
     * Column-Skip: rückt die nächste Cell um {@code (colspan − 1)} Spalten weiter.
     * <b>Nicht</b> Cell-Stretch — die zuvor hinzugefügte Cell wird nicht verbreitert
     * (siehe {@code docs/UI_GRID_LAYOUT_SPEC.md} §6.1 für Details).
     * Validierung: {@code colspan >= 1} und {@code colspan <= remainingCols}.
     */
    public Layout span(int colspan) {
        if (colspan <= 0) throw new IllegalArgumentException("colspan>0");
        // Code-Reviewer M-1: Skip-validation gegen verbleibende Spalten. Springt nicht
        // ueber Row-Boundary (advance() macht row++ nur bei col >= cols).
        if (colspan > cols - col)
            throw new IllegalArgumentException(
                "colspan " + colspan + " exceeds remaining cols " + (cols - col)
                + " at current row=" + row);
        if (colspan > 1) advance(colspan - 1);
        return this;
    }

    // ──────────────────────────────────────────────────────────────────
    // 4 — Render-Pipeline (Pre-Compute + Switch-Render)
    // ──────────────────────────────────────────────────────────────────

    /** Pre-Compute-Pass ohne Render (für Tests / Dry-Run). */
    public Layout precompute() {
        if (row > MAX_GRID_ROWS) {
            throw new IllegalStateException("Layout row " + row + " exceeds MAX_GRID_ROWS=" + MAX_GRID_ROWS);
        }
        for (Cell c : cells) {
            c.x = originX + c.col * (cellWidth + gap);
            c.y = originY + c.row * rowHeight;
        }
        return this;
    }

    /**
     * Terminal: Pre-Compute + Render aller Cells.
     * Wird genau einmal pro Layout-Instanz aufgerufen
     * (danach ist die interne Cell-Liste konsumiert).
     */
    public void build(GuiSection parent) {
        precompute();
        for (Cell c : cells) {
            c.render(parent);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    private void advance(int colspan) {
        col += colspan;
        if (col >= cols) {
            col = 0;
            row++;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Cell (Union-Type mit enum-Dispatch) — single class, mutable
    // ──────────────────────────────────────────────────────────────────

    /**
     * Single mutable Cell-Class. Per-Kind Felder koexistieren (kept allocated,
     * ignored bei non-matching kind). Sealed {@link CellKind}-enum-Switch im
     * Render-Pfad dispatcht ohne VTable-Indirection.
     *
     * <p>Trade-Off vs Polymorphic Sub-Classes:
     * <ul>
     *   <li>+ Kein Generic-T, kein Box-Nutzung</li>
     *   <li>+ JIT-foldet Switch auf dichtem tableswitch</li>
     *   <li>- Per-Cell foot-print größer (~120 bytes statt ~32)</li>
     * </ul>
     * Bei 50 Cells = 6KB Foot-Print, akzeptabel für 1×-build-pro-tick Window.</p>
     */
    private static final class Cell {

        enum CellKind { KPI, ICON, SLIDER, CHECKBOX, TEXT }

        CellKind kind;
        int row, col;
        int x = -1, y = -1;                  // mutable, precomputed

        // KPI / ICON (shared fields)
        String kpiLabel, kpiValue;
        COLOR kpiColor;
        SPRITE iconSprite;                    // ICON-only

        // SLIDER
        String sliderLabel;
        IntSupplier sliderSupplier;
        int sliderMin, sliderMax, sliderStep;
        ACTION sliderPlus, sliderMinus;

        // CHECKBOX
        String cbLabel;
        boolean cbInitial;
        Consumer<Boolean> cbSetter;

        // TEXT / HEADER
        String textString;
        int textWidth;
        boolean isHeader;

        // Factories (kein Generic-T, kein Builder-Pattern für Factories — direkt)
        static Cell kpi(int row, int col, String label, String value, COLOR color) {
            Cell c = new Cell();
            c.kind = CellKind.KPI;
            c.row = row; c.col = col;
            c.kpiLabel = label; c.kpiValue = value; c.kpiColor = color;
            return c;
        }

        static Cell icon(int row, int col, SPRITE sprite, String label, String value, COLOR color) {
            Cell c = new Cell();
            c.kind = CellKind.ICON;
            c.row = row; c.col = col;
            c.iconSprite = sprite;
            c.kpiLabel = label; c.kpiValue = value; c.kpiColor = color;
            return c;
        }

        static Cell slider(int row, int col, String label, IntSupplier supplier,
                           int min, int max, int step, ACTION plus, ACTION minus) {
            Cell c = new Cell();
            c.kind = CellKind.SLIDER;
            c.row = row; c.col = col;
            c.sliderLabel = label;
            c.sliderSupplier = supplier;
            c.sliderMin = min; c.sliderMax = max; c.sliderStep = step;
            c.sliderPlus = plus; c.sliderMinus = minus;
            return c;
        }

        static Cell checkbox(int row, int col, String label, boolean initial, Consumer<Boolean> setter) {
            Cell c = new Cell();
            c.kind = CellKind.CHECKBOX;
            c.row = row; c.col = col;
            c.cbLabel = label; c.cbInitial = initial; c.cbSetter = setter;
            return c;
        }

        static Cell text(int row, int col, String text, int width, boolean isHeader) {
            Cell c = new Cell();
            c.kind = CellKind.TEXT;
            c.row = row; c.col = col;
            c.textString = text; c.textWidth = width; c.isHeader = isHeader;
            return c;
        }

        /**
         * Sealed-Switch-Render (kein Reflection, keine VTable-Indirection).
         * JIT kompiliert Switch bei 5 konstante Cases (oder 6 mit TEXT-Variante)
         * zu dichtem {@code tableswitch}.
         */
        void render(GuiSection parent) {
            switch (this.kind) {
                case KPI:
                    EconWindowBase.addKpi(parent, x, y, kpiLabel, kpiValue, kpiColor);
                    break;
                case ICON:
                    EconWindowBase.addKpi(parent, x, y, iconSprite, kpiLabel, kpiValue, kpiColor);
                    break;
                case SLIDER:
                    EconWindowBase.addSlider(parent, x, y, sliderLabel, sliderSupplier,
                            sliderMin, sliderMax, sliderStep, sliderPlus, sliderMinus);
                    break;
                case CHECKBOX:
                    OverviewHelpers.addCheckbox(parent, x, y, cbLabel, cbInitial, cbSetter);
                    break;
                case TEXT:
                    renderText(parent);
                    break;
                default:
                    throw new IllegalStateException("Unknown CellKind: " + kind);
            }
        }

        private void renderText(GuiSection parent) {
            GText t = new GText(init.sprite.UI.UI.FONT().M, textWidth);
            t.set(textString);
            if (isHeader) {
                t.lablify();
            }
            parent.add(t, x, y);
        }
    }

}
