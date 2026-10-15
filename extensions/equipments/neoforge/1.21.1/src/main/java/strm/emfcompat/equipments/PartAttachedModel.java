package strm.emfcompat.equipments;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

/**
 * A worn model that was built in the entity's own space instead of hanging off a body part, drawn
 * from that part instead. Valid only where the part at rest coincides with the entity's space -
 * true for the humanoid torso, whose pivot is the model origin.
 *
 * Such a model fakes the wearer's moves itself (a crouch as a fixed tilt); under a pack's animation
 * those fakes are wrong, so {@code resetPose} puts the model back at rest before it is drawn.
 */
public final class PartAttachedModel extends Model {

    private final Model inner;
    private final ModelPart part;
    private final Runnable resetPose;

    public PartAttachedModel(Model inner, ModelPart part, Runnable resetPose) {
        super(inner::renderType);
        this.inner = inner;
        this.part = part;
        this.resetPose = resetPose;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        resetPose.run();
        poseStack.pushPose();
        part.translateAndRotate(poseStack);
        inner.renderToBuffer(poseStack, buffer, light, overlay, color);
        poseStack.popPose();
    }
}
