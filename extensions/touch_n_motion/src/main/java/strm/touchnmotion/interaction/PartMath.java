package strm.touchnmotion.interaction;

import org.joml.Matrix4f;

/** A model part's pose as the transform the game draws it by: no game classes, so it is tested without the game. */
public final class PartMath {
    private PartMath() {
    }

    /**
     * What a part does to everything under it ({@code ModelPart.translateAndRotate}): moved by its
     * pivot (pixels, a sixteenth of the model's unit), turned about Z, then Y, then X, scaled.
     */
    public static Matrix4f transform(float x, float y, float z, float xRot, float yRot, float zRot,
                                     float xScale, float yScale, float zScale) {
        return new Matrix4f().translation(x / 16f, y / 16f, z / 16f).rotateZYX(zRot, yRot, xRot).scale(xScale, yScale, zScale);
    }
}
