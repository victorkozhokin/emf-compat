package strm.emfcompat.animationadditions.interaction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * Sitting on a seat - Create's cushion, a chair of any mod: a rider of something that is neither
 * alive nor a boat nor a minecart. The hands are free there and use what is in front of them - a
 * steering wheel, a lever, a panel - as standing; the legs and the reaching pose of the whole body
 * stay out of it.
 */
public final class Seated {

    private Seated() {
    }

    public static boolean seated(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle != null && !(vehicle instanceof LivingEntity) && !(vehicle instanceof Boat) && !(vehicle instanceof AbstractMinecart);
    }

    /** Whether the hands may use blocks as far as footing goes: on the ground, or on a seat. */
    public static boolean steady(Player player) {
        return player.isPassenger() ? seated(player) : player.onGround();
    }
}
