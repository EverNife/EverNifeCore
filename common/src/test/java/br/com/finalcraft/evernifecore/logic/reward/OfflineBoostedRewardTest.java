package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.booster.Booster;
import br.com.finalcraft.evernifecore.booster.BoosterDomain;
import br.com.finalcraft.evernifecore.booster.BoosterJoin;
import br.com.finalcraft.evernifecore.booster.BoosterSource;
import br.com.finalcraft.evernifecore.booster.Boosters;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.ProgressOwner;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestOfflinePermissions;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.await;
import static br.com.finalcraft.evernifecore.logic.reward.RewardFixtures.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A duel winner who already left is paid with the permission booster their permission store holds: the payment loads
 * their permissions off the server thread first, and never waits on that load where the server would stand still. A
 * server that cannot read them pays the base, and the explanation says why. The test's thread plays the server thread
 * and runs the main-thread tasks other threads queue.
 */
class OfflineBoostedRewardTest {

    private static final String GOLD = "fakeduels:gold";
    private static final String NODE = "fakeduels.booster.win.3";

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld duels;
    private TestCurrency gold;
    private TestFPlayerSender cris;
    private RewardOccasion won;
    private BoosterDomain win;

    @BeforeEach
    void open() {
        world = Platforms.lenient().mainThreadQueued().install();
        duels = LogicWorld.open("FakeDuels", tempDir.resolve("duels"));
        gold = Economies.currency(GOLD, CurrencyOwner.Kind.PLAYER);
        world.withCurrency(duels.getOwner(), gold);
        duels.deliveries();
        cris = duels.player("Cris");
        duels.quit(cris);
        won = RewardOccasion.builder(duels.getOwner(), "duel-won").phase("round").build();
        win = BoosterDomain.of(duels.getOwner(), "win").join(BoosterJoin.sumBonus()).permission("fakeduels.booster.win", 2, 3).register();
    }

    @AfterEach
    void close() {
        duels.close();
        world.close();
    }

    @Test
    void anOfflineWinnerHoldingTheNodeIsPaidTheBoostedAmount() {
        TestOfflinePermissions store = world.platform().useTestOfflinePermissions().grant(cris.getUniqueId(), NODE);
        assertFalse(store.isLoaded(cris.getUniqueId()), "left long ago: not in memory");

        RewardReceipt receipt = onTheServer(winTable().pay(context().boostedBy(win).build()));

        RewardReceipt.Line line = receipt.getStep(0).getLine(cris.getUniqueId());
        assertEquals(RewardReceipt.Status.DELIVERED, line.getStatus());
        assertNumber("3", line.getMultiplier());
        assertNumber("1500", line.getAmount());
        List<Delivery> kept = await(duels.deliveries().listOf(cris.getUniqueId()));
        assertEquals("1500", kept.get(0).getVariables().get("amount"), "the delivery keeps base x factor");
        assertTrue(store.isLoaded(cris.getUniqueId()), "the payment loaded them first");
    }

    @Test
    void aServerThatCannotReadOfflinePermissionsPaysTheBaseAndExplainSaysWhy() {
        RewardReceipt receipt = await(winTable().pay(context().boostedBy(win).build()));

        assertNumber("500", receipt.getStep(0).getLine(cris.getUniqueId()).getAmount());
        assertNumber("1", receipt.getStep(0).getLine(cris.getUniqueId()).getMultiplier());
        String explained = win.resolve(cris.getUniqueId()).explain().getText();
        assertTrue(explained.contains("not read: evernifecore:permission: this server reads no permission of an offline player"), explained);
    }

    @Test
    void permissionsNotLoadedYetCountNothingAndExplainSaysWhy() {
        world.platform().useTestOfflinePermissions().grant(cris.getUniqueId(), NODE);

        RewardReceipt preview = winTable().preview(context().boostedBy(win).build());

        assertNumber("500", preview.getStep(0).getLine(cris.getUniqueId()).getAmount(), "a preview loads nothing");
        String explained = win.resolve(cris.getUniqueId()).explain().getText();
        assertTrue(explained.contains("were not loaded"), explained);
    }

    @Test
    void payingAnOfflineWinnerOnTheServerThreadNeverWaitsOnALoad() throws Exception {
        TestOfflinePermissions store = world.platform().useTestOfflinePermissions().grant(cris.getUniqueId(), NODE);
        quietly(Boosters.loadPermissions(Collections.singletonList(win), Collections.singletonList(cris.getUniqueId())));
        assertTrue(store.isLoaded(cris.getUniqueId()));

        AtomicReference<RewardReceipt> paid = new AtomicReference<>();
        List<String> logged = Logs.capture(() -> paid.set(quietly(winTable().pay(context().boostedBy(win).build()))));

        assertTrue(logged.stream().noneMatch(line -> line.contains("blocked the server thread")), logged.toString());
        assertNumber("3", paid.get().getStep(0).getLine(cris.getUniqueId()).getMultiplier());
    }

    @Test
    void afterALoadTheStepSettlesBackOnTheServerThread() {
        world.platform().useTestOfflinePermissions().grant(cris.getUniqueId(), NODE);
        List<Boolean> settledOnTheServer = new CopyOnWriteArrayList<>();
        Boosters.addSource(duels.getOwner(), new BoosterSource() {
            @Override
            public String getId() {
                return "fakeduels:thread-probe";
            }

            @Override
            public Collection<Booster> boostersFor(ExecutionContext ctx, BoosterDomain domain) {
                settledOnTheServer.add(ECScheduler.isServerThread());
                return Collections.emptyList();
            }
        });

        CompletableFuture<RewardReceipt> paying = winTable().pay(context().boostedBy(win).build());
        assertFalse(paying.isDone(), "the load is still on the store's thread");
        RewardReceipt receipt = onTheServer(paying);

        assertEquals(Collections.singletonList(true), settledOnTheServer, "resolved once, on the server thread");
        assertNumber("3", receipt.getStep(0).getLine(cris.getUniqueId()).getMultiplier());
    }

    @Test
    void aPaymentWithNothingToLoadStaysSynchronous() {
        world.platform().useTestOfflinePermissions();
        TestFPlayerSender ana = duels.player("Ana");
        ana.grant(NODE);
        RewardScene scene = RewardScene.builder(won).participant(ana.getUniqueId(), "Ana", BigDecimal.ONE).build();

        CompletableFuture<RewardReceipt> paying = winTable().pay(RewardContext.builder(duels.caller("duel"), ProgressOwner.global())
                .scene(scene).boostedBy(win).build());

        assertTrue(paying.isDone(), "settled and paid before pay returned");
        assertEquals(0, world.platform().getQueuedMainThreadTasks());
        assertNumber("3", paying.join().getStep(0).getLine(ana.getUniqueId()).getMultiplier());
    }

    // ------------------------------------------------------------------

    /** Runs the main-thread tasks other threads queue, on this (the server) thread, until {@code future} completes. */
    private RewardReceipt onTheServer(CompletableFuture<RewardReceipt> future) {
        long until = System.currentTimeMillis() + 5000;
        while (!future.isDone()) {
            world.platform().runQueuedMainThreadTasks();
            if (System.currentTimeMillis() > until) {
                throw new AssertionError("the payment never completed; queued: " + world.platform().getQueuedMainThreadTasks());
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
        return future.join();
    }

    private RewardTable winTable() {
        return RewardTable.parse(duels.getOwner(), won, node("[{'effects': ["
                + "{'type': 'money', 'amount': 500, 'currency': '" + GOLD + "'},"
                + "{'type': 'message', 'text': '+{amount} (base {base-amount})'}]}]"));
    }

    private RewardContext.Builder context() {
        RewardScene scene = RewardScene.builder(won).participant(cris.getUniqueId(), "Cris", BigDecimal.ONE).build();
        return RewardContext.builder(duels.caller("duel"), ProgressOwner.global()).scene(scene);
    }

    /** Waits through a plain future: a {@code get()} on a watched one would itself be the warning this test looks for. */
    private static <T> T quietly(CompletableFuture<T> future) {
        CompletableFuture<T> plain = new CompletableFuture<>();
        future.whenComplete((value, failure) -> {
            if (failure != null) {
                plain.completeExceptionally(failure);
            } else {
                plain.complete(value);
            }
        });
        try {
            return plain.get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void assertNumber(String expected, BigDecimal actual) {
        assertNumber(expected, actual, "");
    }

    private static void assertNumber(String expected, BigDecimal actual, String why) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + ", got " + actual + " " + why);
    }
}
