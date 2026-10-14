package br.com.finalcraft.evernifecore.hytale.inventory.player;

import br.com.finalcraft.evernifecore.hytale.api.HytaleFPlayer;
import br.com.finalcraft.evernifecore.hytale.inventory.GenericInventory;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A snapshot of a player inventory is only worth taking if it goes back where it came from. Restoring also
 * blanks every slot it does not fill, so two sections sharing one container do not merely misplace an item -
 * the later write wipes the earlier one and the player loses the difference. These tests pin the pairing on
 * both halves of the round trip, section by section - on the mirrors themselves and on the public restore
 * that drives them.
 */
class FCPlayerInventorySectionsTest {

    /** The pairing as it is meant to be, written once and read by every test below. */
    private enum Section {
        STORAGE ("storage-item",  FakeInventory::getStorage,  FCPlayerInventory::getStorage),
        ARMOR   ("armor-item",    FakeInventory::getArmor,    FCPlayerInventory::getArmor),
        HOTBAR  ("hotbar-item",   FakeInventory::getHotbar,   FCPlayerInventory::getHotbar),
        UTILITY ("utility-item",  FakeInventory::getUtility,  FCPlayerInventory::getUtility),
        TOOLS   ("tools-item",    FakeInventory::getTools,    FCPlayerInventory::getTools),
        BACKPACK("backpack-item", FakeInventory::getBackpack, FCPlayerInventory::getBackpack);

        final String itemId;
        final Function<FakeInventory, ItemContainer> container;
        final Function<FCPlayerInventory, GenericInventory> section;

        Section(String itemId, Function<FakeInventory, ItemContainer> container, Function<FCPlayerInventory, GenericInventory> section) {
            this.itemId = itemId;
            this.container = container;
            this.section = section;
        }
    }

    /**
     * A live inventory whose six sections are six separate containers, sized apart so a section landing in a
     * neighbour is visible, answered by section id the way a player's inventory components are. The real
     * ones hang off an entity store a test JVM has no way to build.
     */
    private static class FakeInventory implements IntFunction<ItemContainer> {
        private final ItemContainer storage = new SimpleItemContainer((short) 30);
        private final ItemContainer armor = new SimpleItemContainer((short) 4);
        private final ItemContainer hotbar = new SimpleItemContainer((short) 9);
        private final ItemContainer utility = new SimpleItemContainer((short) 3);
        private final ItemContainer tools = new SimpleItemContainer((short) 5);
        private final ItemContainer backpack = new SimpleItemContainer((short) 12);

        ItemContainer getStorage() { return storage; }
        ItemContainer getArmor() { return armor; }
        ItemContainer getHotbar() { return hotbar; }
        ItemContainer getUtility() { return utility; }
        ItemContainer getTools() { return tools; }
        ItemContainer getBackpack() { return backpack; }

        @Override
        public ItemContainer apply(int sectionId) {
            switch (sectionId) {
                case InventoryComponent.STORAGE_SECTION_ID: return storage;
                case InventoryComponent.ARMOR_SECTION_ID: return armor;
                case InventoryComponent.HOTBAR_SECTION_ID: return hotbar;
                case InventoryComponent.UTILITY_SECTION_ID: return utility;
                case InventoryComponent.TOOLS_SECTION_ID: return tools;
                case InventoryComponent.BACKPACK_SECTION_ID: return backpack;
                default: return null;
            }
        }
    }

    /** A restore reaches the inventory through {@code getInventorySection}; the entity behind it needs a live Universe. */
    private static class FakeFPlayer extends HytaleFPlayer<FakeInventory> {
        FakeFPlayer(FakeInventory inventory) {
            super(inventory);
        }

        @Override
        public ItemContainer getInventorySection(int sectionId) {
            return getDelegate().apply(sectionId);
        }

        @Override
        public PlayerRef getPlayerRef() {
            throw new UnsupportedOperationException("a restore never asks for the PlayerRef");
        }
    }

    /**
     * Naming an item is all a real ItemStack cannot do here: resolving an id needs the server's asset
     * store, and {@code isValid()} is that lookup. It is answered by hand, so a test can have both an
     * id the store knows and one it does not.
     */
    private static class FakeItemStack extends ItemStack {
        private final boolean valid;

        FakeItemStack(String itemId) {
            this(itemId, true);
        }

        FakeItemStack(String itemId, boolean valid) {
            this.itemId = itemId;
            this.quantity = 1;
            this.valid = valid;
        }

        @Override
        public boolean isValid() {
            return valid;
        }
    }

    /** What is left in a slot after the mod that added the item is gone: an id nothing answers for. */
    private static FakeItemStack unresolvable(String itemId) {
        return new FakeItemStack(itemId, false);
    }

    private static FakeInventory inventoryWithAnItemInEverySection() {
        FakeInventory inventory = new FakeInventory();
        for (Section section : Section.values()) {
            section.container.apply(inventory).setItemStackForSlot((short) 0, new FakeItemStack(section.itemId));
        }
        return inventory;
    }

    private static FCPlayerInventory snapshotWithAnItemInEverySection() {
        return new FCPlayerInventory(
                sectionHolding(Section.STORAGE),
                sectionHolding(Section.ARMOR),
                sectionHolding(Section.HOTBAR),
                sectionHolding(Section.UTILITY),
                sectionHolding(Section.TOOLS),
                sectionHolding(Section.BACKPACK),
                new ArrayList<>()
        );
    }

    private static GenericInventory sectionHolding(Section section) {
        GenericInventory sectionInventory = new GenericInventory();
        sectionInventory.setItem(0, new FakeItemStack(section.itemId));
        return sectionInventory;
    }

    private static String itemIdAt(ItemContainer container, int slot) {
        return idOf(container.getItemStack((short) slot));
    }

    private static String itemIdAt(GenericInventory section, int slot) {
        return idOf(section.getItem(slot));
    }

    private static String idOf(ItemStack itemStack) {
        return itemStack == null || itemStack.isEmpty() ? null : itemStack.getItemId();
    }

    @Test
    void eachSectionIsSnapshottedFromItsOwnContainer() {
        FCPlayerInventory snapshot = new FCPlayerInventory();

        snapshot.snapshotSectionsFrom(inventoryWithAnItemInEverySection());

        for (Section section : Section.values()) {
            assertEquals(section.itemId, itemIdAt(section.section.apply(snapshot), 0),
                    section + " was read out of somebody else's container");
        }
    }

    @Test
    void eachSectionIsRestoredIntoItsOwnContainer() {
        FakeInventory target = new FakeInventory();

        snapshotWithAnItemInEverySection().restoreSectionsTo(target);

        for (Section section : Section.values()) {
            assertEquals(section.itemId, itemIdAt(section.container.apply(target), 0),
                    section + " was written into somebody else's container");
        }
    }

    /** Two sections sharing a container is not a misplaced item: whoever writes first is simply gone. */
    @Test
    void restoringOneSectionDoesNotEraseAnother() {
        FakeInventory target = new FakeInventory();

        snapshotWithAnItemInEverySection().restoreSectionsTo(target);

        for (Section section : Section.values()) {
            assertFalse(section.container.apply(target).isEmpty(),
                    section + " came back empty, so a later section overwrote it");
        }
    }

    @Test
    void aRoundTripPutsEveryItemBackWhereItCameFrom() {
        FakeInventory source = inventoryWithAnItemInEverySection();
        FCPlayerInventory snapshot = new FCPlayerInventory();
        snapshot.snapshotSectionsFrom(source);

        FakeInventory target = new FakeInventory();
        snapshot.restoreSectionsTo(target);

        for (Section section : Section.values()) {
            assertEquals(itemIdAt(section.container.apply(source), 0), itemIdAt(section.container.apply(target), 0),
                    section + " did not survive the round trip");
        }
    }

    /**
     * A snapshot is what will be written back, so an id nothing answers for has to be dropped on the way
     * in - restoring it would put an item into the world that the server has no asset for.
     */
    @Test
    void anItemTheAssetStoreCannotResolveIsLeftOutOfTheSnapshot() {
        FakeInventory source = new FakeInventory();
        source.getStorage().setItemStackForSlot((short) 0, new FakeItemStack("storage-item"));
        source.getStorage().setItemStackForSlot((short) 1, unresolvable("removed-mod:ancient-pickaxe"));

        FCPlayerInventory snapshot = new FCPlayerInventory();
        snapshot.snapshotSectionsFrom(source);

        assertEquals("storage-item", itemIdAt(snapshot.getStorage(), 0), "the item that does resolve is kept");
        assertNull(itemIdAt(snapshot.getStorage(), 1), "the one that does not is not an item at all");
    }

    /**
     * The factory collection is nullable on the public restore just like on the constructor, and null
     * means "no extra inventories to apply" - not "skip the six sections the caller asked to restore".
     */
    @Test
    void aRestoreWithoutFactoriesStillPutsEverySectionBack() {
        FakeInventory target = new FakeInventory();

        snapshotWithAnItemInEverySection().restoreTo(new FakeFPlayer(target), null);

        for (Section section : Section.values()) {
            assertEquals(section.itemId, itemIdAt(section.container.apply(target), 0),
                    section + " did not survive a restore with no extra-inventory factories");
        }
    }

    /** Restoring means "leave it exactly like the snapshot", which is why a stray section is destructive. */
    @Test
    void aRestoreBlanksTheSlotsTheSnapshotDoesNotFill() {
        FakeInventory target = new FakeInventory();
        target.getTools().setItemStackForSlot((short) 2, new FakeItemStack("left-over"));

        snapshotWithAnItemInEverySection().restoreSectionsTo(target);

        assertEquals(Section.TOOLS.itemId, itemIdAt(target.getTools(), 0));
        assertNull(itemIdAt(target.getTools(), 2), "a slot missing from the snapshot has to end up empty");
    }
}
