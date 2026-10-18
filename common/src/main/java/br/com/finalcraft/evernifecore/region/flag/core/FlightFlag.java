package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.HeldPlayerState;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * {@code allow-fly} and {@code force-fly}, which share the player's flight ({@link HeldPlayerState#FLIGHT}). Where no region
 * defines one it is {@link Tristate#UNDEFINED} and holds nothing. Together, {@code force-fly = TRUE} wins: the player
 * flies, may fly, and cannot land ({@link EventSource#FLIGHT_TOGGLE}). Else {@code allow-fly = TRUE} lets them fly, and
 * {@code allow-fly = FALSE} lands everyone but a spectator - creative included.
 */
public final class FlightFlag extends PlayerStateFlag<Tristate> {

    private final boolean forced;

    private FlightFlag(String name, boolean forced) {
        super(name, FlagTypes.TRISTATE, forced ? new HashSet<>(Arrays.asList(EventSource.MOVE, EventSource.FLIGHT_TOGGLE))
                : Collections.singleton(EventSource.MOVE), Tristate.UNDEFINED, IPlayerStateAccess.Capability.FLIGHT);
        this.forced = forced;
    }

    public static FlightFlag allowFly() {
        return new FlightFlag("allow-fly", false);
    }

    public static FlightFlag forceFly() {
        return new FlightFlag("force-fly", true);
    }

    /** Whether this is {@code force-fly}. */
    public boolean isForced() {
        return forced;
    }

    @Override
    public String getDescription() {
        return forced ? "TRUE: whoever stands in the region flies and cannot land; it wins over allow-fly = FALSE. The flight they had"
                + " comes back on the way out."
                : "TRUE lets whoever stands in the region fly; FALSE takes flight from everyone but a spectator, creative included."
                + " The flight they had comes back on the way out.";
    }

    /** {@code force-fly}: TRUE wins a tie. {@code allow-fly}: FALSE wins it. */
    @Override
    public Tristate resolveTie(List<Tristate> values) {
        Tristate winner = forced ? Tristate.TRUE : Tristate.FALSE;
        return values.contains(winner) ? winner : values.get(0);
    }

    @Override
    protected boolean acts(IPlayerStateAccess access, Tristate value) {
        return forced ? value == Tristate.TRUE : value != Tristate.UNDEFINED;
    }

    @Override
    protected String getSlot() {
        return HeldPlayerState.FLIGHT;
    }

    @Override
    protected void apply(SessionContext context, IPlayerStateAccess access, HeldPlayerState held) {
        FPlayer player = context.getPlayer();
        Tristate force = Tristate.UNDEFINED;
        Tristate allow = Tristate.UNDEFINED;
        for (Map.Entry<FlagKey, Object> holder : held.getHolders(getSlot()).entrySet()) {
            if (holder.getKey().equals(CoreFlags.FORCE_FLY.getKey())) {
                force = (Tristate) holder.getValue();
            } else if (holder.getKey().equals(CoreFlags.ALLOW_FLY.getKey())) {
                allow = (Tristate) holder.getValue();
            }
        }
        if (force == Tristate.TRUE) {
            access.setAllowFlight(player, true);
            access.setFlying(player, true);
        } else if (allow == Tristate.TRUE) {
            access.setAllowFlight(player, true);
        } else if (allow == Tristate.FALSE && !isSpectator(access, player)) {
            access.setAllowFlight(player, false);
        }
    }

    private static boolean isSpectator(IPlayerStateAccess access, FPlayer player) {
        return access.supports(IPlayerStateAccess.Capability.GAME_MODE) && access.getGameMode(player) == ECGameMode.SPECTATOR;
    }
}
