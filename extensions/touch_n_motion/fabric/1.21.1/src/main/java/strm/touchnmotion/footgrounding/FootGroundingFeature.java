package strm.touchnmotion.footgrounding;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.touchnmotion.footgrounding.compat.FootGrounding;
import strm.touchnmotion.footgrounding.compat.HorseFootGrounding;

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
    public static final String KEY_TERRAIN = "footgrounding.terrain_balance";

    /** Whether the rider being drawn was moved with its horse, so the post event undoes it. */
    private static boolean riderMoved;

    private FootGroundingFeature() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Foot IK (experimental)", true,
                "On", "On uneven ground (steps, slabs) lower the body and raise the leg on the step, so both feet stand.",
                "Off", "Leave the legs to EMF; one foot may hang in the air.")
                .addChild(KEY_ENABLED, KEY_TERRAIN, "Balance on narrow supports and slopes", true,
                        "On", "Bring the feet onto narrow collision surfaces and balance with the torso and free arms.",
                        "Off", "Only correct the height of the feet.")
                .addChild(KEY_ENABLED, KEY_HORSES, "Foot IK for horses", true,
                        "On", "Horses, donkeys and mules also stand on uneven ground: the body is lowered and pitched, and the rider goes down with it.",
                        "Off", "Only players.");
    }

    public static boolean isTerrainEnabled() {
        return EMFCompatConfig.getBoolean(KEY_TERRAIN, true);
    }

    public static boolean isHorsesEnabled() {
        return EMFCompatConfig.getBoolean(KEY_HORSES, true);
    }

    /**
     * The rider goes down and pitches with its horse. Innermost of what moves the rider: called
     * last before the draw and undone first after it ({@code mixin/PlayerRenderEventsMixin}).
     */
    public static void onRenderPlayerPre(net.minecraft.world.entity.player.Player player, com.mojang.blaze3d.vertex.PoseStack stack, float partialTick) {
        riderMoved = false;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return;
        stack.pushPose();
        riderMoved = HorseFootGrounding.moveRider(horse, player, stack, partialTick);
        if (!riderMoved) stack.popPose();
    }

    public static void onRenderPlayerPost(com.mojang.blaze3d.vertex.PoseStack stack) {
        if (riderMoved) stack.popPose();
        riderMoved = false;
    }

    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }
}
