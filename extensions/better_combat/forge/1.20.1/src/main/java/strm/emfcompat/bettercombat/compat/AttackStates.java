package strm.emfcompat.bettercombat.compat;

import strm.emfcompat.bettercombat.EMFCompatBetterCombatMod;
import strm.emfcompat.core.PoseSnapshot;

import java.util.UUID;

/**
 * What an attack's capture tells the rest: how its arm pose comes in, and what the player is doing
 * for other mods (Touch'n Motion) through the core's shared states.
 */
public final class AttackStates {
    /** Arms held by an attack; Touch'n Motion keeps the swing's arms through its own lean and leaves them alone. */
    public static final String ATTACKING = "attacking";
    /** A weapon stance held: a background pose, which a deliberate gesture of Touch'n Motion may take an arm from. */
    public static final String WEAPON_STANCE = "weapon_stance";

    /** Shared states need EMF Compat Core 2.4.0; with an older core they are simply not told. */
    private static boolean noStates;

    private AttackStates() {
    }

    /**
     * An attack's arm or leg pose as it is handed to the core: on its full weight from the first
     * frame - the arm keeps time with the weapon Better Combat swings on its own - unless "Smooth
     * attack start" is on. Either way the core eases it back out when the attack ends.
     */
    public static PoseSnapshot attack(PoseSnapshot pose) {
        return EMFCompatBetterCombatMod.isSmoothAttackStart() ? pose : pose.blended(1f);
    }

    public static void attacking(UUID uuid, boolean on) {
        state(uuid, ATTACKING, on, true);
    }

    public static void stance(UUID uuid, boolean on) {
        state(uuid, WEAPON_STANCE, on, false);
    }

    private static void state(UUID uuid, String name, boolean on, boolean takesArms) {
        if (noStates || uuid == null) return;
        try {
            if (!on) strm.emfcompat.core.BodyStates.clear(uuid, name);
            else if (takesArms) strm.emfcompat.core.BodyStates.set(uuid, name, 1f, strm.emfcompat.core.BodyStates.Part.ARMS);
            else strm.emfcompat.core.BodyStates.set(uuid, name, 1f);
        } catch (LinkageError olderCore) {
            noStates = true;
        }
    }
}
