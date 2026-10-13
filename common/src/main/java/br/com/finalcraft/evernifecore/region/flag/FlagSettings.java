package br.com.finalcraft.evernifecore.region.flag;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everyconfig.annotation.Comment;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;

import java.time.Duration;

/**
 * The server-wide settings of one flag, kept apart from the values regions hold: a Jackson POJO read from
 * {@code <owner's data folder>/regions/flags/<name>.yml}, under its {@code Settings:} root, while the file is
 * generated ({@link RegionFlag#getSettingsFilePolicy()}). A flag with settings of its own extends this class and
 * returns it from {@link RegionFlag#getSettingsType()}.
 *
 * <p>Times are written as {@link FCTimeFrame} ({@code 2s}, {@code 1h30m}); the {@link Duration} accessors are what
 * code reads.</p>
 */
public class FlagSettings {

    @Comment({"The least time between two effects of this flag on the same player (a deny message, a sound...).",
            "0s runs the effect on every denial; the flag may declare a higher floor."})
    @JsonProperty
    private FCTimeFrame triggerInterval = FCTimeFrame.of(Duration.ZERO);

    @Comment({"How often a player standing inside a region with this flag is checked again, for flags that re-check",
            "over time. null keeps the interval the flag itself declares."})
    @JsonProperty
    private @Nullable FCTimeFrame revalidationInterval;

    /** At most one {@link RegionFlag#onApplied} per player in this window; zero runs it every time. */
    @JsonIgnore
    public Duration getTriggerInterval() {
        return triggerInterval.toDuration();
    }

    @JsonIgnore
    public void setTriggerInterval(Duration triggerInterval) {
        this.triggerInterval = FCTimeFrame.of(triggerInterval);
    }

    /** Overrides the period of a {@link Revalidation#interval} policy; null keeps the flag's own. */
    @JsonIgnore
    public @Nullable Duration getRevalidationInterval() {
        return revalidationInterval == null ? null : revalidationInterval.toDuration();
    }

    @JsonIgnore
    public void setRevalidationInterval(@Nullable Duration revalidationInterval) {
        this.revalidationInterval = revalidationInterval == null ? null : FCTimeFrame.of(revalidationInterval);
    }
}
