package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.group.GroupId;
import br.com.finalcraft.evernifecore.region.RegionId;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Who a counter, a variable or a balance belongs to: a {@code kind} and an {@code id} - {@code player:<uuid>},
 * {@code global}, {@code region:<owner>:<name>}, {@code world:<name>}, {@code group:<id>}. A kind other than the
 * core's four is answered by a {@link ProgressOwnerProvider} registered in {@link ProgressOwners}; the value itself
 * builds without one. Immutable.
 */
public final class ProgressOwner {

    public static final String PLAYER = "player";
    public static final String GLOBAL = "global";
    public static final String REGION = "region";
    public static final String WORLD = "world";
    public static final String GROUP = "group";

    private static final ProgressOwner GLOBAL_OWNER = new ProgressOwner(GLOBAL, "");

    private final String kind;
    private final String id;

    private ProgressOwner(String kind, String id) {
        this.kind = kind;
        this.id = id;
    }

    /**
     * @throws IllegalArgumentException when the kind breaks {@code [a-z0-9_-]{1,64}}, or the id is empty for a kind
     * other than {@code global}
     */
    public static ProgressOwner of(String kind, String id) {
        String checked = RegionId.requireName("owner kind", kind);
        if (checked.equals(GLOBAL)) {
            return GLOBAL_OWNER;
        }
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("The owner of kind '" + checked + "' needs an id, written " + checked + ":<id>"
                    + " (e.g. group:finalparty/abc); only 'global' has none.");
        }
        return new ProgressOwner(checked, id.trim());
    }

    public static ProgressOwner player(UUID player) {
        return new ProgressOwner(PLAYER, Objects.requireNonNull(player, "player").toString());
    }

    /** The server as a whole. */
    public static ProgressOwner global() {
        return GLOBAL_OWNER;
    }

    public static ProgressOwner region(RegionId region) {
        return new ProgressOwner(REGION, region.toString());
    }

    public static ProgressOwner world(String worldName) {
        return of(WORLD, worldName);
    }

    /** A group, of a {@code GroupManager}, a region or a registered {@code GroupProvider}: {@code group:<owner>:<name>}. */
    public static ProgressOwner group(GroupId group) {
        return new ProgressOwner(GROUP, group.toString());
    }

    /**
     * Reads what {@link #toString()} writes: {@code global}, or {@code kind:id} split at the first {@code :}.
     *
     * @throws IllegalArgumentException when the text is neither
     */
    public static ProgressOwner parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            if (trimmed.equalsIgnoreCase(GLOBAL)) {
                return GLOBAL_OWNER;
            }
            throw new IllegalArgumentException("'" + trimmed + "' is not an owner: write global, or kind:id such as player:<uuid>,"
                    + " region:finalprotection:arena, world:world or group:finalparty/abc.");
        }
        return of(trimmed.substring(0, colon), trimmed.substring(colon + 1));
    }

    public String getKind() {
        return kind;
    }

    /** Empty for {@code global}. */
    public String getId() {
        return id;
    }

    public boolean isKind(String kind) {
        return this.kind.equals(kind.toLowerCase(Locale.ROOT));
    }

    /** {@code global}, or {@code kind:id}. */
    @Override
    public String toString() {
        return id.isEmpty() ? kind : kind + ":" + id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProgressOwner && ((ProgressOwner) other).kind.equals(kind) && ((ProgressOwner) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + id.hashCode();
    }
}
