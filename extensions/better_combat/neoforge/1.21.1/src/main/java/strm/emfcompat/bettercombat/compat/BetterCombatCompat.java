package strm.emfcompat.bettercombat.compat;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.client.animation.AttackAnimationSubStack;
import net.bettercombat.client.animation.PoseSubStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Helpers for detecting Better Combat attack state on a client player.
 */
public final class BetterCombatCompat {

    private static final Field ATTACK_ANIMATION_FIELD;

    /**
     * Better Combat's weapon <em>pose</em> stacks — the stance a two-handed weapon holds the player
     * in, separate from any attack. 13 of the 33 stock weapon types define one
     * ({@code "pose": "bettercombat:pose_two_handed_polearm"} on spears, likewise on tridents,
     * claymores, katanas, halberds, glaives, scythes, hammers, heavy axes, two-handed bows and
     * crossbows, soul knives and twin blades).
     */
    private static final Field[] POSE_ANIMATION_FIELDS;

    static {
        ATTACK_ANIMATION_FIELD = lookupField("attackAnimation");
        POSE_ANIMATION_FIELDS = java.util.Arrays.stream(new String[]{
                        "mainHandBodyPose", "mainHandItemPose", "offHandBodyPose", "offHandItemPose"})
                .map(BetterCombatCompat::lookupField)
                .filter(java.util.Objects::nonNull)
                .toArray(Field[]::new);
    }

    @Nullable
    private static Field lookupField(String name) {
        try {
            Field field = AbstractClientPlayer.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /**
     * Returns {@code true} while any of Better Combat's weapon-pose stacks is active — i.e. the
     * player is holding a weapon that puts them in a stance, such as a spear.
     *
     * <p>Unlike on Fabric this is purely cosmetic here: Better Combat drives these through kosmx
     * Player Animator, which EMF does not pause for (its built-in pause covers zigythebird PAL
     * only), so without capturing the stance EMF simply animates over it and the stance is lost —
     * nothing freezes. That is why the capture is opt-in on this loader.</p>
     */
    public static boolean isPoseStackActive(AbstractClientPlayer player) {
        for (Field field : POSE_ANIMATION_FIELDS) {
            try {
                if (field.get(player) instanceof PoseSubStack stack && stack.base.isActive()) {
                    return true;
                }
            } catch (IllegalAccessException ignored) {
                // Treated as "not posing"; a single unreadable field must not break the others.
            }
        }
        return false;
    }

    private BetterCombatCompat() {
    }

    /**
     * Returns {@code true} if the player is currently executing a Better Combat attack
     * (upswing + swing), not merely holding a weapon that Better Combat recognises.
     */
    public static boolean isAttackActive(AbstractClientPlayer player) {
        return getAttackHand(player) != null;
    }

    /**
     * Returns the active {@link AttackHand}, or {@code null} if the player is not currently
     * in the middle of an attack.
     */
    @Nullable
    public static AttackHand getAttackHand(AbstractClientPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();

        // For the local player Better Combat tracks the ongoing swing on the client instance.
        // isWeaponSwingInProgress() only covers the swing itself; the underlying animation layer
        // is also active during upswing/recovery, so check it too to keep arms captured for the
        // whole attack sequence.
        if (player == minecraft.player) {
            MinecraftClient_BetterCombat client = (MinecraftClient_BetterCombat) minecraft;
            if (client.isWeaponSwingInProgress() || isAttackAnimationActive(player)) {
                return client.getCurrentAttackHand();
            }
            return null;
        }

        // For other players, EntityPlayer_BetterCombat#getCurrentAttack is non-null whenever the
        // held item has Better Combat attributes, even while idle. Check the actual attack
        // animation layer to decide whether an attack is in progress.
        if (!(player instanceof EntityPlayer_BetterCombat bcPlayer)) {
            return null;
        }
        if (!isAttackAnimationActive(player)) {
            return null;
        }
        return bcPlayer.getCurrentAttack();
    }

    private static boolean isAttackAnimationActive(AbstractClientPlayer player) {
        if (ATTACK_ANIMATION_FIELD == null) {
            return false;
        }
        try {
            Object value = ATTACK_ANIMATION_FIELD.get(player);
            if (!(value instanceof AttackAnimationSubStack subStack)) {
                return false;
            }
            IAnimation animation = subStack.base.getAnimation();
            return animation != null && animation.isActive();
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
