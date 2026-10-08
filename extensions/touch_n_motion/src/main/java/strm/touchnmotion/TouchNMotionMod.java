package strm.touchnmotion;

/**
 * Touch'n Motion: small player-animation features that are not a compat layer for any one
 * mod, gathered in one addon. Each one registers its own toggles in the shared config section and
 * lives in its own package; {@link TouchNMotionHook} puts them all on the model:
 *
 * <ul>
 *   <li>{@code horsesync} - the rider follows a horse animated by EMF, with a riding pose;</li>
 *   <li>{@code footgrounding} - Foot IK: both feet stand on uneven ground, a horse's too;</li>
 *   <li>{@code wallhand} - standing at a wall, the hands rest on it; in a gap narrower than the shoulders the torso turns to fit;</li>
 *   <li>{@code plantreach} - in grass or crops, the hands brush the plants;</li>
 *   <li>{@code lookat} - standing idle, the head turns to a creature nearby;</li>
 *   <li>{@code buttonpress} - the right hand reaches for a button or a lever about to be used, a foot stamps on a button on the floor;</li>
 *   <li>{@code blockuse} - the hand goes to the spot of a block it uses, and puts in, takes out, taps or holds on;</li>
 *   <li>{@code doorhold} - near a door the hands go to its handles and hold it going through;</li>
 *   <li>{@code furniture} - looking at a lectern or a chest close by, both hands go to it;</li>
 *   <li>{@code motion} - how the player moves as smooth signals; the pack's pose cuts settle with inertia;</li>
 *   <li>{@code torso} - the torso leans with the motion, after the head and over the foot that carries the weight;</li>
 *   <li>{@code mining} - breaking a block, the swing brings the tool's head onto the point hit;</li>
 *   <li>{@code pocket} - a moment after a run of pick-ups the right hand tucks it all away at the hip;</li>
 *   <li>{@code gesture} - short gestures after a real action: feeding, milking and shearing, an armour stand,
 *       a seed planted, armour and accessories put on, a shake after water, looking through a container;</li>
 *   <li>{@code ejector} - on a Create weighted ejector the body braces, and flies when thrown.</li>
 * </ul>
 *
 * <p>This class is what every loader shares of the mod's own: its names. The entry points are the
 * loaders' - {@code neoforge.TouchNMotionNeoForge}, {@code fabric.TouchNMotionFabric}.</p>
 */
public final class TouchNMotionMod {

    public static final String MOD_ID = "touch_n_motion";
    /** The addon's master option; the core's global switch covers it by its suffix. */
    public static final String KEY_ENABLED = "touchnmotion.enabled";

    private TouchNMotionMod() {
    }
}
