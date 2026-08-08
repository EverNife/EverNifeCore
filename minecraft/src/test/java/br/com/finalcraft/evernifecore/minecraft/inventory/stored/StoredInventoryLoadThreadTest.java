package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.config.factory.ConfigFactoryCodec;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.everyconfig.config.Config;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which thread a stored inventory is read back on.
 *
 * <p>Storage answers on a worker thread and never on the main one - a database callback, a file read, a
 * player's data arriving while the server ticks something else. An inventory being rebuilt there is
 * nobody's yet: no window shows it, no other thread holds it, and the thread rule that keeps a live one
 * from being written twice has nothing to protect. What it must not do is come back empty, because the
 * next save writes that emptiness over the real thing.</p>
 */
class StoredInventoryLoadThreadTest {

    private static final AtomicInteger UNIQUE_FILE = new AtomicInteger();

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

    @BeforeEach
    void setup() {
        //this world's main thread is the test thread, so anything below runs off it for real
        world = GuiTestWorld.install(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static ItemStack diamonds(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
    }

    private static StoredInventory filled() {
        StoredInventory inventory = new StoredInventory(27);
        inventory.setItemSilently(2, diamonds(4));
        inventory.setItemSilently(26, new ItemStack(Material.DIRT, 3));
        inventory.setMaxStackSize(0, 1);
        return inventory;
    }

    private static void assertHoldsWhatWasSaved(StoredInventory reloaded) {
        assertNotNull(reloaded, "an inventory read back is an inventory");
        assertEquals(27, reloaded.getSize());
        assertNotNull(reloaded.getItem(2), "slot 2 held four diamonds when it was written");
        assertEquals(Material.DIAMOND, reloaded.getItem(2).getType());
        assertEquals(4, reloaded.getItem(2).getAmount());
        assertEquals(Material.DIRT, reloaded.getItem(26).getType());
        assertEquals(3, reloaded.getItem(26).getAmount());
        assertNull(reloaded.getItem(1), "a slot nothing was in stays empty");
        assertEquals(1, reloaded.getMaxStackSize(0), "and a slot that holds one still holds one");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Coming back from where it was stored
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anInventoryStoredInABackendComesBackFilledWhenTheWorkerThreadReadsIt() {
        VaultedItems saved = new VaultedItems();
        saved.vault = filled();

        ConfigFactoryCodec<VaultedItems> codec = ConfigFactoryCodec.json(VaultedItems.class);
        byte[] stored = codec.encode(saved);

        VaultedItems read = offTheMainThread(() -> codec.decode(stored));

        assertHoldsWhatWasSaved(read.vault);
    }

    @Test
    void anInventoryStoredInAFileComesBackFilledWhenTheWorkerThreadReadsIt() {
        Path file = tempDir.resolve("storage-" + UNIQUE_FILE.incrementAndGet() + ".yml");
        Config config = ConfigFactory.open(file);
        config.setValue("backpack", filled());
        config.save();

        StoredInventory reloaded = offTheMainThread(
                () -> ConfigFactory.open(file).getValue("backpack", StoredInventory.class));

        assertHoldsWhatWasSaved(reloaded);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  And guarded again the moment anybody can see it
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anInventoryHandedBackByTheReadIsGuardedLikeAnyOther() {
        ConfigFactoryCodec<VaultedItems> codec = ConfigFactoryCodec.json(VaultedItems.class);
        byte[] stored = codec.encode(new VaultedItems());

        Throwable refusal = whatWasThrownOffTheMainThread(() -> {
            VaultedItems read = codec.decode(stored);
            read.vault.setItemSilently(5, diamonds(1));
            return null;
        });

        assertTrue(refusal instanceof IllegalStateException, "reading it is done, so the next hand to touch "
                + "it is one hand too many: " + refusal);
        assertTrue(String.valueOf(refusal.getMessage()).contains("FCScheduler"), refusal.getMessage());
    }

    @Test
    void theDoorThatFillsWithoutTheGuardShutsWhenTheInventoryIsHandedOver() {
        StoredInventory.Restoring restoring = StoredInventory.restoring(9);
        restoring.setItem(0, diamonds(2));
        restoring.build();

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> restoring.setItem(1, diamonds(1)));

        assertTrue(refusal.getMessage().contains("main thread"), "it says where a change goes now: "
                + refusal.getMessage());
        assertThrows(IllegalStateException.class, restoring::build, "and there is only one of it to give");
    }

    @Test
    void aLiveInventoryStillRefusesAChangeFromAnotherThread() {
        StoredInventory open = filled();

        Throwable refusal = whatWasThrownOffTheMainThread(() -> {
            open.setItemSilently(2, null);
            return null;
        });

        assertTrue(refusal instanceof IllegalStateException, String.valueOf(refusal));
        assertEquals(4, open.getItem(2).getAmount(), "nothing landed");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    /** A persisted entity with an inventory in it, which is the shape a plugin actually stores. */
    public static class VaultedItems {

        public StoredInventory vault = new StoredInventory(27);

        public VaultedItems() {                               //Jackson

        }

    }

    /** Runs {@code body} on a thread that is not this world's main one and answers what it answered. */
    private static <T> T offTheMainThread(Callable<T> body) {
        Outcome<T> outcome = runOffTheMainThread(body);
        if (outcome.thrown != null) {
            throw new AssertionError("The read failed off the main thread", outcome.thrown);
        }
        return outcome.value;
    }

    /** As above, for the calls that are expected to be refused: what it threw, or {@code null}. */
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
        }, "storage-worker");
        worker.start();
        try {
            worker.join();
            return answer.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the storage worker", interrupted);
        } catch (ExecutionException e) {
            throw new AssertionError("The storage worker never answered", e);
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
