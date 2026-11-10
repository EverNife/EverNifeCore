package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.await;
import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.yaml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reward files of the plugins that pay through the core, each under the occasion its plugin declares, paying that
 * occasion's sample scene. Steve and Alex are online, Bob is not.
 */
class RewardConsumersTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld plugin;
    private TestFPlayerSender steve;
    private TestFPlayerSender alex;
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadInline().install();
        plugin = LogicWorld.open("FakeRaid", tempDir.resolve("raid"));
        world.withCurrency(plugin.getOwner(), Economies.currency("fakeraid:gold", CurrencyOwner.Kind.PLAYER));
        plugin.deliveries();
        steve = plugin.player("Steve");
        alex = plugin.player("Alex");
    }

    @AfterEach
    void close() {
        plugin.close();
        world.close();
    }

    @Test
    void raid() {
        RewardOccasion occasion = RewardOccasion.builder(plugin.getOwner(), "stage-won").phase("stage").marks("alive", "dead")
                .variables("stage", "faction", "boss")
                .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.TEN, "alive")
                        .participant(bob, "Bob", BigDecimal.ONE, "dead")
                        .variable("stage", 5).variable("faction", "Dragons").variable("boss", "MewTwo"))
                .build();

        RewardReceipt receipt = pay(plugin, occasion, "raid-stage-5.yml", 5, "5");

        assertEquals(RewardReceipt.Status.DELIVERED, receipt.getStep(3).getLine(bob).getStatus());
        assertEquals(1, commands().size());
    }

    @Test
    void boss() {
        LogicWorld duel = LogicWorld.open("TheDuel", tempDir.resolve("duel"));
        try {
            Rewards.registry().register(duel.getOwner(), TypeDescriptor.<RewardPiece>of("theduel:boss-share")
                    .describe("The pot split by the plugin's own book: the top damage takes twice a share.")
                    .factory(params -> (Distribution) (eligible, ctx) -> {
                        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
                        for (UUID member : eligible) {
                            shares.put(member, new BigDecimal("0.5"));
                        }
                        return shares;
                    })
                    .build());
            RewardOccasion occasion = RewardOccasion.builder(duel.getOwner(), "boss-killed").marks("fallen")
                    .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.TEN)
                            .participant(alex.getUniqueId(), "Alex", BigDecimal.TEN, "fallen")
                            .participant(bob, "Bob", BigDecimal.ONE))
                    .build();

            RewardReceipt receipt = pay(duel, occasion, "boss.yml", 1, "default");

            assertEquals(2, receipt.getStep(0).getLines().size(), "Bob's 1 is under 60% of the fair share: " + receipt);
            steve.assertAnyMessageContains("your part is 0.5");
            alex.assertAnyMessageContains("You fell, Alex");
        } finally {
            duel.close();
        }
    }

    @Test
    void maze() {
        RewardOccasion occasion = RewardOccasion.builder(plugin.getOwner(), "arrived").phase("position").variables("position")
                .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.ONE).variable("position", 6))
                .build();

        pay(plugin, occasion, "maze.yml", 6, "4-10");

        steve.assertAnyMessageContains("Position 6: +100");
        assertEquals("default", PhasedRewardTable.parse(plugin.getOwner(), occasion, yaml("maze.yml")).getKey(11));
    }

    @Test
    void tournament() {
        UUID trainer = UUID.randomUUID();
        RewardOccasion occasion = RewardOccasion.builder(plugin.getOwner(), "placed").phase("place").marks("trainer").variables("place")
                .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.ONE)
                        .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE)
                        .participant(trainer, "Brock", BigDecimal.ONE, "trainer")
                        .variable("place", 1))
                .build();

        RewardReceipt receipt = pay(plugin, occasion, "tournament.yml", 1, "1");

        assertEquals(null, receipt.getStep(0).getLine(trainer), "the trainer is paid nothing");
        steve.assertAnyMessageContains("Champions! Your part: 1500");
    }

    @Test
    void mob() {
        RewardOccasion occasion = RewardOccasion.builder(plugin.getOwner(), "wave-cleared").phase("wave").marks("fighting").variables("wave")
                .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "fighting")
                        .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE, "fighting")
                        .variable("wave", 10))
                .build();

        pay(plugin, occasion, "mob.yml", 10, "every 5");

        alex.assertAnyMessageContains("Boss wave 10 cleared: +250");
        assertEquals("default", PhasedRewardTable.parse(plugin.getOwner(), occasion, yaml("mob.yml")).getKey(7));
    }

    @Test
    void pvp() {
        RewardOccasion occasion = RewardOccasion.builder(plugin.getOwner(), "duel-won").marks("winner")
                .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "winner")
                        .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE))
                .build();

        RewardReceipt receipt = pay(plugin, occasion, "pvp.yml", 1, "default");

        assertEquals(1, receipt.getStep(0).getLines().size());
        assertEquals(steve.getUniqueId(), receipt.getStep(0).getLines().get(0).getMember());
        steve.assertAnyMessageContains("You won the duel: +200");
    }

    @Test
    void territory() {
        RewardOccasion occasion = RewardOccasion.builder(plugin.getOwner(), "territory-invaded").marks("damaged")
                .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.TEN)
                        .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE, "damaged")
                        .participant(bob, "Bob", BigDecimal.ONE, "damaged"))
                .build();

        RewardReceipt receipt = pay(plugin, occasion, "territory.yml", 1, "default");

        assertEquals(RewardReceipt.Status.LOST, receipt.getStep(0).getLine(bob).getStatus(), "Bob is offline and forfeits");
        assertTrue(receipt.getStep(0).getLine(steve.getUniqueId()).getStatus().isPaid(), "Steve is the top defender");
        assertTrue(await(plugin.deliveries().listOf(bob)).isEmpty());
    }

    @Test
    void pocketPokeBoss() {
        RewardOccasion occasion = RewardOccasion.builder(plugin.getOwner(), "boss-stage").phase("stage").marks("won").variables("stage")
                .sample(scene -> scene.participant(steve.getUniqueId(), "Steve", BigDecimal.ONE, "won")
                        .participant(alex.getUniqueId(), "Alex", BigDecimal.ONE)
                        .variable("stage", 3))
                .build();

        RewardReceipt receipt = pay(plugin, occasion, "pocketpokeboss.yml", 3, "default");

        assertEquals(Collections.singletonList("console: broadcast The boss of stage 3 fell"), commands());
        alex.assertAnyMessageContains("Stage 3: you were there, Alex.");
        assertEquals(1, receipt.getStep(2).getLines().size());
    }

    // ------------------------------------------------------------------

    /** Reads {@code fixture} under {@code occasion}, checks which key takes {@code phase}, and pays the occasion's sample. */
    private RewardReceipt pay(LogicWorld owner, RewardOccasion occasion, String fixture, int phase, String key) {
        PhasedRewardTable rewards = PhasedRewardTable.parse(owner.getOwner(), occasion, yaml(fixture));
        assertEquals(key, rewards.getKey(phase));
        RewardTable table = rewards.getTable(phase);
        assertFalse(table.isEmpty());

        RewardReceipt receipt = await(table.pay(RewardContext.builder(owner.caller(occasion.getId()), ProgressOwner.global())
                .scene(occasion.getSample()).build()));

        assertEquals(table.getSteps().size(), receipt.getSteps().size());
        return receipt;
    }

    private List<String> commands() {
        return world.platform().getTestPlayerState().getCommands();
    }
}
