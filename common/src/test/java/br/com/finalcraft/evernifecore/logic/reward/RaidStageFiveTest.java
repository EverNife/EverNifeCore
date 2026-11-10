package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.await;
import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.yaml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 5 of a raid, as a plugin pays it: Ana 900 alive, Bia 600 alive, Caio 300 dead, all online, and Duda 200 alive
 * but offline; a word to all, 2000 split by contribution, 300 to the alive, 50 to the dead, one broadcast.
 */
class RaidStageFiveTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld raid;
    private TestCurrency gold;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        raid = LogicWorld.open("FakeRaid", tempDir.resolve("raid"));
        gold = Economies.currency("fakeraid:gold", CurrencyOwner.Kind.PLAYER);
        world.withCurrency(raid.getOwner(), gold);
        raid.deliveries();
    }

    @AfterEach
    void close() {
        raid.close();
        world.close();
    }

    @Test
    void stageFivePaysWhatTheRaidPromises() {
        TestFPlayerSender ana = raid.player("Ana");
        TestFPlayerSender bia = raid.player("Bia");
        TestFPlayerSender caio = raid.player("Caio");
        UUID duda = UUID.randomUUID();
        RewardOccasion stageWon = RewardOccasion.builder(raid.getOwner(), "stage-won").phase("stage")
                .marks("alive", "dead").variables("stage", "faction", "boss").build();
        RewardScene scene = RewardScene.builder(stageWon)
                .participant(ana.getUniqueId(), "Ana", new BigDecimal(900), "alive")
                .participant(bia.getUniqueId(), "Bia", new BigDecimal(600), "alive")
                .participant(caio.getUniqueId(), "Caio", new BigDecimal(300), "dead")
                .participant(duda, "Duda", new BigDecimal(200), "alive")
                .variable("stage", 5).variable("faction", "Dragons").variable("boss", "MewTwo")
                .build();
        PhasedRewardTable rewards = PhasedRewardTable.parse(raid.getOwner(), stageWon, yaml("raid-stage-5.yml"));

        RewardReceipt receipt = await(rewards.getTable(5).pay(RewardContext.builder(raid.caller("raid"), ProgressOwner.global()).scene(scene).build()));

        //the receipt, line by line
        assertAmount("1200", receipt.getAmount(ana.getUniqueId()));
        assertAmount("900", receipt.getAmount(bia.getUniqueId()));
        assertAmount("350", receipt.getAmount(caio.getUniqueId()));
        assertAmount("500", receipt.getAmount(duda));
        assertAmount("900", receipt.getStep(1).getLine(ana.getUniqueId()).getAmount());
        assertAmount("200", receipt.getStep(1).getLine(duda).getAmount());
        assertEquals(RewardReceipt.Status.DELIVERED, receipt.getStep(1).getLine(duda).getStatus());
        assertEquals(RewardReceipt.Status.DELIVERED, receipt.getStep(2).getLine(duda).getStatus());
        assertEquals(RewardReceipt.Status.ONLINE, receipt.getStep(3).getLine(caio.getUniqueId()).getStatus());
        assertEquals(null, receipt.getStep(3).getLine(ana.getUniqueId()), "the dead step pays only the dead");
        assertTrue(receipt.getStep(4).isOnce() && receipt.getStep(4).isRun() && receipt.getStep(4).getLines().isEmpty());
        assertEquals("broadcast", receipt.getStep(4).getName());

        //the world
        assertBalance(ana.getUniqueId(), "1200");
        assertBalance(bia.getUniqueId(), "900");
        assertBalance(caio.getUniqueId(), "350");
        assertBalance(duda, "0");
        assertEquals(Collections.singletonList("console: fbroad &aThe Faction &b[Dragons] &awon stage 5!"),
                world.platform().getTestPlayerState().getCommands());
        ana.assertAnyMessageContains("Congrats! Stage 5 beaten! MewTwo is down.");
        ana.assertAnyMessageContains("Your part of the pool: 900");
        ana.assertAnyMessageContains("Still standing: +300");
        caio.assertAnyMessageContains("You fell, Caio: +50");
        assertFalse(await(raid.deliveries().listOf(duda)).isEmpty(), "Duda's part waits for the login");

        TestFPlayerSender login = Senders.player("Duda", duda);
        await(raid.deliveries().claimAll(login));

        assertBalance(duda, "500");
        assertTrue(await(raid.deliveries().listOf(duda)).isEmpty());
        assertEquals(1, world.platform().getTestPlayerState().getCommands().size(), "the login runs no second broadcast");
    }

    private void assertBalance(UUID player, String expected) {
        assertAmount(expected, gold.getBalance(CurrencyOwner.player(player)));
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }
}
