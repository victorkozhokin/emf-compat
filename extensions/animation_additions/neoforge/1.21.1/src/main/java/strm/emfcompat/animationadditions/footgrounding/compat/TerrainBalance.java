package strm.emfcompat.animationadditions.footgrounding.compat;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.interaction.Smoothing;
import strm.emfcompat.animationadditions.interaction.SubLevels;
import strm.emfcompat.animationadditions.wallhand.WallSqueeze;
import strm.emfcompat.core.PoseManager;
import strm.emfcompat.core.ik.IKFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Ground contact only: separate from clearance at shoulder height (WallSqueeze).
 * Straight FA+Player legs keep their animation; only their pivots and the balance pose change. */
final class TerrainBalance {
    record Floor(Vec3 position, Vec3 normal, double supportY) {}
    private static final float SPACING = 1.5f;
    private static final SupportSurface EMPTY = new SupportSurface(List.of(), SPACING);
    private SupportSurface surface = EMPTY;
    private long sampledAt;
    private Vec3 sampledPosition;
    private float sampledYaw;
    private float pitchTarget, rollTarget;
    private float narrow, pitch, roll;
    private final float[] right = new float[2], left = new float[2];
    private boolean armsFree;
    private boolean raisedCollision;
    private float contactDrop;

    void solve(AbstractClientPlayer player, IKFrame frame, float[] r, float[] l, double dt) {
        long now = System.nanoTime();
        Vec3 centre = frame.jointWorld(new Vector3f(0, 24, 0));
        // Expensive terrain scan at 20 Hz, contact correction every solve. Motion and turns
        // invalidate earlier, so stepping off an edge never waits for the next scan.
        if (sampledPosition == null || sampledPosition.distanceToSqr(centre) > 0.0025
                || Math.abs(player.yBodyRot - sampledYaw) > 4 || now - sampledAt > 50_000_000L) {
            sampledAt = now; sampledPosition = centre; sampledYaw = player.yBodyRot;
            List<SubLevels.Space> spaces = SubLevels.around(player.level(),
                    new AABB(centre, centre).inflate(1));
            List<SupportSurface.Point> points = new ArrayList<>();
            List<BalanceMath.Sample> heights = new ArrayList<>();
            Vec3 normal = Vec3.ZERO;
            raisedCollision = false;
            contactDrop = 0;
            // Near a fence edge the hitbox can still overlap its support while the model
            // centre is outside it. The small scan then sees only one edge and mistakes
            // the fence for broad floor. Expand only when that scan cannot identify a beam.
            for (int radius : new int[]{4, 8}) {
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                    if (radius == 8 && Math.abs(x) <= 4 && Math.abs(z) <= 4) continue;
                    Vec3 foot = frame.jointWorld(new Vector3f(x * SPACING, 24, z * SPACING));
                    Floor hit = floor(player, foot, spaces);
                    if (hit != null && Math.abs(hit.supportY - player.getY()) <= 0.6) {
                        float height = frame.relativeToJoint(hit.position, new Vector3f(0, 24, 0)).y;
                        heights.add(new BalanceMath.Sample(x * SPACING, z * SPACING, height));
                    }
                    // Only the surface carrying the hitbox, never a distant floor below a fence
                    // or a wall above it. Slabs/stairs still use the existing vertical foot solver.
                    if (hit == null || Math.abs(hit.supportY - player.getY()) > 0.15) continue;
                    points.add(new SupportSurface.Point(x * SPACING, z * SPACING));
                    raisedCollision |= hit.supportY - hit.position.y > 0.2;
                    contactDrop = Math.max(contactDrop, (float)((hit.supportY - hit.position.y) * 16 / 0.9375));
                    normal = normal.add(hit.normal);
                }
                surface = new SupportSurface(points, SPACING, radius * SPACING);
                if (surface.narrow > 0 || radius == 4 && points.size() == 81) break;
            }
            pitchTarget = rollTarget = 0;
            if (!points.isEmpty()) {
                Vec3 n = normal.normalize();
                Vector3f model = frame.relativeToJoint(centre.add(n), new Vector3f(0, 24, 0));
                float[] tilt = BalanceMath.slope(model.x, model.y, model.z);
                if (Math.abs(model.x) + Math.abs(model.z) < 0.01f) {
                    // Stair/slab ramps: the tread normals alone are all vertical.
                    tilt = BalanceMath.surfaceSlope(heights);
                }
                pitchTarget = tilt[0]; rollTarget = tilt[1];
            }
        }
        float k = Smoothing.snapFirst(dt, 0.12);
        narrow += (surface.narrow - narrow) * k;
        pitch += (pitchTarget - pitch) * k; roll += (rollTarget - roll) * k;
        float idle = SupportSurface.clamp(1 - player.walkAnimation.speed() / 0.25f, 0, 1);
        shift(balanced(r), -1.9f, right, dt, idle); shift(balanced(l), 1.9f, left, dt, idle);
        armsFree = !player.swinging && !player.isUsingItem()
                && !PoseManager.hasArmPoseExcept(player.getUUID(), "");
    }

    void reset() {
        surface = EMPTY; sampledPosition = null; sampledAt = 0;
        pitchTarget = rollTarget = pitch = roll = narrow = 0;
        right[0] = right[1] = left[0] = left[1] = 0;
        armsFree = false;
        raisedCollision = false;
        contactDrop = 0;
    }

    private void shift(float[] pose, float hip, float[] shift, double dt, float idle) {
        Vector3f sole = new Vector3f(0, 12, 0);
        if (pose != null) new Quaternionf().rotationZYX(pose[2], pose[1], pose[0]).transform(sole);
        float x = (pose == null ? hip : pose[3]) + sole.x;
        float z = (pose == null ? 0 : pose[5]) + sole.z;
        SupportSurface.Placement target = surface.stance(x, z, Math.signum(hip), idle);
        float k = Smoothing.snapFirst(dt, 0.06);
        shift[0] += (target.x() - x - shift[0]) * k;
        shift[1] += (target.z() - z - shift[1]) * k;
    }

    Vector3f hip(Vector3f hip, boolean r) {
        float[] shift = r ? right : left;
        return new Vector3f(hip).add(shift[0], 0, shift[1]);
    }

    float[] pose(float[] pose, boolean r) {
        if (pose == null) return null;
        float[] copy = balanced(pose), shift = r ? right : left;
        copy[3] += shift[0]; copy[5] += shift[1];
        return copy;
    }

    private float[] balanced(float[] pose) {
        if (pose == null) return null;
        float[] copy = pose.clone();
        copy[0] = BalanceMath.balancePitch(copy[0], SupportSurface.clamp(narrow * 3, 0, 1));
        return copy;
    }

    float[] hint(float support) {
        // Phase comes from the pack's actual stance foot, not an independent sine wave.
        float side = (1 - 2 * support) * narrow;
        return new float[]{pitch, 0, roll - side * 0.045f, side * 0.7f};
    }

    boolean needsSoleContact() { return raisedCollision; }
    float contactDrop() { return Math.min(12, contactDrop); }

    void apply(UUID uuid, Function<String, ModelPart> parts, float[] r, float[] l) {
        float gait = SupportSurface.clamp(narrow * 3, 0, 1);
        move(parts.apply("right_leg"), right, balanced(r), gait);
        move(parts.apply("left_leg"), left, balanced(l), gait);
        float[] wall = WallSqueeze.torsoHint(uuid);
        if (!armsFree || wall != null && (Math.abs(wall[1]) > 0.01f || wall.length > 3 && Math.abs(wall[3]) > 0.01f)) return;
        arm(parts.apply("right_arm"), -1, narrow * (1 - InteractionRuntime.weight(uuid, Effector.RIGHT_ARM)));
        arm(parts.apply("left_arm"), 1, narrow * (1 - InteractionRuntime.weight(uuid, Effector.LEFT_ARM)));
    }

    private static void move(ModelPart leg, float[] shift, float[] animated, float gait) {
        if (leg == null) return;
        leg.xRot = BalanceMath.balancePitch(leg.xRot, gait);
        if (Math.abs(shift[0]) + Math.abs(shift[1]) < 1e-4f) return;
        float dx = shift[0], dz = shift[1];
        if (animated != null) {
            Vector3f packed = new Quaternionf().rotationZYX(animated[2], animated[1], animated[0])
                    .transform(new Vector3f(0, 12 * animated[6], 0));
            Vector3f grounded = new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot)
                    .transform(new Vector3f(0, 12 * leg.yScale, 0));
            // The height solver also steps out/forward. Cancel only that extra horizontal
            // displacement here, otherwise a raised leg can leave a two-pixel support again.
            dx += animated[3] + packed.x - leg.x - grounded.x;
            dz += animated[5] + packed.z - leg.z - grounded.z;
        }
        BalanceMath.Leg solved = BalanceMath.leg(leg.xRot, leg.yRot, leg.zRot,
                12 * leg.yScale, dx, dz);
        leg.xRot = solved.pitch(); leg.yRot = solved.yaw(); leg.zRot = solved.roll();
        leg.y += solved.pivotY();
    }

    private static void arm(ModelPart arm, float side, float weight) {
        if (arm == null) return;
        // Moderate spread, well below a T pose; existing arm swing remains visible.
        arm.zRot -= side * 0.55f * weight;
        arm.xRot *= 1 - 0.35f * weight;
    }

    static Floor floor(AbstractClientPlayer player, Vec3 foot) {
        return floor(player, foot, SubLevels.around(player.level(), new AABB(foot, foot).inflate(1)));
    }

    String trace() {
        return "points=" + surface.points.size() + " narrow=" + narrow + " pitch=" + pitch
                + " roll=" + roll + " rx=" + right[0] + " rz=" + right[1]
                + " lx=" + left[0] + " lz=" + left[1];
    }

    private static Floor floor(AbstractClientPlayer player, Vec3 foot, List<SubLevels.Space> spaces) {
        Floor best = null;
        Vec3 start = foot.add(0, 0.7, 0), end = foot.add(0, -0.8, 0);
        for (SubLevels.Space space : spaces) {
            var hit = player.level().clip(new ClipContext(space.toLocal(start), space.toLocal(end),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.isInside()) continue;
            Vec3 normal = space.directionToWorld(Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
            Vec3 point = space.isWorld() ? strm.emfcompat.animationadditions.ejector.EjectorLid.onLid(player, hit)
                    : space.toWorld(hit.getLocation());
            if (normal.y < 0.5 || point.y >= start.y - 0.01) continue;
            double supportY = point.y;
            var block = player.level().getBlockState(hit.getBlockPos());
            if (block.getBlock() instanceof FenceBlock || block.getBlock() instanceof WallBlock
                    || block.getBlock() instanceof FenceGateBlock) {
                // Fence collision extends to 1.5 blocks; its visible top is 1 block. Standing
                // on the invisible collision made both soles float. Validate with collision,
                // then use only that same block's outline for the visual contact.
                var visible = block.getShape(player.level(), hit.getBlockPos())
                        .clip(space.toLocal(start), space.toLocal(end), hit.getBlockPos());
                if (visible != null && !visible.isInside()) point = space.toWorld(visible.getLocation());
            }
            if (best == null || supportY > best.supportY) best = new Floor(point, normal, supportY);
        }
        return best;
    }
}
