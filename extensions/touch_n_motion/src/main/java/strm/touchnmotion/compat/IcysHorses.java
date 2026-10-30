package strm.touchnmotion.compat;

/**
 * Icy's Better Horses: its breeds are horses drawn by its own renderer, and it carries their rider to the
 * saddle and with the gait itself ({@code EntityRenderDispatcherRiderMixin}) - so the rider is not carried
 * here as well. Found by class name, looked up once; without the mod nothing is a breed.
 */
public final class IcysHorses {

    private static final class Breed {
        static final Class<?> TYPE = find();

        private static Class<?> find() {
            try {
                return Class.forName("icy.betterhorses.net.entity.BhBreedHorse");
            } catch (Throwable absent) {
                return null;
            }
        }
    }

    private IcysHorses() {
    }

    /** Whether this is one of Icy's Better Horses' breeds. */
    public static boolean isBreed(Object entity) {
        Class<?> type = Breed.TYPE;
        return type != null && type.isInstance(entity);
    }
}
