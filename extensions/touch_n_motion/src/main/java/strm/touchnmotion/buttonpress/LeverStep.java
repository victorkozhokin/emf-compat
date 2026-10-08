package strm.touchnmotion.buttonpress;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Vector3f;
import strm.touchnmotion.torso.PelvisFollow;
import strm.emfcompat.core.ik.IKFrame;
import java.util.function.Function;
import strm.touchnmotion.interaction.Ease;
import strm.touchnmotion.interaction.Body;
import strm.touchnmotion.interaction.FrameClock;
import strm.touchnmotion.torso.Stride;

/** One short support step on the click; the other sole stays planted. */
public final class LeverStep {
    AbstractClientPlayer player;
    IKFrame space;
    boolean eligible, holding;
    public long press, consumed;
    final FrameClock clock = new FrameClock();
    public float progress = 1;
    int foot;
    public float stride = 1, duration = .24f, height = .35f;
    final Vector3f grip = new Vector3f(), offset = new Vector3f();
    final Vector3f start = new Vector3f(), end = new Vector3f();

    public void observe(AbstractClientPlayer player, IKFrame space, boolean holding, Vector3f grip, long press) {
        this.player = player;
        this.space = space;
        this.holding = holding;
        this.eligible = Body.planted(player);
        this.grip.set(grip);
        this.press = press;
    }

    public void apply(Function<String, ModelPart> parts, float owned) {
        if (player == null || space == null) return;
        ModelPart[] legs = {parts.apply("right_leg"), parts.apply("left_leg")};
        if (legs[0] == null || legs[1] == null) return;
        float dt = (float) clock.tick();
        if (dt >= 0) {
            if (!eligible) { offset.zero(); start.zero(); end.zero(); progress = 1; consumed = press; }
            else {
                if (holding && owned > .5f && press > 0 && press != consumed) {
                    consumed = press;
                    if (offset.lengthSquared() < 1e-5f && progress >= 1) {
                        foot = grip.x <= 0 ? 0 : 1;
                        Vector3f wanted = LeverEffort.step(grip).mul(stride);
                        if (safe(legs[foot], offset, wanted)) begin(wanted);
                    }
                }
                if ((!holding || owned < .01f) && progress >= 1 && offset.lengthSquared() > 1e-5f
                        && safe(legs[foot], offset, new Vector3f())) begin(new Vector3f());
                if (progress < 1) {
                    if (!safe(legs[foot], start, end)) { offset.set(start); progress = 1; }
                    else {
                        progress = Math.min(1, progress + dt / duration);
                        offset.set(start).lerp(end, Ease.smooth(progress));
                    }
                }
            }
        }
        Vector3f stepping = new Vector3f(offset);
        if (progress < 1) stepping.y -= LeverEffort.lift(progress) * (player.isCrouching() ? height * .57f : height);
        PelvisFollow.step(parts, foot == 0 ? stepping : new Vector3f(), foot == 1 ? stepping : new Vector3f(), 0, 0);
    }

    private void begin(Vector3f wanted) { start.set(offset); end.set(wanted); progress = 0; }

    private boolean safe(ModelPart leg, Vector3f from, Vector3f to) {
        return Stride.level(player, space, leg, from, to);
    }
}
