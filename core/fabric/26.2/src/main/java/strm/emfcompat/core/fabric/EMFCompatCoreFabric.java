package strm.emfcompat.core.fabric;

import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric entry point for the core EMF compatibility framework.
 *
 * <p>The core framework is intentionally static; this initializer exists only
 * so that Fabric registers the mod container.</p>
 */
public class EMFCompatCoreFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Core functionality is provided via static helpers and mixins.
    }
}
