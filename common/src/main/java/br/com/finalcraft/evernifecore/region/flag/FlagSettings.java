package br.com.finalcraft.evernifecore.region.flag;

import jakarta.annotation.Nullable;

import java.time.Duration;

/**
 * The server-wide settings of one flag, kept apart from the values regions hold: a Jackson POJO read from
 * {@code <owner's data folder>/regions/flags/<name>.yml}. A flag with settings of its own extends this class and
 * returns it from {@link RegionFlag#getSettingsType()}.
 */
public class FlagSettings {

    private Duration triggerInterval = Duration.ofSeconds(2);
    private @Nullable Duration revalidationInterval;

    /** At most one {@link RegionFlag#onApplied} per player in this window; zero leaves the pace to the flag. */
    public Duration getTriggerInterval() {
        return triggerInterval;
    }

    public void setTriggerInterval(Duration triggerInterval) {
        this.triggerInterval = triggerInterval;
    }

    /** Overrides the period of a {@link Revalidation#interval} policy; null keeps the flag's own. */
    public @Nullable Duration getRevalidationInterval() {
        return revalidationInterval;
    }

    public void setRevalidationInterval(@Nullable Duration revalidationInterval) {
        this.revalidationInterval = revalidationInterval;
    }
}
