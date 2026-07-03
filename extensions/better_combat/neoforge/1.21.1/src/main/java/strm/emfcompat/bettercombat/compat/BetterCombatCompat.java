package strm.emfcompat.bettercombat.compat;

import dev.kosmx.playerAnim.api.layered.IAnimation;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.client.animation.AttackAnimationSubStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Helpers for detecting Better Combat attack state on a client player.
 */
public final class BetterCombatCompat {

    private static final Field ATTACK_ANIMATION_FIELD;

    static {
        Field field;
        try {
            field = AbstractClientPlayer.class.getDeclaredField("attackAnimation");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            field = null;
        }
        ATTACK_ANIMATION_FIELD = field;
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
