package strm.emfcompat.animationadditions.blockuse;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Simulated's linked typewriter: looked at, both hands are over its keyboard, one on each half,
 * and stay there while the player types on it, wherever they look; a key held down, the hand of
 * its half goes down onto that very key and holds it. Optional, by name.
 *
 * <p>The keys are where {@code LinkedTypewriterRenderer} draws them: fourteen of them - a row of
 * six, a row of seven nearer the typist and lower, and the space bar - 2 px apart. Which of them a
 * key of the keyboard is, is the mod's own rule ({@code LinkedTypewriterInteractionHandler}): a
 * few are set ({@code presetKeys}: Q, W, E on the far row, A, S, D on the near one), the rest are
 * dealt out by a random number seeded with the key's code. The keys held ({@code getPressedKeys},
 * key codes) and who types ({@code currentUser}) are every client's to see.</p>
 */
final class Typewriter implements BlockTarget {

    private static final String BLOCK = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlock";
    private static final String HANDLER = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler";
    private static final ModAccess PRESSED_KEYS = new ModAccess("getPressedKeys");
    private static final ModAccess USER = new ModAccess("currentUser");

    /** Pixels in the block's model, made facing north: where a hand waits over its half, off the middle. */
    private static final double REST_SIDE = 3, REST_Y = 6.5, REST_Z = 4.5;
    /** A key held: this far under its top - the hand waits a little off what it is at, and the key goes down. */
    private static final double PRESSED = 2.25;
    private static final int FAR_ROW = 6, NEAR_ROW = 7, KEYS = 14;

    private static Int2IntMap preset;
    private static java.lang.reflect.Method ownPressed;
    private static boolean looked;

    @Override
    public boolean matches(BlockState block) {
        return block.getBlock().getClass().getName().equals(BLOCK);
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        return hand(player, pos, block, player.getMainArm() == HumanoidArm.RIGHT);
    }

    @Override
    public Spot supportHand(AbstractClientPlayer player, BlockPos pos, BlockState block) {
        return hand(player, pos, block, player.getMainArm() != HumanoidArm.RIGHT);
    }

    /** Typing, the player looks round freely - the keys take the keyboard, not the look: the hands stay on them. */
    @Override
    public boolean holds(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return player.getUUID().equals(USER.read(level.getBlockEntity(pos)));
    }

    @Override
    public Gesture changed(BlockPos pos, Object before, Object now) {
        return null;
    }

    /** Where the typist's right or left hand is: on the last key held in its half, or waiting over the half. */
    private static Spot hand(AbstractClientPlayer player, BlockPos pos, BlockState block, boolean right) {
        double[] key = null;
        // Our own player's keys are known at once, as the drawn keys they are; another's come with the block entity, as key codes.
        boolean own = player == Minecraft.getInstance().player;
        Object held = own ? ownHeld() : PRESSED_KEYS.read(player.level().getBlockEntity(pos));
        if (held instanceof List<?> keys) {
            for (Object each : List.copyOf(keys)) {
                if (!(each instanceof Integer k)) continue;
                double[] at = key(own ? Math.max(0, Math.min(KEYS - 1, k)) : index(k));
                // The typist's right is the model's low x; the middle is the right hand's.
                if ((at[0] <= 8) == right) key = at;
            }
        }
        Direction facing = block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // The model is made facing north; Spots.turned takes one made facing south.
        return key != null ? Spots.turned(pos, facing.getOpposite(), key[0], key[1] - PRESSED, key[2])
                : Spots.turned(pos, facing.getOpposite(), right ? 8 - REST_SIDE : 8 + REST_SIDE, REST_Y, REST_Z);
    }

    /** A cockpit typist uses one hand for the whole keyboard; the other keeps the rim. */
    static Spot cockpitSpot(BlockPos pos,BlockState block,int pressed,boolean right) {
        Direction facing=block.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if(pressed<0)return Spots.turned(pos,facing.getOpposite(),8,REST_Y,REST_Z);
        double[] at=key(Math.min(KEYS-1,pressed));
        // Space spans ten pixels: press its outer portion rather than trapping the wrist at its centre.
        if(pressed==13)at[0]=right?4:12;
        return Spots.turned(pos,facing.getOpposite(),at[0],at[1]-PRESSED,at[2]);
    }
    static int pressedKey() {
        Object held=ownHeld();int result=-1;
        if(held instanceof List<?> keys)for(Object each:List.copyOf(keys))
            if(each instanceof Integer k)result=Math.max(0,Math.min(KEYS-1,k));
        return result;
    }
    private static Field activeField;
    private static Class<?> activeHandler;
    private static java.lang.reflect.Method activeMode;
    private static boolean activeAbsent;

    static BlockPos activePosition(AbstractClientPlayer player) {
        if(player!=Minecraft.getInstance().player)return null;
        try {
            // Asked every frame of our own player: the class and the method are found once.
            if(activeAbsent)return null;
            if(activeMode==null) {
                try {activeHandler=Class.forName(HANDLER);activeMode=activeHandler.getMethod("getMode");}
                catch(ReflectiveOperationException | LinkageError missing) {activeAbsent=true;return null;}
            }
            Class<?> handler=activeHandler;
            if(!"ACTIVE".equals(String.valueOf(activeMode.invoke(null))))return null;
            if(activeField==null){activeField=handler.getDeclaredField("TYPEWRITER");activeField.setAccessible(true);}
            Object reference=activeField.get(null);
            Object value=reference instanceof java.lang.ref.Reference<?> ref?ref.get():null;
            if(value instanceof net.minecraft.world.level.block.entity.BlockEntity be && !be.isRemoved() && be.getLevel()==player.level())
                return be.getBlockPos();
        } catch(ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        return null;
    }

    /** The top of key {@code i}, pixels in the model: the renderer's own places, about the block's middle and turned half round. */
    private static double[] key(int i) {
        double x, y, z;
        if (i < FAR_ROW) {
            x = -0.4375 + 0.125 * (i + 1);
            y = 0.0625;
            z = 0.125;
        } else if (i < FAR_ROW + NEAR_ROW) {
            x = -0.5 + 0.125 * (i - FAR_ROW + 1);
            y = 0;
            z = 0.25;
        } else {
            x = 0;
            y = -0.0625;
            z = 0.375;
        }
        return new double[]{8 - 16 * x, 4 + 16 * y + 1, 8 - 16 * z};
    }

    /** The drawn keys our own player holds down ({@code LinkedTypewriterInteractionHandler.getPressedKeys}). */
    private static Object ownHeld() {
        try {
            if (ownPressed == null) ownPressed = Class.forName(HANDLER).getMethod("getPressedKeys");
            return ownPressed.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    /** Which drawn key a key code is, as the mod's client decides it. */
    private static int index(int code) {
        if (!looked) {
            looked = true;
            try {
                Field field = Class.forName(HANDLER).getDeclaredField("presetKeys");
                field.setAccessible(true);
                preset = (Int2IntMap) field.get(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }
        int index = preset != null && preset.containsKey(code) ? preset.get(code) : RandomSource.create(code).nextInt(KEYS - 1);
        return Math.max(0, Math.min(KEYS - 1, index));
    }
}
