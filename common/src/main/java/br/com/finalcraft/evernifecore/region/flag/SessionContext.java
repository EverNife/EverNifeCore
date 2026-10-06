package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;

import java.util.Objects;

/** The player and the region a {@link FlagCategory#SESSION} flag is applied to. */
public final class SessionContext {

    private final FPlayer player;
    private final Region region;

    public SessionContext(FPlayer player, Region region) {
        this.player = Objects.requireNonNull(player, "player");
        this.region = Objects.requireNonNull(region, "region");
    }

    public FPlayer getPlayer() {
        return player;
    }

    public Region getRegion() {
        return region;
    }

    public RegionPlayerSession getSession() {
        return RegionLookup.get().getSession(player);
    }
}
