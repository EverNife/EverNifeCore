package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import jakarta.annotation.Nullable;

/** A player is about to enter a region; leaves are posted before enters. */
public class RegionEnterEvent extends RegionBoundaryEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionEnterEvent.class);
    }

    public RegionEnterEvent(Region region, FPlayer player, @Nullable WorldLocPos from, @Nullable WorldLocPos to, MoveCause cause) {
        super(region, player, from, to, cause);
    }
}
