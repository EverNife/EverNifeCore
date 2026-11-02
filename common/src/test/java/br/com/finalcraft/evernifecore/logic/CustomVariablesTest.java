package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.ScriptedStorage;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A plugin's own variables, defined by whoever reaches them first. */
class CustomVariablesTest {

    private static final int THREADS = 8;
    private static final int ROUNDS = 20;

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;

    @BeforeEach
    void open() {
        world = Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().install();
        shop = LogicWorld.open("FakeShop", tempDir);
    }

    @AfterEach
    void close() {
        shop.close();
        world.close();
    }

    @Test
    void theRowHandedToTheStorageIsASnapshot() {
        CustomVariables.CustomVariable<BigDecimal> score = shop.customVariables().define("score", BigDecimal.class, BigDecimal.ZERO);
        TestFPlayerSender alex = shop.player("Alex");
        score.set(shop.context(alex).build(), BigDecimal.ONE);
        CustomVariables.Row saved = shop.getBackend().lastSaved(CustomVariables.COLLECTION, CustomVariables.Row.class).get();

        score.set(shop.context(alex).build(), BigDecimal.TEN);

        assertEquals("1", saved.value);
    }

    @Test
    void twoWritesOfOneValueLandingNewestFirstLeaveTheLastValueInTheBackend() {
        CustomVariables.CustomVariable<BigDecimal> score = shop.customVariables().define("score", BigDecimal.class, BigDecimal.ZERO);
        TestFPlayerSender alex = shop.player("Alex");
        ScriptedStorage backend = shop.getBackend();
        backend.holdWrites();
        score.set(shop.context(alex).build(), BigDecimal.ONE);
        score.set(shop.context(alex).build(), BigDecimal.TEN);

        while (backend.heldWrites() > 0) {
            backend.releaseNewestWrite();
        }
        backend.releaseWrites();
        CustomVariables.closeOwnedBy(shop.getOwner());

        assertEquals("10", backend.lastLanded(CustomVariables.COLLECTION, CustomVariables.Row.class).get().value);
    }

    @Test
    void manyThreadsDefiningOneVariableAtOnceAllGetTheSameOneAndNoneThrows() throws Exception {
        CustomVariables variables = shop.customVariables();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                String name = "score-" + round;
                CyclicBarrier start = new CyclicBarrier(THREADS);
                List<CompletableFuture<CustomVariables.CustomVariable<BigDecimal>>> defined = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    defined.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            start.await(5, TimeUnit.SECONDS);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                        return variables.define(name, BigDecimal.class, BigDecimal.ZERO);
                    }, pool));
                }

                CustomVariables.CustomVariable<BigDecimal> first = defined.get(0).get(10, TimeUnit.SECONDS);
                for (CompletableFuture<CustomVariables.CustomVariable<BigDecimal>> each : defined) {
                    assertSame(first, each.get(10, TimeUnit.SECONDS), "round " + round);
                }
                assertTrue(Variables.registry().find("fakeshop:" + name).isPresent(), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
