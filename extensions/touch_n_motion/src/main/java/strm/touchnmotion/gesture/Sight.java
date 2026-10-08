package strm.touchnmotion.gesture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What a player has under the crosshair. For this player the game knows; for another it is worked
 * out from where their eyes are and which way they look, a few times a second - the server sends
 * their look once a tick, so it is a tick behind and good to a block's corner, which is enough to
 * tell that a hand should be held out.
 */
final class Sight {
    private static final long EVERY_NANOS = 200_000_000L;

    private record Seen(long at, HitResult hit) {
    }

    private static final Map<AbstractClientPlayer, Seen> SEEN = new WeakHashMap<>();

    private Sight() {
    }

    static HitResult of(AbstractClientPlayer player, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return mc.hitResult;
        // Their own game's word, when the server passes it on, is better than any ray of ours.
        if (strm.touchnmotion.net.Inputs.told(player)) return strm.touchnmotion.net.Inputs.sight(player, 4.5, 1f);
        Seen seen = SEEN.get(player);
        if (seen != null && now - seen.at < EVERY_NANOS) return seen.hit;
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        double blocks = strm.touchnmotion.platform.Platform.blockReach(player), entities = strm.touchnmotion.platform.Platform.entityReach(player);
        HitResult hit = player.level().clip(new ClipContext(eye, eye.add(look.scale(blocks)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        // An entity nearer than the block behind it is what is looked at.
        double limit = hit.getType() == HitResult.Type.MISS ? entities * entities : Math.min(entities * entities, hit.getLocation().distanceToSqr(eye));
        Vec3 reach = look.scale(entities);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, eye.add(reach),
                player.getBoundingBox().expandTowards(reach).inflate(1), e -> !e.isSpectator() && e.isPickable(), limit);
        if (entity != null) hit = entity;
        SEEN.put(player, new Seen(now, hit));
        return hit;
    }
}
