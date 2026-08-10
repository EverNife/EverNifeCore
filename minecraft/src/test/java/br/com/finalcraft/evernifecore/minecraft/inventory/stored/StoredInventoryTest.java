package br.com.finalcraft.evernifecore.minecraft.inventory.stored;

import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.Items;
import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import br.com.finalcraft.evernifecore.minecraft.inventory.UpdateCause;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The storage type itself, away from any screen: how big it is, how much each slot holds, who is asked
 * before a change and who is told after one, and which thread may make one at all.
 */
class StoredInventoryTest {

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;

    @BeforeEach
    void setup() {
        //for the main thread this world declares, which is what a change is measured against
        world = GuiTestWorld.install(tempDir);
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    private static ItemStack diamonds(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Capacity
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void anInventoryHasAsManySlotsAsItWasGivenAndNoMore() {
        StoredInventory inventory = new StoredInventory(9);

        assertEquals(9, inventory.getCapacity());
        assertEquals(9, inventory.getCapacity(), "the seam and the type answer the same number");

        IndexOutOfBoundsException failure = assertThrows(IndexOutOfBoundsException.class,
                () -> inventory.setItemSilently(9, diamonds(1)));
        assertTrue(failure.getMessage().contains("setCapacity"), "the complaint says how to get more slots: "
                + failure.getMessage());
    }

    @Test
    void growingIsFreeAndShrinkingOverAnItemIsRefused() {
        StoredInventory inventory = new StoredInventory(2);
        inventory.setItemSilently(1, diamonds(3));

        inventory.setCapacity(5);
        assertEquals(5, inventory.getCapacity());
        assertEquals(3, inventory.getItem(1).getAmount(), "what it held is where it was");

        inventory.setItemSilently(4, new ItemStack(Material.DIRT));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> inventory.setCapacity(3));
        assertTrue(failure.getMessage().contains("slot 4"), failure.getMessage());
        assertEquals(5, inventory.getCapacity(), "and the refusal left it as it was");

        inventory.setItemSilently(4, null);
        inventory.setCapacity(3);
        assertEquals(3, inventory.getCapacity(), "over an empty slot there is nothing to lose");
    }

    @Test
    void anInventoryOfNoSlotsIsRefusedWhereItIsDeclared() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new StoredInventory(0));
        assertTrue(failure.getMessage().contains("holds nothing"), failure.getMessage());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The maximum stack size of one slot
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aSlotHoldsWhatTheItemAllowsUntilSomebodySaysOtherwise() {
        StoredInventory inventory = new StoredInventory(3);

        assertEquals(StoredInventory.ITEM_DEFAULT, inventory.getMaxStackSize(0));
        assertEquals(ItemStore.maxStackSizeOf(diamonds(1)), inventory.getMaxStackSize(0, diamonds(1)));

        inventory.setMaxStackSize(0, 16);

        assertEquals(16, inventory.getMaxStackSize(0));
        assertEquals(16, inventory.getMaxStackSize(0, diamonds(1)));
        assertEquals(1, inventory.getMaxStackSize(0, Items.single(Material.DIAMOND_SWORD)),
                "a cap of sixteen does not make a sword stack: the smaller of the two wins");
        assertEquals(ItemStore.maxStackSizeOf(diamonds(1)), inventory.getMaxStackSize(1, diamonds(1)),
                "and the slot next to it was not touched");

        inventory.setMaxStackSize(1, 100);
        assertEquals(ItemStore.maxStackSizeOf(diamonds(1)), inventory.getMaxStackSize(1, diamonds(1)),
                "a cap bigger than the item allows does not make the item bigger");
    }

    @Test
    void aSlotThatWouldHoldNothingIsRefused() {
        StoredInventory inventory = new StoredInventory(3);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> inventory.setMaxStackSize(0, -1));
        assertTrue(failure.getMessage().contains("ITEM_DEFAULT"), failure.getMessage());
    }

    @Test
    void aStackTooBigForItsSlotIsRefusedByTheDoorThatAsks() {
        StoredInventory inventory = new StoredInventory(3).setMaxStackSize(0, 4);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> inventory.setItem(UpdateCause.PLUGIN, 0, diamonds(5)));
        assertTrue(failure.getMessage().contains("setMaxStackSize"), failure.getMessage());

        inventory.setItemSilently(0, diamonds(5));
        assertEquals(5, inventory.getItem(0).getAmount(), "putting back what a container already holds is "
                + "never refused - refusing it would be destroying it");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Copies
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void nothingTheInventoryHoldsIsAStackSomebodyElseCanChange() {
        StoredInventory inventory = new StoredInventory(3);
        ItemStack given = diamonds(5);
        inventory.setItemSilently(0, given);

        given.setAmount(64);
        assertEquals(5, inventory.getItem(0).getAmount(), "what was handed over was copied on the way in");

        ItemStack taken = inventory.getItem(0);
        taken.setAmount(1);
        assertEquals(5, inventory.getItem(0).getAmount(), "and copied again on the way out");

        List<ItemStack> contents = inventory.getContents();
        contents.get(0).setAmount(2);
        assertEquals(5, inventory.getItem(0).getAmount());
        assertNotSame(inventory.getItem(0), inventory.getItem(0));
    }

    @Test
    void anInventoryKnowsWhichOfItsSlotsAreFilled() {
        StoredInventory inventory = new StoredInventory(5);
        assertTrue(inventory.isEmpty());
        assertEquals(0, inventory.getOccupiedSlots().length);

        inventory.setItemSilently(1, diamonds(2));
        inventory.setItemSilently(4, new ItemStack(Material.DIRT));

        assertFalse(inventory.isEmpty());
        assertArrayEqualsInAnyOrder(new int[]{1, 4}, inventory.getOccupiedSlots());

        inventory.setItemSilently(1, null);
        assertArrayEqualsInAnyOrder(new int[]{4}, inventory.getOccupiedSlots());
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Being asked, and being told
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void theHandlerAskedFirstSeesHowManyUnitsAreLeavingAndArriving() {
        List<StoredInventoryItemPreUpdateEvent> asked = new ArrayList<>();
        StoredInventory inventory = new StoredInventory(3).onPreUpdate(asked::add);
        inventory.setItemSilently(0, diamonds(5));

        inventory.setItem(UpdateCause.PLAYER, 0, diamonds(2));

        assertEquals(1, asked.size());
        assertEquals(0, asked.get(0).getSlot());
        assertEquals(UpdateCause.PLAYER, asked.get(0).getCause());
        assertEquals(3, asked.get(0).getRemovedAmount(), "five became two");
        assertEquals(0, asked.get(0).getAddedAmount());
        assertEquals(5, asked.get(0).getPreviousItem().getAmount());
        assertEquals(2, asked.get(0).getNewItem().getAmount());
    }

    @Test
    void changingTheItemItselfRemovesAllOfOneAndAddsAllOfTheOther() {
        List<StoredInventoryItemPreUpdateEvent> asked = new ArrayList<>();
        StoredInventory inventory = new StoredInventory(3).onPreUpdate(asked::add);
        inventory.setItemSilently(0, diamonds(5));

        inventory.setItem(UpdateCause.PLUGIN, 0, new ItemStack(Material.DIRT, 2));

        assertEquals(5, asked.get(0).getRemovedAmount(), "no diamond stayed");
        assertEquals(2, asked.get(0).getAddedAmount(), "and two of something else arrived");
    }

    @Test
    void anEmptiedSlotRemovesEverythingAndAFilledOneAddsEverything() {
        List<StoredInventoryItemPreUpdateEvent> asked = new ArrayList<>();
        StoredInventory inventory = new StoredInventory(3).onPreUpdate(asked::add);

        inventory.setItem(UpdateCause.PLUGIN, 0, diamonds(4));
        assertEquals(4, asked.get(0).getAddedAmount());
        assertEquals(0, asked.get(0).getRemovedAmount());
        assertNull(asked.get(0).getPreviousItem());

        inventory.setItem(UpdateCause.PLUGIN, 0, null);
        assertEquals(0, asked.get(1).getAddedAmount());
        assertEquals(4, asked.get(1).getRemovedAmount());
        assertNull(asked.get(1).getNewItem());
    }

    @Test
    void aRefusedChangeDoesNotHappenAndIsNotReported() {
        List<StoredInventoryItemPostUpdateEvent> told = new ArrayList<>();
        StoredInventory inventory = new StoredInventory(3)
                .onPreUpdate(event -> event.setCancelled(true))
                .onPostUpdate(told::add);
        inventory.setItemSilently(0, diamonds(5));

        assertFalse(inventory.setItem(UpdateCause.PLAYER, 0, diamonds(1)));

        assertEquals(5, inventory.getItem(0).getAmount(), "the slot is as it was");
        assertTrue(told.isEmpty(), "and nothing that did not happen was reported");
    }

    @Test
    void theHandlerToldAfterwardsFindsTheChangeAlreadyMade() {
        List<Integer> seen = new ArrayList<>();
        StoredInventory inventory = new StoredInventory(3);
        inventory.onPostUpdate(event -> seen.add(event.getInventory().getItem(event.getSlot()).getAmount()));

        assertTrue(inventory.setItem(UpdateCause.PLUGIN, 0, diamonds(7)));

        assertEquals(1, seen.size());
        assertEquals(7, seen.get(0).intValue(), "a handler that persists has to read what it is persisting");
    }

    @Test
    void aSilentWriteAsksNobodyAndTellsNobody() {
        List<StoredInventoryItemUpdateEvent> heard = new ArrayList<>();
        StoredInventory inventory = new StoredInventory(3)
                .onPreUpdate(heard::add)
                .onPostUpdate(heard::add);

        inventory.setItemSilently(0, diamonds(3));

        assertEquals(3, inventory.getItem(0).getAmount());
        assertTrue(heard.isEmpty(), "it is how a container already holding the item is read back");
    }

    @Test
    void aHandlerThatThrowsRefusesTheChangeInsteadOfLettingItThrough() {
        StoredInventory inventory = new StoredInventory(3).onPreUpdate(event -> {
            throw new IllegalStateException("a handler with a bug in it");
        });

        assertFalse(inventory.setItem(UpdateCause.PLAYER, 0, diamonds(1)));
        assertNull(inventory.getItem(0), "of the two ways to be wrong, the one that can be seen is chosen");
    }

    @Test
    void anInventoryOnlyVetsWhenItHasSomethingToSay() {
        assertFalse(new StoredInventory(3).vetsUpdates());
        assertTrue(new StoredInventory(3).onPreUpdate(event -> {
        }).vetsUpdates());
        assertTrue(new StoredInventory(3).setMaxStackSize(0, 8).vetsUpdates(),
                "a slot that holds less than the item would is a rule the framework has to enforce too");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The thread contract
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void aChangeFromAnotherThreadIsRefusedAndSaysWhichThreadTried() throws Exception {
        StoredInventory inventory = new StoredInventory(3);

        Throwable refusal = offThread(() -> {
            inventory.setItemSilently(0, diamonds(1));
            return null;
        });

        assertTrue(refusal instanceof IllegalStateException, String.valueOf(refusal));
        assertTrue(refusal.getMessage().contains("virtual-inventory-test"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("FCScheduler"), "and it says where to go instead: "
                + refusal.getMessage());
        assertNull(inventory.getItem(0), "nothing landed");
    }

    @Test
    void everyDoorThatChangesSomethingRefusesTheSameWay() throws Exception {
        StoredInventory inventory = new StoredInventory(3);

        assertTrue(offThread(() -> inventory.setItem(UpdateCause.PLUGIN, 0, diamonds(1)))
                instanceof IllegalStateException);
        assertTrue(offThread(() -> inventory.setCapacity(9)) instanceof IllegalStateException);
        assertEquals(3, inventory.getCapacity());
    }

    @Test
    void readingFromAnotherThreadIsAllowedAndAnswersASnapshot() throws Exception {
        StoredInventory inventory = new StoredInventory(3);
        inventory.setItemSilently(0, diamonds(6));

        CompletableFuture<Object> read = new CompletableFuture<>();
        Thread reader = new Thread(() -> read.complete(inventory.getItem(0)), "virtual-inventory-reader");
        reader.start();
        reader.join();

        assertEquals(6, ((ItemStack) read.get()).getAmount());
        assertEquals(3, inventory.getContents().size());
    }

    /** Runs {@code body} on a named thread and answers whatever it threw, or {@code null}. */
    private static Throwable offThread(Callable<Object> body) throws Exception {
        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                body.call();
                outcome.complete(null);
            } catch (Throwable thrown) {
                outcome.complete(thrown);
            }
        }, "virtual-inventory-test-worker");
        thread.start();
        thread.join();
        try {
            return outcome.get();
        } catch (ExecutionException e) {
            throw new AssertionError("the off-thread call never answered", e);
        }
    }

    private static void assertArrayEqualsInAnyOrder(int[] expected, int[] actual) {
        int[] sortedExpected = expected.clone();
        int[] sortedActual = actual.clone();
        Arrays.sort(sortedExpected);
        Arrays.sort(sortedActual);
        assertEquals(Arrays.toString(sortedExpected), Arrays.toString(sortedActual));
    }

}
