package strm.emfcompat.animationadditions.fright;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import strm.emfcompat.animationadditions.DebugLog;
import strm.emfcompat.animationadditions.interaction.Body;
import strm.emfcompat.animationadditions.interaction.Candidate;
import strm.emfcompat.animationadditions.interaction.Effector;
import strm.emfcompat.animationadditions.interaction.EntityStates;
import strm.emfcompat.animationadditions.interaction.InteractionContext;
import strm.emfcompat.animationadditions.interaction.InteractionProvider;
import strm.emfcompat.animationadditions.interaction.InteractionRuntime;
import strm.emfcompat.animationadditions.torso.TorsoLean;
import strm.emfcompat.core.ConfigRegistry;
import strm.emfcompat.core.EMFCompatConfig;
import strm.emfcompat.core.ik.IKMath;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Fright at a sound: sculk, the warden, the creaking, a creeper's fuse, an explosion, thunder. The
 * client hears the sound and where it is; every player it draws near enough takes fright - a start,
 * a scare or a terror by what it was and how near ({@link FrightMotion}).
 *
 * <p>Nothing is taken from what the player is doing: the fright is added over the pose as it is -
 * a shudder of the torso, the shoulders up, the head - and the hands stay with their work. It runs
 * out to nothing by itself.</p>
 */
public final class Fright implements InteractionProvider {

    public static final Fright INSTANCE = new Fright();
    public static final String KEY_ENABLED = "fright.enabled";
    public static final String KEY_SCULK = "fright.sculk", KEY_WARDEN = "fright.warden", KEY_CREAKING = "fright.creaking",
            KEY_CREEPER = "fright.creeper", KEY_EXPLOSION = "fright.explosions", KEY_THUNDER = "fright.thunder", KEY_USED = "fright.getUsedTo", KEY_VARIANT = "fright.variant";
    private static final Logger LOGGER = LoggerFactory.getLogger("EMFCompatFright");

    /** Seconds: a sound no worse than the last is not jumped at again this soon; each fright counts against the next for this long. */
    private static final double AGAIN_SECONDS = 3.0, NERVE_SECONDS = 20.0;
    /** A sound is heard for this long: a player first drawn a moment after it still takes fright. */
    private static final long HEARD_NANOS = 250_000_000L;
    /** Seconds the torso's lean runs behind what it is asked: the shudder is asked for that much ahead. */
    private static final float LEAN_LAG = 0.12f;
    /** Radians: how far round to the sound the head goes, and the share of it the torso takes. Pixels: the shoulder on the sound's side up more, the head down between the shoulders by this share of the shrug. */
    private static final float ROUND_LIMIT = 1.25f, ROUND_TORSO = 0.28f;

    /** What frightens, by the sound's name: the setting it falls under, how bad it is close by, and how far "close by" is, blocks. */
    private record Kind(String key, String sound, int level, double near) {
    }

    /** By the start of the sound's path, the first match; the creaking's are by name, for versions that have it. */
    private static final Kind[] KINDS = {
            new Kind(KEY_SCULK, "block.sculk_shrieker.shriek", FrightMotion.STRONG, 10),
            new Kind(KEY_SCULK, "block.sculk_sensor.clicking_stop", 0, 0),
            new Kind(KEY_SCULK, "block.sculk_sensor.clicking", FrightMotion.LIGHT, 6),
            new Kind(KEY_WARDEN, "entity.warden.sonic_boom", FrightMotion.STRONG, 14),
            new Kind(KEY_WARDEN, "entity.warden.roar", FrightMotion.STRONG, 14),
            new Kind(KEY_WARDEN, "entity.warden.emerge", FrightMotion.STRONG, 10),
            new Kind(KEY_WARDEN, "entity.warden.nearby_closest", FrightMotion.MEDIUM, 12),
            new Kind(KEY_WARDEN, "entity.warden.listening_angry", FrightMotion.MEDIUM, 8),
            new Kind(KEY_CREAKING, "entity.creaking.activate", FrightMotion.MEDIUM, 8),
            new Kind(KEY_CREAKING, "entity.creaking.unfreeze", FrightMotion.MEDIUM, 8),
            new Kind(KEY_CREAKING, "entity.creaking.attack", FrightMotion.MEDIUM, 6),
            new Kind(KEY_CREAKING, "entity.creaking.step", FrightMotion.LIGHT, 5),
            new Kind(KEY_CREAKING, "block.creaking_heart.spawn", FrightMotion.LIGHT, 8),
            new Kind(KEY_CREAKING, "block.creaking_heart.hurt", FrightMotion.LIGHT, 6),
            new Kind(KEY_CREEPER, "entity.creeper.primed", FrightMotion.MEDIUM, 5),
            new Kind(KEY_EXPLOSION, "entity.generic.explode", FrightMotion.STRONG, 10),
            new Kind(KEY_EXPLOSION, "entity.dragon_fireball.explode", FrightMotion.STRONG, 10),
            new Kind(KEY_THUNDER, "entity.lightning_bolt.impact", FrightMotion.STRONG, 16),
            new Kind(KEY_THUNDER, "entity.lightning_bolt.thunder", FrightMotion.STRONG, 16),
    };

    /** A sound heard: what, where, when, and its number in the order heard. */
    private record Heard(Kind kind, Vec3 at, long nanos, long number) {
    }

    private static final Deque<Heard> HEARD = new ArrayDeque<>();
    private static long heardCount;
    private static boolean listening;

    private static final class State {
        int level;
        long since, lastAt, number;
        /** Frights lately, fading: the more, the less the next one is. */
        float nerve;
        Vec3 source;
        /** The way round to the sound, radians, against the body as drawn (to the right above zero). */
        float yaw;
        boolean grounded, wary, landed;
        int variant = FrightMotion.RECOIL;
        /** How far the soles are to their stand, 0..1, eased; seconds into the fright. */
        float stand, in;
        float sx, sz;
        AbstractClientPlayer player;
        strm.emfcompat.core.ik.IKFrame frame;
        final strm.emfcompat.animationadditions.torso.BraceSteps.State feet = new strm.emfcompat.animationadditions.torso.BraceSteps.State();
        FrightMotion.Pose pose = FrightMotion.Pose.NONE, ahead = FrightMotion.Pose.NONE;
        final DebugLog.Pace trace = new DebugLog.Pace();
    }

    private static final EntityStates<State> STATES = new EntityStates<>(State::new);

    private Fright() {
    }

    public static void register(ConfigRegistry.Group config) {
        config.addBoolean(KEY_ENABLED, "Fright at sounds", true,
                "On", "A frightening sound near by makes the character shudder and draw the shoulders up; a worse one makes them jump, duck and look round at it.",
                "Off", "Sounds are not reacted to.");
        config.addChild(KEY_ENABLED, KEY_SCULK, "Sculk", true, "On", "A sculk sensor going off startles; a shrieker frightens.", "Off", "Sculk sounds are not reacted to.");
        config.addChild(KEY_ENABLED, KEY_WARDEN, "Warden", true, "On", "The warden coming up, its roar and its sonic boom frighten.", "Off", "The warden's sounds are not reacted to.");
        config.addChild(KEY_ENABLED, KEY_CREAKING, "Creaking", true, "On", "A creaking coming alive near by frightens (on versions that have it).", "Off", "The creaking's sounds are not reacted to.");
        config.addChild(KEY_ENABLED, KEY_CREEPER, "Creeper's fuse", true, "On", "A creeper starting to hiss near by frightens.", "Off", "A creeper's hiss is not reacted to.");
        config.addChild(KEY_ENABLED, KEY_EXPLOSION, "Explosions", true, "On", "An explosion near by frightens.", "Off", "Explosions are not reacted to.");
        config.addChild(KEY_ENABLED, KEY_THUNDER, "Thunder", true, "On", "Lightning striking near by frightens.", "Off", "Thunder is not reacted to.");
        config.addChoice(KEY_ENABLED, KEY_VARIANT, "Way of taking fright (to choose)", new double[]{1, 2, 3},
                new String[]{"A: recoil", "B: jump", "C: freeze"}, 1,
                "Three takes on the same fright, to pick one: A steps back from the sound and keeps the eyes on it; B hops on the spot and looks about; C stands stock still and takes one slow look.");
        config.addChild(KEY_ENABLED, KEY_USED, "Get used to it", true, "On", "Frightened again and again, each fright is taken more lightly for a while.", "Off", "Every sound frightens as much as the first.");
    }

    @Override
    public String id() {
        return "Fright";
    }

    @Override
    public boolean isEnabled() {
        return EMFCompatConfig.getBoolean(KEY_ENABLED, true);
    }

    /** The game's sounds, as they are played: the ones that frighten are kept for a moment for every player drawn. */
    private static void listen() {
        if (listening) return;
        listening = true;
        Minecraft.getInstance().getSoundManager().addListener((sound, accessor, range) -> heard(sound));
    }

    private static void heard(SoundInstance sound) {
        if (sound == null || sound.isRelative() || !INSTANCE.isEnabled()) return;
        String name = sound.getLocation().getPath();
        for (Kind kind : KINDS) {
            if (!name.startsWith(kind.sound)) continue;
            if (kind.level > 0 && EMFCompatConfig.getBoolean(kind.key, true)) {
                synchronized (HEARD) {
                    HEARD.addLast(new Heard(kind, new Vec3(sound.getX(), sound.getY(), sound.getZ()), System.nanoTime(), ++heardCount));
                    while (HEARD.size() > 16) HEARD.removeFirst();
                }
            }
            return;
        }
    }

    /** How bad a sound is at this distance: as bad as it gets within its near range, a step less to twice that, another to four times. */
    private static int level(Kind kind, double distance) {
        int band = distance <= kind.near ? 0 : distance <= kind.near * 2 ? 1 : distance <= kind.near * 4 ? 2 : 3;
        return Math.max(0, kind.level - band);
    }

    @Override
    public void collect(InteractionContext context, List<Candidate> out) {
        listen();
        AbstractClientPlayer player = context.player();
        long now = context.now();
        State state = STATES.seen(player.getUUID(), now).value;
        state.grounded = player.onGround() && !player.isPassenger();
        state.player = player;
        state.frame = context.frame();
        state.nerve *= (float) Math.exp(-context.dt() / NERVE_SECONDS);
        boolean able = !player.isSleeping() && !player.isSwimming() && !player.isFallFlying()
                && (player.getPose() == Pose.STANDING || player.getPose() == Pose.CROUCHING);

        Heard worst = null;
        int worstLevel = 0;
        synchronized (HEARD) {
            long real = System.nanoTime();
            for (Heard heard : HEARD) {
                if (heard.number <= state.number) continue;
                state.number = heard.number;
                if (real - heard.nanos > HEARD_NANOS) continue;
                int level = level(heard.kind, heard.at.distanceTo(player.getEyePosition()));
                if (level > worstLevel) {
                    worst = heard;
                    worstLevel = level;
                }
            }
        }
        if (worst != null && able) {
            // Jumpy already, the next one is taken more lightly.
            int level = worstLevel - (EMFCompatConfig.getBoolean(KEY_USED, true) ? (int) (state.nerve / 2f) : 0);
            boolean soon = (now - state.lastAt) / 1e9 < AGAIN_SECONDS;
            if (level > 0 && (!soon || level > state.level)) {
                state.level = level;
                state.since = state.lastAt = now;
                state.source = worst.at;
                state.nerve += 1f;
                state.variant = Mth.clamp((int) Math.round(EMFCompatConfig.getNumber(KEY_VARIANT, 1)), FrightMotion.RECOIL, FrightMotion.FREEZE);
                state.landed = false;
                if (DebugLog.decisions()) LOGGER.info("[Fright] {} {} level={} at {}", player.getName().getString(), worst.kind.sound, level,
                        Math.round(worst.at.distanceTo(player.getEyePosition()) * 10) / 10.0);
            }
        }
        float in = (float) ((now - state.since) / 1e9);
        state.in = in;
        boolean planted = Body.planted(player);
        // Kept for its time; then the feet are sent home, and it is over when they are there and the jolt has run out.
        state.wary = state.level > 0 && able && planted && in < FrightMotion.kept(state.level);
        if (state.level > 0 && (!able || in >= FrightMotion.kept(state.level) && in >= FrightMotion.jolt(state.level)
                && (state.feet.resting() || !planted) && state.stand < 0.02f)) state.level = 0;
        if (state.level == 0) {
            state.pose = state.ahead = FrightMotion.Pose.NONE;
            state.wary = false;
            state.stand = 0f;
            context.decide("calm");
            return;
        }
        // Which way the sound is, level, against the body as it is drawn (forward is -z, the right is -x).
        Vector3f to = Body.model(context.frame(), state.source);
        state.yaw = to.x * to.x + to.z * to.z < 16f ? 0f : Mth.clamp((float) Math.atan2(-to.x, -to.z), -ROUND_LIMIT, ROUND_LIMIT);
        float flat = (float) Math.sqrt(to.x * to.x + to.z * to.z);
        state.sx = flat < 4f ? 0f : to.x / flat;
        state.sz = flat < 4f ? -1f : to.z / flat;
        // How far the soles have come to their stand: the body goes with them, never ahead. With no stand to take, the clock stands in for it.
        float far = new Vector3f(FrightMotion.foot(state.variant, state.level, true, state.sx, state.sz))
                .add(FrightMotion.foot(state.variant, state.level, false, state.sx, state.sz)).mul(0.5f).length();
        float stood = far > 0.15f && planted ? Mth.clamp(state.feet.mean().length() / far, 0f, 1f)
                : in < FrightMotion.kept(state.level) ? Mth.clamp(in / 0.3f, 0f, 1f) : 0f;
        state.stand += (stood - state.stand) * strm.emfcompat.animationadditions.interaction.Smoothing.follow(context.dt(), 0.12);
        state.pose = FrightMotion.pose(state.variant, state.level, in, state.stand, state.sz);
        state.ahead = FrightMotion.pose(state.variant, state.level, in + LEAN_LAG, state.stand, state.sz);
        context.decide((state.level == FrightMotion.STRONG ? "terror" : state.level == FrightMotion.MEDIUM ? "scare" : "start")
                + (state.variant == FrightMotion.RECOIL ? " recoil" : state.variant == FrightMotion.JUMP ? " jump" : " freeze") + (state.wary ? "" : " home"));
    }

    /** The bow for the torso - asked for as it will be when the lean has caught up - and, in a terror, its share of the turn to the sound. The shudder is too quick for the lean: {@link #apply} lays it on directly. */
    public static TorsoLean.Hint torsoHint(UUID uuid) {
        State state = STATES.fresh(uuid);
        if (state == null || state.level == 0 || !INSTANCE.isEnabled()) return null;
        FrightMotion.Pose pose = state.ahead;
        // The torso goes a little with the head's look to the sides.
        return TorsoLean.Hint.turn(pose.bow(), state.yaw * ROUND_TORSO * pose.round() + pose.glance() * 0.35f, 0f);
    }

    private static final Vector3f HOME = new Vector3f();

    /**
     * The feet, before the torso, and the body gone with them: into the wary stand by a step each
     * - or in the air, by the hop - and home by a step each, never a slide. The body is carried by
     * where the soles are, so it is never ahead of them.
     */
    public static void support(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.player == null || !INSTANCE.isEnabled()) return;
        if (state.level == 0 && state.feet.resting()) return;
        Vector3f right = state.wary ? FrightMotion.foot(state.variant, state.level, true, state.sx, state.sz) : HOME;
        Vector3f left = state.wary ? FrightMotion.foot(state.variant, state.level, false, state.sx, state.sz) : HOME;
        float hop = state.level == 0 ? 0f : FrightMotion.hop(state.variant, state.level);
        Vector3f mean;
        if (state.wary && state.grounded && hop > 0f && state.in < hop && FrightMotion.lands(state.variant, state.level)) {
            // In the air: both feet go to where they will land at once.
            float way = Mth.clamp(state.in / hop, 0f, 1f);
            way = way * way * (3f - 2f * way);
            Vector3f r = new Vector3f(right).mul(way), l = new Vector3f(left).mul(way);
            strm.emfcompat.animationadditions.torso.PelvisFollow.step(parts, r, l, 0f, 0f);
            state.feet.place(r, l);
            mean = new Vector3f(r).add(l).mul(0.5f);
        } else {
            strm.emfcompat.animationadditions.torso.BraceSteps.apply(state.feet, state.player, state.frame, parts, right, left, 1f, 0f, LOGGER, "FrightStance");
            mean = state.feet.mean();
        }
        float carry = state.pose.carry();
        if (carry > 0f && mean.lengthSquared() > 1e-4f) strm.emfcompat.animationadditions.torso.PelvisFollow.shift(parts, mean.x * carry, mean.z * carry);
    }

    /**
     * Over everything else, last: the shoulders up, the arms a little off the body where nothing
     * else has them, the head, and the jump. Added to the pose as it is - nothing is taken.
     */
    public static void apply(UUID uuid, Function<String, ModelPart> parts) {
        State state = STATES.fresh(uuid);
        if (state == null || state.level == 0 || !INSTANCE.isEnabled()) return;
        FrightMotion.Pose pose = state.pose;
        ModelPart head = parts.apply("head"), hat = parts.apply("hat"), body = parts.apply("body");
        // The shudder: the torso twisted at the waist, the shoulders going round with it, the head and the legs left still.
        float cos = Mth.cos(pose.yaw()), sin = Mth.sin(pose.yaw());
        if (body != null) {
            body.yRot += pose.yaw();
            body.zRot += pose.roll();
        }
        for (boolean right : new boolean[]{true, false}) {
            ModelPart arm = parts.apply(right ? "right_arm" : "left_arm");
            if (arm == null) continue;
            float x = arm.x, z = arm.z;
            arm.x = x * cos + z * sin;
            arm.z = -x * sin + z * cos;
            arm.yRot += pose.yaw();
            // A hand at work is left on it.
            if (InteractionRuntime.aim(uuid, right ? Effector.RIGHT_ARM : Effector.LEFT_ARM) != null) continue;
            arm.xRot += -pose.armsUp() + (right ? pose.tremble() : -pose.tremble());
            // Out from the body is +zRot for the right arm and -zRot for the left.
            // Both to the same side: +zRot takes either hand to the right.
            arm.zRot += (right ? 1f : -1f) * pose.armsOut() + pose.sway();
        }
        if (head != null) {
            if (InteractionRuntime.aim(uuid, Effector.HEAD) == null) {
                float round = Mth.clamp(state.yaw * (1f - ROUND_TORSO), -1.2f, 1.2f);
                head.yRot += pose.glance() + IKMath.wrap(round - head.yRot) * pose.round();
                head.xRot += pose.duck();
            }
            if (hat != null) {
                hat.yRot = head.yRot;
                hat.xRot = head.xRot;
            }
        }
        if (state.grounded && pose.hop() > 0f) {
            for (String name : new String[]{"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
                ModelPart part = parts.apply(name);
                if (part != null) part.y -= pose.hop();
            }
        }
        if (DebugLog.trace() && state.trace.due(40_000_000L)) {
            LOGGER.info("[FrightTrace] level={} shrug={} hop={} round={} bodyYaw={} headYaw={} headPitch={}", state.level, Math.round(pose.shrug() * 10) / 10f,
                    Math.round(pose.hop() * 10) / 10f, Math.round(pose.round() * 100) / 100f, body == null ? 0 : Math.round(Math.toDegrees(body.yRot)),
                    head == null ? 0 : Math.round(Math.toDegrees(head.yRot)), head == null ? 0 : Math.round(Math.toDegrees(head.xRot)));
        }
    }
}
