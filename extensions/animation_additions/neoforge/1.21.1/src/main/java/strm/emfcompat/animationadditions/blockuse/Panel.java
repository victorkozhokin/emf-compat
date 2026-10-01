package strm.emfcompat.animationadditions.blockuse;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The blocks of Create, of the mods made on it and of Supplementaries that a click works but leaves nothing a hand
 * could be told by - it opens a screen (a sequenced gearshift, a display link, a stock ticker, a
 * station, a postbox), assembles (a bearing), sends an item on (a funnel): looked at, the hand
 * waits at the point of the block the look is on; the player's arm swung - a use that did
 * something swings it, and every client sees that - the hand taps there.
 *
 * <p>By the block's class, or one it extends, by name; after every other target, so a block with a
 * box or a gesture of its own has that where it applies and this elsewhere.</p>
 */
final class Panel implements BlockTarget {

    private static final String SUPPLEMENTARIES = "net.mehvahdjukaar.supplementaries.common.block.blocks.";

    private static final List<String> BLOCKS = List.of(
            "com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock",
            "com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock",
            "com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock",
            "com.simibubi.create.content.logistics.stockTicker.StockTickerBlock",
            "com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlock",
            "com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock",
            "com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock",
            "com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock",
            "com.simibubi.create.content.logistics.depot.EjectorBlock",
            "com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock",
            "com.simibubi.create.content.trains.station.StationBlock",
            "com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock",
            "com.simibubi.create.content.contraptions.bearing.BearingBlock",
            "dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlock",
            "dev.simulated_team.simulated.content.blocks.handle.HandleBlock",
            "dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlock",
            "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlock",
            // Supplementaries: levers, lids, locks.
            SUPPLEMENTARIES + "SconceLeverBlock",
            SUPPLEMENTARIES + "SafeBlock", SUPPLEMENTARIES + "LunchBoxBlock", SUPPLEMENTARIES + "AbstractPresentBlock",
            SUPPLEMENTARIES + "CageBlock", SUPPLEMENTARIES + "FaucetBlock", SUPPLEMENTARIES + "BellowsBlock",
            SUPPLEMENTARIES + "SpeakerBlock", SUPPLEMENTARIES + "TurnTableBlock", SUPPLEMENTARIES + "SackBlock",
            SUPPLEMENTARIES + "CannonBlock", SUPPLEMENTARIES + "PulleyBlock", SUPPLEMENTARIES + "LockBlock",
            SUPPLEMENTARIES + "DoormatBlock");

    private static final Map<Class<?>, Boolean> KNOWN = new HashMap<>();

    /** Where the look is on the block, and the arm's swing: a swing begun is a use. */
    private record Seen(BlockState block, boolean swinging, int swingTime, Spot looked) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Seen s && s.block == block && s.swinging == swinging && s.swingTime == swingTime;
        }

        @Override
        public int hashCode() {
            return swingTime;
        }
    }

    private final Map<UUID, Spot> looked = new HashMap<>();

    @Override
    public boolean matches(BlockState block) {
        return KNOWN.computeIfAbsent(block.getBlock().getClass(), type -> {
            for (String name : BLOCKS) if (ModAccess.is(type, name)) return true;
            return false;
        });
    }

    @Override
    public Spot hover(AbstractClientPlayer player, BlockPos pos, BlockState block, BlockHitResult hit) {
        Spot spot = new Spot(hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getNormal()));
        looked.put(player.getUUID(), spot);
        return spot;
    }

    @Override
    public Object snapshot(AbstractClientPlayer player, Level level, BlockPos pos, BlockState block) {
        return new Seen(block, player.swinging, player.swingTime, looked.get(player.getUUID()));
    }

    @Override
    public Gesture changed(BlockPos pos, Object was, Object is) {
        if (!(was instanceof Seen before) || !(is instanceof Seen now) || now.looked == null) return null;
        boolean begun = now.swinging && (!before.swinging || now.swingTime < before.swingTime);
        return begun ? new Gesture(now.looked, Motion.TAP) : null;
    }
}
