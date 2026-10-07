package strm.touchnmotion.ride;

import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.vehicle.Boat;
import strm.emfcompat.core.ConfigRegistry;

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

    /** Boat model pixels: the chest's side, and where its face towards the rower is - in a boat it ends at the bow's board, on a raft at the deck's end. */
    private static final float CHEST = 12f, NEAR = 4.5f, RAFT_NEAR = 2.5f;

    private ChestInBow() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addChild(BoatRide.KEY_ENABLED, KEY_ENABLED, "Chest in the bow", true,
                "On", "A boat's chest is drawn in the bow, facing the rower, who sits a little further aft with the paddles - clear of the stroke's lean back. A server with this addon seats the rower there for everyone.",
                "Off", "The chest stays in the stern, as the game has it - unless the server seats the rower aft.");
    }

    /** After the model has been posed for {@code boat}, before it is drawn. The model is one for every boat of its wood, so it is put back as well as moved. */
    public static void place(ListModel<Boat> model, Boat boat) {
        if (!(model instanceof net.minecraft.client.model.ChestBoatModel) && !(model instanceof net.minecraft.client.model.ChestRaftModel)) return;
        List<ModelPart> parts = new ArrayList<>();
        model.parts().forEach(parts::add);
        int n = parts.size();
        // The model's own order: ..., the two paddles, the chest's bottom, lid and lock.
        if (n < 5) return;
        boolean moved = BoatSeats.chestInBow(boat);
        float aft = moved ? (float) (BoatSeats.aft(boat) * 16) : 0f;
        for (int i = n - 5; i < n - 3; i++) {
            ModelPart paddle = parts.get(i);
            paddle.x = paddle.getInitialPose().x - aft;
        }
        // The bottom is a box from its corner, a quarter turn round: its middle is half a side astern and half a side across.
        PartPose bottom = parts.get(n - 3).getInitialPose();
        float middleX = bottom.x - CHEST / 2f, middleZ = bottom.z + CHEST / 2f;
        float near = (boat.getVariant() == net.minecraft.world.entity.vehicle.Boat.Type.BAMBOO) ? RAFT_NEAR : NEAR;
        for (int i = n - 3; i < n; i++) {
            ModelPart part = parts.get(i);
            PartPose pose = part.getInitialPose();
            if (!moved) {
                part.x = pose.x;
                part.z = pose.z;
                part.yRot = pose.yRot;
                continue;
            }
            // Half a turn about the chest's middle, so the lock is towards the rower, and forward to the bow.
            part.x = 2f * middleX - pose.x + near + CHEST / 2f - middleX;
            part.z = 2f * middleZ - pose.z;
            part.yRot = pose.yRot + (float) Math.PI;
        }
    }
}
