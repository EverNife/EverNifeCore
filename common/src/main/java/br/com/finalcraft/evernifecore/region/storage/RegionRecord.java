package br.com.finalcraft.evernifecore.region.storage;

import br.com.finalcraft.evernifecore.region.Persistence;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal: one region as its owner's storage keeps it, format version {@link #v}. Flag values use the raw form
 * {@code {value, expiresAt?, permanent?}} by {@code owner:name}, so a value of a flag that is not registered is
 * written back exactly as it was read. The shape is kept as a tree and read per record, so one unreadable shape
 * skips its region instead of failing the whole load.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RegionRecord {

    public static final int VERSION = 1;

    public int v = VERSION;
    public String name;
    public String world;
    public int priority;
    /** The parent's name - the same owner's, the same world's. */
    public @Nullable String parent;
    public Persistence persistence = Persistence.PERSISTENT;
    public @Nullable Instant expiresAt;
    public JsonNode shape;
    public Map<String, JsonNode> flags = new LinkedHashMap<>();
    public Map<String, GroupRecord> groups = new LinkedHashMap<>();
    /** The region's own settings override of a flag, by {@code owner:name}: the settings POJO as a tree. */
    public Map<String, JsonNode> flagSettings = new LinkedHashMap<>();
    public List<String> tags = new ArrayList<>();
    public @Nullable JsonNode metadata;

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class GroupRecord {
        public int priority;
        public @Nullable String permission;
        /** {@code MembershipCheck} in its text form. */
        public String check = "exact";
        /** By the member's UUID. */
        public Map<String, MemberRecord> members = new LinkedHashMap<>();
        public Map<String, JsonNode> flags = new LinkedHashMap<>();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MemberRecord {
        public Instant joinedAt;
        public @Nullable Instant expiresAt;

        public MemberRecord() {
        }

        public MemberRecord(Instant joinedAt, @Nullable Instant expiresAt) {
            this.joinedAt = joinedAt;
            this.expiresAt = expiresAt;
        }
    }
}
