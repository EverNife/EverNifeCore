package br.com.finalcraft.evernifecore.group.event;

import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The group events declare nothing that would break them on a platform's native bus. */
class GroupEventConformanceTest {

    @Test
    void everyGroupEventConforms() {
        assertEquals(Arrays.asList(), ECEventConformance.checkAll(GroupCreatedEvent.class, GroupMemberPreJoinEvent.class,
                GroupMemberJoinedEvent.class, GroupMemberLeftEvent.class, GroupRoleChangedEvent.class, GroupLeaderChangedEvent.class,
                GroupInviteEvent.class, GroupFlagChangedEvent.class, GroupDissolvedEvent.class));
    }
}
