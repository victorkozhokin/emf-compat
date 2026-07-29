package strm.emfcompat.bettercombat.compat;

import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.client.animation.AttackAnimationStack;
import net.bettercombat.client.animation.PoseAnimationStack;
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
     * Better Combat's weapon <em>pose</em> controllers — the stance a two-handed weapon holds the
     * player in, separate from any attack. 13 of the 33 stock weapon types define one
     * ({@code "pose": "bettercombat:pose_two_handed_polearm"} on spears, and likewise on tridents,
     * claymores, katanas, halberds, glaives, scythes, hammers, heavy axes, two-handed bows and
     * crossbows, soul knives and twin blades), so this is the common case, not an edge case.
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
        AttackAnimationStack stack = getAttackAnimationStack(player);
        if (stack == null) {
            return false;
        }
        IAnimation animation = stack.base.getAnimation();
        return animation != null && animation.isActive();
    }

    /**
     * Returns {@code true} while Better Combat's <em>attack</em> controller is doing anything at
     * all — including the fade-out that continues after the attack hand has already been cleared.
     *
     * <p>EMF pauses for as long as the PAL manager is active, and Better Combat's controller is one
     * of its layers. {@link #getAttackHand} goes null as soon as Better Combat drops the current
     * attack, which is several frames before the animation finishes fading; during that gap EMF
     * would pause with nothing of ours to restore and the model would snap to vanilla. This is the
     * signal for "the manager is active because of Better Combat", so the pause override can span
     * exactly that window and no more — unlike the whole-manager check, which would also swallow
     * unrelated PAL animations such as Take a Seat's.</p>
     */
    public static boolean isAttackStackAnimating(AbstractClientPlayer player) {
        AttackAnimationStack stack = getAttackAnimationStack(player);
        if (stack == null) {
            return false;
        }
        if (stack.isActive()) {
            return true;
        }
        IAnimation animation = stack.base.getAnimation();
        return animation != null && animation.isActive();
    }

    /**
     * Returns {@code true} while any of Better Combat's weapon-pose controllers is active — i.e.
     * the player is holding a weapon that puts them in a stance, such as a spear.
     *
     * <p>Unlike an attack this lasts for as long as the weapon is held, which is why the pose is
     * captured under its own source and does not drive the first-person vanilla-model override.</p>
     */
    public static boolean isPoseStackActive(AbstractClientPlayer player) {
        for (Field field : POSE_ANIMATION_FIELDS) {
            try {
                if (field.get(player) instanceof PoseAnimationStack stack && stack.isActive()) {
                    return true;
                }
            } catch (IllegalAccessException ignored) {
                // Treated as "not posing"; a single unreadable field must not break the others.
            }
        }
        return false;
    }

    @Nullable
    private static AttackAnimationStack getAttackAnimationStack(AbstractClientPlayer player) {
        if (ATTACK_ANIMATION_FIELD == null) {
            return null;
        }
        try {
            Object value = ATTACK_ANIMATION_FIELD.get(player);
            return value instanceof AttackAnimationStack stack ? stack : null;
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
