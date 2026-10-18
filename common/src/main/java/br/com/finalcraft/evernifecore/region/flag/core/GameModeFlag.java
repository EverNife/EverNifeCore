package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.HeldPlayerState;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;

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
        access.setGameMode(context.getPlayer(), (ECGameMode) held.getHolders(getSlot()).get(getKey()));
    }
}
