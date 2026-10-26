package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionCreatedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.group.DissolveCause;
import br.com.finalcraft.evernifecore.group.Group;
import br.com.finalcraft.evernifecore.group.GroupKind;
import br.com.finalcraft.evernifecore.group.JoinCause;
import br.com.finalcraft.evernifecore.group.LeaveCause;
import br.com.finalcraft.evernifecore.group.event.GroupCreatedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberJoinedEvent;
import br.com.finalcraft.evernifecore.group.event.GroupMemberLeftEvent;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Every change of a live region comes out as the event of its kind, with the right type or cause. */
class RegionEventsTest extends ManagedRegionFixture {

    @Test
    void everyPropertyChangeIsARegionChangedEventOfItsType() {
        Region parent = created(manager().create("parent", WORLD, box(0, 0, 20, 20)));
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        List<RegionChangedEvent> changes = record(RegionChangedEvent.class);

        region.setPriority(4);
        region.setParent(parent);
        region.addTag("a");
        region.addTag("a");   //no change, no event
        region.removeTag("a");
        region.setMetadata(JsonNodeFactory.instance.objectNode());
        region.setExpiresAt(Instant.parse("2040-01-01T00:00:00Z"));

        List<RegionChangedEvent.Type> types = new ArrayList<>();
        for (RegionChangedEvent change : changes) {
            assertSame(region, change.getRegion());
            types.add(change.getType());
        }
        assertEquals(Arrays.asList(RegionChangedEvent.Type.PRIORITY, RegionChangedEvent.Type.PARENT, RegionChangedEvent.Type.TAGS,
                RegionChangedEvent.Type.TAGS, RegionChangedEvent.Type.METADATA, RegionChangedEvent.Type.EXPIRES_AT), types);
    }

    @Test
    void theDisplayNameAndTheDescriptionHaveATypeEachAndBlankClears() {
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        List<RegionChangedEvent> changes = record(RegionChangedEvent.class);

        region.setDisplayName("&6Arena");
        region.setDescription("a room");
        region.setDisplayName("  ");

        assertEquals(Arrays.asList(RegionChangedEvent.Type.DISPLAY_NAME, RegionChangedEvent.Type.DESCRIPTION, RegionChangedEvent.Type.DISPLAY_NAME),
                changes.stream().map(RegionChangedEvent::getType).collect(Collectors.toList()));
        assertNull(region.getDisplayName());
        assertEquals("a room", region.getDescription());
    }

    @Test
    void flagChangesOfTheRegionAndOfItsGroupsAreFlagChangedEvents() {
        RoomFlag flag = registeredRoomFlag(owner);
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        Group group = region.getGroup("members");
        List<RegionFlagChangedEvent> changes = record(RegionFlagChangedEvent.class);

        region.setFlag(flag, Tristate.TRUE);
        group.setFlag(flag, Tristate.FALSE);
        region.removeFlag(flag);

        assertEquals(3, changes.size());
        assertNull(changes.get(0).getGroup());
        assertEquals(FlagChangeCause.SET, changes.get(0).getCause());
        assertNull(changes.get(0).getOldValue());
        assertEquals(Tristate.TRUE, changes.get(0).getNewValue().getValue());
        assertSame(group, changes.get(1).getGroup());
        assertSame(flag, changes.get(1).getFlag());
        assertEquals(Tristate.FALSE, changes.get(1).getNewValue().getValue());
        assertEquals(FlagChangeCause.REMOVED, changes.get(2).getCause());
        assertNull(changes.get(2).getNewValue());
    }

    @Test
    void groupChangesOfARegionAreGroupEventsThatNameTheRegionAndThePlayer() {
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        List<GroupEvent> changes = new ArrayList<>();
        onEvent(GroupCreatedEvent.class, changes::add);
        onEvent(GroupMemberJoinedEvent.class, changes::add);
        onEvent(GroupMemberLeftEvent.class, changes::add);
        onEvent(GroupDissolvedEvent.class, changes::add);
        UUID player = UUID.randomUUID();
        UUID stays = UUID.randomUUID();

        Group group = region.getGroup("members");
        group.add(player);
        group.remove(player, LeaveCause.PLUGIN);
        group.setPriority(1);
        group.setPermission("p");
        group.setPredicate(p -> true);
        group.add(stays);
        region.removeGroup("members");

        assertEquals(Arrays.asList("GroupCreatedEvent", "GroupMemberJoinedEvent", "GroupMemberLeftEvent", "GroupMemberJoinedEvent",
                "GroupMemberLeftEvent", "GroupDissolvedEvent"), changes.stream().map(e -> e.getClass().getSimpleName()).collect(Collectors.toList()));
        for (GroupEvent change : changes) {
            assertSame(region, change.getGroup().getRegion(), "a local group's event names its region");
            assertSame(GroupKind.REGION_LOCAL, change.getGroup().getKind());
        }
        assertEquals(player, ((GroupMemberJoinedEvent) changes.get(1)).getPlayer());
        assertEquals(JoinCause.ADDED, ((GroupMemberJoinedEvent) changes.get(1)).getCause());
        assertEquals(LeaveCause.PLUGIN, ((GroupMemberLeftEvent) changes.get(2)).getCause());
        assertEquals(LeaveCause.DISSOLVED, ((GroupMemberLeftEvent) changes.get(4)).getCause());
        assertEquals(DissolveCause.REGION_REMOVED, ((GroupDissolvedEvent) changes.get(5)).getCause());
        assertEquals(stays, ((GroupDissolvedEvent) changes.get(5)).getLastMembers().iterator().next().getUuid());
        assertNull(region.getGroupIfPresent("members"));
    }

    @Test
    void removingARegionDissolvesItsGroups() {
        Region region = created(manager().create("doomed", WORLD, box(0, 0, 9, 9)));
        region.getGroup("members").add(UUID.randomUUID());
        List<GroupDissolvedEvent> dissolved = record(GroupDissolvedEvent.class);

        manager().remove(region);

        assertEquals(1, dissolved.size());
        assertEquals(DissolveCause.REGION_REMOVED, dissolved.get(0).getCause());
        assertEquals(1, dissolved.get(0).getLastMembers().size());
    }

    @Test
    void createAndRemoveAreEventedAndADetachedRegionIsSilent() {
        List<RegionEvent> everything = record(RegionEvent.class);
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        manager().remove(region);
        int afterRemoval = everything.size();

        region.setPriority(9);   //a removed region still holds its fields, but nobody hears about them

        assertEquals(2, afterRemoval);
        assertEquals(RegionCreatedEvent.class, everything.get(0).getClass());
        assertEquals(RegionRemovedEvent.Cause.API, ((RegionRemovedEvent) everything.get(1)).getCause());
        assertEquals(afterRemoval, everything.size());
    }

    @Test
    void withNobodyListeningNoEventIsBuilt() {
        assertFalse(ECEventBus.global().hasListeners(RegionChangedEvent.class));
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        AtomicInteger built = new AtomicInteger();

        region.setPriority(3);
        ECEventBus.global().postIfListened(RegionChangedEvent.class, () -> {
            built.incrementAndGet();
            return new RegionChangedEvent(region, RegionChangedEvent.Type.PRIORITY);
        });

        assertEquals(0, built.get(), "postIfListened - the only way region events are posted - never runs the supplier unheard");
        assertEquals(3, region.getPriority());
    }
}
