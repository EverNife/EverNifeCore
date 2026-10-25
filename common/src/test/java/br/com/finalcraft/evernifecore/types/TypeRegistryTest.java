package br.com.finalcraft.evernifecore.types;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registering, resolving names, validating with messages that teach, keeping absent types pending, refusing unsupported ones. */
class TypeRegistryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private ECPluginData jobs;
    private ECPluginData quests;
    private TypeRegistry<Object> conditions;

    @BeforeEach
    void setUp() {
        world = Platforms.lenient().playerState().install();
        core = Plugins.fakePluginData("EverNifeCore", tempDir.resolve("core").toFile());
        jobs = Plugins.fakePluginData("FinalJobs", tempDir.resolve("jobs").toFile());
        quests = Plugins.fakePluginData("FinalQuests", tempDir.resolve("quests").toFile());
        conditions = TypeRegistry.of(Object.class, "condition");
    }

    @AfterEach
    void tearDown() {
        Plugins.forget("EverNifeCore");
        Plugins.forget("FinalJobs");
        Plugins.forget("FinalQuests");
        world.close();
    }

    private static JsonNode node(String singleQuotedJson) {
        try {
            return JSON.readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static TypeDescriptor<Object> compare() {
        return TypeDescriptor.of("evernifecore:compare")
                .describe("Compares a variable with an amount.")
                .param(ParamSpec.required("variable", ParamSpec.Kind.ID, "The variable read."))
                .param(ParamSpec.required("op", ParamSpec.Kind.STRING, "How to compare.").oneOf("<", "<=", "=", ">=", ">", "!="))
                .param(ParamSpec.required("amount", ParamSpec.Kind.NUMBER, "The amount compared with."))
                .factory(params -> params)
                .build();
    }

    private static TypeDescriptor<Object> named(String key) {
        return TypeDescriptor.of(key).describe("A test type.").factory(params -> key).build();
    }

    @Test
    void aFullNodeReadsTypedParametersWithDefaultsApplied() {
        conditions.register(core, TypeDescriptor.of("evernifecore:wait")
                .describe("Waits.")
                .param(ParamSpec.required("time", ParamSpec.Kind.DURATION, "How long."))
                .param(ParamSpec.optional("times", ParamSpec.Kind.NUMBER, 1, "How many times."))
                .param(ParamSpec.optional("when", ParamSpec.Kind.WINDOW, null, "When it resets."))
                .param(ParamSpec.optional("tags", ParamSpec.Kind.LIST, null, "Tags."))
                .factory(params -> params)
                .build());

        ParsedParams params = (ParsedParams) conditions.parse(core, node("{'type': 'wait', 'time': '1m30s', 'when': 'daily at 04:00', 'tags': 'a'}"),
                ZoneId.of("UTC"));

        assertEquals(Duration.ofSeconds(90), params.getDuration("time"));
        assertEquals(new BigDecimal("1"), params.getNumber("times"));
        assertEquals(Window.daily(java.time.LocalTime.of(4, 0), ZoneId.of("UTC")), params.getWindow("when"));
        assertEquals(java.util.Collections.singletonList("a"), params.getList("tags"));
        assertEquals(core, params.getCaller());
    }

    @Test
    void theShortcutFormFillsTheOnlyRequiredParameter() {
        conditions.register(core, TypeDescriptor.of("evernifecore:permission")
                .describe("Has a permission.")
                .param(ParamSpec.required("node", ParamSpec.Kind.ID, "The permission node."))
                .factory(params -> params)
                .build());

        ParsedParams params = (ParsedParams) conditions.parse(core, node("{'permission': 'shop.vip'}"));

        assertEquals("shop.vip", params.getString("node"));
    }

    @Test
    void anInvalidParameterNamesTheTypeTheValueTheFormatAndEveryParameter() {
        conditions.register(core, compare());

        TypeRegistryException error = assertThrows(TypeRegistryException.class,
                () -> conditions.parse(core, node("{'type': 'compare', 'variable': 'exp-level', 'op': '>=', 'amount': 'abc'}")));

        assertEquals("The condition 'compare' cannot read amount = \"abc\": it expects a number, e.g. 32 or 2.5.\n"
                + "Parameters of evernifecore:compare: variable (id, required), op (one of <, <=, =, >=, >, !=, required),"
                + " amount (number, required).", error.getMessage());
    }

    @Test
    void aValueOutsideOneOfListsTheAcceptedValues() {
        conditions.register(core, compare());

        TypeRegistryException error = assertThrows(TypeRegistryException.class,
                () -> conditions.parse(core, node("{'type': 'compare', 'variable': 'x', 'op': '=>', 'amount': 1}")));

        assertTrue(error.getMessage().startsWith("The condition 'compare' cannot read op = \"=>\": it expects one of <, <=, =, >=, >, !=."),
                error.getMessage());
    }

    @Test
    void aMissingRequiredParameterAndAnUnknownOneAreNamed() {
        conditions.register(core, compare());

        TypeRegistryException missing = assertThrows(TypeRegistryException.class,
                () -> conditions.parse(core, node("{'type': 'compare', 'variable': 'x', 'op': '='}")));
        TypeRegistryException unknown = assertThrows(TypeRegistryException.class,
                () -> conditions.parse(core, node("{'type': 'compare', 'variable': 'x', 'op': '=', 'amount': 1, 'amout': 2}")));

        assertTrue(missing.getMessage().startsWith("The condition 'compare' is missing amount: it expects a number"), missing.getMessage());
        assertTrue(unknown.getMessage().startsWith("The condition 'compare' has no parameter 'amout'."), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("Parameters of evernifecore:compare:"), unknown.getMessage());
    }

    @Test
    void aScalarIsAnErrorThatTeachesBothForms() {
        TypeRegistryException error = assertThrows(TypeRegistryException.class, () -> conditions.parse(core, node("'compare'")));

        assertTrue(error.getMessage().contains("{type: <name>, <parameter>: <value>...}"), error.getMessage());
        assertTrue(error.getMessage().contains("permission: shop.vip"), error.getMessage());
    }

    @Test
    void aBareNameAmbiguousBetweenTwoOwnersListsBothKeysAndOwners() {
        conditions.register(jobs, named("finaljobs:pay"));
        conditions.register(quests, named("finalquests:pay"));

        TypeRegistryException error = assertThrows(TypeRegistryException.class, () -> conditions.parse(core, node("{'type': 'pay'}")));

        assertEquals("The condition 'finaljobs:pay' by FinalJobs and 'finalquests:pay' by FinalQuests both answer to 'pay': write"
                + " type: finaljobs:pay or type: finalquests:pay - the bare name 'pay' is ambiguous for EverNifeCore.", error.getMessage());
    }

    @Test
    void aBareNameResolvesToTheCallersOwnTypeFirstThenToTheOnlyOne() {
        conditions.register(jobs, named("finaljobs:pay"));
        conditions.register(quests, named("finalquests:pay"));
        conditions.register(core, TypeDescriptor.of("evernifecore:money").alias("cash").describe("Money.").factory(p -> "money").build());

        assertEquals("finaljobs:pay", conditions.parse(jobs, node("{'type': 'pay'}")));
        assertEquals("finalquests:pay", conditions.parse(jobs, node("{'type': 'finalquests:pay'}")));
        assertEquals("money", conditions.parse(jobs, node("{'type': 'cash'}")));
    }

    @Test
    void aSecondRegistrationOfTheSameKeyNamesBothOwners() {
        conditions.register(jobs, named("finaljobs:level"));

        IllegalStateException collision = assertThrows(IllegalStateException.class, () -> conditions.register(jobs, named("finaljobs:level")));

        assertTrue(collision.getMessage().contains("finaljobs:level is already registered by FinalJobs, so FinalJobs cannot register it"),
                collision.getMessage());
    }

    @Test
    void aKeyThatNamesAnotherPluginIsRefusedTeachingTheRightKey() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> conditions.register(quests, named("finaljobs:level")));

        assertTrue(refused.getMessage().contains("TypeDescriptor.of(\"finalquests:level\")"), refused.getMessage());
    }

    @Test
    void anAbsentTypeFailsTheStrictParseListingTheRegisteredOnes() {
        conditions.register(core, compare());

        TypeRegistryException error = assertThrows(TypeRegistryException.class, () -> conditions.parse(core, node("{'type': 'has-job'}")));

        assertTrue(error.getMessage().startsWith("No condition 'has-job' is registered. The registered ones are: compare."), error.getMessage());
    }

    @Test
    void aPendingDefinitionResolvesWhenItsTypeRegisters() {
        PendingDefinition<Object> pending = conditions.parseLater(core, node("{'type': 'finaljobs:has-job', 'job': 'miner'}"));
        List<Object> resolved = new ArrayList<>();
        pending.whenResolved(resolved::add);
        assertFalse(pending.isResolved());
        assertEquals("finaljobs:has-job", pending.getMissingType());
        assertTrue(pending.getCause().contains("kept pending"), pending.getCause());

        conditions.register(jobs, TypeDescriptor.of("finaljobs:has-job")
                .describe("Has a job.")
                .param(ParamSpec.required("job", ParamSpec.Kind.ID, "The job."))
                .factory(params -> "job=" + params.getString("job"))
                .build());

        assertTrue(pending.isResolved());
        assertEquals("job=miner", pending.get().orElse(null));
        assertEquals(java.util.Collections.singletonList("job=miner"), resolved);
        assertTrue(conditions.getPending().isEmpty());
    }

    @Test
    void aPendingDefinitionThatDoesNotReadWhenItsTypeArrivesStaysPendingAndWarnsTheTypesOwner() {
        PendingDefinition<Object> pending = conditions.parseLater(core, node("{'type': 'has-job', 'level': 'high'}"));

        List<String> logged = Logs.capture(() -> conditions.register(jobs, TypeDescriptor.of("finaljobs:has-job")
                .describe("Has a job.")
                .param(ParamSpec.required("level", ParamSpec.Kind.NUMBER, "The level."))
                .factory(params -> "ok")
                .build()));

        assertFalse(pending.isResolved());
        assertTrue(pending.getCause().contains("cannot read level = \"high\""), pending.getCause());
        assertTrue(logged.stream().anyMatch(line -> line.contains("kept raw, nothing was deleted")), String.valueOf(logged));
        assertEquals(1, conditions.getPending().size());
    }

    @Test
    void aChildWithAnAbsentTypeIsPendingWithoutDiscardingItsParent() {
        conditions.register(core, TypeDescriptor.of("evernifecore:all")
                .describe("Every child passes.")
                .param(ParamSpec.required("conditions", ParamSpec.Kind.NODE_LIST, "The children.").nested(conditions))
                .factory(params -> params)
                .build());
        conditions.register(core, named("evernifecore:yes"));

        ParsedParams all = (ParsedParams) conditions.parse(core, node("{'type': 'all', 'conditions': [{'type': 'yes'}, {'type': 'finaljobs:later'}]}"));

        assertEquals(java.util.Collections.singletonList("evernifecore:yes"), all.getNodes("conditions", Object.class));
        PendingDefinition<Object> later = all.<Object>getPendingNodes("conditions").get(1);
        assertFalse(later.isResolved());

        conditions.register(jobs, named("finaljobs:later"));

        assertEquals("finaljobs:later", later.get().orElse(null));
        assertEquals(2, all.getNodes("conditions", Object.class).size());
    }

    @Test
    void aTypeForAnotherPlatformFailsTheParseNamingThePlatform() {
        conditions.register(core, TypeDescriptor.of("evernifecore:fish").describe("Fishes.").platforms("minecraft").factory(p -> "fish").build());

        TypeRegistryException error = assertThrows(TypeRegistryException.class, () -> conditions.parse(core, node("{'type': 'fish'}")));

        assertEquals("The condition 'evernifecore:fish' runs only on [minecraft], not on this platform (test). Remove it from the"
                + " definition or guard it with a platform-specific file.", error.getMessage());
    }

    @Test
    void aTypeNeedingAMissingCapabilityFailsTheParseAndGetUnsupportedSaysWhyWithoutThrowing() {
        world.platform().getTestPlayerState().without(IPlayerStateAccess.Capability.HEALTH);
        TypeDescriptor<Object> health = TypeDescriptor.of("evernifecore:health").describe("Health.")
                .needsCapability(IPlayerStateAccess.Capability.HEALTH).factory(p -> "health").build();
        conditions.register(core, health);

        TypeRegistryException error = assertThrows(TypeRegistryException.class, () -> conditions.parse(core, node("{'type': 'health'}")));

        assertTrue(error.getMessage().contains("needs HEALTH on a player, which this platform (test) cannot"), error.getMessage());
        assertEquals(error.getMessage(), conditions.getUnsupported(health).orElse(null));
        assertFalse(conditions.getUnsupported(named("evernifecore:free")).isPresent());
    }

    @Test
    void unregisterAllTakesOneOwnersTypesAndPendingDefinitionsAndLeavesTheOthers() {
        TypeRegistry<Object> effects = TypeRegistry.of(Object.class, "effect");
        conditions.register(jobs, named("finaljobs:level"));
        effects.register(jobs, named("finaljobs:pay"));
        conditions.register(quests, named("finalquests:stage"));
        conditions.parseLater(jobs, node("{'type': 'absent'}"));
        conditions.parseLater(quests, node("{'type': 'absent'}"));

        TypeRegistries.unregisterAll(jobs);

        assertFalse(conditions.find("finaljobs:level").isPresent());
        assertFalse(effects.find("pay").isPresent());
        assertTrue(conditions.find("stage").isPresent());
        assertEquals(1, conditions.getPending().size());
        assertEquals(quests, conditions.getPending().get(0).getCaller());
    }

    @Test
    void aReservedKeyPassesThroughUntouchedAndNoTypeMayDeclareIt() {
        TypeRegistry<Object> effects = TypeRegistry.of(Object.class, "effect").reserveKeys("delay");
        effects.register(core, named("evernifecore:noop"));

        assertEquals("evernifecore:noop", effects.parse(core, node("{'type': 'noop', 'delay': '2s'}")));
        assertThrows(IllegalArgumentException.class, () -> effects.register(core, TypeDescriptor.of("evernifecore:wait").describe("Waits.")
                .param(ParamSpec.required("delay", ParamSpec.Kind.DURATION, "How long.")).factory(p -> "x").build()));
    }

    @Test
    void aDescriptorWithoutDescriptionOrWithAnUnnestedNodeParameterIsRefusedAtBuild() {
        assertThrows(IllegalStateException.class, () -> TypeDescriptor.of("evernifecore:x").factory(p -> "x").build());
        assertThrows(IllegalStateException.class, () -> TypeDescriptor.of("evernifecore:x").describe("X.")
                .param(ParamSpec.required("child", ParamSpec.Kind.NODE, "A child.")).factory(p -> "x").build());
        assertNull(conditions.find("x").orElse(null));
    }
}
