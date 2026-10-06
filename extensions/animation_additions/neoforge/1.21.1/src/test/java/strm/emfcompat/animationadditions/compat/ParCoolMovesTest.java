package strm.emfcompat.animationadditions.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParCoolMovesTest {
    @Test
    void aFastRunKeepsTheAddonOn() {
        assertFalse(ParCoolMoves.yields(List.of()));
        assertFalse(ParCoolMoves.yields(List.of("fast_run")));
        assertFalse(ParCoolMoves.yields(List.of("fast_run", "stride")));
        assertFalse(ParCoolMoves.yields(List.of("fast_run", "wall_run")));
    }

    @Test
    void theMovesMeasuredToStayOnStayOn() {
        for (String name : List.of("slide_down", "breakfall_no_move", "pole_climb", "hide_in_block_stand", "charge_jump", "long_jump"))
            assertFalse(ParCoolMoves.yields(List.of(name)), name);
    }

    @Test
    void oneWeakMoveAmongOthersTurnsItOff() {
        assertTrue(ParCoolMoves.yields(List.of("fast_run", "slide")));
        assertTrue(ParCoolMoves.yields(List.of("crawl", "fast_run", "slide")));
        assertTrue(ParCoolMoves.yields(List.of("hang_on", "slide_down")));
        assertTrue(ParCoolMoves.yields(List.of("fast_run", "horizontal_wall_run")));
    }

    @Test
    void everyDirectionOfAMoveIsCovered() {
        for (String name : List.of("dodge_right", "dodge_front", "dodge_back", "vault_forward", "vault_side",
                "hang_down", "hang_down_jump_forward", "hang_down_jump_backward", "dive", "dive_in_air", "skydive", "skydive_in_air"))
            assertTrue(ParCoolMoves.yields(List.of(name)), name);
    }

    @Test
    void theRunUpToAWallRunIsNotAWeakMove() {
        assertFalse(ParCoolMoves.WEAK.contains(ParCoolMoves.FAST_RUN));
    }
}
