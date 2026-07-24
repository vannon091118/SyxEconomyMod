package vannon.syx.economy.ui;

import init.sprite.UI.UI;
import java.util.Map;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;

public final class EconWidgets {

    private EconWidgets() {}

    /** Single recycled GText instance for widget labels. The UI is single-threaded and immediate-mode.
     *  Lazy-init: UI.FONT() ist erst nach Engine-Initialisierung verfügbar. */
    private static GText labelText;

    private static GText labelText() {
        if (labelText == null) {
            labelText = new GText(UI.FONT().M, 256);
        }
        return labelText;
    }

    @SuppressWarnings("unchecked")
    private static <T> T state(EconContext ctx, String key, T initial) {
        Object v = ctx.state.get(key);
        if (v == null) {
            ctx.state.put(key, initial);
            return initial;
        }
        return (T) v;
    }

    public static int slider(EconContext ctx, String id, int x, int y, int value,
                             int min, int max, int step) {
        if (min >= max) return value;
        int w = 200;
        int h = 12;
        boolean over = ctx.mouseX >= x && ctx.mouseX <= x + w
                    && ctx.mouseY >= y && ctx.mouseY <= y + h;
        String grabbedKey = "grabbed:" + id;
        boolean grabbed = Boolean.TRUE.equals(state(ctx, grabbedKey, Boolean.FALSE));
        if (ctx.leftDown && (grabbed || over)) {
            ctx.state.put(grabbedKey, Boolean.TRUE);
            int nx = Math.max(x, Math.min(x + w, ctx.mouseX));
            double ratio = (double) (nx - x) / w;
            int range = max - min;
            int raw = min + (int) Math.round(range * ratio / step) * step;
            value = Math.max(min, Math.min(max, raw));
        } else {
            ctx.state.put(grabbedKey, Boolean.FALSE);
        }
        // Fill with clamping to avoid overflow
        int fillX = x + (int) ((double) (value - min) / (max - min) * w);
        fillX = Math.max(x, Math.min(x + w, fillX));
        COLOR.WHITE100.render(ctx.renderer, x, fillX, y, y + h);
        COLOR.WHITE35.render(ctx.renderer, fillX, x + w, y, y + h);
        return value;
    }

    public static boolean button(EconContext ctx, String label, int x, int y, int w, int h) {
        GText lt = labelText();
        lt.clear();
        lt.add(label);
        int textW = lt.width();
        int pad = 4;
        int actualW = w;
        if (textW + pad * 2 > w) {
            actualW = Math.min(textW + pad * 2, ctx.windowX + ctx.windowW - x);
        }
        boolean over = ctx.mouseX >= x && ctx.mouseX <= x + actualW
                    && ctx.mouseY >= y && ctx.mouseY <= y + h;
        COLOR c = over ? COLOR.WHITE120 : COLOR.WHITE35;
        c.render(ctx.renderer, x, x + actualW, y, y + h);
        lt.color(over ? COLOR.WHITE200 : COLOR.WHITE100);
        lt.render(ctx.renderer, x + pad, x + actualW - pad, y + 2, y + h - 2);
        return ctx.consumeClick() && over;
    }

    public static boolean toggle(EconContext ctx, String label, boolean value,
                                  int x, int y) {
        int w = 24;
        int h = 12;
        GText lt = labelText();
        lt.clear();
        lt.add(label);
        int labelW = lt.width();
        boolean over = ctx.mouseX >= x && ctx.mouseX <= x + w + labelW
                    && ctx.mouseY >= y && ctx.mouseY <= y + h;
        if (value) {
            COLOR.GREEN100.render(ctx.renderer, x, x + w, y, y + h);
        } else {
            COLOR.WHITE35.render(ctx.renderer, x, x + w, y, y + h);
        }
        lt.color(COLOR.WHITE100);
        lt.render(ctx.renderer, x + w + 4, x + w + labelW + 4, y, y + h);
        return (ctx.consumeClick() && over) ? !value : value;
    }

    /** Render a single line of text using the shared label GText. */
    public static void text(EconContext ctx, CharSequence text, int x, int y, COLOR color) {
        GText lt = labelText();
        lt.clear();
        lt.add(text);
        lt.color(color);
        int w = lt.width();
        lt.render(ctx.renderer, x, x + w, y, y + 16);
    }

    /** Vertical scrollbar. Returns the new scroll offset. */
    public static int scrollbar(EconContext ctx, String id, int contentHeight,
                                int viewportHeight, int currentScroll, int x, int y, int h) {
        if (contentHeight <= viewportHeight) return 0;
        String grabbedKey = "scroll:grabbed:" + id;
        String startScrollKey = "scroll:startScroll:" + id;
        String startYKey = "scroll:startY:" + id;
        boolean grabbed = Boolean.TRUE.equals(state(ctx, grabbedKey, Boolean.FALSE));
        int trackH = h;
        int thumbH = Math.max(20, viewportHeight * trackH / contentHeight);
        int maxScroll = contentHeight - viewportHeight;
        if (trackH <= thumbH || maxScroll <= 0) {
            COLOR.WHITE35.render(ctx.renderer, x, x + 8, y, y + trackH);
            return 0;
        }
        int thumbY = y + currentScroll * (trackH - thumbH) / maxScroll;

        boolean overThumb = ctx.mouseX >= x && ctx.mouseX <= x + 8
                         && ctx.mouseY >= thumbY && ctx.mouseY <= thumbY + thumbH;

        if (ctx.leftDown && (grabbed || overThumb)) {
            if (!grabbed) {
                ctx.state.put(grabbedKey, Boolean.TRUE);
                ctx.state.put(startScrollKey, currentScroll);
                ctx.state.put(startYKey, ctx.mouseY);
            }
            Integer startScroll = (Integer) ctx.state.get(startScrollKey);
            Integer startY = (Integer) ctx.state.get(startYKey);
            if (startScroll != null && startY != null) {
                int deltaPixels = ctx.mouseY - startY;
                int scrollDelta = deltaPixels * maxScroll / (trackH - thumbH);
                currentScroll = Math.max(0, Math.min(maxScroll, startScroll + scrollDelta));
            }
        } else {
            ctx.state.put(grabbedKey, Boolean.FALSE);
            ctx.state.remove(startScrollKey);
            ctx.state.remove(startYKey);
        }

        COLOR.WHITE35.render(ctx.renderer, x, x + 8, y, y + trackH);
        COLOR.WHITE100.render(ctx.renderer, x, x + 8, thumbY, thumbY + thumbH);
        return currentScroll;
    }
}
