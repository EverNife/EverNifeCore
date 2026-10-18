package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagSettings;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.Revalidation;
import br.com.finalcraft.evernifecore.region.flag.context.SessionContext;
import br.com.finalcraft.evernifecore.region.playerstate.HeldPlayerState;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerTime;

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
        TimeLock value = (TimeLock) held.getHolders(getSlot()).get(getKey());
        TimeLockSettings settings = context.getRegion().getEffectiveFlagSettings(this);
        access.setTime(context.getPlayer(), new PlayerTime(value.getTime().getTicks(), value.isRelative()), settings.getTransition());
    }
}
