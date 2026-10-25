package br.com.finalcraft.evernifecore.action.provenance;

import br.com.finalcraft.evernifecore.action.TypeKey;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Where something came from. {@link #NATURAL} is the absence of a record and is never stored. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Provenance {

    public static final Provenance NATURAL = new Provenance(OriginKind.NATURAL, null, Instant.EPOCH, null);

    private OriginKind kind;
    private @Nullable UUID player;
    private long at;
    private @Nullable String key;

    /** Jackson's: a stored block value. */
    private Provenance() {
    }

    public Provenance(OriginKind kind, @Nullable UUID player, Instant at, @Nullable TypeKey key) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.player = player;
        this.at = at.toEpochMilli();
        this.key = key == null ? null : key.toString();
    }

    public OriginKind getKind() {
        return kind;
    }

    /** Who placed, dropped, bred or threw the egg, when the platform said; null for SPAWNER, COMMAND and NATURAL. */
    public @Nullable UUID getPlayer() {
        return player;
    }

    public Instant getAt() {
        return Instant.ofEpochMilli(at);
    }

    /** What was placed, spawned or dropped - tells a placed sapling from the tree that grew where it stood. */
    public @Nullable TypeKey getKey() {
        return key == null ? null : TypeKey.parse(key);
    }

    /** Whether {@code player} placed or dropped it themselves. */
    public boolean isOwn(@Nullable FPlayer player) {
        return player != null && (kind == OriginKind.PLAYER_PLACED || kind == OriginKind.PLAYER_DROP)
                && player.getUniqueId().equals(this.player);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Provenance)) {
            return false;
        }
        Provenance that = (Provenance) other;
        return kind == that.kind && at == that.at && Objects.equals(player, that.player) && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, player, at, key);
    }

    @Override
    public String toString() {
        return "Provenance{" + kind + (player == null ? "" : ", " + player) + (key == null ? "" : ", " + key) + ", " + getAt() + "}";
    }
}
