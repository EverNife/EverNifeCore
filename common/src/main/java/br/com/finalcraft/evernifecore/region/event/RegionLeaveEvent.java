package br.com.finalcraft.evernifecore.region.event;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import jakarta.annotation.Nullable;

/** A player is about to leave a region; leaves are posted before enters. */
public class RegionLeaveEvent extends RegionBoundaryEvent {

    /** Bukkit-only: this event's own native handler list - see {@link ECEvent#getHandlerListOf(Class)}. */
    public static Object getHandlerList() {
        return ECEvent.getHandlerListOf(RegionLeaveEvent.class);
    }

    public RegionLeaveEvent(Region region, FPlayer player, @Nullable WorldLocPos from, @Nullable WorldLocPos to, MoveCause cause,
                            @Nullable Object nativeEvent) {
        super(region, player, from, to, cause, nativeEvent);
    }
}
