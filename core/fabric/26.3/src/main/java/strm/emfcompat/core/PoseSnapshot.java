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
    /**
     * How much of this pose is shown over the resource pack's animation, 0 to 1. Always 1 unless
     * the source hands over its own blend through {@link #blended(float)}.
     */
    public final float weight;
    /**
     * Whether the source blends this pose in and out itself and keeps it continuous. The core then
     * takes {@link #weight} as the fade weight instead of running its own fade in, and never reads
     * a change of the pose as a switch between animations.
     */
    public final boolean selfBlended;

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
        this.weight = 1f;
        this.selfBlended = false;
    }

    private PoseSnapshot(PoseSnapshot from, float weight) {
        this.xRot = from.xRot;
        this.yRot = from.yRot;
        this.zRot = from.zRot;
        this.x = from.x;
        this.y = from.y;
        this.z = from.z;
        this.xScale = from.xScale;
        this.yScale = from.yScale;
        this.zScale = from.zScale;
        this.visible = from.visible;
        this.skipDraw = from.skipDraw;
        this.rotationOnly = from.rotationOnly;
        this.weight = weight;
        this.selfBlended = true;
    }

    /**
     * This pose, marked as blended by its source: shown at {@code weight} over whatever the pack
     * animates the part to.
     *
     * <p>For mods that ease their own animations in and out and hand over between them smoothly,
     * like ParCool. Their pose is the target, the weight is their own blend factor, and the core
     * fades against the pack with it rather than against the vanilla pose the mod blended from.</p>
     */
    public PoseSnapshot blended(float weight) {
        float clamped = Float.isFinite(weight) ? Math.max(0f, Math.min(1f, weight)) : 1f;
        return new PoseSnapshot(this, clamped);
    }

    /**
     * Copies a part's whole transform onto another, without going through a snapshot.
     *
     * <p>For the layers that must sit exactly on the part they belong to — sleeves, trousers, hat,
     * jacket. The restore path does this six times per player per frame, which is the one place
     * where a throwaway snapshot per part was worth removing.</p>
     */
    public static void copy(ModelPart from, ModelPart to) {
        to.xRot = from.xRot;
        to.yRot = from.yRot;
        to.zRot = from.zRot;
        to.x = from.x;
        to.y = from.y;
        to.z = from.z;
        to.xScale = from.xScale;
        to.yScale = from.yScale;
        to.zScale = from.zScale;
        to.visible = from.visible;
        to.skipDraw = from.skipDraw;
    }

    public void apply(ModelPart part) {
        if (this.weight < 1f) {
            applyWeighted(part);
            return;
        }
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
        if (this.weight < 1f) {
            part.xRot = lerpAngle(part.xRot, this.xRot, this.weight);
            part.yRot = lerpAngle(part.yRot, this.yRot, this.weight);
            part.zRot = lerpAngle(part.zRot, this.zRot, this.weight);
            return;
        }
        part.xRot = this.xRot;
        part.yRot = this.yRot;
        part.zRot = this.zRot;
    }

    /** {@link #apply} for a partial weight: the part's current values are what the pose blends over. */
    private void applyWeighted(ModelPart part) {
        applyRotation(part);
        if (this.rotationOnly) {
            return;
        }
        float w = this.weight;
        part.x += (this.x - part.x) * w;
        part.y += (this.y - part.y) * w;
        part.z += (this.z - part.z) * w;
        part.xScale += (this.xScale - part.xScale) * w;
        part.yScale += (this.yScale - part.yScale) * w;
        part.zScale += (this.zScale - part.zScale) * w;
        if (w >= 0.5f) {
            part.visible = this.visible;
            part.skipDraw = this.skipDraw;
        }
    }

    /** The short way around, so a partial pose never swings through the long arc. */
    static float lerpAngle(float from, float to, float t) {
        float delta = (to - from + (float) Math.PI) % ((float) Math.PI * 2f);
        if (delta < 0f) {
            delta += (float) Math.PI * 2f;
        }
        return from + (delta - (float) Math.PI) * t;
    }
}
