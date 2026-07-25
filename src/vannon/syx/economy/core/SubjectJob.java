package vannon.syx.economy.core;

import init.constant.C;
import init.sprite.UI.UI;
import settlement.entity.humanoid.Humanoid;
import settlement.room.main.RoomInstance;
import settlement.stats.STATS;
import snake2d.Renderer;
import snake2d.SPRITE_RENDERER;
import snake2d.util.color.COLOR;
import util.gui.misc.GText;
import view.main.VIEW;

/**
 * Right-side job/workplace overlay — mirrors {@link SubjectWallet} (which
 * shows wallet on the left). Renders a self-drawn panel next to the selected
 * citizen with their current job, workplace name, and employment status.
 *
 * <p>Position: right edge of screen, same Y as SubjectWallet.
 * Hidden if no subject is selected, subject is dead/removed, or unemployed
 * with no relevant data to show.
 */
public final class SubjectJob {
    /** Right-edge X: same width as SubjectWallet (150), mirrored from left. */
    private static final int W = 150;
    private static final int H = 26;
    private final GText text;

    public SubjectJob() {
        this.text = new GText(UI.FONT().S, 64);
    }

    public void render(Renderer r, float ds) {
        if (!EconConfig.windowEnabled) {
            return;
        }
        EconomySim sim = EconomySim.active();
        if (sim == null) {
            return;
        }
        if (VIEW.s() == null) {
            return;
        }
        Humanoid h = VIEW.s().ui.subjects.current();
        if (h == null || h.isRemoved()) {
            return;
        }

        // Position: mirror of SubjectWallet — right edge of screen
        int x0 = C.WIDTH() - 166;
        int x1 = C.WIDTH() - 16;
        int y0 = 56;
        int y1 = 82;

        // Resolve job info
        RoomInstance workplace = (RoomInstance) STATS.WORK().EMPLOYED.get(h.indu());
        String jobText;
        if (workplace != null && workplace.blueprintI() != null) {
            String bpName = workplace.blueprintI().key;
            jobText = (bpName != null && !bpName.isEmpty()) ? bpName : "?";
        } else if (workplace != null) {
            jobText = "?";
        } else {
            jobText = "Arbeitslos";
        }

        // Self-drawn panel (same style as SubjectWallet)
        COLOR.WHITE15.render((SPRITE_RENDERER) r, x0, x1, y0, y1);
        // Border: top, bottom, left, right
        COLOR.WHITE35.render((SPRITE_RENDERER) r, x0, x1, y0, y0 + 1);
        COLOR.WHITE35.render((SPRITE_RENDERER) r, x0, x1, y1 - 1, y1);
        COLOR.WHITE35.render((SPRITE_RENDERER) r, x0, x0 + 1, y0, y1);
        COLOR.WHITE35.render((SPRITE_RENDERER) r, x1 - 1, x1, y0, y1);

        // Icon: work-related (hammer/pickaxe from vanilla icons)
        UI.icons().s.hammer.render((SPRITE_RENDERER) r, x0 + 6, y0 + 5);

        // Job text — same Y offset as SubjectWallet (y0+7 to y1)
        this.text.clear().add(jobText);
        this.text.color(workplace != null ? COLOR.WHITE200 : COLOR.WHITE100);
        this.text.render((SPRITE_RENDERER) r, x0 + 26, x1 - 4, y0 + 7, y1);
    }
}
