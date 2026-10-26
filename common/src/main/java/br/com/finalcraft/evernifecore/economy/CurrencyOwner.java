package br.com.finalcraft.evernifecore.economy;

import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import jakarta.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Whose wallet a {@link Currency} moves: a player, a group or the server as a whole. A currency says which kinds it
 * keeps wallets for through {@link Currency#supports}. Immutable, any thread.
 */
public final class CurrencyOwner {

    public enum Kind { PLAYER, GROUP, GLOBAL }

    /** The {@link ProgressOwner} kind a group is written with. */
    public static final String GROUP_KIND = "group";

    private static final CurrencyOwner GLOBAL_OWNER = new CurrencyOwner(Kind.GLOBAL, null, null);

    private final Kind kind;
    private final UUID uuid;
    private final String groupId;

    private CurrencyOwner(Kind kind, UUID uuid, String groupId) {
        this.kind = kind;
        this.uuid = uuid;
        this.groupId = groupId;
    }

    public static CurrencyOwner player(UUID uuid) {
        return new CurrencyOwner(Kind.PLAYER, Objects.requireNonNull(uuid, "A player wallet is keyed by the player's uuid - 'uuid' cannot be null."), null);
    }

    /** @param groupId the group's own id, {@code owner:name} of the plugin that keeps groups */
    public static CurrencyOwner group(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("A group wallet needs the group's id, e.g. group(\"finalparty:abc\").");
        }
        return new CurrencyOwner(Kind.GROUP, null, groupId.trim());
    }

    public static CurrencyOwner global() {
        return GLOBAL_OWNER;
    }

    /** The wallet of {@code owner}; empty for a region or world owner - no currency has a wallet for a place. */
    public static Optional<CurrencyOwner> of(ProgressOwner owner) {
        if (owner == null) {
            return Optional.empty();
        }
        if (owner.isKind(ProgressOwner.PLAYER)) {
            return Optional.of(player(UUID.fromString(owner.getId())));
        }
        if (owner.isKind(GROUP_KIND)) {
            return Optional.of(group(owner.getId()));
        }
        if (owner.isKind(ProgressOwner.GLOBAL)) {
            return Optional.of(GLOBAL_OWNER);
        }
        return Optional.empty();
    }

    /**
     * Reads what {@link #toString()} writes.
     *
     * @throws IllegalArgumentException when the text is none of {@code player:<uuid>}, {@code group:<id>}, {@code global}
     */
    public static CurrencyOwner parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.equalsIgnoreCase("global")) {
            return GLOBAL_OWNER;
        }
        int colon = trimmed.indexOf(':');
        String kind = colon < 0 ? "" : trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
        String id = colon < 0 ? "" : trimmed.substring(colon + 1);
        try {
            if (kind.equals("player")) {
                return player(UUID.fromString(id));
            }
            if (kind.equals(GROUP_KIND)) {
                return group(id);
            }
        } catch (IllegalArgumentException malformed) {
            //falls through to the message that shows every accepted form
        }
        throw new IllegalArgumentException("'" + trimmed + "' is not a wallet owner: write player:<uuid>, group:<group id> or global.");
    }

    public Kind getKind() {
        return kind;
    }

    /** The player's uuid; null unless {@link Kind#PLAYER}. */
    public @Nullable UUID getUuid() {
        return uuid;
    }

    /** The group's id; null unless {@link Kind#GROUP}. */
    public @Nullable String getGroupId() {
        return groupId;
    }

    /** {@code player:<uuid>}, {@code group:<id>} or {@code global} - the form {@link #parse} and the config codec read. */
    @Override
    public String toString() {
        switch (kind) {
            case PLAYER:
                return "player:" + uuid;
            case GROUP:
                return GROUP_KIND + ":" + groupId;
            default:
                return "global";
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CurrencyOwner)) {
            return false;
        }
        CurrencyOwner that = (CurrencyOwner) other;
        return kind == that.kind && Objects.equals(uuid, that.uuid) && Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, uuid, groupId);
    }
}
