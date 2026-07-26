package strm.emfcompat.horsesync;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Client command {@code /hsride} for live-tuning the horse {@link RidingPose} without rebuilds.
 *
 * <ul>
 *   <li>{@code /hsride set <param> <value>} — set a pose parameter (applies immediately).</li>
 *   <li>{@code /hsride dump} — print all current parameters (paste back to bake as defaults).</li>
 *   <li>{@code /hsride reset} — restore the built-in defaults.</li>
 * </ul>
 */
public final class RidingPoseCommand {

    private static final SuggestionProvider<CommandSourceStack> PARAM_SUGGESTIONS = (ctx, builder) -> {
        String remaining = builder.getRemainingLowerCase();
        for (String key : RidingPose.keys()) {
            if (key.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(key);
            }
        }
        return builder.buildFuture();
    };

    private RidingPoseCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hsride")
                .then(Commands.literal("dump").executes(ctx -> {
                    dump(ctx.getSource());
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    RidingPose.reset();
                    ctx.getSource().sendSuccess(() -> Component.literal("[hsride] reset to defaults"), false);
                    return 1;
                }))
                .then(Commands.literal("set")
                        .then(Commands.argument("param", StringArgumentType.word())
                                .suggests(PARAM_SUGGESTIONS)
                                .then(Commands.argument("value", FloatArgumentType.floatArg())
                                        .executes(ctx -> {
                                            String param = StringArgumentType.getString(ctx, "param");
                                            float value = FloatArgumentType.getFloat(ctx, "value");
                                            if (RidingPose.set(param, value)) {
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        "[hsride] " + param + " = " + fmt(value)), false);
                                                return 1;
                                            }
                                            ctx.getSource().sendFailure(Component.literal(
                                                    "[hsride] unknown param: " + param));
                                            return 0;
                                        })))));
    }

    private static void dump(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[hsride] pose params:"), false);
        RidingPose.snapshot().forEach((key, value) ->
                source.sendSuccess(() -> Component.literal("  " + key + " = " + fmt(value)), false));
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
