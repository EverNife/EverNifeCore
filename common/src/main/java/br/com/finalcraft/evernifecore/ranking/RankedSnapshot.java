package br.com.finalcraft.evernifecore.ranking;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One ordered read of the source, numbered and already cut to the list's ceiling. Immutable and shared by every
 * reader of the list - the chat page, the placeholder and any screen read the same instance.
 */
public final class RankedSnapshot<T> {

    private final List<RankedEntry<T>> entries;
    private final int totalBeforeCap;
    private final long builtAt;

    RankedSnapshot(List<RankedEntry<T>> entries, int totalBeforeCap, long builtAt) {
        this.entries = Collections.unmodifiableList(entries);
        this.totalBeforeCap = totalBeforeCap;
        this.builtAt = builtAt;
    }

    public List<RankedEntry<T>> getEntries() {
        return entries;
    }

    /** How many entries this snapshot carries, after the ceiling. */
    public int getShownCount() {
        return entries.size();
    }

    /** How many the source returned, before {@code maxEntries} cut it. */
    public int getTotalCount() {
        return totalBeforeCap;
    }

    /** Whether the ceiling dropped something the source had returned. */
    public boolean isTruncated() {
        return totalBeforeCap > entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** When the read finished, in epoch millis of the {@code ECClock}. */
    public long getBuiltAt() {
        return builtAt;
    }

    /**
     * The 1-based position of {@code player} on this snapshot, or {@code null} when they are not on it - or the
     * entries are not players at all. A whole scan: ask once.
     */
    public @Nullable Integer positionOf(@Nullable UUID player) {
        if (player == null) {
            return null;
        }
        for (RankedEntry<T> entry : entries) {
            if (player.equals(uniqueIdOf(entry.getObject()))) {
                return entry.getPosition();
            }
        }
        return null;
    }

    static @Nullable UUID uniqueIdOf(Object object) {
        if (object instanceof FPlayer) return ((FPlayer) object).getUniqueId();
        if (object instanceof IPlayerData) return ((IPlayerData) object).getUniqueId();
        return null;
    }
}
