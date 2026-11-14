package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.booster.BoosterJoin;
import br.com.finalcraft.evernifecore.logic.reward.command.RewardCommandNode;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;

import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.await;
import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.yaml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The reward editor's way back: it reads the export a raid's {@link RewardCommandNode} writes and writes the plugin's
 * YAML. The fixtures under {@code /rewards/editor} are what the editor's fixture script wrote from that export
 * ({@code node reward-editor/base/tools/write-fixtures.js}): the core reads them and pays what the editor promised.
 */
class RewardEditorRoundTripTest {

    /** Where the export of today's core lands, for the editor's fixture script to read. */
    private static final File FRESH_EXPORT = new File("build/reward-editor/rewards-export.json");

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld raid;
    private TestCurrency gold;
    private RewardOccasion stageWon;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        raid = LogicWorld.open("FakeRaid", tempDir.resolve("raid"));
        gold = Economies.currency("fakeraid:gold", CurrencyOwner.Kind.PLAYER);
        world.withCurrency(raid.getOwner(), gold);
        raid.deliveries();
        stageWon = RewardOccasion.builder(raid.getOwner(), "stage-won").phase("stage")
                .marks("alive", "dead").variables("stage", "faction", "boss")
                .sample(scene -> scene
                        .participant(fixed("Ana"), "Ana", new BigDecimal(900), "alive")
                        .participant(fixed("Bia"), "Bia", new BigDecimal(600), "alive")
                        .participant(fixed("Caio"), "Caio", new BigDecimal(300), "dead")
                        .participant(fixed("Duda"), "Duda", new BigDecimal(200), "alive")
                        .variable("stage", 5).variable("faction", "Dragons").variable("boss", "MewTwo"))
                .build();
    }

    @AfterEach
    void close() {
        raid.close();
        world.close();
    }

    @Test
    void theEditorsExportFixtureIsWhatTheNodeWritesToday() throws IOException {
        JsonNode raidRewards = yaml("raid-stage-5.yml");
        BoosterDomain stage = BoosterDomain.of(raid.getOwner(), "stage").join(BoosterJoin.sumBonus().cap(new BigDecimal(3)))
                .permission("fakeraid.booster.stage", 2, 3).register();
        ObjectNode export = new RewardCommandNode(raid.getOwner()).occasion(stageWon, () -> raidRewards, stage).toJson();
        FRESH_EXPORT.getAbsoluteFile().getParentFile().mkdirs();
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(FRESH_EXPORT, export);
        JsonNode fresh = new ObjectMapper().readTree(FRESH_EXPORT); //as a reader sees it: a number node of the file's own type

        JsonNode fixture = editorFixture("rewards-export.json");
        String wayOut = "the editor's export fixture is stale: run this test, then node reward-editor/base/tools/write-fixtures.js "
                + FRESH_EXPORT.getAbsolutePath() + " common/src/test/resources/rewards/editor";
        for (String field : new String[]{"format", "plugin", "effect-modifiers", "always-accepted", "domains", "occasions"}) {
            assertEquals(fresh.get(field), fixture.get(field), field + ": " + wayOut);
        }
        //another test of this JVM may have registered types of its own; the core's are the stable part
        for (Iterator<String> kinds = fresh.path("types").fieldNames(); kinds.hasNext(); ) {
            String kind = kinds.next();
            assertEquals(coreTypes(fresh.path("types").path(kind)), coreTypes(fixture.path("types").path(kind)), "types." + kind + ": " + wayOut);
        }
    }

    @Test
    void theYamlTheEditorWritesUneditedPaysWhatTheOriginalPays() {
        JsonNode written = yaml("editor/stage-won.yml");
        assertEquals(editorFixture("rewards-export.json").path("occasions").get(0).path("rewards"), written,
                "the editor writes back the config it read, node for node");

        Raiders raiders = new Raiders();
        RewardReceipt original = PhasedRewardTable.parse(raid.getOwner(), stageWon, yaml("raid-stage-5.yml")).getTable(5).preview(raiders.context());
        RewardReceipt edited = PhasedRewardTable.parse(raid.getOwner(), stageWon, written).getTable(5).preview(raiders.context());

        assertEquals(original.toString(), edited.toString());
    }

    @Test
    void theYamlTheEditorWritesAfterAnEditLoadsAndPaysTheEdit() {
        Raiders raiders = new Raiders();
        PhasedRewardTable rewards = PhasedRewardTable.parse(raid.getOwner(), stageWon, yaml("editor/stage-won-alive-350.yml"));

        RewardReceipt receipt = await(rewards.getTable(5).pay(raiders.context()));

        assertEquals("alive", receipt.getStep(2).getName());
        assertAmount("350", receipt.getStep(2).getLine(raiders.ana.getUniqueId()).getAmount());
        assertEquals(RewardReceipt.Status.DELIVERED, receipt.getStep(2).getLine(raiders.duda).getStatus());
        assertAmount("1250", receipt.getAmount(raiders.ana.getUniqueId()));
        assertAmount("950", receipt.getAmount(raiders.bia.getUniqueId()));
        assertAmount("350", receipt.getAmount(raiders.caio.getUniqueId()));
        assertAmount("550", receipt.getAmount(raiders.duda));
        assertAmount("1250", gold.getBalance(CurrencyOwner.player(raiders.ana.getUniqueId())));
        assertAmount("350", gold.getBalance(CurrencyOwner.player(raiders.caio.getUniqueId())));
        raiders.ana.assertAnyMessageContains("Still standing: +350");
    }

    // ------------------------------------------------------------------

    /** Stage 5 as {@link RaidStageFiveTest} plays it: Ana, Bia and Caio online, Duda offline. */
    private final class Raiders {
        final TestFPlayerSender ana = raid.player("Ana");
        final TestFPlayerSender bia = raid.player("Bia");
        final TestFPlayerSender caio = raid.player("Caio");
        final UUID duda = UUID.randomUUID();

        RewardContext context() {
            RewardScene scene = RewardScene.builder(stageWon)
                    .participant(ana.getUniqueId(), "Ana", new BigDecimal(900), "alive")
                    .participant(bia.getUniqueId(), "Bia", new BigDecimal(600), "alive")
                    .participant(caio.getUniqueId(), "Caio", new BigDecimal(300), "dead")
                    .participant(duda, "Duda", new BigDecimal(200), "alive")
                    .variable("stage", 5).variable("faction", "Dragons").variable("boss", "MewTwo")
                    .build();
            return RewardContext.builder(raid.caller("raid"), ProgressOwner.global()).scene(scene).build();
        }
    }

    /** The sample's uuids are fixed so the export fixture stays comparable. */
    private static UUID fixed(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static ArrayNode coreTypes(JsonNode types) {
        ArrayNode core = JsonNodeFactory.instance.arrayNode();
        for (JsonNode type : types) {
            if ("EverNifeCore".equals(type.path("registered-by").asText())) {
                core.add(type);
            }
        }
        return core;
    }

    private static JsonNode editorFixture(String name) {
        try (InputStream in = RewardEditorRoundTripTest.class.getResourceAsStream("/rewards/editor/" + name)) {
            assertNotNull(in, "/rewards/editor/" + name + " is missing: run theEditorsExportFixtureIsWhatTheNodeWritesToday, then"
                    + " node reward-editor/base/tools/write-fixtures.js " + FRESH_EXPORT.getAbsolutePath() + " common/src/test/resources/rewards/editor");
            return new ObjectMapper().readTree(in);
        } catch (IOException unreadable) {
            throw new AssertionError(unreadable);
        }
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }
}
