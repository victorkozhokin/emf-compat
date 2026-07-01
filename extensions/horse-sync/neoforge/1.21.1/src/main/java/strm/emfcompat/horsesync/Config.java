package strm.emfcompat.horsesync;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable synchronization of the player position with the horse's animated body.")
            .define("enabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
