package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.logic.LogicFixtures.JournalCost;
import br.com.finalcraft.evernifecore.logic.LogicFixtures.RecordingEffect;
import br.com.finalcraft.evernifecore.logic.audit.LogicTrace;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static br.com.finalcraft.evernifecore.logic.LogicFixtures.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@code /ecore logic trace} sends: every line run for the traced player, the caller in front, filtered by plugin. */
class LogicTraceTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;

    @BeforeEach
    void setUp() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().playerState().install();
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
        alex = shop.player("Alex");
    }

    @AfterEach
    void tearDown() {
        Multipliers.unregisterAll(shop.getOwner());
        shop.close();
        world.close();
    }

    private static boolean anyContains(List<String> lines, String snippet) {
        return lines.stream().anyMatch(line -> line.contains(snippet));
    }

    @Test
    void aTraceReceivesEveryConditionCostAndEffectOfThePlayerWithTheCallerInFront() {
        List<String> lines = shop.trace(alex);
        ExecutionContext ctx = shop.context(alex).build();

        Gate.builder().require(fixed("vip", true)).cost(new JournalCost("coins", new ArrayList<>())).build().pay(ctx);
        Effects.apply(ctx, new RecordingEffect("hello"));

        assertTrue(anyContains(lines, "[fakeshop:test] condition vip -> passed"), lines.toString());
        assertTrue(anyContains(lines, "[fakeshop:test] check "), lines.toString());
        assertTrue(anyContains(lines, "[fakeshop:test] COST_TAKEN 1 coins"), lines.toString());
        assertTrue(anyContains(lines, "[fakeshop:test] GATE_PAID 1 coins"), lines.toString());
        assertTrue(anyContains(lines, "[fakeshop:test] EFFECT_APPLIED hello"), lines.toString());
    }

    @Test
    void aFailedConditionIsTracedWithItsReason() {
        List<String> lines = shop.trace(alex);

        Gate.builder().require(fixed("vip", false)).build().pay(shop.context(alex).build());

        assertTrue(anyContains(lines, "CONDITION_FAILED vip: vip is not met"), lines.toString());
        assertTrue(anyContains(lines, "-> DENIED"), lines.toString());
    }

    @Test
    void aTraceSkipsOtherPlayersAndTheCallersOfOtherPlugins() {
        TestFPlayerSender bob = shop.player("Bob");
        List<String> lines = shop.trace(alex);
        try (LogicWorld other = LogicWorld.open("OtherPlugin", tempDir.resolve("other"))) {
            Gate.builder().require(fixed("other-plugin", true)).build().check(other.context(alex).build());
            Gate.builder().require(fixed("other-player", true)).build().check(shop.context(bob).build());
            Gate.builder().require(fixed("global", true)).build().check(shop.context().build());
        }

        assertEquals(new ArrayList<String>(), lines);
    }

    @Test
    void aTracePrintsEachMultiplierProviderAndItsFactor() {
        List<String> lines = shop.trace(alex);
        Multipliers.register(shop.getOwner(), new MultiplierProvider() {
            @Override
            public BigDecimal getMultiplier(ExecutionContext ctx, String variableId) {
                return new BigDecimal("2");
            }

            @Override
            public String toString() {
                return "double-xp";
            }
        });

        BigDecimal factor = shop.context(alex).build().getMultiplier("exp-points");

        assertEquals(0, new BigDecimal("2").compareTo(factor));
        assertTrue(anyContains(lines, "multiplier exp-points: double-xp x2"), lines.toString());
    }

    @Test
    void aWatcherThatQuitsStopsWatching() {
        UUID admin = UUID.randomUUID();
        List<String> lines = new ArrayList<>();
        LogicTrace.start(admin.toString(), alex.getUniqueId(), null, lines::add);

        ECEventBus.global().post(new ECPlayerQuitEvent(new PlayerData(admin, "Admin")));
        Gate.builder().require(fixed("vip", true)).build().check(shop.context(alex).build());

        assertFalse(LogicTrace.get(admin.toString()).isPresent());
        assertTrue(lines.isEmpty(), lines.toString());
    }
}
