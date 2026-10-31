package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.group.event.GroupDissolvedEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwnerProvider;
import br.com.finalcraft.evernifecore.logic.ProgressOwners;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code ProgressOwner.group(id)} answered by the core's provider, and the {@code dissolve-group} effect. */
@ECoreTest
class ProgressOwnerGroupTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private GroupWorld parties;
    private GroupKind party;
    private ECPluginData core;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        party = parties.kind("party", GroupKind.Cardinality.ONE);
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreGroups.registerAll(core);
    }

    @AfterEach
    void close() {
        Plugins.forget(Plugins.CORE);
        parties.close();
        world.close();
    }

    private ProgressOwnerProvider groups() {
        return ProgressOwners.find(ProgressOwner.GROUP).orElseThrow(() -> new AssertionError("no provider for the group kind"));
    }

    @Test
    void aGroupOwnerIsAliveWhileTheGroupExistsAndItsMembersAreTheOnlineOnes() {
        TestFPlayerSender steve = parties.player("Steve");
        UUID offline = UUID.randomUUID();
        Group alpha = parties.group("alpha", party);
        alpha.add(steve.getUniqueId());
        alpha.add(offline);
        ProgressOwner owner = ProgressOwner.group(alpha.getId());

        assertEquals("group:fakeparty:alpha", owner.toString());
        assertEquals(owner, ProgressOwner.parse(owner.toString()), "it reads back from its text");
        assertTrue(groups().isAlive(owner));
        assertEquals(Collections.singletonList(steve), groups().getMembers(owner), "the online members, the offline one left out");
        assertTrue(GroupLookup.get().getMembers(alpha.getId()).contains(offline), "the lookup still counts the offline member");

        alpha.dissolve(DissolveCause.OWNER);

        assertFalse(groups().isAlive(owner), "a dissolved group is gone");
        assertTrue(groups().getMembers(owner).isEmpty());
    }

    @Test
    void aRegionLocalGroupIsAnOwnerToo() {
        Group vip = parties.regions().cuboid("spawn", "world", 0, 0, 0, 9, 9, 9).getGroup("vip");
        UUID member = UUID.randomUUID();
        vip.add(member);

        ProgressOwner owner = ProgressOwner.group(vip.getId());

        assertTrue(groups().isAlive(owner));
        assertEquals(Collections.singleton(member), GroupLookup.get().getMembers(vip.getId()));
    }

    @Test
    void dissolveGroupDissolvesTheGroupItNames() throws IOException {
        Group alpha = parties.group("alpha", party);
        alpha.add(UUID.randomUUID());
        List<GroupDissolvedEvent> dissolved = parties.recorded(GroupDissolvedEvent.class);
        Effect effect = effect("{\"type\": \"dissolve-group\", \"group\": \"fakeparty:alpha\"}");

        effect.apply(ExecutionContext.builder(Caller.of(parties.getOwner(), "test")).build());

        assertEquals(1, dissolved.size());
        assertEquals(DissolveCause.EFFECT, dissolved.get(0).getCause());
        assertTrue(alpha.isDissolved());
    }

    @Test
    void dissolveGroupWithoutAGroupDissolvesTheOwnerOfTheContextAndRefusesAnotherOwner() throws IOException {
        Group alpha = parties.group("alpha", party);
        Effect effect = effect("{\"type\": \"dissolve-group\"}");
        Caller caller = Caller.of(parties.getOwner(), "test");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> effect.apply(ExecutionContext.builder(caller).owner(ProgressOwner.global()).build()));
        effect.apply(ExecutionContext.builder(caller).owner(ProgressOwner.group(alpha.getId())).build());

        assertTrue(refused.getMessage().contains("write group: <owner:name>"), refused.getMessage());
        assertTrue(alpha.isDissolved());
    }

    private Effect effect(String json) throws IOException {
        JsonNode node = new ObjectMapper().readTree(json);
        return Effects.parse(parties.getOwner(), node, ZoneOffset.UTC);
    }
}
