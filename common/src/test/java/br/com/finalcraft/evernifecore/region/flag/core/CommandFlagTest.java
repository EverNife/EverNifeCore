package br.com.finalcraft.evernifecore.region.flag.core;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.region.placeholder.RegionPlaceholders;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@code command-on-enter} and {@code command-on-exit} run as the console unless {@code player:}, with placeholders resolved. */
@ECoreTest
class CommandFlagTest {

    private static final String WORLD = "world";
    private static final WorldLocPos OUTSIDE = WorldLocPos.of(50, 5, 5, WORLD);
    private static final WorldLocPos INSIDE = WorldLocPos.of(5.5, 5, 5, WORLD);

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private RegionWorld regions;
    private TestPlayerState state;
    private TestFPlayerSender alice;

    @BeforeEach
    void open() {
        alice = Senders.player("alice").at(WORLD, 50, 5, 5);
        AtomicReference<RegexReplacer<IPlayerData>> replacer = new AtomicReference<>();
        world = Platforms.lenient()
                .onlinePlayers(Collections.<FPlayer>singletonList(alice))
                .papiPresent(true)
                .recordingPlaceholderIntegrations()
                //PlaceholderAPI's part on a real server: strip the prefix and hand the rest to the expansion that serves it
                .parsingWith((player, text) -> {
                    PlayerData data = new PlayerData(player.getUniqueId(), player.getName());
                    data.setPlayer(player);
                    return replacer.get().apply(text.replace("%" + RegionPlaceholders.CORE_PREFIX + "_", "%"), data);
                })
                .install();
        state = world.platform().getTestPlayerState();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        regions = RegionWorld.open("Arenas", tempDir.resolve("arenas"));
        replacer.set(RegionPlaceholders.registerCore(core));
    }

    @AfterEach
    void close() {
        RegionMovementTracker.get().onQuit(alice);
        FlagRegistry.get().unregisterAll(regions.getOwner());
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        regions.close();
        world.close();
    }

    @Test
    void enterAndExitRunAsTheConsoleOrThePlayerWithTheRegionPlaceholderResolved() {
        Region arena = regions.cuboid("arena", WORLD, 0, 0, 0, 10, 10, 10);
        arena.setFlag(CoreFlags.COMMAND_ON_ENTER, Arrays.asList("say %player_name_never_served% in %evernifecore_region_id%",
                "player: /spawn %evernifecore_region_id%"));
        arena.setFlag(CoreFlags.COMMAND_ON_EXIT, Collections.singletonList("console: /bye %evernifecore_region_[arenas:arena]_id%"));

        RegionMovementTracker.get().onJoin(alice, OUTSIDE);
        RegionMovementTracker.get().onMove(alice, OUTSIDE, INSIDE, MoveCause.MOVE, null);
        assertEquals(Arrays.asList("console: say %player_name_never_served% in arenas:arena", "alice: spawn arenas:arena"), state.getCommands());

        RegionMovementTracker.get().onMove(alice, INSIDE, OUTSIDE, MoveCause.MOVE, null);
        assertEquals("console: bye arenas:arena", state.getCommands().get(2), "the exit line runs as the console, resolved");
        assertEquals(3, state.getCommands().size());
    }
}
