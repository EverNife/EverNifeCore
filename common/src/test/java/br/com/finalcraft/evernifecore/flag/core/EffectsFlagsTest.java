package br.com.finalcraft.evernifecore.flag.core;

import br.com.finalcraft.evernifecore.player.state.ECGameMode;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.flag.Flag;
import br.com.finalcraft.evernifecore.flag.FlagParseException;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.logic.core.CoreLogic;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.command.CoreRegionCommands;
import br.com.finalcraft.evernifecore.region.session.MoveCause;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.RegionWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code effects-on-enter}, {@code effects-on-exit} and {@code effects-while-inside} apply the logic's effects as a player crosses. */
@ECoreTest
class EffectsFlagsTest {

    private static final String WORLD = "world";
    private static final WorldLocPos OUTSIDE = WorldLocPos.of(50, 5, 5, WORLD);
    private static final WorldLocPos IN_A = WorldLocPos.of(5.5, 5, 5, WORLD);
    private static final WorldLocPos IN_B = WorldLocPos.of(15.5, 5, 5, WORLD);

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
        world = Platforms.lenient().onlinePlayers(Collections.<FPlayer>singletonList(alice)).install();
        state = world.platform().getTestPlayerState();
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreLogic.registerAll(core);
        CoreFlags.registerAll(core);
        regions = RegionWorld.open("Arenas", tempDir.resolve("arenas"));
        RegionMovementTracker.get().onJoin(alice, OUTSIDE);
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

    private static <T> T typed(Flag<T> flag, String text) throws FlagParseException {
        return flag.getType().parse(text);
    }

    private void move(WorldLocPos from, WorldLocPos to) {
        RegionMovementTracker.get().onMove(alice, from, to, MoveCause.MOVE, null);
    }

    @Test
    void onEnterAppliesWhenThePlayerEntersAndNotWhenTheyLeave() throws Exception {
        Region a = regions.cuboid("a", WORLD, 0, 0, 0, 10, 10, 10);
        a.setFlag(CoreFlags.EFFECTS_ON_ENTER, typed(CoreFlags.EFFECTS_ON_ENTER, "[{command: 'say in {player}'}, {type: command, command: wave, as: player}]"));

        move(OUTSIDE, IN_A);
        assertEquals(Arrays.asList("console: say in alice", "alice: wave"), state.getCommands());
        move(IN_A, OUTSIDE);
        assertEquals(2, state.getCommands().size(), "leaving applies nothing");
    }

    @Test
    void onExitAppliesWhenThePlayerLeavesAndNotWhenTheyEnter() throws Exception {
        Region a = regions.cuboid("a", WORLD, 0, 0, 0, 10, 10, 10);
        a.setFlag(CoreFlags.EFFECTS_ON_EXIT, typed(CoreFlags.EFFECTS_ON_EXIT, "{command: say out}"));

        move(OUTSIDE, IN_A);
        assertEquals(Collections.emptyList(), state.getCommands(), "entering applies nothing");
        move(IN_A, OUTSIDE);
        assertEquals(Collections.singletonList("console: say out"), state.getCommands());
    }

    @Test
    void crossingStraightFromAToBRunsTheExitOfAAndTheEnterOfB() throws Exception {
        Region a = regions.cuboid("a", WORLD, 0, 0, 0, 10, 10, 10);
        Region b = regions.cuboid("b", WORLD, 11, 0, 0, 20, 10, 10);
        for (Region region : Arrays.asList(a, b)) {
            region.setFlag(CoreFlags.EFFECTS_ON_ENTER, typed(CoreFlags.EFFECTS_ON_ENTER, "{command: say enter " + region.getId().getName() + "}"));
            region.setFlag(CoreFlags.EFFECTS_ON_EXIT, typed(CoreFlags.EFFECTS_ON_EXIT, "{command: say exit " + region.getId().getName() + "}"));
        }

        move(OUTSIDE, IN_A);
        move(IN_A, IN_B);
        //the two flags of one crossing run in no promised order
        assertEquals(new HashSet<>(Arrays.asList("console: say enter a", "console: say exit a", "console: say enter b")),
                new HashSet<>(state.getCommands()));
        assertEquals(3, state.getCommands().size());
    }

    @Test
    void anEffectOfAnAbsentPluginIsSkippedKeptAsWrittenAndListedAsPending() throws Exception {
        Region a = regions.cuboid("a", WORLD, 0, 0, 0, 10, 10, 10);
        a.setFlag(CoreFlags.EFFECTS_ON_ENTER, typed(CoreFlags.EFFECTS_ON_ENTER,
                "[{type: finaljobs:grant-xp, amount: 10}, {command: say still runs}]"));

        move(OUTSIDE, IN_A);
        assertEquals(Collections.singletonList("console: say still runs"), state.getCommands(), "the pending effect is skipped, the rest applies");
        assertTrue(a.getFlagStore().getValue(CoreFlags.EFFECTS_ON_ENTER).getValue().get(0).getRaw().toString().contains("finaljobs:grant-xp"),
                "kept as written");

        TestCommandSender admin = Senders.console();
        CoreRegionCommands.sendInfo(admin, a);
        assertTrue(admin.getMessages().stream().anyMatch(line -> line.contains("evernifecore:effects-on-enter")
                && line.contains("(pending: finaljobs:grant-xp)")), String.valueOf(admin.getMessages()));
    }

    @Test
    void effectsWhileInsideRefusesAnEffectThatCannotBeHeldTeachingEffectsOnEnter() {
        FlagParseException refused = assertThrows(FlagParseException.class,
                () -> typed(CoreFlags.EFFECTS_WHILE_INSIDE, "{message: hello}"));
        assertTrue(refused.getMessage().contains("effects-on-enter"), refused.getMessage());
        assertTrue(refused.getMessage().contains("'message'"), refused.getMessage());
    }

    @Test
    void aHeldEffectIsGivenBackWhenThePlayerLeavesAndWhenTheyQuit() throws Exception {
        Region a = regions.cuboid("a", WORLD, 0, 0, 0, 10, 10, 10);
        a.setFlag(CoreFlags.EFFECTS_WHILE_INSIDE, typed(CoreFlags.EFFECTS_WHILE_INSIDE, "{gamemode: creative}"));

        move(OUTSIDE, IN_A);
        assertEquals(ECGameMode.CREATIVE, state.of(alice).gameMode, "held while inside");
        move(IN_A, OUTSIDE);
        assertEquals(ECGameMode.SURVIVAL, state.of(alice).gameMode, "given back on leaving");

        move(OUTSIDE, IN_A);
        assertEquals(ECGameMode.CREATIVE, state.of(alice).gameMode);
        RegionMovementTracker.get().onQuit(alice);
        assertEquals(ECGameMode.SURVIVAL, state.of(alice).gameMode, "given back on quitting");
    }

    @Test
    void twoOverlappingRegionsEachApplyTheirOwnEffects() throws Exception {
        Region a = regions.cuboid("a", WORLD, 0, 0, 0, 10, 10, 10);
        Region inner = regions.cuboid("inner", WORLD, 0, 0, 0, 10, 10, 10);
        a.setFlag(CoreFlags.EFFECTS_ON_ENTER, typed(CoreFlags.EFFECTS_ON_ENTER, "{command: say a}"));
        inner.setFlag(CoreFlags.EFFECTS_ON_ENTER, typed(CoreFlags.EFFECTS_ON_ENTER, "{command: say inner}"));

        move(OUTSIDE, IN_A);
        assertEquals(2, state.getCommands().size(), String.valueOf(state.getCommands()));
        assertTrue(state.getCommands().containsAll(Arrays.asList("console: say a", "console: say inner")));
    }
}
