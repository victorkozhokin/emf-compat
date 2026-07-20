package strm.emfcompat.takeaseat.compat;

/**
 * The partial pause (everything except the head) is handled by the mixin
 * {@link strm.emfcompat.takeaseat.mixin.EMFModelPartRootMixin}, because EMF's
 * {@code pauseCustomAnimationsForThesePartsOfEntity()} is not consistently
 * respected by the ASM animation backend in the versions we target.
 */
public class EMFCompat {
    public static void init() {
        // No direct EMF API registration is needed; the mixin captures and
        // restores body/limb poses around EMF's animation pass.
    }
}
