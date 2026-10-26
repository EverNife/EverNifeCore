package br.com.finalcraft.evernifecore.action;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagCategory;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionLookup;
import br.com.finalcraft.evernifecore.region.session.RegionPlayerSession;
import jakarta.annotation.Nullable;

import java.util.Objects;

/** The player a {@link FlagCategory#SESSION} flag is applied to, and the region the value came from when one did. */
public final class SessionContext {

    private final FPlayer player;
    private final @Nullable Region region;
    private final @Nullable RegionPlayerSession session;

    public SessionContext(FPlayer player, @Nullable Region region) {
        this(player, region, null);
    }

    /**
     * @param region  the region the value came from; null for a value only a holder that follows the player gave
     * @param session the session the flag is applied in; null reads the player's from {@link RegionLookup#get()}
     */
    public SessionContext(FPlayer player, @Nullable Region region, @Nullable RegionPlayerSession session) {
        this.player = Objects.requireNonNull(player, "player");
        this.region = region;
        this.session = session;
    }

    public FPlayer getPlayer() {
        return player;
    }

    /** The region the value came from; null when only a holder that follows the player (a party) gave it. */
    public @Nullable Region getRegion() {
        return region;
    }

    /** The settings of {@code flag} in effect here: the region's own when there is a region, else the server's. */
    public <S extends FlagSettings> S getSettings(Flag<?> flag) {
        return region != null ? region.getEffectiveFlagSettings(flag) : flag.getSettings();
    }

    public RegionPlayerSession getSession() {
        return session != null ? session : RegionLookup.get().getSession(player);
    }
}
