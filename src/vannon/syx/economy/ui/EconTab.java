package vannon.syx.economy.ui;

/** One tab inside an {@link EconWindowBase}. */
public interface EconTab {
    CharSequence title();
    /** Called when the tab is opened (resets scroll etc.). */
    void onOpen();
    /** Called every frame for hover/tooltip handling. */
    void hover(EconContext ctx);
    /** Render the tab content. yStart is the y coordinate below the KPI header. */
    void render(EconContext ctx, int yStart);
    /** Called when the user clicks inside the tab content (after tab buttons were missed). */
    default void click(EconContext ctx, snake2d.MButt button) {
        // default: no-op
    }
    /**
     * If {@code true}, the base window skips its default KPI header strip on this
     * tab. Used by tabs that draw their own landing surface — e.g. DashboardTab
     * draws a master banner that already covers the same numbers.
     * <p>Default: {@code false} (legacy tabs keep the KPI strip).
     */
    default boolean drawsOwnHeader() {
        return false;
    }
}
