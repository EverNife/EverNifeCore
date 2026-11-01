package br.com.finalcraft.evernifecore.flag.core;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;

import java.util.Collections;

/** Shows whoever stands in the region one weather, and gives back the weather they saw on the way out. */
public final class WeatherLockFlag extends PlayerStateFlag<String> {

    public WeatherLockFlag() {
        super("weather-lock", FlagTypes.WEATHER, Collections.singleton(EventSource.MOVE), null, IPlayerStateAccess.Capability.WEATHER);
    }

    @Override
    public String getDescription() {
        return "Shows whoever stands in the region one weather - clear, rain or a weather id of the server - and gives back the"
                + " weather they saw on the way out.";
    }

    @Override
    protected String getSlot() {
        return HeldPlayerState.WEATHER;
    }

    @Override
    protected void apply(SessionContext context, IPlayerStateAccess access, HeldPlayerState held) {
        Object weather = held.getHolders(getSlot()).get(getHolderId());
        if (weather != null) { //null: this flag does not hold the slot
            access.setWeather(context.getPlayer(), (String) weather);
        }
    }

    @Override
    public Set<String> getChannels() {
        return new HashSet<>(Arrays.asList("slot:weather"));
    }
}
