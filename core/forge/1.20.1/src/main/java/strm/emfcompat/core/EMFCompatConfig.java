package strm.emfcompat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loader-agnostic configuration store for the EMF compatibility framework.
 *
 * <p>Values are held in a flat key→value map keyed by option id (e.g.
 * {@code "carryon.bodyFollowArms"}) and serialised to {@code config/emf_compat.json} with
 * GSON, modelled on the NotEnoughAnimations config. The map keeps the core decoupled from
 * addon-specific fields: each addon registers its options with {@link ConfigRegistry} and
 * reads them here by key. A {@code configVersion} is kept for future migrations.</p>
 */
public final class EMFCompatConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("emf_compat");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Naming convention for an addon's master option; see {@link #getBoolean}. */
    private static final String ENABLED_SUFFIX = ".enabled";

    /** Bumped when the schema changes so a future upgrader can migrate old files. */
    public int configVersion = 1;

    /** Boolean options, keyed by option id. Sparse: absent keys fall back to their default. */
    public Map<String, Boolean> booleans = new LinkedHashMap<>();

    private static volatile EMFCompatConfig instance = new EMFCompatConfig();
    private static File file;

    private EMFCompatConfig() {
    }

    public static EMFCompatConfig get() {
        return instance;
    }

    /**
     * Reads a boolean option, honouring the global switch.
     *
     * <p>By convention every addon's master option is named {@code <addon>.enabled} and gates all
     * of that addon's behaviour. So while {@link EMFCompatCore#isCompatEnabled()} is off, those
     * keys all read as {@code false} and the addons turn themselves off — mod-specific extras
     * (first-person vanilla-model conditions, EMF un-pausing, animation spoofs) included. New
     * addons must follow the same naming to be covered. Use {@link #getBooleanRaw} to read the
     * stored value regardless (the config screen does, so it shows real settings).</p>
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        if (!EMFCompatCore.isCompatEnabled()
                && key.endsWith(ENABLED_SUFFIX)
                && !EMFCompatCore.KEY_COMPAT_ENABLED.equals(key)) {
            return false;
        }
        return getBooleanRaw(key, defaultValue);
    }

    /** Reads the stored value of an option, ignoring the global switch. */
    public static boolean getBooleanRaw(String key, boolean defaultValue) {
        Boolean v = instance.booleans.get(key);
        return v != null ? v : defaultValue;
    }

    public static void setBoolean(String key, boolean value) {
        instance.booleans.put(key, value);
        if (EMFCompatCore.KEY_COMPAT_ENABLED.equals(key)) {
            EMFCompatCore.setCompatEnabled(value);
        }
    }

    /** Binds the config file and loads it (writing defaults if absent). */
    public static void init(File configFile) {
        file = configFile;
        load();
    }

    public static void load() {
        if (file != null && file.isFile()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                EMFCompatConfig loaded = GSON.fromJson(reader, EMFCompatConfig.class);
                if (loaded != null) {
                    if (loaded.booleans == null) {
                        loaded.booleans = new LinkedHashMap<>();
                    }
                    instance = loaded;
                }
            } catch (Exception e) {
                LOGGER.warn("[emf_compat] Failed to read config {}, using defaults", file, e);
            }
        }
        // Mirror the global switch into the core, which reads it on the render path.
        EMFCompatCore.setCompatEnabled(getBooleanRaw(EMFCompatCore.KEY_COMPAT_ENABLED, true));
        save();
    }

    public static void save() {
        if (file == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("[emf_compat] Failed to write config {}", file, e);
        }
    }
}
