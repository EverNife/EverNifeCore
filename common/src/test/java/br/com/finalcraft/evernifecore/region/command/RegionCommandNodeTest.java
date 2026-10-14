package br.com.finalcraft.evernifecore.region.command;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.WorldBlockPos;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.FlagValue;
import br.com.finalcraft.evernifecore.region.flag.RegionFlag;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.MoveDenySettings;
import br.com.finalcraft.evernifecore.region.group.AccessGroup;
import br.com.finalcraft.evernifecore.region.overlap.Overlap;
import br.com.finalcraft.evernifecore.region.overlap.OverlapProvider;
import br.com.finalcraft.evernifecore.region.overlap.OverlapProviders;
import br.com.finalcraft.evernifecore.region.overlap.OverlapSource;
import br.com.finalcraft.evernifecore.region.selection.SelectionClick;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.session.RevertStrategy;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The region verbs a plugin mounts under its own command, with their permissions and the line between owners. */
class RegionCommandNodeTest extends RegionCommandFixture {

    private Region arena;
    private Region duelArena;

    @BeforeEach
    void regions() {
        arena = prot.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10);
        duelArena = duel.cuboid("arena", WORLD, 0, 0, 0, 20, 20, 20);
        steve.grant("fp.region");
    }

    private FinalCMDPluginCommand mount(RegionCommandNode node) {
        return harness.register(new Fp_Cmd(node));
    }

    private FinalCMDPluginCommand mount() {
        return mount(protNode());
    }

    private void selectBox(FinalCMDPluginCommand fp, String nodePath, int x1, int y1, int z1, int x2, int y2, int z2) {
        harness.dispatch(fp, steve, nodePath + " select");
        assertTrue(SelectionTool.handleClick(steve, WorldBlockPos.of(x1, y1, z1, WORLD), SelectionClick.LEFT));
        assertTrue(SelectionTool.handleClick(steve, WorldBlockPos.of(x2, y2, z2, WORLD), SelectionClick.LEFT));
    }

    // ------------------------------------------------------------------
    //  The verbs
    // ------------------------------------------------------------------

    @Test
    void treeHasEveryVerb() {
        FinalCMDPluginCommand fp = mount();
        harness.tree(fp).at("region").hasChildren("define", "flag", "group", "here", "info", "list", "parent", "priority",
                "redefine", "remove", "select", "settings", "tag", "tp", "view");
        harness.tree(fp).at("region.group").hasChildren("add", "flag", "list", "permission", "priority", "remove");
    }

    @Test
    void selectThenDefineCreatesInTheNodesManager() {
        FinalCMDPluginCommand fp = mount();
        selectBox(fp, "region", 100, 60, 100, 90, 70, 110);
        assertTrue(SelectionTool.handleClick(steve, WorldBlockPos.of(0, 0, 0, WORLD), SelectionClick.SNEAK_RIGHT));
        steve.assertAnyMessageContains("/fp region define");

        harness.dispatch(fp, steve, "region define home");

        Region home = prot.getManager().get("home");
        assertNotNull(home, steve.getMessages().toString());
        assertEquals(CuboidShape.of(BlockPos.of(90, 60, 100), BlockPos.of(100, 70, 110)), home.getShape());
        assertNull(duel.getManager().get("home"));
        assertNull(SelectionTool.getSession(steve));

        steve.clearMessages();
        harness.dispatch(fp, steve, "region define again");
        assertNull(prot.getManager().get("again"), "the selection was used up");
        steve.assertAnyMessageContains("/fp region select");
    }

    @Test
    void defineWithoutASessionTeachesSelect() {
        harness.dispatch(mount(), steve, "region define home");

        assertNull(prot.getManager().get("home"));
        steve.assertAnyMessageContains("/fp region select");
    }

    @Test
    void defineOverAnOverlapRefusesWithoutForce() {
        FinalCMDPluginCommand fp = mount();
        selectBox(fp, "region", 15, 0, 15, 30, 10, 30);

        harness.dispatch(fp, steve, "region define corner");

        assertNull(prot.getManager().get("corner"));
        steve.assertAnyMessageContains("theduel:arena");
        steve.assertAnyMessageContains("-f");
        assertNotNull(SelectionTool.getSession(steve), "a refused define keeps the selection");
    }

    @Test
    void defineBeforeTheRegionsLoadSaysSoAndChecksNothing() {
        try (RegionWorld loading = RegionWorld.openUnloaded("LateProt", tempDir.resolve("late"))) {
            AtomicInteger overlapChecks = new AtomicInteger();
            OverlapProviders.register(loading.getOwner(), new OverlapProvider() {
                @Override
                public OverlapSource getSource() {
                    return OverlapSource.TRIGGER_VOLUMES;
                }

                @Override
                public List<Overlap> findOverlaps(String world, Shape shape) {
                    overlapChecks.incrementAndGet();
                    return Collections.emptyList();
                }
            });
            FinalCMDPluginCommand fp = mount(new RegionCommandNode(loading.getManager(), SelectionSpec.cuboid()));
            selectBox(fp, "region", 200, 0, 200, 210, 10, 210);
            steve.clearMessages();

            harness.dispatch(fp, steve, "region define early");

            steve.assertAnyMessageContains("still loading");
            assertNull(loading.getManager().get("early"));
            assertEquals(0, overlapChecks.get(), "no overlap is decided against regions not loaded yet");
            assertNotNull(SelectionTool.getSession(steve), "the selection waits for a define that can run");
        }
    }

    @Test
    void forceAcceptsTheOverlap() {
        FinalCMDPluginCommand fp = mount();
        selectBox(fp, "region", 15, 0, 15, 30, 10, 30);

        harness.dispatch(fp, steve, "region define corner -f");

        assertNotNull(prot.getManager().get("corner"), steve.getMessages().toString());
    }

    @Test
    void flagSetsClearsAndGivesATtl() {
        FinalCMDPluginCommand fp = mount();

        harness.dispatch(fp, steve, "region flag arena deny-pvp true -t 10m");

        FlagValue<?> value = arena.getFlagValue(flag("deny-pvp"));
        assertNotNull(value, steve.getMessages().toString());
        assertEquals(Tristate.TRUE, value.getValue());
        assertEquals(prot.now().plus(Duration.ofMinutes(10)), value.getExpiresAt());

        harness.dispatch(fp, steve, "region flag arena deny-pvp false");
        assertNull(arena.getFlagValue(flag("deny-pvp")).getExpiresAt(), "a value with no -t is permanent");

        harness.dispatch(fp, steve, "region flag arena deny-pvp -");
        assertNull(arena.getFlagValue(flag("deny-pvp")));
    }

    @Test
    void denyBuildSetsBreakAndPlace() {
        harness.dispatch(mount(), steve, "region flag arena deny-build true");

        assertEquals(Tristate.TRUE, valueOf(arena, "deny-block-break"));
        assertEquals(Tristate.TRUE, valueOf(arena, "deny-block-place"));
    }

    @Test
    void settingsOverridesPerRegionAndDashClears() {
        FinalCMDPluginCommand fp = mount();
        RegionFlag<?> denyEntry = flag("deny-entry");

        harness.dispatch(fp, steve, "region settings arena deny-entry revert=trail:2");

        MoveDenySettings own = assertInstanceOf(MoveDenySettings.class, arena.getFlagSettings(denyEntry), steve.getMessages().toString());
        assertEquals(RevertStrategy.trail(2), own.getRevert());
        assertEquals(RevertStrategy.lastValid(), ((MoveDenySettings) denyEntry.getSettings()).getRevert(), "the server's settings stay");
        steve.assertAnyMessageContains("has no value for");

        harness.dispatch(fp, steve, "region settings arena deny-entry -");
        assertNull(arena.getFlagSettings(denyEntry));
    }

    @Test
    void settingsWithoutValuePrintsTheEffectiveOnes() {
        FinalCMDPluginCommand fp = mount();

        harness.dispatch(fp, steve, "region settings arena deny-entry");
        assertTrue(steve.getMessages().stream().anyMatch(line -> line.contains("revert") && line.contains("(server)")), steve.getMessages().toString());

        harness.dispatch(fp, steve, "region settings arena deny-entry revert=trail:3");
        steve.clearMessages();
        harness.dispatch(fp, steve, "region settings arena deny-entry");

        List<String> lines = steve.getMessages();
        assertTrue(lines.stream().anyMatch(line -> line.contains("revert") && line.contains("trail:3") && line.contains("(override)")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> !line.contains("revert") && line.contains("(server)")), lines.toString());
    }

    @Test
    void settingsRejectsAnUnknownFieldNamingTheAccepted() {
        harness.dispatch(mount(), steve, "region settings arena deny-entry color=red");

        assertNull(arena.getFlagSettings(flag("deny-entry")));
        steve.assertAnyMessageContains("color");
        steve.assertAnyMessageContains("revert");
    }

    private MoveDenySettings ownDenyEntrySettings() {
        return assertInstanceOf(MoveDenySettings.class, arena.getFlagSettings(flag("deny-entry")), steve.getMessages().toString());
    }

    @Test
    void flagSettingsSetsTheValueAndTheSettingsInOneLine() {
        harness.dispatch(mount(), steve, "region flag arena deny-entry true --fs revert=spawn");

        assertEquals(Tristate.TRUE, valueOf(arena, "deny-entry"));
        assertEquals(RevertStrategy.spawn(), ownDenyEntrySettings().getRevert());
    }

    @Test
    void flagSettingsReadsAJsonObject() {
        harness.dispatch(mount(), steve, "region flag arena deny-entry true --fs {\"revert\":\"worldspawn\"}");

        assertEquals(RevertStrategy.worldSpawn(), ownDenyEntrySettings().getRevert());
    }

    @Test
    void repeatedFlagSettingsMergeInOrderAndTheLastKeyWins() {
        harness.dispatch(mount(), steve, "region flag arena deny-entry true --fs 'revert=spawn stuckAfter=3' --flag-settings revert=trail:2");

        MoveDenySettings own = ownDenyEntrySettings();
        assertEquals(RevertStrategy.trail(2), own.getRevert(), "the later revert wins");
        assertEquals(3, own.getStuckAfter(), "a key only the first one named still counts");
    }

    @Test
    void anUnknownSettingIsRefusedNamingTheFieldsAndTheValueStays() {
        harness.dispatch(mount(), steve, "region flag arena deny-entry true --fs color=red");

        assertEquals(Tristate.TRUE, valueOf(arena, "deny-entry"), "the value went in before the settings were read");
        assertNull(arena.getFlagSettings(flag("deny-entry")));
        steve.assertAnyMessageContains("color");
        steve.assertAnyMessageContains("revert");
        steve.assertAnyMessageContains("The value was set");
    }

    @Test
    void flagSettingsWithoutAValueOnlyChangesTheSettings() {
        harness.dispatch(mount(), steve, "region flag arena deny-entry --fs revert=worldspawn");

        assertNull(arena.getFlagValue(flag("deny-entry")), "no value was written, and nothing was shown instead");
        assertEquals(RevertStrategy.worldSpawn(), ownDenyEntrySettings().getRevert());
    }

    @Test
    void settingsReadsAJsonObjectToo() {
        harness.dispatch(mount(), steve, "region settings arena deny-entry {\"stuckAfter\":5,\"revert\":\"fixed:1,2,3\"}");

        MoveDenySettings own = ownDenyEntrySettings();
        assertEquals(5, own.getStuckAfter());
        assertEquals(RevertStrategy.fixed(1, 2, 3), own.getRevert());
    }

    @Test
    void groupAddRemoveAndFlag() {
        FinalCMDPluginCommand fp = mount();
        Region garden = prot.cuboid("garden", WORLD, 40, 0, 40, 45, 5, 45);
        UUID alex = UUID.randomUUID();

        harness.dispatch(fp, steve, "region group garden membros add " + alex + " -t 15m");
        AccessGroup group = garden.getGroupIfPresent("membros");
        assertNotNull(group, steve.getMessages().toString());
        assertEquals(prot.now().plus(Duration.ofMinutes(15)), group.getMembership(alex).getExpiresAt());

        harness.dispatch(fp, steve, "region group garden membros flag deny-pvp false");
        assertEquals(Tristate.FALSE, group.getFlag(flag("deny-pvp")));

        harness.dispatch(fp, steve, "region group garden membros list");
        steve.assertAnyMessageContains(alex.toString());

        harness.dispatch(fp, steve, "region group garden membros remove " + alex);
        assertNull(group.getMembership(alex));
    }

    @Test
    void priorityAndParentAndDash() {
        FinalCMDPluginCommand fp = mount();
        Region inner = prot.cuboid("inner", WORLD, 2, 2, 2, 4, 4, 4);

        harness.dispatch(fp, steve, "region priority arena 7");
        harness.dispatch(fp, steve, "region parent inner arena");

        assertEquals(7, arena.getPriority());
        assertSame(arena, inner.getParent(), steve.getMessages().toString());

        harness.dispatch(fp, steve, "region parent arena inner");   //a cycle is the region's refusal, said to the sender
        assertNull(arena.getParent());
        assertFalse(steve.getMessages().isEmpty());

        harness.dispatch(fp, steve, "region parent inner -");
        assertNull(inner.getParent());
    }

    @Test
    void removeWithChildrenRefusesNamingTheStrategies() {
        Region inner = prot.cuboid("inner", WORLD, 2, 2, 2, 4, 4, 4);
        inner.setParent(arena);

        harness.dispatch(mount(), steve, "region remove arena");

        assertSame(arena, prot.getManager().get("arena"));
        steve.assertAnyMessageContains("--cascade");
        steve.assertAnyMessageContains("--orphan");
    }

    @Test
    void cascadeAndOrphan() {
        FinalCMDPluginCommand fp = mount();
        Region inner = prot.cuboid("inner", WORLD, 2, 2, 2, 4, 4, 4);
        inner.setParent(arena);

        harness.dispatch(fp, steve, "region remove arena --orphan");
        assertNull(prot.getManager().get("arena"));
        assertSame(inner, prot.getManager().get("inner"));
        assertNull(inner.getParent());

        Region top = prot.cuboid("top", WORLD, 50, 0, 50, 60, 10, 60);
        Region child = prot.cuboid("child", WORLD, 52, 2, 52, 54, 4, 54);
        child.setParent(top);
        harness.dispatch(fp, steve, "region remove top --cascade");
        assertNull(prot.getManager().get("top"));
        assertNull(prot.getManager().get("child"));
    }

    @Test
    void redefineFromTheSession() {
        FinalCMDPluginCommand fp = mount();
        selectBox(fp, "region", 50, 0, 50, 55, 5, 55);

        harness.dispatch(fp, steve, "region redefine arena");

        assertEquals(CuboidShape.of(BlockPos.of(50, 0, 50), BlockPos.of(55, 5, 55)), arena.getShape(), steve.getMessages().toString());
        assertNull(SelectionTool.getSession(steve));
    }

    @Test
    void tagAddAndRemove() {
        FinalCMDPluginCommand fp = mount();

        harness.dispatch(fp, steve, "region tag arena add pvp");
        assertTrue(arena.getTags().contains("pvp"), steve.getMessages().toString());

        harness.dispatch(fp, steve, "region tag arena remove pvp");
        assertFalse(arena.getTags().contains("pvp"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tpUsesTeleportPointOrTheCenter() {
        FinalCMDPluginCommand fp = mount();

        harness.dispatch(fp, steve, "region tp arena");
        assertEquals(WorldLocPos.of(5.5, 11, 5.5, WORLD), steve.getTeleports().get(0).getWorldLocPos());

        arena.setFlag((RegionFlag<WorldLocPos>) flag("teleport-point"), WorldLocPos.of(1, 2, 3, WORLD));
        harness.dispatch(fp, steve, "region tp arena");
        assertEquals(WorldLocPos.of(1, 2, 3, WORLD), steve.getTeleports().get(1).getWorldLocPos());
    }

    @Test
    void viewSaysHowToTurnTheVisualizerOn() {
        harness.dispatch(mount(), steve, "region view arena");

        steve.assertAnyMessageContains("Regions.visualizer.enabled: true");
    }

    @Test
    void hereListsOnlyThisOwner() {
        harness.dispatch(mount(), steve, "region here");

        steve.assertAnyMessageContains("fakeprot:arena");
        steve.assertAnyMessageContains("prio 0");
        assertFalse(steve.anyMessageContains("theduel:arena"), steve.getMessages().toString());
    }

    // ------------------------------------------------------------------
    //  Permissions
    // ------------------------------------------------------------------

    @Test
    void mountPermissionGatesEveryVerb() {
        FinalCMDPluginCommand fp = mount();
        TestFPlayerSender stranger = Senders.player("Stranger").at(WORLD, 5, 5, 5);

        harness.dispatch(fp, stranger, "region flag arena deny-pvp true");
        harness.dispatch(fp, stranger, "region remove arena");
        harness.dispatch(fp, stranger, "region priority arena 9");

        assertNull(valueOf(arena, "deny-pvp"));
        assertSame(arena, prot.getManager().get("arena"));
        assertEquals(0, arena.getPriority());
        stranger.assertAnyMessageContains("fp.region");
    }

    @Test
    void permissionBaseGatesPerVerb() {
        FinalCMDPluginCommand fp = mount(new RegionCommandNode(prot.getManager(), SelectionSpec.cuboid(), "fp.region"));
        TestFPlayerSender moderator = Senders.player("Moderator").grant("fp.region").grant("fp.region.info");

        harness.dispatch(fp, moderator, "region parent nowhere arena");
        moderator.assertAnyMessageContains("fp.region.parent");
        assertFalse(moderator.anyMessageContains("There is no region called"), "the permission is checked before the region is read");

        moderator.clearMessages();
        harness.dispatch(fp, moderator, "region info arena");
        moderator.assertAnyMessageContains("fakeprot:arena");
    }

    @Test
    void permissionBaseHidesTheVerbFromTabAndHelp() {
        FinalCMDPluginCommand fp = mount(new RegionCommandNode(prot.getManager(), SelectionSpec.cuboid(), "fp.region"));
        TestFPlayerSender moderator = Senders.player("Moderator").grant("fp.region").grant("fp.region.info").grant("fp.region.flag")
                .grant("fp.region.add");

        List<String> verbs = harness.tab(fp, moderator, "region", "");
        assertTrue(verbs.contains("info") && verbs.contains("flag"), verbs.toString());
        assertFalse(verbs.contains("define") || verbs.contains("remove") || verbs.contains("parent"), verbs.toString());

        List<String> groupVerbs = harness.tab(fp, moderator, "region", "group", "arena", "membros", "");
        assertTrue(groupVerbs.contains("add"), groupVerbs.toString());
        assertFalse(groupVerbs.contains("priority") || groupVerbs.contains("list"), groupVerbs.toString());

        harness.dispatch(fp, moderator, "region");
        List<String> help = moderator.getMessages();
        assertTrue(help.stream().anyMatch(line -> line.contains("info")), help.toString());
        assertFalse(help.stream().anyMatch(line -> line.contains("define") || line.contains("parent")), help.toString());
    }

    @Test
    void theGroupPlayerArgumentTabCompletesOnlineNames() {
        FinalCMDPluginCommand fp = mount();
        harness.platform.getOnlinePlayers().add(Senders.player("Alex"));

        List<String> adding = harness.tab(fp, steve, "region", "group", "arena", "membros", "add", "");
        assertTrue(adding.contains("Alex"), adding.toString());
        assertFalse(adding.contains("<player>"), "the slot's name is not a suggestion: " + adding);

        List<String> removing = harness.tab(fp, steve, "region", "group", "arena", "membros", "remove", "al");
        assertEquals(Collections.singletonList("Alex"), removing);
    }

    // ------------------------------------------------------------------
    //  Another owner's regions
    // ------------------------------------------------------------------

    @Test
    void foreignRegionIsRefusedByDefault() {
        FinalCMDPluginCommand fp = mount();

        harness.dispatch(fp, steve, "region flag theduel:arena deny-pvp true");
        harness.dispatch(fp, steve, "region priority theduel:arena 50");

        steve.assertAnyMessageContains("belongs to §etheduel");
        assertNull(valueOf(duelArena, "deny-pvp"));
        assertEquals(0, duelArena.getPriority());
    }

    @Test
    void allowForeignAllEditsAnotherOwnersRegionByFullId() {
        FinalCMDPluginCommand fp = mount(protNode().allowForeign(ForeignAccess.all()));
        prot.flush();
        duel.flush();
        int protSaves = prot.getBackend().callsTo("save");
        int duelSaves = duel.getBackend().callsTo("save");

        harness.dispatch(fp, steve, "region flag theduel:arena deny-pvp true");
        prot.flush();
        duel.flush();

        assertEquals(Tristate.TRUE, valueOf(duelArena, "deny-pvp"), steve.getMessages().toString());
        assertTrue(duel.getBackend().callsTo("save") > duelSaves, "written by the region's owner");
        assertEquals(protSaves, prot.getBackend().callsTo("save"), "nothing written by the node's owner");
    }

    @Test
    void allowForeignOwnersLimitsToTheNamedOwners() {
        FinalCMDPluginCommand someoneElse = mount(protNode().allowForeign(ForeignAccess.owners("SomeoneElse")));
        harness.dispatch(someoneElse, steve, "region flag theduel:arena deny-pvp true");
        assertNull(valueOf(duelArena, "deny-pvp"));
        steve.assertAnyMessageContains("belongs to");

        FinalCMDPluginCommand theDuel = mount(protNode().allowForeign(ForeignAccess.owners("theduel")));
        harness.dispatch(theDuel, steve, "region flag theduel:arena deny-pvp true");
        assertEquals(Tristate.TRUE, valueOf(duelArena, "deny-pvp"));
    }

    @Test
    void ownerRefusingForeignAccessWins() {
        FinalCMDPluginCommand fp = mount(protNode().allowForeign(ForeignAccess.all()));
        duel.getManager().setForeignAccess(false);

        harness.dispatch(fp, steve, "region flag theduel:arena deny-pvp true");
        assertNull(valueOf(duelArena, "deny-pvp"));
        steve.assertAnyMessageContains("does not accept changes from other plugins");

        steve.clearMessages();
        harness.dispatch(fp, steve, "region info theduel:arena");
        steve.assertAnyMessageContains("theduel:arena");
    }

    @Test
    void bareNameNeverReachesAForeignRegion() {
        Region solo = duel.cuboid("solo", WORLD, 300, 0, 300, 310, 10, 310);
        FinalCMDPluginCommand fp = mount(protNode().allowForeign(ForeignAccess.all()));

        harness.dispatch(fp, steve, "region flag solo deny-pvp true");

        assertNull(valueOf(solo, "deny-pvp"));
        steve.assertAnyMessageContains("There is no region called");
    }

    // ------------------------------------------------------------------
    //  Mounting
    // ------------------------------------------------------------------

    @Test
    void mountedByAnotherPluginKeepsItsOwnLocaleAndManager() {
        Plugins.Routing routing = Plugins.routing()
                .plugin("EverNifeCore", tempDir.resolve("core").toFile(), "br.com.finalcraft.evernifecore.region")
                .plugin("OtherProt", tempDir.resolve("other").toFile(), "br.com.finalcraft.evernifecore.region.command.RegionCommandFixture");
        harness.withPluginExtractor(routing);
        ECPluginData corePd = ECPluginManager.getOrCreateECorePluginData(routing.getToken("EverNifeCore"));
        ECPluginData otherPd = ECPluginManager.getOrCreateECorePluginData(routing.getToken("OtherProt"));
        try {
            FCLocaleManager.loadLocale(corePd, RegionCommandNode.class);
            FinalCMDPluginCommand fp = FinalCMDManager.registerCommand(otherPd, new Fp_Cmd(protNode())).get(0);

            CommandNode regionNode = fp.getRoot().getChild("region");
            assertEquals("EverNifeCore", regionNode.getProvidingPlugin().getMetaInfo().getName());
            assertNotEquals("EverNifeCore", fp.getRoot().getProvidingPlugin().getMetaInfo().getName());

            harness.dispatch(fp, steve, "region define home");
            assertFalse(steve.getMessages().isEmpty());
            steve.assertAnyMessageContains("/fp region select");

            harness.dispatch(fp, steve, "region info arena");
            steve.assertAnyMessageContains("fakeprot:arena");

            assertTrue(corePd.getLocalizedMessages().keySet().contains("fp.region.DEFINE"), corePd.getLocalizedMessages().keySet().toString());
        } finally {
            Plugins.forget("EverNifeCore");
            Plugins.forget("OtherProt");
        }
    }

    @FinalCMD(aliases = "twice")
    public static class Twice_Cmd {
        @FinalCMD.Node(subcmd = "prot")
        final RegionCommandNode protRegions;

        @FinalCMD.Node(subcmd = "duel")
        final RegionCommandNode duelRegions;

        Twice_Cmd(RegionCommandNode protRegions, RegionCommandNode duelRegions) {
            this.protRegions = protRegions;
            this.duelRegions = duelRegions;
        }
    }

    @Test
    void mountedTwiceUsesEachManager() {
        FinalCMDPluginCommand twice = harness.register(new Twice_Cmd(protNode(), new RegionCommandNode(duel.getManager(), SelectionSpec.cuboid())));

        selectBox(twice, "prot", 100, 0, 100, 110, 10, 110);
        harness.dispatch(twice, steve, "prot define one");
        selectBox(twice, "duel", 200, 0, 200, 210, 10, 210);
        harness.dispatch(twice, steve, "duel define two");

        assertNotNull(prot.getManager().get("one"), steve.getMessages().toString());
        assertNotNull(duel.getManager().get("two"), steve.getMessages().toString());
        assertNull(duel.getManager().get("one"));
        assertNull(prot.getManager().get("two"));
    }
}
