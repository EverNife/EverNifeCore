package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.playerstate.HeldPlayerState;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;

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
        access.setWeather(context.getPlayer(), (String) held.getHolders(getSlot()).get(getKey()));
    }
}
