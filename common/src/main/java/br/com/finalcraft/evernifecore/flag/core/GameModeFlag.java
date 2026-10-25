package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.player.state.ECGameMode;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;

import java.util.Collections;

/** Puts whoever stands in the region in one game mode, and gives back the mode they had on the way out. */
public final class GameModeFlag extends PlayerStateFlag<ECGameMode> {

    public GameModeFlag() {
        super("game-mode", FlagTypes.enumOf(ECGameMode.class), Collections.singleton(EventSource.MOVE), null,
                IPlayerStateAccess.Capability.GAME_MODE);
    }

    @Override
    public String getDescription() {
        return "Puts whoever stands in the region in one game mode, and gives back the mode they had on the way out. A mode the"
                + " server does not have (spectator before 1.8) does nothing.";
    }

    /** A mode this server version lacks changes nothing. */
    @Override
    protected boolean acts(IPlayerStateAccess access, ECGameMode value) {
        return access.supportsGameMode(value);
    }

    @Override
    protected String getSlot() {
        return HeldPlayerState.GAME_MODE;
    }

    @Override
    protected void apply(SessionContext context, IPlayerStateAccess access, HeldPlayerState held) {
        //after this flag let go, another holder (a held effect) keeps the slot: the mode it asked for stands alone
        ECGameMode mode = null;
        for (Object value : held.getHolders(getSlot()).values()) {
            if (value instanceof ECGameMode) {
                mode = (ECGameMode) value;
            }
        }
        Object own = held.getHolders(getSlot()).get(getHolderId());
        mode = own instanceof ECGameMode ? (ECGameMode) own : mode;
        if (mode != null) {
            access.setGameMode(context.getPlayer(), mode);
        }
    }
}
