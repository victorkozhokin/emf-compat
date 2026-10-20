package strm.touchnmotion.compat;

import strm.emfcompat.core.BodyStates;
import strm.touchnmotion.interaction.Effector;
import strm.touchnmotion.interaction.InteractionRuntime;
import strm.touchnmotion.torso.TorsoLean;
import strm.touchnmotion.wallhand.WallSqueeze;

import java.util.UUID;

/**
 * What this mod is doing with a player, told as shared states ({@link BodyStates}) - and so as EMF
 * variables, {@code emfc_tnm_<name>}, for resource packs and for other mods to give way by. They
 * take no parts: they are what this mod does, not what it asks others to leave alone.
 *
 * <ul>
 *   <li>{@code tnm_right_arm}, {@code tnm_left_arm}, {@code tnm_head}, {@code tnm_body}: how much
 *   of each this mod holds, 0 to 1;</li>
 *   <li>{@code tnm_reach}, {@code tnm_hold}, {@code tnm_lean}, {@code tnm_gesture},
 *   {@code tnm_work}, {@code tnm_ride}: what it is doing, as the weight of what it holds for it;</li>
 *   <li>{@code tnm_catwalk}: walking or standing on a fence, a wall or a gate, 0 to 1;</li>
 *   <li>{@code tnm_lean_x}, {@code tnm_lean_z}: the torso's lean it adds, radians.</li>
 * </ul>
 */
public final class PackStates {
    private static final String[] GROUPS = {"tnm_reach", "tnm_hold", "tnm_lean", "tnm_gesture", "tnm_work", "tnm_ride"};

    private PackStates() {
    }

    /** Once a frame for a player, after this mod has posed it. */
    public static void publish(UUID uuid) {
        float[] groups = new float[GROUPS.length];
        for (Effector effector : Effector.values()) {
            float weight = InteractionRuntime.weight(uuid, effector);
            BodyStates.set(uuid, "tnm_" + effector.part, weight);
            String owner = InteractionRuntime.owner(uuid, effector);
            var provider = InteractionRuntime.provider(owner);
            String group = provider == null ? null : provider.packState();
            if (group == null) continue;
            for (int i = 0; i < GROUPS.length; i++) if (GROUPS[i].equals(group)) groups[i] = Math.max(groups[i], weight);
        }
        if (WallSqueeze.isActive(uuid)) groups[2] = Math.max(groups[2], 1f);
        for (int i = 0; i < GROUPS.length; i++) BodyStates.set(uuid, GROUPS[i], groups[i]);
        BodyStates.set(uuid, "tnm_body", InteractionRuntime.bodyWeight(uuid));
        BodyStates.set(uuid, "tnm_catwalk", strm.touchnmotion.footgrounding.compat.FootGrounding.catwalk(uuid));
        float[] lean = TorsoLean.lean(uuid);
        BodyStates.set(uuid, "tnm_lean_x", lean == null ? 0f : lean[0]);
        BodyStates.set(uuid, "tnm_lean_z", lean == null ? 0f : lean[1]);
    }
}
