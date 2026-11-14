package br.com.finalcraft.evernifecore.logic.reward.command;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.booster.BoosterJoin;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.reward.RewardOccasion;
import br.com.finalcraft.evernifecore.logic.reward.RewardPaidEvent;
import br.com.finalcraft.evernifecore.testing.Commands;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reward verbs two plugins mount, each over its own occasions and config: a raid (stage 5 of the PokeRaidArena
 * fixture) and a mob arena keyed by wave. Each export carries only its plugin's part, and simulate applies nothing.
 */
class RewardCommandNodeTest {

    private static final String BASE = "fakeraid.rewards";

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;
    private LogicWorld raid;
    private LogicWorld arena;
    private TestFPlayerSender ana;
    private TestFPlayerSender bia;
    private UUID duda;
    private TestCommandSender admin;
    private FinalCMDPluginCommand raidCmd;
    private FinalCMDPluginCommand arenaCmd;

    @FinalCMD(aliases = "raid")
    public static class Raid_Cmd {
        @FinalCMD.Node(subcmd = "rewards", permission = BASE)
        final RewardCommandNode rewards;

        Raid_Cmd(RewardCommandNode rewards) {
            this.rewards = rewards;
        }
    }

    @FinalCMD(aliases = "arena")
    public static class Arena_Cmd {
        @FinalCMD.Node(subcmd = "rewards", permission = "fakearena.rewards")
        final RewardCommandNode rewards;

        Arena_Cmd(RewardCommandNode rewards) {
            this.rewards = rewards;
        }
    }

    @FinalCMD(aliases = "duel")
    public static class Duel_Cmd {
        @FinalCMD.Node(subcmd = "rewards", permission = "fakeduels.rewards")
        final RewardCommandNode rewards;

        Duel_Cmd(RewardCommandNode rewards) {
            this.rewards = rewards;
        }
    }

    @BeforeEach
    void open() {
        harness = Commands.harness("RewardCmd", tempDir.resolve("cmd"));
        FCLocaleManager.loadLocale(harness.ecPluginData, RewardCommandNode.class);
        raid = LogicWorld.open("FakeRaid", tempDir.resolve("raid"));
        arena = LogicWorld.open("FakeArena", tempDir.resolve("arena"));
        Currencies.register(raid.getOwner(), Economies.currency("fakeraid:gold", CurrencyOwner.Kind.PLAYER));
        raid.deliveries();
        ana = raid.player("Ana");
        bia = raid.player("Bia");
        duda = UUID.randomUUID();

        RewardOccasion stageWon = RewardOccasion.builder(raid.getOwner(), "stage-won").phase("stage")
                .marks("alive", "dead").variables("stage", "faction", "boss")
                .sample(scene -> scene
                        .participant(ana.getUniqueId(), "Ana", new BigDecimal(900), "alive")
                        .participant(bia.getUniqueId(), "Bia", new BigDecimal(600), "alive")
                        .participant(duda, "Duda", new BigDecimal(200), "dead")
                        .variable("stage", 5).variable("faction", "Dragons").variable("boss", "MewTwo"))
                .build();
        RewardOccasion waveCleared = RewardOccasion.builder(arena.getOwner(), "wave-cleared").phase("wave").variables("wave").build();
        JsonNode raidRewards = yaml("raid-stage-5.yml");
        JsonNode arenaRewards = json("{'every 5': [{'name': 'boss-wave', 'effects': [{'type': 'message', 'text': 'Wave {wave}!'}]}]}");

        raidCmd = harness.register(new Raid_Cmd(new RewardCommandNode(raid.getOwner(), BASE).occasion(stageWon, () -> raidRewards)));
        arenaCmd = harness.register(new Arena_Cmd(new RewardCommandNode(arena.getOwner()).occasion(waveCleared, () -> arenaRewards)));
        admin = Senders.console("admin").grant(BASE).grant(BASE + ".export").grant(BASE + ".simulate").grant("fakearena.rewards");
    }

    @AfterEach
    void close() {
        Currencies.unregisterAll(raid.getOwner());
        arena.close();
        raid.close();
        harness.close();
    }

    @Test
    void eachPluginsExportCarriesOnlyItsOwnOccasionsAndConfig() throws IOException {
        harness.dispatch(raidCmd, admin, "rewards export");
        harness.dispatch(arenaCmd, admin, "rewards export");

        JsonNode raidExport = read(tempDir.resolve("raid").resolve("export").resolve("rewards.json").toFile());
        JsonNode arenaExport = read(tempDir.resolve("arena").resolve("export").resolve("rewards.json").toFile());

        assertEquals("FakeRaid", raidExport.path("plugin").asText());
        assertEquals(1, raidExport.path("occasions").size());
        JsonNode stageWon = raidExport.path("occasions").get(0);
        assertEquals("stage-won", stageWon.path("id").asText());
        assertEquals("stage", stageWon.path("phase").asText());
        assertEquals("[\"alive\",\"dead\"]", stageWon.path("marks").toString());
        assertEquals(3, stageWon.path("sample").path("participants").size());
        assertEquals("congrats", stageWon.path("rewards").path("5").get(0).path("name").asText(), "the config, as the plugin's file has it");

        assertEquals("FakeArena", arenaExport.path("plugin").asText());
        assertEquals(1, arenaExport.path("occasions").size());
        assertEquals("wave-cleared", arenaExport.path("occasions").get(0).path("id").asText());
        assertEquals("boss-wave", arenaExport.path("occasions").get(0).path("rewards").path("every 5").get(0).path("name").asText());
        assertFalse(arenaExport.toString().contains("stage-won"), "nothing of the raid in the arena's export");
        assertFalse(raidExport.toString().contains("wave-cleared"), "nothing of the arena in the raid's export");
    }

    @Test
    void theExportLandsInTheFolderOfThePluginThatMountedTheNode() {
        harness.dispatch(raidCmd, admin, "rewards export");

        File expected = tempDir.resolve("raid").resolve("export").resolve("rewards.json").toFile();
        assertTrue(expected.isFile(), "written under the raid's data folder");
        assertFalse(tempDir.resolve("arena").resolve("export").toFile().exists(), "not under another plugin's");
        assertTrue(plain(admin.getMessages()).stream().anyMatch(line -> line.contains(expected.getAbsolutePath())), admin.getMessages().toString());
    }

    @Test
    void theExportCarriesItsFormatTheTypesByRoleAndTheAcceptedValues() throws IOException {
        harness.dispatch(raidCmd, admin, "rewards export");
        JsonNode export = read(tempDir.resolve("raid").resolve("export").resolve("rewards.json").toFile());

        assertEquals(RewardCommandNode.FORMAT, export.path("format").asInt());
        JsonNode offline = byName(export.path("effect-modifiers"), "offline");
        assertEquals("[\"run\",\"skip\",\"on-login\"]", offline.path("one-of").toString());
        assertEquals("run", offline.path("default").asText());
        JsonNode mark = byKey(export.path("types").path("eligibility"), "evernifecore:mark");
        assertEquals("EverNifeCore", mark.path("registered-by").asText());
        assertEquals("ID", byName(mark.path("params"), "mark").path("kind").asText());
        assertTrue(byKey(export.path("types").path("absence"), "evernifecore:deliver").isObject());
        assertTrue(byKey(export.path("types").path("eligibility"), "evernifecore:completed-objective").isObject(), "the quest's piece too");
        for (JsonNode uncategorized : export.path("types").path("reward-piece")) {
            assertFalse("EverNifeCore".equals(uncategorized.path("registered-by").asText()), "every core piece names its role: " + uncategorized);
        }
        assertEquals("[\"keeps\",\"forfeits\"]", byName(byKey(export.path("types").path("membership"), "evernifecore:membership").path("params"), "leaver")
                .path("one-of").toString());
        assertTrue(byKey(export.path("types").path("effect"), "evernifecore:money").isObject());
        assertTrue(export.path("always-accepted").toString().contains("\"amount\""));
    }

    @Test
    void simulateShowsThePreviewOfThePhaseAndAppliesNothing() throws Exception {
        raid.recorded(RewardPaidEvent.class); //recording from here on

        harness.dispatch(raidCmd, admin, "rewards simulate stage-won 5");

        List<String> said = plain(admin.getMessages());
        assertTrue(said.get(0).contains("stage-won") && said.get(0).contains("stage 5") && said.get(0).contains("key 5"), said.toString());
        assertTrue(said.contains(" #1 pool: 3 member(s)"), said.toString());
        assertTrue(said.contains("    Ana: share 0.5294117647, amount 1058.8235294 × 1 = 1058.8235294, online"), said.toString());
        assertTrue(said.contains("    Duda: share 0.1176470588, amount 235.2941176 × 1 = 235.2941176, delivered"), said.toString());
        assertTrue(said.contains(" #3 dead: 1 member(s)"), said.toString());
        assertTrue(said.contains(" #4 broadcast: once, from the console"), said.toString());

        ana.assertNoMessageSent();
        assertTrue(raid.deliveries().listOf(duda).get(5, TimeUnit.SECONDS).isEmpty(), "no delivery kept");
        assertTrue(raid.recorded(RewardPaidEvent.class).isEmpty(), "no RewardPaidEvent");
    }

    @Test
    void simulateShowsTheBaseTimesTheMultiplierOfABoostedParticipant() throws Exception {
        try (Duels duels = new Duels()) {
            ana.grant("fakeduels.booster.win.2");
            duels.world.boosters().grant(Booster.on(duels.win).bonus(0.5).forOwner(ProgressOwner.player(ana.getUniqueId())).build())
                    .get(5, TimeUnit.SECONDS);

            harness.dispatch(duels.cmd, duels.admin, "rewards simulate duel-won 1");

            List<String> said = plain(duels.admin.getMessages());
            assertTrue(said.contains("    Ana: share 1, amount 500 × 2.5 = 1250, online"), said.toString());
            assertBalanceUntouched();
        }
    }

    @Test
    void theExportListsThePluginsDomainsAndWhichBoostEachOccasion() throws IOException {
        try (Duels duels = new Duels()) {
            harness.dispatch(duels.cmd, duels.admin, "rewards export");

            JsonNode export = read(tempDir.resolve("duels").resolve("export").resolve("rewards.json").toFile());
            JsonNode win = byId(export.path("domains"), "fakeduels:win");
            assertEquals("sum-bonus cap 3", win.path("join").asText());
            assertEquals("fakeduels.booster.win", win.path("permission-prefix").asText());
            assertEquals(2, win.path("permission-factors").get(0).asInt());
            assertEquals("[\"fakeduels:win\"]", byId(export.path("occasions"), "duel-won").path("boosted-by").toString());
            assertTrue(export.path("always-accepted").toString().contains("\"base-amount\""));
        }
    }

    @Test
    void simulateOfAnOccasionThePluginDidNotDeclareListsItsOwn() {
        harness.dispatch(raidCmd, admin, "rewards simulate wave-cleared 5");

        List<String> said = plain(admin.getMessages());
        assertEquals(1, said.size(), said.toString());
        assertTrue(said.get(0).contains("FakeRaid has no occasion wave-cleared - it has: stage-won"), said.toString());
    }

    @Test
    void theOccasionCompletesWithTheNodesOwnAndEachVerbAsksItsPermission() {
        assertEquals("[stage-won]", harness.tab(raidCmd, admin, "rewards", "simulate", "").toString());
        assertEquals("[wave-cleared]", harness.tab(arenaCmd, admin, "rewards", "simulate", "").toString());

        TestCommandSender doorOnly = Senders.console("door").grant(BASE);
        harness.dispatch(raidCmd, doorOnly, "rewards export");
        assertFalse(tempDir.resolve("raid").resolve("export").toFile().exists(), "export needs " + BASE + ".export");
    }

    // ------------------------------------------------------------------

    /** A duel plugin whose win is boosted by its domain {@code fakeduels:win}, its node mounted under {@code /duel}. */
    private final class Duels implements AutoCloseable {
        final LogicWorld world = LogicWorld.open("FakeDuels", tempDir.resolve("duels"));
        final BoosterDomain win = BoosterDomain.of(world.getOwner(), "win").join(BoosterJoin.sumBonus().cap(new BigDecimal(3)))
                .permission("fakeduels.booster.win", 2).register();
        final TestCommandSender admin = Senders.console("duel-admin").grant("fakeduels.rewards");
        final FinalCMDPluginCommand cmd;

        Duels() {
            RewardOccasion duelWon = RewardOccasion.builder(world.getOwner(), "duel-won").phase("round")
                    .sample(scene -> scene.participant(ana.getUniqueId(), "Ana", BigDecimal.ONE))
                    .build();
            JsonNode rewards = json("{'1': [{'effects': [{'type': 'money', 'amount': 500, 'currency': 'fakeraid:gold'}]}]}");
            cmd = harness.register(new Duel_Cmd(new RewardCommandNode(world.getOwner()).occasion(duelWon, () -> rewards, win)));
        }

        @Override
        public void close() {
            world.close();
        }
    }

    private void assertBalanceUntouched() {
        assertEquals(0, BigDecimal.ZERO.compareTo(Currencies.find("fakeraid:gold").get().getBalance(CurrencyOwner.player(ana.getUniqueId()))),
                "simulate pays nothing");
    }

    private static JsonNode byId(JsonNode array, String id) {
        for (JsonNode each : array) {
            if (id.equals(each.path("id").asText())) {
                return each;
            }
        }
        throw new AssertionError("no " + id + " in " + array);
    }

    private static List<String> plain(List<String> messages) {
        List<String> plain = new ArrayList<>();
        for (String message : messages) {
            plain.add(message.replaceAll("§.", "").replace(" ▶ ", ""));
        }
        return plain;
    }

    private static JsonNode byKey(JsonNode array, String key) {
        for (JsonNode each : array) {
            if (key.equals(each.path("key").asText())) {
                return each;
            }
        }
        throw new AssertionError("no " + key + " in " + array);
    }

    private static JsonNode byName(JsonNode array, String name) {
        for (JsonNode each : array) {
            if (name.equals(each.path("name").asText())) {
                return each;
            }
        }
        throw new AssertionError("no " + name + " in " + array);
    }

    private static JsonNode read(File file) throws IOException {
        return new ObjectMapper().readTree(file);
    }

    private static JsonNode json(String singleQuoted) {
        try {
            return new ObjectMapper().readTree(singleQuoted.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }

    private static JsonNode yaml(String name) {
        try (InputStream in = RewardCommandNodeTest.class.getResourceAsStream("/rewards/" + name)) {
            return new YAMLMapper().readTree(in);
        } catch (IOException unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }
}
