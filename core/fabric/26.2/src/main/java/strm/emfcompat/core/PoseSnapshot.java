package strm.emfcompat.core;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Immutable snapshot of a {@link ModelPart} transformation.
 */
public class PoseSnapshot {
    public final float xRot, yRot, zRot;
    public final float x, y, z;
    public final float xScale, yScale, zScale;
    public final boolean visible;
    public final boolean skipDraw;
    public final boolean rotationOnly;

    public PoseSnapshot(ModelPart part) {
        this(part, false);
    }

    public PoseSnapshot(ModelPart part, boolean rotationOnly) {
        this.xRot = part.xRot;
        this.yRot = part.yRot;
        this.zRot = part.zRot;
        this.x = part.x;
        this.y = part.y;
        this.z = part.z;
        this.xScale = part.xScale;
        this.yScale = part.yScale;
        this.zScale = part.zScale;
        this.visible = part.visible;
        this.skipDraw = part.skipDraw;
        this.rotationOnly = rotationOnly;
    }

    public void apply(ModelPart part) {
        part.xRot = this.xRot;
        part.yRot = this.yRot;
        part.zRot = this.zRot;
        if (this.rotationOnly) {
            return;
        }
        part.x = this.x;
        part.y = this.y;
        part.z = this.z;
        part.xScale = this.xScale;
        part.yScale = this.yScale;
        part.zScale = this.zScale;
        part.visible = this.visible;
        part.skipDraw = this.skipDraw;
    }

    public void applyRotation(ModelPart part) {
        part.xRot = this.xRot;
        part.yRot = this.yRot;
        part.zRot = this.zRot;
    }
}
