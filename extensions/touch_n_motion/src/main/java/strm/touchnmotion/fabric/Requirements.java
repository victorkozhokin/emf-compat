package strm.touchnmotion.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * What the mod needs that Fabric cannot be told of. Entity Model Features and EMF Compat Core are
 * the client's alone, and a dependency in Fabric's own list is asked of a server as well - a
 * dedicated server could then never have the mod. So they are only suggested there, and asked
 * for here, of a client, before the game starts.
 */
public class Requirements implements PreLaunchEntrypoint {

    private static final String[][] NEEDED = {
            {"entity_model_features", "Entity Model Features", "3.3.2"},
            {"emf_compat_core", "EMF Compat Core", "2.3.0"},
    };

    @Override
    public void onPreLaunch() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT) return;
        StringBuilder missing = new StringBuilder();
        for (String[] mod : NEEDED) {
            var found = loader.getModContainer(mod[0]);
            if (found.isPresent() && atLeast(found.get().getMetadata().getVersion(), mod[2])) continue;
            missing.append("\n - ").append(mod[1]).append(' ').append(mod[2]).append(" or newer")
                    .append(found.isPresent() ? " (installed: " + found.get().getMetadata().getVersion().getFriendlyString() + ")" : " (not installed)");
        }
        if (missing.length() > 0) throw new IllegalStateException("Touch'n Motion needs on a client:" + missing);
    }

    private static boolean atLeast(Version installed, String wanted) {
        try {
            return installed.compareTo(Version.parse(wanted)) >= 0;
        } catch (Exception e) {
            return true;
        }
    }
}
