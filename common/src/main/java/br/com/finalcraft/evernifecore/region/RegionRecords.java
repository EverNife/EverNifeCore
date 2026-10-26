package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupMember;
import br.com.finalcraft.evernifecore.group.GroupRole;
import br.com.finalcraft.evernifecore.group.Invite;
import br.com.finalcraft.evernifecore.group.MembershipCheck;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.storage.RegionRecord;
import br.com.finalcraft.evernifecore.region.storage.RegionRecord.GroupRecord;
import br.com.finalcraft.evernifecore.region.storage.RegionRecord.InviteRecord;
import br.com.finalcraft.evernifecore.region.storage.RegionRecord.MemberRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Turns a region into its stored record and back. The parent is stored by name and linked by the caller. */
final class RegionRecords {

    private RegionRecords() {
    }

    private static ObjectMapper mapper() {
        return ConfigFactory.inMemoryCodec().getObjectMapper();
    }

    static RegionRecord toRecord(Region region) {
        RegionRecord record = new RegionRecord();
        record.name = region.getId().getName();
        record.world = region.getWorld();
        record.priority = region.getPriority();
        Region parent = region.getParent();
        record.parent = parent == null ? null : parent.getId().getName();
        record.persistence = region.getPersistence();
        record.expiresAt = region.getExpiresAt();
        record.shape = mapper().valueToTree(region.getShape());
        record.flags.putAll(region.getFlagStore().toRaw());
        for (Group group : region.getGroups().values()) {
            GroupRecord stored = new GroupRecord();
            stored.priority = group.getPriority();
            stored.permission = group.getPermission();
            stored.check = group.getMembershipCheck().toText();
            for (GroupMember member : group.getMembers()) {
                List<String> roles = new ArrayList<>();
                for (GroupRole role : member.getRoles()) {
                    roles.add(role.getName());
                }
                roles.addAll(member.getUnknownRoles());
                stored.members.put(member.getUuid().toString(), new MemberRecord(member.getJoinedAt(), member.getExpiresAt(), roles));
            }
            for (Invite invite : group.getInvites()) {
                stored.invites.put(invite.getPlayer().toString(), new InviteRecord(invite.getInvitedAt(), invite.getExpiresAt(),
                        invite.getInvitedBy() == null ? null : invite.getInvitedBy().toString()));
            }
            stored.leader = group.getLeader() == null ? null : group.getLeader().toString();
            stored.displayName = group.getDisplayName();
            stored.flags.putAll(group.getFlagStore().toRaw());
            record.groups.put(group.getName(), stored);
        }
        record.flagSettings.putAll(region.getFlagSettingsForStorage());
        record.tags = new ArrayList<>(region.getTags());
        record.metadata = region.getMetadata();
        record.displayName = region.getDisplayName();
        record.description = region.getDescription();
        return record;
    }

    /**
     * A detached region of {@code manager} as the record describes it, without its parent. Loading is not a
     * change: the region is not attached yet, so nothing it does here is evented or written.
     *
     * @throws IllegalArgumentException when the record is incomplete or a part of it does not read
     */
    static Region toRegion(RegionManager manager, RegionRecord record) {
        if (record.name == null || record.world == null || record.shape == null) {
            throw new IllegalArgumentException("the record has no " + (record.name == null ? "name" : record.world == null ? "world" : "shape"));
        }
        Shape shape;
        try {
            shape = mapper().treeToValue(record.shape, Shape.class);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalArgumentException("its shape " + record.shape + " does not read: " + unreadable.getOriginalMessage(), unreadable);
        }
        RegionOptions options = RegionOptions.defaults()
                .withPriority(record.priority)
                .withExpiresAt(record.expiresAt)
                .withTags(record.tags.toArray(new String[0]));
        if (record.metadata != null) {
            options = options.withMetadata(record.metadata);
        }
        Region region = new Region(manager, record.name, record.world, shape, options);
        region.setDisplayName(record.displayName);
        region.setDescription(record.description);
        record.flags.forEach(region.getFlagStore()::putRaw);
        for (Map.Entry<String, GroupRecord> entry : record.groups.entrySet()) {
            GroupRecord stored = entry.getValue();
            Group group = region.getGroup(entry.getKey());
            group.restore(stored.priority, stored.permission, MembershipCheck.parse(stored.check), stored.displayName, null);
            stored.members.forEach((uuid, member) -> group.restoreMember(UUID.fromString(uuid), member.joinedAt, member.expiresAt,
                    member.roles));
            stored.invites.forEach((uuid, invite) -> group.restoreInvite(UUID.fromString(uuid), invite.invitedAt, invite.expiresAt,
                    invite.invitedBy == null ? null : UUID.fromString(invite.invitedBy)));
            group.restoreLeader(stored.leader == null ? null : UUID.fromString(stored.leader));
            stored.flags.forEach(group.getFlagStore()::putRaw);
        }
        record.flagSettings.forEach(region::putRawFlagSettings);
        return region;
    }
}
