package strm.emfcompat.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-fades captured poses against the resource pack's animation, so a pose that appears,
 * disappears or is handed over between animations eases in and out instead of cutting.
 *
 * <p>Every restore the core does is a straight assignment: the part is animated by the pack and
 * then overwritten with what an addon captured. That is instant in both directions, which is what
 * makes a limb jump the moment a mod starts or stops posing it — Not Enough Animations letting go
 * of an eating arm, or a stance ending mid-stride.</p>
 *
 * <h2>The blend</h2>
 * <p>One formula covers all three cases:</p>
 * <pre>out = lerp(base, pose, weight)</pre>
 * <ul>
 *     <li><b>Pose appears</b> — {@code base} is the live part, i.e. whatever EMF has just animated,
 *     and the weight runs to 1. The pack animation keeps playing underneath while the pose takes
 *     over.</li>
 *     <li><b>Pose disappears</b> — the last captured pose is frozen as the target, {@code base} is
 *     again the live part, and the weight runs back to 0, so the limb slides into wherever the
 *     pack's animation has got to.</li>
 *     <li><b>Pose jumps</b> — a mod switching animations on a limb it keeps holding. The value we
 *     last emitted is frozen as the base and the weight restarts, fading from the old pose to the
 *     new one.</li>
 * </ul>
 *
 * <p>Every pose source is faded; one can opt out with {@link #excludeSource}. At rest the weight
 * sits at exactly 1 and the pose is emitted verbatim, so holding a pose costs nothing and adds no
 * lag. This is deliberately not the low-pass filter NotEnoughAnimations
 * uses for its own smoothing: that one damps the whole animation and trades a permanent delay for
 * it, which is the wrong tool when the mod driving the pose is already smooth and only the seams
 * are rough.</p>
 *
 * <h2>Frames</h2>
 * <p>EMF calls the animation hook once per <em>rendered entity</em>, not once per frame — a player
 * drawn again for the inventory portrait, a cosmetics mod or an armour layer gets another call in
 * the same frame. The fade may only advance once per frame, so every part remembers the frame it
 * last advanced on and later calls re-emit the same weight against that model's own live values.
 * The frame marker comes from EMF (see {@link #beginFrame}), which already skips Iris' shadow
 * pass.</p>
 */
public final class PoseInterpolator {

    private PoseInterpolator() {
    }

    /** Config key for the whole feature; registered by the core's Config tab. */
    public static final String KEY_ENABLED = "core.smoothPoseTransitions";

    /**
     * Time constant of the fade, in seconds: the weight covers ~63% of the remaining distance in
     * one of these, so a transition reads as finished after roughly three (~200ms). Long enough to
     * see, short enough that a pose still feels like it snaps on.
     */
    private static final float TAU_SECONDS = 0.07f;

    /** Frames longer than this are hitches (or a loading pause); the fade holds still through them. */
    private static final float MAX_FRAME_SECONDS = 0.1f;

    /**
     * Angular speed, in radians per second, above which a change of target counts as a cut rather
     * than motion. Roughly 2600°/s — far above what any hand animation moves at, so only an actual
     * switch between poses restarts the fade.
     */
    private static final float CUT_ROTATION_SPEED = 45f;

    /**
     * The same for position, in model units per second. The step that has to be caught is a mod
     * animation overriding vanilla's crouch offsets — 3.2 units on the body, 3.2 on the arms, in a
     * single frame, which is ~190 units/s at 60fps. Animations translate a limb far more slowly
     * than that (a few units over a swing, so tens of units/s), so this sits well between them.
     */
    private static final float CUT_TRANSLATION_SPEED = 60f;

    /** A fading-out part below this weight is close enough to the pack animation to let go of. */
    private static final float DONE_WEIGHT = 0.02f;

    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = (float) (Math.PI * 2);

    /**
     * Pose sources that must keep the old instant restore. Empty by default — every source is
     * faded, so an addon gets this without asking and a new one is covered the day it is written.
     * An addon whose pose turns out to be a bad candidate (something very short, where a fade
     * would read as mush) excludes itself here.
     */
    private static final Set<String> EXCLUDED_SOURCES = new HashSet<>();

    private static final Map<UUID, Map<String, PartFade>> STATES = new HashMap<>();

    private static float lastFrameMarker = Float.NaN;
    private static long lastFrameNanos;
    private static float frameSeconds;
    private static long frameId;

    /**
     * Opts a pose source out of fading, restoring it instantly as the core did before. Called by an
     * addon at startup.
     */
    public static void excludeSource(String source) {
        EXCLUDED_SOURCES.add(source);
    }

    /** Whether the feature is switched on at all. */
    public static boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /**
     * Whether this player's restore should go through the fade, and the point where fades that are
     * no longer wanted are dropped. Called once per restore.
     *
     * <p>One excluded source posing the player turns the fade off for all of them: the sources are
     * merged per part before anything is applied, and two of them routinely share a limb through
     * {@link PoseManager#getSavedPoses(UUID)}, so fading one while the other snaps on the same arm
     * would look worse than either alone. A fade already in flight is always seen through, though —
     * abandoning one halfway is exactly the snap this class exists to remove.</p>
     */
    public static boolean claim(UUID uuid) {
        if (!isEnabled()) {
            // Switched off mid-game: let go of whatever was in flight rather than freezing it.
            STATES.remove(uuid);
            return false;
        }
        if (STATES.containsKey(uuid)) {
            return true;
        }
        return EXCLUDED_SOURCES.isEmpty()
                || PoseManager.allActiveSourcesMatch(uuid, source -> !EXCLUDED_SOURCES.contains(source));
    }

    /** Whether anything is still being faded for this player, including a fade back out. */
    public static boolean isActive(UUID uuid) {
        return !STATES.isEmpty() && STATES.containsKey(uuid);
    }

    /**
     * Opens a frame. Cheap and idempotent — the restore calls it for every rendered entity and only
     * the first call of a frame does anything.
     *
     * @param frameMarker EMF's frame counter, which advances once per rendered frame and stands
     *                    still during Iris' shadow pass.
     */
    public static void beginFrame(float frameMarker) {
        if (frameMarker == lastFrameMarker) {
            return;
        }
        lastFrameMarker = frameMarker;
        frameId++;

        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;
        if (previous == 0L) {
            frameSeconds = 0f;
            return;
        }
        float dt = (now - previous) / 1_000_000_000f;
        // A hitch would jump the fade to its end; a paused game would run it while nothing else
        // moves. Both are answered by simply not advancing.
        boolean skip = dt <= 0f || dt > MAX_FRAME_SECONDS || Minecraft.getInstance().isPaused();
        frameSeconds = skip ? 0f : dt;
    }

    /**
     * Applies a pose from the part map, faded. Mirrors {@link PoseSnapshot#apply(ModelPart)}:
     * rotation always, and position, scale and visibility only for a full snapshot.
     */
    public static void applyPart(UUID uuid, String name, ModelPart part, PoseSnapshot snap) {
        if (snap.rotationOnly) {
            blend(uuid, name, part, snap, snap.xRot, snap.yRot, snap.zRot, false, 0f, 0f, 0f);
            return;
        }
        float scaleX = part.xScale, scaleY = part.yScale, scaleZ = part.zScale;
        blend(uuid, name, part, snap, snap.xRot, snap.yRot, snap.zRot, true, snap.x, snap.y, snap.z);
        applyScaleAndVisibility(part, snap, scaleX, scaleY, scaleZ);
    }

    /** Scale follows the fade weight of a self-blended pose; everything else takes it outright. */
    private static void applyScaleAndVisibility(ModelPart part, PoseSnapshot snap,
                                                float liveX, float liveY, float liveZ) {
        if (snap.selfBlended && snap.weight < 1f) {
            part.xScale = lerp(liveX, snap.xScale, snap.weight);
            part.yScale = lerp(liveY, snap.yScale, snap.weight);
            part.zScale = lerp(liveZ, snap.zScale, snap.weight);
            if (snap.weight >= 0.5f) {
                part.visible = snap.visible;
                part.skipDraw = snap.skipDraw;
            }
            return;
        }
        part.xScale = snap.xScale;
        part.yScale = snap.yScale;
        part.zScale = snap.zScale;
        part.visible = snap.visible;
        part.skipDraw = snap.skipDraw;
    }

    /**
     * Applies an arm slot pose, faded. Mirrors the core's own arm restore: rotation is absolute,
     * and the position only follows when a body delta was worked out for this frame.
     */
    public static void applyArm(UUID uuid, String name, ModelPart part, PoseSnapshot snap,
                                @Nullable Vector3f bodyDelta) {
        if (bodyDelta == null) {
            blend(uuid, name, part, snap, snap.xRot, snap.yRot, snap.zRot, false, 0f, 0f, 0f);
            return;
        }
        blend(uuid, name, part, snap, snap.xRot, snap.yRot, snap.zRot, true,
                snap.x + bodyDelta.x, snap.y + bodyDelta.y, snap.z + bodyDelta.z);
        part.xScale = snap.xScale;
        part.yScale = snap.yScale;
        part.zScale = snap.zScale;
        part.visible = snap.visible;
    }

    /**
     * Fades every part that was posed before but is not this frame back into the pack's animation,
     * and drops the ones that have arrived. Call once, after the poses of the frame are applied.
     */
    public static void fadeReleased(UUID uuid, Map<String, ? extends ModelPart> partsByName) {
        Map<String, PartFade> states = STATES.get(uuid);
        if (states == null) {
            return;
        }
        Iterator<Map.Entry<String, PartFade>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PartFade> entry = it.next();
            PartFade fade = entry.getValue();
            if (fade.posedFrame == frameId) {
                continue;
            }
            ModelPart part = partsByName.get(entry.getKey());
            if (part == null) {
                // The model has no such part any more (a variant swap); nothing to fade into.
                it.remove();
                continue;
            }
            fade.sourceWeight = Float.NaN;
            emit(fade, part, false, 0f, 0f, 0f, fade.blendsPosition, 0f, 0f, 0f);
            if (!fade.posed && fade.weight <= DONE_WEIGHT) {
                it.remove();
            }
        }
        if (states.isEmpty()) {
            STATES.remove(uuid);
        }
    }

    /**
     * Overwrites a part with the value the fade emitted this frame, if it has one.
     *
     * <p>For the humanoid models EMF copies the biped pose onto — armour, and the extra player
     * models cosmetics mods draw. They have to move with the body they belong to, so they take the
     * blended value rather than the raw snapshot the body no longer shows.</p>
     */
    public static void copyBlended(UUID uuid, String name, ModelPart part) {
        Map<String, PartFade> states = STATES.get(uuid);
        if (states == null) {
            return;
        }
        PartFade fade = states.get(name);
        if (fade == null) {
            return;
        }
        part.xRot = fade.outXRot;
        part.yRot = fade.outYRot;
        part.zRot = fade.outZRot;
        if (fade.blendsPosition) {
            part.x = fade.outX;
            part.y = fade.outY;
            part.z = fade.outZ;
        }
    }

    /** Drops fades for players no longer in the level. Called by the core cleanup. */
    public static void retainOnly(Collection<UUID> activeUUIDs) {
        STATES.keySet().retainAll(activeUUIDs);
    }

    private static void blend(UUID uuid, String name, ModelPart part, PoseSnapshot snap,
                              float xRot, float yRot, float zRot,
                              boolean withPosition, float x, float y, float z) {
        PartFade fade = STATES.computeIfAbsent(uuid, k -> new HashMap<>())
                .computeIfAbsent(name, k -> new PartFade());
        fade.sourceWeight = snap.selfBlended ? snap.weight : Float.NaN;
        emit(fade, part, true, xRot, yRot, zRot, withPosition, x, y, z);
    }

    /**
     * Advances the fade (once per frame) and writes the result onto the part.
     *
     * @param posed whether a source is posing this part right now; when false the frozen pose from
     *              the last frame that had one is faded back out into the live animation.
     */
    private static void emit(PartFade fade, ModelPart part, boolean posed,
                             float xRot, float yRot, float zRot,
                             boolean withPosition, float x, float y, float z) {
        if (posed) {
            // Marks the part as claimed for this frame, on every pass — a second render of the
            // same player must not be mistaken for the source having let go.
            fade.posedFrame = frameId;
        }
        if (fade.frame != frameId) {
            fade.frame = frameId;
            if (posed && !Float.isNaN(fade.sourceWeight)) {
                // The source blends this pose itself: its weight is the fade, it is continuous by
                // contract, and the pack's live animation is always what it blends over.
                if (!fade.selfBlended && (fade.posed || fade.weight > DONE_WEIGHT)) {
                    // Taking over a part that another pose holds or is still leaving. The source's
                    // own weight knows nothing of that pose, so hand over from what is on screen.
                    fade.handoverXRot = fade.outXRot;
                    fade.handoverYRot = fade.outYRot;
                    fade.handoverZRot = fade.outZRot;
                    fade.handoverX = fade.outX;
                    fade.handoverY = fade.outY;
                    fade.handoverZ = fade.outZ;
                    fade.handover = 0f;
                }
                fade.selfBlended = true;
                fade.blendsPosition = withPosition;
                fade.baseIsLive = true;
                fade.poseXRot = xRot;
                fade.poseYRot = yRot;
                fade.poseZRot = zRot;
                if (withPosition) {
                    fade.poseX = x;
                    fade.poseY = y;
                    fade.poseZ = z;
                }
                fade.posed = true;
                fade.weight = fade.sourceWeight;
            } else if (posed) {
                fade.selfBlended = false;
                fade.handover = 1f;
                if (withPosition != fade.blendsPosition) {
                    // The pose started or stopped driving position, so the frozen values for the
                    // channels that just changed mean nothing: start over from the live animation.
                    fade.blendsPosition = withPosition;
                    fade.baseIsLive = true;
                    fade.weight = 0f;
                } else if (!fade.posed) {
                    // Appearing. Start from whatever the part is showing — the pack's animation for
                    // a part nothing was holding, or a fade-out that had not finished yet, which is
                    // what a pose flickering on and off around some threshold looks like.
                    if (fade.weight > 0f) {
                        freezeBase(fade);
                    } else {
                        fade.baseIsLive = true;
                    }
                    fade.weight = 0f;
                } else if (isCut(fade, xRot, yRot, zRot, withPosition, x, y, z)) {
                    // Same source, different pose: fade from what the model is actually showing.
                    freezeBase(fade);
                    fade.weight = 0f;
                }
                fade.poseXRot = xRot;
                fade.poseYRot = yRot;
                fade.poseZRot = zRot;
                if (withPosition) {
                    fade.poseX = x;
                    fade.poseY = y;
                    fade.poseZ = z;
                }
                fade.posed = true;
            } else if (fade.posed && fade.selfBlended) {
                // A self-blending source let go. Its last pose and weight are exactly what is on
                // screen, over the live animation, so the fade simply carries on from there. Freezing
                // the output instead would pin the part to a stale frame while the pack keeps moving,
                // and restart it at full weight - a visible jump when the source was nearly out.
                fade.selfBlended = false;
                fade.posed = false;
            } else if (fade.posed) {
                fade.selfBlended = false;
                fade.handover = 1f;
                // Let go of. What is on screen becomes the target — the fade may have been part
                // way in, and jumping to the full pose to fade out of it would be a snap of its
                // own — and the weight runs back down into the pack's animation.
                fade.poseXRot = fade.outXRot;
                fade.poseYRot = fade.outYRot;
                fade.poseZRot = fade.outZRot;
                fade.poseX = fade.outX;
                fade.poseY = fade.outY;
                fade.poseZ = fade.outZ;
                fade.baseIsLive = true;
                fade.weight = 1f;
                fade.posed = false;
            }
            float step = 1f - (float) Math.exp(-frameSeconds / TAU_SECONDS);
            if (!posed || Float.isNaN(fade.sourceWeight)) {
                fade.weight += ((fade.posed ? 1f : 0f) - fade.weight) * step;
            }
            if (fade.handover < 1f) {
                fade.handover += (1f - fade.handover) * step;
                if (fade.handover > 1f - DONE_WEIGHT) {
                    fade.handover = 1f;
                }
            }
        }

        float weight = fade.weight;
        fade.outXRot = lerpAngle(fade.baseIsLive ? part.xRot : fade.baseXRot, fade.poseXRot, weight);
        fade.outYRot = lerpAngle(fade.baseIsLive ? part.yRot : fade.baseYRot, fade.poseYRot, weight);
        fade.outZRot = lerpAngle(fade.baseIsLive ? part.zRot : fade.baseZRot, fade.poseZRot, weight);
        if (fade.handover < 1f) {
            fade.outXRot = lerpAngle(fade.handoverXRot, fade.outXRot, fade.handover);
            fade.outYRot = lerpAngle(fade.handoverYRot, fade.outYRot, fade.handover);
            fade.outZRot = lerpAngle(fade.handoverZRot, fade.outZRot, fade.handover);
        }
        part.xRot = fade.outXRot;
        part.yRot = fade.outYRot;
        part.zRot = fade.outZRot;

        if (fade.blendsPosition) {
            fade.outX = lerp(fade.baseIsLive ? part.x : fade.baseX, fade.poseX, weight);
            fade.outY = lerp(fade.baseIsLive ? part.y : fade.baseY, fade.poseY, weight);
            fade.outZ = lerp(fade.baseIsLive ? part.z : fade.baseZ, fade.poseZ, weight);
            if (fade.handover < 1f) {
                fade.outX = lerp(fade.handoverX, fade.outX, fade.handover);
                fade.outY = lerp(fade.handoverY, fade.outY, fade.handover);
                fade.outZ = lerp(fade.handoverZ, fade.outZ, fade.handover);
            }
            part.x = fade.outX;
            part.y = fade.outY;
            part.z = fade.outZ;
        }

    }

    /** Freezes what is currently on screen as the start of a new fade. */
    private static void freezeBase(PartFade fade) {
        fade.baseXRot = fade.outXRot;
        fade.baseYRot = fade.outYRot;
        fade.baseZRot = fade.outZRot;
        fade.baseX = fade.outX;
        fade.baseY = fade.outY;
        fade.baseZ = fade.outZ;
        fade.baseIsLive = false;
    }

    /**
     * Whether the target moved further this frame than any animation would, i.e. it was replaced.
     *
     * <p>Position is tested as well as rotation, and it is the half that matters for crouching: the
     * offsets vanilla puts on the body, arms and legs while sneaking are pure translation, and so is
     * what a mod's animation does when it overrides them — Better Combat pins {@code torso.y} to 0
     * in every keyframe of an attack, which steps the body 3.2 units the moment a crouched player
     * swings. A rotation-only test never saw any of it.</p>
     */
    private static boolean isCut(PartFade fade, float xRot, float yRot, float zRot,
                                 boolean withPosition, float x, float y, float z) {
        if (frameSeconds <= 0f) {
            return false;
        }
        float limit = CUT_ROTATION_SPEED * frameSeconds;
        if (Math.abs(wrap(xRot - fade.poseXRot)) > limit
                || Math.abs(wrap(yRot - fade.poseYRot)) > limit
                || Math.abs(wrap(zRot - fade.poseZRot)) > limit) {
            return true;
        }
        if (!withPosition) {
            // Position is not being faded for this part, so the frozen values mean nothing.
            return false;
        }
        float positionLimit = CUT_TRANSLATION_SPEED * frameSeconds;
        return Math.abs(x - fade.poseX) > positionLimit
                || Math.abs(y - fade.poseY) > positionLimit
                || Math.abs(z - fade.poseZ) > positionLimit;
    }

    /** Interpolates two angles the short way around, so a fade never takes the long way. */
    private static float lerpAngle(float from, float to, float t) {
        if (!Float.isFinite(from) || !Float.isFinite(to)) {
            // A broken value would be frozen into the fade for good; take the target and move on.
            // NotEnoughAnimations hit this with quick-charge crossbows.
            return Float.isFinite(to) ? to : 0f;
        }
        return from + wrap(to - from) * t;
    }

    private static float lerp(float from, float to, float t) {
        if (!Float.isFinite(from) || !Float.isFinite(to)) {
            return Float.isFinite(to) ? to : 0f;
        }
        return from + (to - from) * t;
    }

    /** Normalises an angle to [-π, π]; Java's remainder keeps the sign of the dividend. */
    private static float wrap(float angle) {
        float wrapped = (angle + PI) % TWO_PI;
        if (wrapped < 0f) {
            wrapped += TWO_PI;
        }
        return wrapped - PI;
    }

    /** The fade of one model part of one player. */
    private static final class PartFade {
        /** Frozen start of the current fade; only read while {@link #baseIsLive} is false. */
        float baseXRot, baseYRot, baseZRot, baseX, baseY, baseZ;
        /** The pose being faded towards, kept after the source lets go so it can be faded out of. */
        float poseXRot, poseYRot, poseZRot, poseX, poseY, poseZ;
        /** What was last written to the part — the start of the next fade, and what armour copies. */
        float outXRot, outYRot, outZRot, outX, outY, outZ;
        /** Whether the fade starts from the live pack animation rather than a frozen pose. */
        boolean baseIsLive = true;
        /** Whether a source posed this part on the last frame that was advanced. */
        boolean posed;
        /** The last frame a source posed this part, on any of that frame's render passes. */
        long posedFrame = -1L;
        /** Whether position is being faded too, or only rotation. */
        boolean blendsPosition;
        float weight;
        long frame = -1L;
        /** The weight a self-blending source handed over this frame, or NaN when the core fades. */
        float sourceWeight = Float.NaN;
        /** Whether the pose on this part came from a self-blending source last frame. */
        boolean selfBlended;
        /** What was on screen when a self-blending source took the part from another pose. */
        float handoverXRot, handoverYRot, handoverZRot, handoverX, handoverY, handoverZ;
        /** How far that takeover has got; 1 when there is none. */
        float handover = 1f;
    }
}
