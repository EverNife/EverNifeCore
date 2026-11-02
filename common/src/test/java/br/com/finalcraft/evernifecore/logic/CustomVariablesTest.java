package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
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
