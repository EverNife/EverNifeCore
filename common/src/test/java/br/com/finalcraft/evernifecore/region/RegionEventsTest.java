package br.com.finalcraft.evernifecore.region;

import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.region.event.RegionChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionCreatedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionEvent;
import br.com.finalcraft.evernifecore.region.event.RegionFlagChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionGroupChangedEvent;
import br.com.finalcraft.evernifecore.region.event.RegionRemovedEvent;
import br.com.finalcraft.evernifecore.region.flag.FlagChangeCause;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.group.GroupChange;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
    void flagChangesOfTheRegionAndOfItsGroupsAreFlagChangedEvents() {
        RoomFlag flag = registeredRoomFlag(owner);
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        AccessGroup group = region.getGroup("members");
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
    void groupChangesAreGroupChangedEventsWithThePlayerWhenThereIsOne() {
        Region region = created(manager().create("region", WORLD, box(0, 0, 9, 9)));
        List<RegionGroupChangedEvent> changes = record(RegionGroupChangedEvent.class);
        UUID player = UUID.randomUUID();

        AccessGroup group = region.getGroup("members");
        group.addMember(player);
        group.removeMember(player);
        group.setPriority(1);
        group.setPermission("p");
        group.setPredicate(p -> true);
        region.removeGroup("members");

        List<GroupChange> types = new ArrayList<>();
        for (RegionGroupChangedEvent change : changes) {
            types.add(change.getType());
        }
        assertEquals(Arrays.asList(GroupChange.CREATED, GroupChange.MEMBER_ADDED, GroupChange.MEMBER_REMOVED, GroupChange.PRIORITY_SET,
                GroupChange.PERMISSION_SET, GroupChange.PREDICATE_SET, GroupChange.REMOVED), types);
        assertEquals(player, changes.get(1).getPlayer());
        assertNull(changes.get(3).getPlayer());
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
