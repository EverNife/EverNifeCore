package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.cooldown.Cooldown;
import br.com.finalcraft.evernifecore.cooldown.Cooldowns;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link RepeatPolicy} over the counters of each owner, and the plugin variables of {@link CustomVariables}. */
class RepeatPolicyTest {

    private static final Window DAILY = Window.daily(LocalTime.of(4, 0), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private PlayerDataWorld players;
    private LogicWorld shop;

    @BeforeEach
    void setUp() {
        world = Platforms.strict().ignoringPlatformRegistrations().platformProviderId("test").mainThreadInline().loggingToStdout().playerState()
                .onlinePlayers(Collections.<FPlayer>emptyList()).install();
        players = PlayerDataWorld.with(Storages.memory()).boot(tempDir);
        shop = LogicWorld.open("FakeShop", tempDir.resolve("shop"));
    }

    @AfterEach
    void tearDown() {
        shop.close();
        players.close();
        world.close();
    }

    private ExecutionContext loggedIn(String name) {
        TestFPlayerSender player = shop.player(name);
        PlayerController.handleLogin(player.getUniqueId(), name).join();
        return shop.context(player).build();
    }

    private Cooldown counter(ExecutionContext ctx, String id, RepeatPolicy policy) {
        return Cooldowns.resolve(ctx.getCaller(), ctx.getOwner(), id, policy);
    }

    @Test
    void aLimitedDailyPolicyIsCountedPerPlayerAndSharedByTheGlobalOwner() {
        RepeatPolicy threeADay = RepeatPolicy.limited(3).window(DAILY);
        ExecutionContext alex = loggedIn("Alex");
        ExecutionContext bia = loggedIn("Bia");

        for (int i = 0; i < 3; i++) {
            assertTrue(counter(alex, "chest", threeADay).tryUse());
        }
        assertFalse(counter(alex, "chest", threeADay).tryUse(), "Alex used his three");
        assertEquals(3, counter(bia, "chest", threeADay).getUsesLeft(), "Bia does not share Alex's counter");

        RepeatPolicy serverWide = threeADay.persistent(false);
        ExecutionContext alexGlobal = alex.derive().owner(ProgressOwner.global()).build();
        ExecutionContext biaGlobal = bia.derive().owner(ProgressOwner.global()).build();
        assertTrue(counter(alexGlobal, "event", serverWide).tryUse(2));
        assertEquals(1, counter(biaGlobal, "event", serverWide).getUsesLeft(), "the global owner is one counter for everyone");
    }

    @Test
    void aNewWindowStartsTheCountOver() {
        RepeatPolicy threeADay = RepeatPolicy.limited(3).window(DAILY);
        ExecutionContext alex = loggedIn("Alex");
        assertTrue(counter(alex, "chest", threeADay).tryUse(3));
        Cooldown spent = counter(alex, "chest", threeADay);
        assertFalse(spent.canUse());
        assertTrue(spent.explain(Locale.ENGLISH).getText().contains("3 of 3 used until"));

        shop.advance(Duration.ofDays(1));

        assertTrue(spent.canUse(), "the day turned at 04:00: the count is zero again");
        assertEquals(3, spent.getUsesLeft());
    }

    @Test
    void aCooldownOfTenMinutesDeniesUntilTenMinutesPass() {
        RepeatPolicy tenMinutes = RepeatPolicy.unlimited().cooldown(Duration.ofMinutes(10));
        ExecutionContext alex = loggedIn("Alex");

        assertTrue(counter(alex, "kit", tenMinutes).tryUse());
        assertFalse(counter(alex, "kit", tenMinutes).tryUse());
        shop.advance(Duration.ofMinutes(9));
        assertFalse(counter(alex, "kit", tenMinutes).canUse());
        shop.advance(Duration.ofMinutes(1));
        assertTrue(counter(alex, "kit", tenMinutes).tryUse());
    }

    @Test
    void asCostIsGivenBackByTheRefundOfTheGate() {
        RepeatPolicy threeADay = RepeatPolicy.limited(3).window(DAILY);
        ExecutionContext alex = loggedIn("Alex");
        Cost broke = new Cost() {
            @Override public ConditionResult test(ExecutionContext ctx) { return ConditionResult.pass(); }
            @Override public boolean take(ExecutionContext ctx) { return false; }
            @Override public void refund(ExecutionContext ctx) { }
            @Override public FancyText describe(ExecutionContext ctx) { return FancyText.of("100 coins"); }
        };

        GateResult denied = Gate.builder().cost(threeADay.asCost("chest"), broke).build().pay(alex);
        assertFalse(denied.isPassed());
        assertEquals(3, counter(alex, "chest", threeADay).getUsesLeft(), "the use taken first came back");

        assertTrue(Gate.builder().cost(threeADay.asCost("chest")).build().pay(alex).isPassed());
        assertEquals(2, counter(alex, "chest", threeADay).getUsesLeft());
    }

    @Test
    void aRegionOwnedCounterLivesInThePluginStorageAndRefusesPersistenceWithoutOne() {
        ExecutionContext arena = shop.context().owner(ProgressOwner.world("arena")).build();

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> counter(arena, "boss", RepeatPolicy.once()));
        assertTrue(refused.getMessage().contains("Cooldowns.of(plugin, storage)"), refused.getMessage());

        shop.cooldowns();
        shop.getBackend().resetCalls();
        assertTrue(counter(arena, "boss", RepeatPolicy.once()).tryUse());
        assertFalse(counter(arena, "boss", RepeatPolicy.once()).canUse());
        assertTrue(shop.getBackend().callsTo("save") >= 1, "the use reached the plugin's storage");
    }

    @Test
    void theSweepPrunesAnOwnedRowPastItsRetention() {
        shop.cooldowns();
        ExecutionContext arena = shop.context().owner(ProgressOwner.world("arena")).build();
        RepeatPolicy daily = RepeatPolicy.once().window(DAILY);
        assertTrue(counter(arena, "boss", daily).tryUse());
        shop.getBackend().resetCalls();

        shop.advance(Duration.ofDays(32));
        shop.runDue();

        assertTrue(shop.getBackend().callsTo("delete") >= 1, "the row past the window plus the retention went away");
    }

    @Test
    void parseReadsEveryKeyAndTeachesAWrongOne() throws IOException {
        ObjectMapper json = new ObjectMapper();
        RepeatPolicy policy = RepeatPolicy.parse(json.readTree(
                "{\"limit\": 3, \"cooldown\": \"10m\", \"window\": \"daily at 04:00\", \"reach\": \"network\"}"), ZoneOffset.UTC);

        assertEquals(3, policy.getLimit());
        assertEquals(Duration.ofMinutes(10), policy.getCooldown());
        assertEquals(DAILY, policy.getWindow());
        assertEquals(RepeatPolicy.Reach.NETWORK, policy.getReach());
        assertTrue(policy.isPersistent());
        assertNull(RepeatPolicy.parse(json.readTree("{}"), ZoneOffset.UTC).getWindow());

        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> RepeatPolicy.parse(json.readTree("{\"limits\": 3}"), ZoneOffset.UTC));
        assertTrue(wrong.getMessage().contains("'limits'"), wrong.getMessage());
    }

    @Test
    void customWithAWindowStartsOverWhenTheWindowTurnsAndPersistsInTheOwnerStorage() {
        CustomVariables.CustomVariable<BigDecimal> earned = shop.customVariables()
                .define("daily-money-earned", BigDecimal.class, BigDecimal.ZERO).window(DAILY);
        ExecutionContext alex = shop.context(shop.player("Alex")).build();
        shop.getBackend().resetCalls();

        earned.set(alex, new BigDecimal("50"));

        assertEquals(new BigDecimal("50"), earned.read(alex));
        assertTrue(shop.getBackend().callsTo("save") >= 1, "the value reached the plugin's storage");
        assertEquals(BigDecimal.ZERO, earned.read(shop.context(shop.player("Bia")).build()), "one value per owner");

        shop.advance(Duration.ofDays(1));

        assertEquals(BigDecimal.ZERO, earned.read(alex), "a new day reads the initial value");
    }

    @Test
    void aCustomVariableIsReachedByItsIdInATextAndByTheStockType() throws IOException {
        CustomVariables.CustomVariable<BigDecimal> points = shop.customVariables().define("points", BigDecimal.class, BigDecimal.ONE);
        ExecutionContext alex = shop.context(shop.player("Alex")).build();
        points.set(alex, new BigDecimal("7"));

        assertEquals("7 points", alex.resolve("{fakeshop:points} points"));
        Variable<?> stock = Variables.registry().parse(shop.getOwner(),
                new ObjectMapper().readTree("{\"type\": \"custom\", \"id\": \"fakeshop:points\"}"));
        assertEquals(new BigDecimal("7"), stock.read(alex));
        assertThrows(IllegalArgumentException.class, () -> shop.customVariables().define("points", String.class, ""));
    }
}
