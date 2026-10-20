package strm.touchnmotion.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

/** Only the local player's held wheel/throttle, or a train they drive, owns facing; other Simulated holds do not. */
public final class ShoulderCamera {
    private ShoulderCamera() {}
    public static boolean atControls(LivingEntity entity) {
        var player = Minecraft.getInstance().player;
        if (player == null || entity != player) return false;
        return strm.touchnmotion.stages.BodyStages.atControls(player);
    }
    /** A visible seated cockpit owns body facing even between individual held controls. */
    public static boolean cockpitCamera() {
        var player = Minecraft.getInstance().player;
        return player != null && (atControls(player)
                || strm.touchnmotion.stages.BodyStages.seatBody(player.getUUID()));
    }
    /** Camera orientation is the rendered world view; Sable's entity view uses a tick-space pose. */
    public static net.minecraft.world.phys.Vec3 worldView(net.minecraft.client.player.AbstractClientPlayer player) {
        var mc = Minecraft.getInstance();
        if (player != mc.player || mc.getCameraEntity() != player) return player.getViewVector(strm.touchnmotion.platform.Platform.partialTick(false));
        var view = strm.touchnmotion.platform.Platform.cameraForward();
        return mc.options.getCameraType().isMirrored() ? view.reverse() : view;
    }
}
