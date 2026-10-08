package strm.touchnmotion.ride;

//? if <1.21.11
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.vehicle.Boat;
import strm.emfcompat.core.ConfigRegistry;
import strm.touchnmotion.platform.Platform;

import java.util.ArrayList;
import java.util.List;

/**
 * A boat with a chest: the game has the chest in the stern, right where the one who rows leans
 * back with each stroke. So the chest is drawn in the bow instead, turned to face the rower, and
 * the rower's place - seat and paddles both - is a little further aft, the legs ending at the
 * chest's front ({@link BoatSeats#chestInBow}).
 *
 * <p>No new model: the chest's own three parts and the two paddles are the boat model's, only
 * placed elsewhere each time the boat is drawn, so a pack's shapes and textures stay as they are.</p>
 */
public final class ChestInBow {

    public static final String KEY_ENABLED = "ride.boat.chest";

    /** Boat model pixels: the chest's side, and where its face towards the rower is - in a boat its far side is against the inside of the bow's board (the board is 14 to 16 px out), almost at the rower's feet; on a raft it ends at the deck's end. */
    private static final float CHEST = 12f, NEAR = 2.0f, RAFT_NEAR = 2.5f;

    private ChestInBow() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addChild(BoatRide.KEY_ENABLED, KEY_ENABLED, "Chest in the bow", true,
                "On", "A boat's chest is drawn in the bow, facing the rower, who sits a little further aft with the paddles - clear of the stroke's lean back. A server with this addon seats the rower there for everyone.",
                "Off", "The chest stays in the stern, as the game has it - unless the server seats the rower aft.");
    }

    //? if <1.21.11 {
    /** After the model has been posed for {@code boat}, before it is drawn. The model is one for every boat of its wood, so it is put back as well as moved. */
    public static void place(ListModel<Boat> model, Boat boat) {
        boolean cannon = CannonInBow.is(boat);
        if (!cannon && !(model instanceof net.minecraft.client.model.ChestBoatModel) && !(model instanceof net.minecraft.client.model.ChestRaftModel)) return;
        List<ModelPart> parts = new ArrayList<>();
        model.parts().forEach(parts::add);
        int n = parts.size();
        if (cannon) {
            // A boat with a cannon is a plain boat's model, the cannon drawn apart: the paddles are its last two parts.
            if (n < 2) return;
            float aft = CannonInBow.moved(boat) ? (float) (BoatSeats.aft(boat) * 16) : 0f;
            for (ModelPart paddle : parts.subList(n - 2, n)) paddle.x = strm.touchnmotion.platform.Platform.x(paddle.getInitialPose()) - aft;
            return;
        }
        // The model's own order: ..., the two paddles, the chest's bottom, lid and lock.
        if (n < 5) return;
        place(parts.subList(n - 5, n - 3), parts.subList(n - 3, n), BoatSeats.chestInBow(boat), strm.touchnmotion.platform.ServerSide.isRaft(boat));
    }
    //?}

    /**
     * Places the two paddles and the chest's three parts - bottom, lid, lock - of a model just posed:
     * the chest in the bow and the paddles aft when {@code moved}, where the model has them when not.
     */
    public static void place(List<ModelPart> paddles, List<ModelPart> chest, boolean moved, boolean raft) {
        float aft = moved ? (float) (BoatSeats.aft(raft) * 16) : 0f;
        for (ModelPart paddle : paddles) {
            paddle.x = Platform.x(paddle.getInitialPose()) - aft;
        }
        // The bottom is a box from its corner, a quarter turn round: its middle is half a side astern and half a side across.
        PartPose bottom = chest.get(0).getInitialPose();
        float middleX = Platform.x(bottom) - CHEST / 2f, middleZ = Platform.z(bottom) + CHEST / 2f;
        float near = raft ? RAFT_NEAR : NEAR;
        for (ModelPart part : chest) {
            PartPose pose = part.getInitialPose();
            if (!moved) {
                part.x = Platform.x(pose);
                part.z = Platform.z(pose);
                part.yRot = Platform.yRot(pose);
                continue;
            }
            // Half a turn about the chest's middle, so the lock is towards the rower, and forward to the bow.
            part.x = 2f * middleX - Platform.x(pose) + near + CHEST / 2f - middleX;
            part.z = 2f * middleZ - Platform.z(pose);
            part.yRot = Platform.yRot(pose) + (float) Math.PI;
        }
    }
}
