package strm.emfcompat.takeaseat.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.PoseSnapshot;
import strm.emfcompat.core.SavedPoses;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class SittingArmorModel<S extends HumanoidRenderState> extends HumanoidModel<S> {
    private static final String TAKEASEAT$POSE_SOURCE = "takeaseat";
    private final UUID uuid;

    public SittingArmorModel(ModelPart root, Function<Identifier, RenderType> layerFactory, UUID uuid) {
        super(root, layerFactory);
        this.uuid = uuid;
    }

    @Override
    public void setupAnim(S state) {
        super.setupAnim(state);

        SavedPoses poses = PoseManager.getSavedPoses(uuid, TAKEASEAT$POSE_SOURCE);
        if (poses == null || poses.parts() == null) return;

        Map<String, PoseSnapshot> parts = poses.parts();
        applyRotation(parts.get("head"), this.head);
        applyRotation(parts.get("hat"), this.hat);
        applyRotation(parts.get("body"), this.body);
        applyRotation(parts.get("right_arm"), this.rightArm);
        applyRotation(parts.get("left_arm"), this.leftArm);
        applyRotation(parts.get("right_leg"), this.rightLeg);
        applyRotation(parts.get("left_leg"), this.leftLeg);
    }

    private static void applyRotation(PoseSnapshot snap, ModelPart part) {
        if (snap == null || part == null) return;
        snap.apply(part);
    }
}
