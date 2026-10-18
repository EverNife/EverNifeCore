package br.com.finalcraft.evernifecore.region.placeholder;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The region placeholders over two prefixes: {@code evernifecore} (every region, full ids) and {@code theduel} (only
 * TheDuel's, bare names). TheDuel owns {@code arena1} (alice, bob and carol inside, carol first in), {@code arena}
 * (empty, its parent) and {@code arena_red} (dave); Other owns {@code other:arena1} (erin). Frank stands nowhere.
 */
@ECoreTest
class RegionPlaceholdersTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private TestPlatform platform;
    private ECPluginData core;
    private RegionWorld duel;
    private RegionWorld other;
    private RegexReplacer<IPlayerData> coreReplacer;
    private RegexReplacer<IPlayerData> duelReplacer;
    private Region arena1;
    private final List<TestFPlayerSender> joined = new ArrayList<>();

    private TestFPlayerSender alice;
    private TestFPlayerSender bob;
    private TestFPlayerSender carol;
    private TestFPlayerSender dave;
    private TestFPlayerSender erin;
    private TestFPlayerSender frank;

    @BeforeEach
    void open() {
        alice = Senders.player("alice").at(WORLD, 5, 5, 5);
        bob = Senders.player("bob").at(WORLD, 6, 5, 5);
        carol = Senders.player("carol").at(WORLD, 7, 5, 5);
        dave = Senders.player("dave").at(WORLD, 25, 5, 5);
        erin = Senders.player("erin").at(WORLD, 105, 5, 5);
        frank = Senders.player("frank").at(WORLD, 500, 5, 5);
        world = Platforms.lenient()
                .onlinePlayers(Arrays.<FPlayer>asList(alice, bob, carol, dave, erin, frank))
                .papiPresent(true)
                .recordingPlaceholderIntegrations()
                .install();
        platform = world.platform();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        duel = RegionWorld.open("TheDuel", tempDir.resolve("duel"));
        other = RegionWorld.open("Other", tempDir.resolve("other"));

        Region arena = duel.cuboid("arena", WORLD, 40, 0, 0, 50, 10, 10);
        arena1 = duel.cuboid("arena1", WORLD, 0, 0, 0, 10, 10, 10);
        duel.cuboid("arena_red", WORLD, 20, 0, 0, 30, 10, 10);
        other.cuboid("arena1", WORLD, 100, 0, 0, 110, 10, 10);
        arena1.setParent(arena);
        arena1.setPriority(3);
        arena1.setDisplayName("&6Arena Vermelha");
        arena1.setDescription("Two teams");
        arena1.setExpiresAt(duel.now().plusSeconds(90));
        arena1.getGroup("fighters").addMember(alice.getUniqueId());
        arena1.getGroup("fighters").addMember(bob.getUniqueId());

        join(carol);
        duel.advance(Duration.ofSeconds(10));
        join(alice);
        duel.advance(Duration.ofSeconds(10));
        join(bob);
        join(dave);
        join(erin);
        join(frank);
        duel.advance(Duration.ofSeconds(30));
        arena1.setFlag(CoreFlags.GREETING, "hi");

        coreReplacer = RegionPlaceholders.registerCore(core);
        duelReplacer = PAPIIntegration.createPlaceholderIntegration(duel.getOwner(), "theduel", IPlayerData.class);
        RegionPlaceholders.register(duelReplacer, RegionPlaceholderScope.ownedBy(duel.getOwner()));
    }

    @AfterEach
    void close() {
        for (TestFPlayerSender player : joined) {
            RegionMovementTracker.get().onQuit(player);
        }
        FlagRegistry.get().unregisterAll(duel.getOwner());
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        other.close();
        duel.close();
        world.close();
    }

    private void join(TestFPlayerSender player) {
        RegionMovementTracker.get().onJoin(player, player.getLocation().getWorldLocPos());
        joined.add(player);
    }

    private static String ask(RegexReplacer<IPlayerData> replacer, String placeholder, @Nullable TestFPlayerSender viewer) {
        PlayerData data = null;
        if (viewer != null) {
            data = new PlayerData(viewer.getUniqueId(), viewer.getName());
            data.setPlayer(viewer);
        }
        String asked = "%" + placeholder + "%";
        String answer = replacer.apply(asked, data);
        return asked.equals(answer) ? "<unanswered>" : answer;
    }

    private String duel(String placeholder, @Nullable TestFPlayerSender viewer) {
        return ask(duelReplacer, placeholder, viewer);
    }

    private TestFPlayerSender viewer(String name) {
        for (TestFPlayerSender player : Arrays.asList(alice, bob, carol, dave, erin, frank)) {
            if (player.getName().equals(name)) {
                return player;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Every key of the table, with the value it gives
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}: {1} as {2}")
    @CsvSource(delimiter = '#', quoteCharacter = '`', value = {
            "in_region#in_region#alice#true",
            "in_region_[nome]#in_region_[arena1]#alice#true",
            "region_count#region_count#alice#1",
            "name#region_name#alice#&6Arena Vermelha",
            "name_[fallback='texto']#region_[arena1]_name_[fallback='texto']#frank#&6Arena Vermelha",
            "has_display_name#region_has_display_name#alice#true",
            "description#region_[arena1]_description#frank#Two teams",
            "id#region_id#alice#arena1",
            "full_id#region_[arena1]_full_id#frank#theduel:arena1",
            "plugin_owner#region_plugin_owner#alice#theduel",
            "world#region_[arena1]_world#frank#world",
            "priority#region_priority#alice#3",
            "parent#region_[arena1]_parent#frank#arena",
            "expires_in#region_expires_in#alice#40",
            "min_x#region_[arena1]_min_x#frank#0",
            "min_y#region_min_y#alice#0",
            "min_z#region_[arena1]_min_z#frank#0",
            "max_x#region_max_x#alice#10",
            "max_y#region_[arena1]_max_y#frank#10",
            "max_z#region_max_z#alice#10",
            "center_x#region_[arena1]_center_x#frank#5",
            "center_y#region_center_y#alice#5",
            "center_z#region_[arena1]_center_z#frank#5",
            "volume#region_volume#alice#1331",
            "players_count#region_[arena1]_players_count#frank#3",
            "players_{n}#region_players_2#alice#bob",
            "players_[index=n|sort=entry]#region_[arena1]_players_[index=1|sort=entry]#frank#carol",
            "players_list#region_players_list#alice#alice, bob, carol",
            "time_inside#region_time_inside#alice#40",
            "has_player_[jogador]#region_[arena1]_has_player_[bob]#frank#true",
            "flag_[flag]#region_flag_[greeting]#alice#hi",
            "has_flag_[flag]#region_[arena1]_has_flag_[evernifecore:greeting]#frank#true",
            "flags_count#region_flags_count#alice#1",
            "groups_count#region_[arena1]_groups_count#frank#1",
            "group_[grupo]_members_count#region_group_[fighters]_members_count#alice#2",
    })
    void everyKeyAnswers(String key, String placeholder, String viewer, String expected) {
        assertEquals(expected, duel(placeholder, viewer(viewer)), key);
    }

    // ------------------------------------------------------------------
    //  Selectors, scopes, absence
    // ------------------------------------------------------------------

    @Test
    void anUnderscoreInTheNameNeverCutsTheSelector() {
        assertEquals("1", duel("region_[arena_red]_players_count", null));
        assertEquals("0", duel("region_[arena]_players_count", null));
        assertEquals("3", duel("region_players_count", alice));
    }

    @Test
    void theCoreSeesEveryRegionByItsFullIdAndAPluginOnlyItsOwnByName() {
        assertEquals("theduel:arena1", ask(coreReplacer, "region_id", alice));
        assertEquals("other:arena1", ask(coreReplacer, "region_id", erin));
        assertEquals("arena1", duel("region_id", alice));
        assertEquals("theduel:arena1", duel("region_[arena1]_full_id", null));
        assertEquals("false", duel("in_region", erin));
        assertEquals("", duel("region_id", erin));
        assertEquals("1", ask(coreReplacer, "region_[other:arena1]_players_count", null));
    }

    @Test
    void whatDoesNotExistIsEmptyAndTheFallbackFillsIt() {
        assertEquals("", duel("region_name", frank));
        assertEquals("Área livre", duel("region_name_[fallback='Área livre']", frank));
        assertEquals("", duel("region_[nowhere]_name", null));
        assertEquals("", duel("region_players_200", alice));
        assertEquals("", duel("region_[arena]_description", null));
        assertEquals("arena", duel("region_[arena]_name", null));
        assertEquals("theduel:arena", ask(coreReplacer, "region_[theduel:arena]_name", null));
        assertEquals("", duel("region_name", null), "a placeholder about the player, asked without one");
    }

    @Test
    void timeInsideCountsForTheOneWhoIsInsideOnly() {
        assertEquals("50", duel("region_[arena1]_time_inside", carol));
        assertEquals("", duel("region_[arena1]_time_inside", dave));
    }

    @Test
    void theEntryOrderIsNotTheAlphabeticalOne() {
        assertEquals("alice", duel("region_[arena1]_players_[index=1]", null));
        assertEquals("carol", duel("region_[arena1]_players_[index=1|sort=entry]", null));
        assertEquals("bob", duel("region_[arena1]_players_[index=3|sort=entry]", null));
        assertEquals("[options: index must be a number, got 'x']", duel("region_[arena1]_players_[index=x]", null));
    }

    @Test
    void aFlagNobodyRegisteredIsNamedWhereTheValueWouldBe() {
        assertEquals("[flag: no registered flag is called 'nope']", duel("region_[arena1]_flag_[nope]", null));
        assertEquals("", duel("region_[arena1]_flag_[farewell]", null));
    }

    // ------------------------------------------------------------------
    //  The one-second cache
    // ------------------------------------------------------------------

    @Test
    void fiftyReadersInOneSecondScanThePlayersOnceAndResolveTheFlagOnce() {
        List<FPlayer> crowd = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            crowd.add(Senders.player("crowd" + i).at(WORLD, 205, 5, 5));
        }
        platform.setOnlinePlayers(crowd);
        Region square = duel.cuboid("square", WORLD, 200, 0, 0, 210, 10, 10);
        CountingFlag counted = new CountingFlag(FlagKey.of(duel.getOwner(), "counted"));
        FlagRegistry.get().register(duel.getOwner(), counted);
        int scansBefore = platform.getOnlinePlayersCalls();

        for (FPlayer reader : crowd) {
            assertEquals("50", duel("region_[square]_players_count", (TestFPlayerSender) reader));
        }
        assertEquals(1, platform.getOnlinePlayersCalls() - scansBefore, "the players inside are read once for 50 readers");

        assertEquals("false", duel("region_[square]_flag_[counted]", null));
        int askedOnce = counted.asked.get();
        for (int i = 0; i < 49; i++) {
            duel("region_[square]_flag_[counted]", null);
        }
        assertEquals(askedOnce, counted.asked.get(), "the flag is resolved once per region and flag");

        int cachedBefore = RegionPlaceholders.getCachedRegions();
        duel.getManager().remove(square);
        assertEquals(cachedBefore - 1, RegionPlaceholders.getCachedRegions(), "a removed region leaves the cache");
    }

    // ------------------------------------------------------------------
    //  The core's own prefix, and formatting
    // ------------------------------------------------------------------

    @Test
    void theCoreServesItsPrefixThroughThePlatformWhenPlaceholderAPIIsThere() {
        RegexReplacer<IPlayerData> served = platform.getPlaceholderIntegration(RegionPlaceholders.CORE_PREFIX);

        assertNotNull(served);
        assertEquals("&6Arena Vermelha", ask(served, "region_[theduel:arena1]_name", null));
    }

    @Test
    void aTristateIsTrueFalseOrEmptyAndAPlaceIsWorldXYZ() {
        assertEquals("true", RegionPlaceholders.format(CoreFlags.DENY_ENTRY, Tristate.TRUE));
        assertEquals("", RegionPlaceholders.format(CoreFlags.DENY_ENTRY, Tristate.UNDEFINED));
        assertEquals("world 10.5 64 -3", RegionPlaceholders.format(CoreFlags.TELEPORT_POINT, WorldLocPos.of(10.5, 64, -3, "world")));
        assertEquals("", RegionPlaceholders.format(CoreFlags.GREETING, null));
    }

    /** A yes/no flag that counts how often its default is asked - once per resolve where the region leaves it unset. */
    private static final class CountingFlag extends TristateFlag {
        final AtomicInteger asked = new AtomicInteger();

        CountingFlag(FlagKey key) {
            super(key, Collections.<EventSource>emptySet(), Tristate.FALSE, Tristate.FALSE);
        }

        @Override
        public Tristate getDefault() {
            asked.incrementAndGet();
            return super.getDefault();
        }
    }
}
