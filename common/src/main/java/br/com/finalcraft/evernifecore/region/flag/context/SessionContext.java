package br.com.finalcraft.evernifecore.region.flag.context;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.flag.FlagCategory;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** The player and the region a {@link FlagCategory#SESSION} flag is applied to. */
public final class SessionContext {

    private final FPlayer player;
    private final Region region;
    private final @Nullable RegionPlayerSession session;

    public SessionContext(FPlayer player, Region region) {
        this(player, region, null);
    }

    /** @param session the session the flag is applied in; null reads the player's from {@link RegionLookup#get()} */
    public SessionContext(FPlayer player, Region region, @Nullable RegionPlayerSession session) {
        this.player = Objects.requireNonNull(player, "player");
        this.region = Objects.requireNonNull(region, "region");
        this.session = session;
    }

    public FPlayer getPlayer() {
        return player;
    }

    public Region getRegion() {
        return region;
    }

    public RegionPlayerSession getSession() {
        return session != null ? session : RegionLookup.get().getSession(player);
    }
}
