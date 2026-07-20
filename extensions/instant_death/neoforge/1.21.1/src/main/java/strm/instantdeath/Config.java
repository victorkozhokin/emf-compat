package strm.instantdeath;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("If true, entities are removed instantly on death and the death animation is skipped.")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue EXCLUDE_PLAYERS = BUILDER
            .comment("If true, players keep the normal death behaviour.")
            .define("exclude_players", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
