package br.com.finalcraft.evernifecore.minecraft.testkit;

import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.testkit.ItemWorld;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which thread a rig calls the server's main one.
 *
 * <p>A rig that answers everyone yes disarms every main-thread guard in the code under test for as
 * long as it is installed, and it does it to the whole suite at once: the answer comes from the
 * server object, and there is one of those per JVM. The guards then hold in production and are
 * unreachable in every test - which is how an inventory read off a worker came to be written back
 * empty with the suite green.</p>
 *
 * <p>So both rigs are pinned here, side by side and in one place, because the danger is not that one
 * of them is wrong: it is that they drift apart and only the honest one gets used.</p>
 */
class DescribedServerThreadTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    // -----------------------------------------------------------------------------------------------------------------
    //  What each rig answers
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theItemRigCallsOnlyTheThreadThatInstalledItTheMainOne() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            assertTrue(Bukkit.getServer().isPrimaryThread(), "the thread that installed the rig is the main one");
            assertFalse(offTheMainThread(() -> Bukkit.getServer().isPrimaryThread()),
                    "and no other thread is, however convenient that would be");
        }
    }

    @Test
    void theGuiRigAnswersTheVerySameWay() {
        try (GuiTestWorld world = GuiTestWorld.install(tempDir)) {
            assertTrue(Bukkit.getServer().isPrimaryThread());
            assertFalse(offTheMainThread(() -> Bukkit.getServer().isPrimaryThread()));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  And what that buys: a guard that can actually fire
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aChangeFromAWorkerIsRefusedWithTheItemRigInstalledToo() {
        try (ItemWorld world = ItemWorld.withMetadata(MCDetailedVersion.v1_21_R1)) {
            StoredInventory inventory = new StoredInventory(9);
            inventory.setItemSilently(0, new ItemStack(Material.DIAMOND, 4));

            Throwable refusal = whatWasThrownOffTheMainThread(() -> {
                inventory.setItemSilently(1, new ItemStack(Material.DIRT, 1));
                return null;
            });

            assertTrue(refusal instanceof IllegalStateException, "the item rig is not a licence to write "
                    + "an inventory from anywhere: " + refusal);
            assertTrue(String.valueOf(refusal.getMessage()).contains("described-server-worker"),
                    "and the refusal names the thread that tried: " + refusal.getMessage());
            assertNull(inventory.getItem(1), "nothing landed");
            assertEquals(4, inventory.getItem(0).getAmount(), "and what was there is untouched");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static <T> T offTheMainThread(Callable<T> body) {
        Outcome<T> outcome = runOffTheMainThread(body);
        if (outcome.thrown != null) {
            throw new AssertionError("The worker failed", outcome.thrown);
        }
        return outcome.value;
    }

    /** As above, for the calls expected to be refused: what it threw, or {@code null}. */
    private static Throwable whatWasThrownOffTheMainThread(Callable<?> body) {
        return runOffTheMainThread(body).thrown;
    }

    private static <T> Outcome<T> runOffTheMainThread(Callable<T> body) {
        CompletableFuture<Outcome<T>> answer = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                answer.complete(new Outcome<>(body.call(), null));
            } catch (Throwable thrown) {
                answer.complete(new Outcome<T>(null, thrown));
            }
        }, "described-server-worker");
        worker.start();
        try {
            worker.join();
            return answer.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the worker", interrupted);
        } catch (ExecutionException e) {
            throw new AssertionError("The worker never answered", e);
        }
    }

    private static final class Outcome<T> {

        private final T value;
        private final Throwable thrown;

        private Outcome(T value, Throwable thrown) {
            this.value = value;
            this.thrown = thrown;
        }

    }

}
