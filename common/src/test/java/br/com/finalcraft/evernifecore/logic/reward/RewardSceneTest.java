package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.EffectList;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.Variable;
import br.com.finalcraft.evernifecore.logic.Variables;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A payment described as a {@link RewardScene} under a {@link RewardOccasion}: who is paid and what its effects read. */
class RewardSceneTest {

    private static final String GOLD = "fakeraid:gold";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld raid;
    private TestCurrency gold;
    private TestFPlayerSender steve;
    private TestFPlayerSender alex;
    private RewardOccasion occasion;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        raid = LogicWorld.open("FakeRaid", tempDir.resolve("raid"));
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(raid.getOwner(), gold);
        raid.deliveries();
        steve = raid.player("Steve");
        alex = raid.player("Alex");
        occasion = RewardOccasion.builder(raid.getOwner(), "raid").marks("alive", "mvp").variables("stage", "boss").build();
    }

    @AfterEach
    void close() {
        raid.close();
        world.close();
    }

    @Test
    void everyParticipantIsPaidEvenOneThatIsMemberOfNoOwner() {
        UUID offline = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        RewardScene scene = RewardScene.builder(occasion)
                .participant(steve.getUniqueId(), "Steve", BigDecimal.TEN, "alive")
                .participant(alex.getUniqueId(), "Alex", BigDecimal.ZERO)
                .participant(offline, "Bob", BigDecimal.ONE)
                .participant(stranger, "Stranger", BigDecimal.ZERO, "mvp")
                .build();
        RewardRule rule = RewardRule.builder().distribution(Distribution.each()).effects(gold(100)).build();

        //the owner is Steve alone: without the scene, Alex and the stranger would never be candidates
        Map<UUID, BigDecimal> paid = pay(rule, context(ProgressOwner.player(steve.getUniqueId())).scene(scene).build());

        assertEquals(Arrays.asList(steve.getUniqueId(), alex.getUniqueId(), offline, stranger), new ArrayList<>(paid.keySet()));
        for (BigDecimal share : paid.values()) {
            assertEquals(0, BigDecimal.ONE.compareTo(share), paid.toString());
        }
        assertEquals(0, new BigDecimal("100").compareTo(gold.getBalance(CurrencyOwner.player(alex.getUniqueId()))));
        assertEquals(1, await(raid.deliveries().listOf(stranger)).size(), "the offline stranger's gold waits as a delivery");
    }

    @Test
    void playerOfAnOfflineParticipantIsTheNameThePluginGaveNotTheLastNameSeen() {
        UUID bob = UUID.randomUUID();
        UUIDsController.addOrUpdateUUIDName(bob, "SeenBob");
        RewardScene scene = RewardScene.builder(occasion).participant(bob, "RaidBob", BigDecimal.ONE).build();

        pay(rule("{'type': 'command', 'command': 'eco give {player} 100'}"), context().scene(scene).build());

        assertEquals(Collections.singletonList("console: eco give RaidBob 100"), world.platform().getTestPlayerState().getCommands());
    }

    @Test
    void aSceneVariableReachesTheTextOfAMessage() {
        RewardScene scene = RewardScene.builder(occasion).participant(steve.getUniqueId(), "Steve", BigDecimal.ONE).variable("stage", 5).build();

        pay(rule("{'type': 'message', 'text': 'Stage {stage} cleared, {player}!'}"), context().scene(scene).build());

        steve.assertAnyMessageContains("Stage 5 cleared, Steve!");
    }

    @Test
    void anAttributeOfTheRewardContextReachesTheEffectsExecutionContext() {
        List<String> seen = new ArrayList<>();
        Effects.registry().register(raid.getOwner(), TypeDescriptor.<Effect>of("fakeraid:read-arena")
                .describe("Records the arena attribute it was paid with.")
                .factory(params -> ctx -> seen.add(ctx.getAttribute("arena", String.class)))
                .build());
        RewardScene scene = RewardScene.builder(occasion).participant(steve.getUniqueId(), "Steve", BigDecimal.ONE).build();

        pay(rule("{'type': 'fakeraid:read-arena'}"), context().scene(scene).attribute("arena", "north").build());

        assertEquals(Collections.singletonList("north"), seen);
    }

    @Test
    void theConditionEligibilitySeesTheSceneVariables() {
        RewardRule rule = RewardRule.builder()
                .eligibility(Eligibility.condition(Conditions.parse(raid.getOwner(),
                        node("{'type': 'compare', 'variable': 'stage', 'op': '>=', 'amount': 5}"), ZoneOffset.UTC)))
                .effects(gold(100)).build();

        Map<UUID, BigDecimal> atFive = pay(rule, context().scene(sceneAtStage(5)).build());
        Map<UUID, BigDecimal> atFour = pay(rule, context().scene(sceneAtStage(4)).build());

        assertEquals(Collections.singleton(steve.getUniqueId()), atFive.keySet(), "stage 5 passes >= 5");
        assertTrue(atFour.isEmpty(), "stage 4 does not: " + atFour);
    }

    @Test
    void aRuleReadUnderTheOccasionRefusesAVariableItDoesNotGiveAndNamesTheAccepted() {
        registerLevel();
        JsonNode accepted = node("{'effects': [{'type': 'message', 'text': '{player} {player-uuid} cleared {stage} at level {level} {fakeraid:level}'}]}");
        RewardRule.parse(raid.getOwner(), occasion, accepted); //R2: {player} and a registered variable are not refused

        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> RewardRule.parse(raid.getOwner(), occasion,
                node("{'effects': [{'type': 'message', 'text': 'You got {variavel-inexistente}'}]}")));

        String message = refused.getMessage();
        assertTrue(message.contains("{variavel-inexistente}") && message.contains("'raid'"), message);
        assertTrue(message.contains("{stage}") && message.contains("{boss}") && message.contains("{player}"), message);
        assertTrue(message.contains(".variables(\"variavel-inexistente\")"), "the way out: " + message);
    }

    @Test
    void aSceneRefusesAMarkOrVariableTheOccasionDoesNotDeclare() {
        IllegalArgumentException mark = assertThrows(IllegalArgumentException.class,
                () -> RewardScene.builder(occasion).participant(UUID.randomUUID(), "Bob", BigDecimal.ONE, "dead"));
        assertTrue(mark.getMessage().contains("'dead'") && mark.getMessage().contains("[alive, mvp]"), mark.getMessage());

        IllegalArgumentException variable = assertThrows(IllegalArgumentException.class,
                () -> RewardScene.builder(occasion).variable("wave", 3));
        assertTrue(variable.getMessage().contains("{wave}") && variable.getMessage().contains("[stage, boss]"), variable.getMessage());

        IllegalArgumentException both = assertThrows(IllegalArgumentException.class, () -> context().contributions(
                Collections.singletonMap(steve.getUniqueId(), BigDecimal.ONE)).scene(sceneAtStage(1)).build());
        assertTrue(both.getMessage().contains("drop .contributions"), both.getMessage());
        assertEquals(new LinkedHashSet<>(Arrays.asList("stage", "boss")), occasion.getVariables());
    }

    // ------------------------------------------------------------------

    private RewardScene sceneAtStage(int stage) {
        return RewardScene.builder(occasion).participant(steve.getUniqueId(), "Steve", BigDecimal.ONE).variable("stage", stage).build();
    }

    private void registerLevel() {
        Variables.registry().register(raid.getOwner(), TypeDescriptor.<Variable<?>>of("fakeraid:level")
                .describe("A fixed level.")
                .factory(params -> new Variable<BigDecimal>() {
                    @Override
                    public String getId() {
                        return "fakeraid:level";
                    }

                    @Override
                    public Class<BigDecimal> getType() {
                        return BigDecimal.class;
                    }

                    @Override
                    public BigDecimal read(ExecutionContext ctx) {
                        return BigDecimal.ONE;
                    }
                })
                .build());
    }

    private Caller caller() {
        return raid.caller("raid");
    }

    private RewardContext.Builder context() {
        return context(ProgressOwner.global());
    }

    private RewardContext.Builder context(ProgressOwner owner) {
        return RewardContext.builder(caller(), owner);
    }

    private RewardRule rule(String effect) {
        return RewardRule.builder().effects(Effects.parseList(raid.getOwner(), node("[" + effect + "]"), ZoneOffset.UTC)).build();
    }

    private EffectList gold(int amount) {
        return Effects.parseList(raid.getOwner(), node("[{'type': 'money', 'amount': " + amount + ", 'currency': '" + GOLD + "'}]"),
                ZoneOffset.UTC);
    }

    private static Map<UUID, BigDecimal> pay(RewardRule rule, RewardContext ctx) {
        return await(Rewards.pay(rule, ctx));
    }

    private static JsonNode node(String singleQuotedJson) {
        try {
            return new ObjectMapper().readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
