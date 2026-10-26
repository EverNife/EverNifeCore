package br.com.finalcraft.evernifecore.group.storage;

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
 * Internal: one group of a {@code GroupManager} as its owner's storage keeps it, format version {@link #v}. Flag values
 * use the raw form of {@code FlagStore} by {@code owner:name}, so a value of a flag that is not registered is written
 * back exactly as it was read; a role the kind does not list stays as its name.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class GroupRecord {

    public static final int VERSION = 1;

    public int v = VERSION;
    public String name;
    /** The kind's key, {@code owner:name}. */
    public String kind;
    public @Nullable String leader;
    public @Nullable String displayName;
    public Instant createdAt;
    /** By the member's UUID. */
    public Map<String, MemberRecord> members = new LinkedHashMap<>();
    /** By the invited player's UUID. */
    public Map<String, InviteRecord> invites = new LinkedHashMap<>();
    public Map<String, JsonNode> flags = new LinkedHashMap<>();
    public @Nullable JsonNode metadata;
    public @Nullable String permission;
    /** {@code MembershipCheck} in its text form. */
    public String check = "exact";
    public int priority;

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class MemberRecord {
        public Instant joinedAt;
        public @Nullable Instant expiresAt;
        public List<String> roles = new ArrayList<>();

        public MemberRecord() {
        }

        public MemberRecord(Instant joinedAt, @Nullable Instant expiresAt, List<String> roles) {
            this.joinedAt = joinedAt;
            this.expiresAt = expiresAt;
            this.roles = roles;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class InviteRecord {
        public Instant invitedAt;
        public Instant expiresAt;
        public @Nullable String invitedBy;

        public InviteRecord() {
        }

        public InviteRecord(Instant invitedAt, Instant expiresAt, @Nullable String invitedBy) {
            this.invitedAt = invitedAt;
            this.expiresAt = expiresAt;
            this.invitedBy = invitedBy;
        }
    }
}
