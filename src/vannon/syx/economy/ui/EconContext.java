package vannon.syx.economy.ui;

import java.util.Map;
import snake2d.Renderer;
import vannon.syx.economy.core.EconomySim;

/** Render context passed to every UI component every frame. */
public final class EconContext {
    public final Renderer renderer;
    public final EconomySim sim;
    public final float ds;
    public final int mouseX;
    public final int mouseY;
    public final boolean leftDown;
    public boolean clicked;
    public final int windowX;
    public final int windowY;
    public final int windowW;
    public final int windowH;
    public final Map<String, Object> state;

    public EconContext(Renderer renderer, EconomySim sim, float ds,
                         int mouseX, int mouseY, boolean leftDown, boolean clicked,
                         int windowX, int windowY, int windowW, int windowH,
                         Map<String, Object> state) {
        this.renderer = renderer;
        this.sim = sim;
        this.ds = ds;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.leftDown = leftDown;
        this.clicked = clicked;
        this.windowX = windowX;
        this.windowY = windowY;
        this.windowW = windowW;
        this.windowH = windowH;
        this.state = state;
    }

    /** Consume the current click for IMGUI widgets. Only the first widget should get it. */
    public boolean consumeClick() {
        if (clicked) {
            clicked = false;
            return true;
        }
        return false;
    }
}
