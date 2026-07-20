package strm.emfcompat.bettercombat.mixin;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Map;

/**
 * Replaces Better Combat's slam animations with toned-down versions.
 *
 * <p>The original animations tilt and displace the torso very aggressively. That looks fine in
 * vanilla, but fights against EMF player animations. This mixin redirects {@code one_handed_slam}
 * and {@code one_handed_slam_instant} to custom versions with reduced torso rotation/translation.</p>
 */
@Mixin(PlayerAnimResources.class)
public class BetterCombatOneHandedSlamMixin {

    @Unique
    private static final Map<Identifier, Identifier> REDIRECTS = Map.of(
            Identifier.fromNamespaceAndPath("bettercombat", "one_handed_slam"),
            Identifier.fromNamespaceAndPath("bettercombat", "one_handed_slam_fixed"),
            Identifier.fromNamespaceAndPath("bettercombat", "one_handed_slam_instant"),
            Identifier.fromNamespaceAndPath("bettercombat", "one_handed_slam_instant_fixed")
    );

    @ModifyVariable(
            method = "getAnimation(Lnet/minecraft/resources/Identifier;)Lcom/zigythebird/playeranimcore/animation/Animation;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Identifier emfcompat$redirectOneHandedSlam(Identifier id) {
        Identifier redirect = REDIRECTS.get(id);
        return redirect != null ? redirect : id;
    }
}
