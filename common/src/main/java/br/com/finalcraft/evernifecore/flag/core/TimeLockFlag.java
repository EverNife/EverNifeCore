package br.com.finalcraft.evernifecore.flag.core;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.SessionContext;
import br.com.finalcraft.evernifecore.flag.FlagSettings;
import br.com.finalcraft.evernifecore.flag.FlagTypes;
import br.com.finalcraft.evernifecore.flag.Revalidation;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.player.state.PlayerTime;

import java.time.Duration;
import java.util.Collections;

/** Locks the clock of whoever stands in the region to one time of day, and gives back the clock they had on the way out. */
public final class TimeLockFlag extends PlayerStateFlag<TimeLock> {

    public TimeLockFlag() {
        super("time-lock", FlagTypes.jackson("time-lock", TimeLock.class), Collections.singleton(EventSource.MOVE), null,
                IPlayerStateAccess.Capability.TIME);
    }

    @Override
    public String getDescription() {
        return "Locks the clock of whoever stands in the region to one time of day (DAY, NOON, NIGHT or MIDNIGHT; relative=true keeps"
                + " it running from there), and gives back the clock they had on the way out.";
    }

    @Override
    public Revalidation getRevalidation() {
        return Revalidation.interval(Duration.ofSeconds(30));   //another plugin may have moved the client's clock meanwhile
    }

    @Override
    public Class<? extends FlagSettings> getSettingsType() {
        return TimeLockSettings.class;
    }

    @Override
    protected String getSlot() {
        return HeldPlayerState.TIME;
    }

    @Override
    protected void apply(SessionContext context, IPlayerStateAccess access, HeldPlayerState held) {
        TimeLock value = (TimeLock) held.getHolders(getSlot()).get(getHolderId());
        if (value == null) { //this flag does not hold the slot
            return;
        }
        TimeLockSettings settings = context.getSettings(this);
        access.setTime(context.getPlayer(), new PlayerTime(value.getTime().getTicks(), value.isRelative()), settings.getTransition());
    }

    @Override
    public Set<String> getChannels() {
        return new HashSet<>(Arrays.asList("slot:time"));
    }
}
