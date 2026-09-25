package strm.emfcompat.animationadditions.footgrounding;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.animationadditions.footgrounding.compat.FootGrounding;
import strm.emfcompat.animationadditions.footgrounding.compat.HorseFootGrounding;

/**
 * Foot IK (experimental).
 *
 * <p>Minecraft stands the player's hitbox on the highest block under it, so on the edge of a step
 * or a slab one foot stands on it and the other hangs in the air above the lower floor. This looks
 * under each foot, lowers the model onto the lower floor and raises the leg on the step so its
 * foot stays planted. See {@link FootGrounding}.</p>
 *
 * <p>Pack legs have no knee (FA+Player: {@code left_leg}/{@code right_leg} are one bone each), so
 * the leg on the step only pitches forward a little and its hip moves up into the torso.</p>
 */
public final class FootGroundingFeature {

    public static final String KEY_ENABLED = "footgrounding.enabled";
    public static final String KEY_HORSES = "footgrounding.horses";
    public static final String KEY_TRACE = "footgrounding.trace";

    /** Whether the rider being drawn was moved with its horse, so the post event undoes it. */
    private static boolean riderMoved;

    private FootGroundingFeature() {
    }

    public static void register(ConfigRegistry.Section config) {
        config.addBoolean(KEY_ENABLED, "Foot IK (experimental)", true,
                "On", "On uneven ground (steps, slabs) lower the body and raise the leg on the step, so both feet stand.",
                "Off", "Leave the legs to EMF; one foot may hang in the air.")
                .addBoolean(KEY_HORSES, "Foot IK for horses", true,
                        "On", "Horses, donkeys and mules also stand on uneven ground: the body is lowered and pitched, and the rider goes down with it.",
                        "Off", "Only players.")
                .addBoolean(KEY_TRACE, "Foot IK trace (debug)", false,
                        "On", "Log what the feet measure and do every frame they do anything - for debugging only, it floods the log.",
                        "Off", "Log only when the decision changes.");
        // Innermost: after everyone else has moved the rider (a cancelled draw never reaches the
        // lowest listener), and undone first, so the push and pop pair up with no one in between.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FootGroundingFeature::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, FootGroundingFeature::onRenderPlayerPost);
    }

    public static boolean isTrace() {
        return EMFCompatConfig.getBoolean(KEY_TRACE, false);
    }

    public static boolean isHorsesEnabled() {
        return EMFCompatConfig.getBoolean(KEY_HORSES, true);
    }

    /** The rider goes down and pitches with its horse. */
    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        riderMoved = false;
        if (!(event.getEntity().getVehicle() instanceof AbstractHorse horse)) return;
        event.getPoseStack().pushPose();
        riderMoved = HorseFootGrounding.moveRider(horse, event.getEntity(), event.getPoseStack(),
                event.getPartialTick());
        if (!riderMoved) event.getPoseStack().popPose();
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (riderMoved) event.getPoseStack().popPose();
        riderMoved = false;
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}
