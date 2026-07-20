package strm.emfcompat.ironspells.compat;

import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Helpers for detecting Iron's Spells 'n Spellbooks casting state on a client player.
 */
public final class IronSpellsCompat {

    private IronSpellsCompat() {
    }

    /**
     * Returns {@code true} if the player is currently casting a spell.
     * <p>For the local player the immediately-updated client cast state is used; for other
     * players the synced spell data sent by the server is used.</p>
     */
    public static boolean isCasting(AbstractClientPlayer player) {
        if (player == Minecraft.getInstance().player) {
            return ClientMagicData.isCasting();
        }
        return ClientMagicData.getSyncedSpellData(player).isCasting();
    }
}
