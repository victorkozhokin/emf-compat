package strm.touchnmotion.ride;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ConfigRegistry;
import strm.touchnmotion.interaction.EntityStates;
import strm.touchnmotion.interaction.Smoothing;

import java.util.UUID;
import java.util.function.Function;

/**
 * Riding any other mount - a mod's animal, a Pokémon, a dragon: the legs lie along its sides by how wide it is
 * ({@link MountShapes}, {@link MountMath}) instead of the game's riding pose, which pushes them into a broad
 * body. Horses, donkeys, mules and camels are {@link HorseRide}'s; boats and carts are their own. Off by default:
 * a mount that poses its rider itself (standing on its back, sat sideways) is not told apart.
 */
public final class MountRide {

    public static final String KEY_ENABLED = "ride.anyMount";
    /** Seconds the legs take to settle on mounting, and to let go on dismounting. */
    private static final double SETTLE_SECONDS = 0.2;

    private static final class State {
        float weight;
        float[] legs = {MountMath.HIP, 0f, 0f};
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private MountRide() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Legs along other mounts", false,
                "On", "On a mount that is not a horse - a mod's animal, a Pokémon, a dragon - the legs lie along its sides by how wide its body is.",
                "Off", "The game's riding pose on mounts other than horses.");
    }

    /** The mount this player rides that the legs are fitted to; {@code null} for none, a horse, a boat or a cart. */
    static LivingEntity mount(Player player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof LivingEntity living) || vehicle instanceof AbstractHorse) return null;
        return living;
    }

    /** The legs along the mount's sides; with the legs, before anything that works from where they are. */
    public static void legs(UUID uuid, Function<String, ModelPart> parts) {
        var level = Minecraft.getInstance().level;
        Player player = level == null ? null : level.getPlayerByUUID(uuid);
        if (player == null) return;
        long now = System.nanoTime();
        EntityStates.Entry<State> entry = STATES.seen(uuid, now);
        State state = entry.value;
        double dt = EntityStates.due(entry, now);
        LivingEntity mount = EMFCompatConfig.getBoolean(KEY_ENABLED, false) ? mount(player) : null;
        if (dt >= 0) {
            if (mount != null) state.legs = MountMath.legs(MountShapes.of(mount).riderHalfWidth());
            state.weight += ((mount != null ? 1f : 0f) - state.weight) * Smoothing.follow(dt, SETTLE_SECONDS);
        }
        if (state.weight < 1e-3f || !player.isPassenger()) return;
        ModelPart r = parts.apply("right_leg"), l = parts.apply("left_leg");
        if (r == null || l == null) return;
        float w = state.weight, hip = state.legs[0], roll = state.legs[1], forward = state.legs[2];
        r.x += (-hip - r.x) * w;
        l.x += (hip - l.x) * w;
        r.xRot += (forward - r.xRot) * w;
        l.xRot += (forward - l.xRot) * w;
        r.yRot += (0f - r.yRot) * w;
        l.yRot += (0f - l.yRot) * w;
        r.zRot += (roll - r.zRot) * w;
        l.zRot += (-roll - l.zRot) * w;
    }
}
