package vannon.syx.economy.core;

import snake2d.SPRITE_RENDERER;
import util.gui.misc.GChart;

/** Thin public wrapper around {@link GChart} so it can be rendered from code
 *  outside the {@code util.gui.misc} package. */
public final class ChartPanel extends GChart {

    @Override
    public void render(SPRITE_RENDERER r, float ds, boolean isHovered) {
        super.render(r, ds, isHovered);
    }
}
