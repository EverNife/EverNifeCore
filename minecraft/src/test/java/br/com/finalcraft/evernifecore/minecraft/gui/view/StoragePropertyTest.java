package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.ClickSimulator;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.GuiTestWorld;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.Items;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlatformMoves;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.minecraft.gui.testkit.SurfaceDouble;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventoryItemPostUpdateEvent;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventoryItemPreUpdateEvent;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import br.com.finalcraft.everyconfig.config.Config;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The property this whole area exists for: <b>no sequence of clicks changes how many items exist</b>.
 *
 * <p>Every other test here drives a case somebody thought of. This one drives cases nobody thought of:
 * it generates sequences of gestures - take, place, swap, shift, number key, drag, drop, the double
 * click that gathers - over stacking and non-stacking items, and after <i>every single step</i> it
 * counts what the player can still reach. The count is the whole assertion. An item duplicated shows up
 * as a count that grew; an item destroyed as one that shrank.</p>
 *
 * <p><b>Why the count means anything.</b> The framework does not move items - the server does, and no
 * test has one - so the moving is done by {@link PlatformMoves}, which only ever transfers: it takes
 * units off one place and puts them on another in the same statement, and a dropped stack leaves
 * through a list this test counts as the ground. A fake platform built that way cannot create or
 * destroy an item even when it is wrong about what a gesture does. So a total that moves is the
 * framework's doing: a region written twice, a share taken off a cursor that never arrived, a store
 * read back over an item that was still there.</p>
 *
 * <p>Two seeds run every time. The fixed one makes a failure reproducible forever; the random one is
 * what finds the case the fixed one never generated, and it is printed with the failure so it can be
 * pinned as a test of its own.</p>
 */
class StoragePropertyTest {

    /** Reproducible forever. A failure here is the same failure on every machine and every run. */
    private static final long FIXED_SEED = 20260807L;

    private static final int SEQUENCES_PER_SEED = 1000;
    private static final int STEPS_PER_SEQUENCE = 12;

    /** The middle row of a 27-slot screen. */
    private static final int[] AREA = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    /** A slot the region does not hold, drawn by a button - what a drag must never be written into. */
    private static final int BUTTON_SLOT = 4;

    /** What the count is kept of. The button is STONE on purpose: it is not one of these. */
    private static final Material[] POOL = {Material.DIAMOND, Material.DIRT, Material.DIAMOND_SWORD};

    private static final AtomicInteger UNIQUE_FILE = new AtomicInteger();

    //NEVER: the locale bootstrap's async saveAsync() can race JUnit's default @TempDir cleanup on Windows
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private GuiTestWorld world;
    private ClickSimulator clicks;

    @BeforeEach
    void setup() {
        world = GuiTestWorld.install(tempDir);
        clicks = world.getClicks();
    }

    @AfterEach
    void teardown() {
        if (world != null) world.close();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The property
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    void everyStepOfEveryGeneratedSequenceLeavesTheSameItemsWithinReach() {
        int moved = 0;
        moved += runSequences(FIXED_SEED, SEQUENCES_PER_SEED, Ending.CLOSED);
        moved += runSequences(randomSeed(), SEQUENCES_PER_SEED, Ending.CLOSED);

        assertTrue(moved > SEQUENCES_PER_SEED * STEPS_PER_SEQUENCE, "the sequences have to actually move "
                + "items, or the count would hold for two thousand sequences in which nothing ever happened "
                + "- only " + moved + " gestures did anything");
    }

    @Test
    void disconnectingAfterEveryStepOfASequenceKeepsTheCountAndGivesTheCursorBack() {
        //every prefix of every sequence, which is what makes this quadratic and worth fewer sequences
        runSequences(FIXED_SEED, 100, Ending.DISCONNECTED_AT_EVERY_PREFIX);
        runSequences(randomSeed(), 100, Ending.DISCONNECTED_AT_EVERY_PREFIX);
    }

    @Test
    void savingAndLoadingInTheMiddleOfASequenceChangesNothing() {
        runSequences(FIXED_SEED, 150, Ending.SAVED_AND_LOADED_MIDWAY);
        runSequences(randomSeed(), 150, Ending.SAVED_AND_LOADED_MIDWAY);
    }

    @Test
    void aScreenReopenedOnWhatWasSavedGoesOnFromWhereItWas() {
        Random random = new Random(FIXED_SEED);
        for (int sequence = 0; sequence < 50; sequence++) {
            Run first = new Run(random);
            for (int step = 0; step < STEPS_PER_SEQUENCE / 2; step++) {
                first.step();
            }
            Map<Material, Integer> midway = first.inReach();
            first.disconnect();
            assertEquals(midway, first.inReach(), first.describe("the count after the screen went away"));

            StoredInventory reloaded = roundTrip(first.store);
            Run second = first.reopenOn(reloaded);
            assertEquals(midway, second.inReach(), second.describe("the count after loading what was saved"));

            for (int step = 0; step < STEPS_PER_SEQUENCE / 2; step++) {
                second.step();
            }
            second.disconnect();
            assertEquals(midway, second.inReach(), second.describe("the count after going on from there"));
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Running them
    // -----------------------------------------------------------------------------------------------------------------

    /** What happens at the end of a generated sequence, which is a whole property of its own. */
    private enum Ending {
        /** The player closes the window. */
        CLOSED,
        /** The player's connection drops after each step in turn, the sequence restarted each time. */
        DISCONNECTED_AT_EVERY_PREFIX,
        /** The store is written out and read back in the middle, and the sequence goes on. */
        SAVED_AND_LOADED_MIDWAY
    }

    /**
     * @return how many of the generated gestures actually moved something, which is what stops a green
     *         run from meaning the generator produced nothing
     */
    private int runSequences(long seed, int sequences, Ending ending) {
        Random random = new Random(seed);
        int moved = 0;
        try {
            for (int sequence = 0; sequence < sequences; sequence++) {
                moved += ending == Ending.DISCONNECTED_AT_EVERY_PREFIX
                        ? runEveryPrefixOf(random)
                        : runOne(random, ending);
            }
        } catch (AssertionError failure) {
            throw new AssertionError("seed " + seed + " (pin it as a test of its own to keep it): "
                    + failure.getMessage(), failure);
        }
        return moved;
    }

    private int runOne(Random random, Ending ending) {
        Run run = new Run(random);
        int savedAt = ending == Ending.SAVED_AND_LOADED_MIDWAY ? 1 + random.nextInt(STEPS_PER_SEQUENCE - 1) : -1;
        for (int step = 0; step < STEPS_PER_SEQUENCE; step++) {
            run.step();
            if (step == savedAt) {
                run.saveAndLoad();
            }
        }
        run.disconnect();
        return run.gesturesThatMoved;
    }

    /**
     * The same generated sequence run once per prefix, each time cut short by the connection dropping:
     * the state a disconnect finds is different after every step, and the one it finds mid-gesture -
     * with a stack on the cursor - is the one that used to lose it.
     */
    private int runEveryPrefixOf(Random random) {
        List<Long> steps = new ArrayList<>();
        for (int step = 0; step < STEPS_PER_SEQUENCE; step++) {
            steps.add(random.nextLong());
        }
        long worldSeed = random.nextLong();

        int moved = 0;
        for (int prefix = 1; prefix <= steps.size(); prefix++) {
            Run run = new Run(new Random(worldSeed));
            for (int step = 0; step < prefix; step++) {
                run.stepWith(new Random(steps.get(step)));
            }
            run.disconnect();
            moved += run.gesturesThatMoved;
        }
        return moved;
    }

    /** A seed nobody chose, said out loud so a failure found by chance can be reproduced on purpose. */
    private static long randomSeed() {
        long seed = new Random().nextLong();
        System.out.println("StoragePropertyTest: generated sequences from seed " + seed);
        return seed;
    }

    /** The store written out and read back, through the real codec and a real file. */
    private StoredInventory roundTrip(StoredInventory store) {
        Path file = tempDir.resolve("property-" + UNIQUE_FILE.incrementAndGet() + ".yml");
        Config config = ConfigFactory.open(file);
        config.setValue("store", store);
        config.save();
        StoredInventory reloaded = ConfigFactory.open(file).getValue("store", StoredInventory.class);
        assertNotNull(reloaded, "what was written has to read back as something");
        return reloaded;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  One sequence
    // -----------------------------------------------------------------------------------------------------------------

    /** One player, one screen, one store, and the count they started with. */
    private final class Run {

        private final Random setupRandom;
        private final PlayerDouble player;
        private final List<ItemStack> ground = new ArrayList<>();
        private final List<StoredInventoryItemPreUpdateEvent> asked = new ArrayList<>();
        private final List<StoredInventoryItemPostUpdateEvent> told = new ArrayList<>();
        private final List<String> history = new ArrayList<>();

        private StoredInventory store;
        private SurfaceDouble surface;
        private GuiView view;
        private Map<Material, Integer> expected;
        private boolean everyQuestionIsRecorded = false;
        private int gesturesThatMoved = 0;

        private Run(Random random) {
            this.setupRandom = random;
            this.player = world.newPlayer("Steve");
            this.store = new StoredInventory(AREA.length);

            //half the runs go through a store that judges its own updates, which is a different path
            //through the framework: a drag is divided here instead of being left to the platform, and
            //every gesture is described before it happens instead of only read afterwards
            if (random.nextBoolean()) {
                if (random.nextBoolean()) {
                    store.setMaxStackSize(random.nextInt(AREA.length), 1 + random.nextInt(8));
                }
                boolean refusesDirt = random.nextBoolean();
                store.onPreUpdate(event -> {
                    asked.add(event);
                    ItemStack arriving = event.getNewItem();
                    event.setCancelled(refusesDirt && arriving != null && arriving.getType() == Material.DIRT);
                });
                everyQuestionIsRecorded = true;
            }
            store.onPostUpdate(told::add);

            for (int slot = 0; slot < AREA.length; slot++) {
                if (random.nextInt(4) == 0) {
                    store.setItemSilently(slot, someItem(random));
                }
            }
            open();

            for (int slot = 0; slot < player.getPlayerInventory().getSize(); slot++) {
                if (random.nextInt(9) == 0) {
                    player.getPlayerInventory().placeWithoutRecording(slot, someItem(random));
                }
            }
            if (random.nextBoolean()) {
                player.holding(someItem(random));
            }
            this.expected = inReach();
        }

        private void open() {
            Gui<LayoutBase> gui = Gui.of(3).debounce(0);
            gui.icon(BUTTON_SLOT, Icon.of(new ItemStack(Material.STONE)));
            gui.storage(Slots.of(AREA)).backedBy(store).policy(ClickPolicy.EDIT_ALL);
            this.view = world.openDetachedAndRegistered(gui, player);
            this.surface = world.getSurface();
        }

        /** Reopens on {@code loaded}: the same player, a new screen, the store that came off disk. */
        private Run reopenOn(StoredInventory loaded) {
            this.store = loaded;
            //handlers are code, so they do not come off a file - a plugin declares them again on load
            loaded.onPreUpdate(asked::add);
            loaded.onPostUpdate(told::add);
            this.everyQuestionIsRecorded = true;
            open();
            this.expected = inReach();
            return this;
        }

        private ItemStack someItem(Random random) {
            Material material = POOL[random.nextInt(POOL.length)];
            return material == Material.DIAMOND_SWORD
                    ? Items.single(material)
                    : Items.of(material, 1 + random.nextInt(12));
        }

        // -------------------------------------------------------------------------------------------------------------
        //  A step, and what has to be true after it
        // -------------------------------------------------------------------------------------------------------------

        private void step() {
            stepWith(setupRandom);
        }

        private void stepWith(Random random) {
            asked.clear();
            told.clear();
            String gesture = makeGesture(random);
            world.advanceTicks(1);
            history.add(gesture);

            assertEquals(expected, inReach(), describe("after [" + gesture + "]"));
            assertStoreAgreesWithTheWindow(gesture);
            assertWhatWasAskedIsWhatHappened(gesture);
        }

        /** The store lags the window by at most the tick between a click and the read that follows it. */
        private void assertStoreAgreesWithTheWindow(String gesture) {
            for (int index = 0; index < AREA.length; index++) {
                ItemStack shown = surface.getItem(AREA[index]);
                ItemStack kept = store.getItem(index);
                assertTrue(GuiBuffer.isSameOutput(shown, kept), describe("slot " + index + " shows "
                        + describeItem(shown) + " and the store kept " + describeItem(kept) + " a tick after ["
                        + gesture + "]"));
            }
        }

        /**
         * What the store was asked about is what it was then told about. The two numbers are worked out
         * by different code - one models the gesture before it happens, the other reads the container
         * afterwards - so a disagreement is a handler being told about a change it never got to refuse.
         */
        private void assertWhatWasAskedIsWhatHappened(String gesture) {
            if (!everyQuestionIsRecorded) {
                return;
            }
            for (StoredInventoryItemPostUpdateEvent happened : told) {
                boolean predicted = false;
                for (StoredInventoryItemPreUpdateEvent question : asked) {
                    predicted |= question.getSlot() == happened.getSlot()
                            && GuiBuffer.isSameOutput(question.getNewItem(), happened.getNewItem());
                }
                assertTrue(predicted, describe("slot " + happened.getSlot() + " ended up holding "
                        + describeItem(happened.getNewItem()) + " after [" + gesture + "], and nothing asked "
                        + "whether it could: " + asked));
            }
        }

        private void saveAndLoad() {
            StoredInventory reloaded = roundTrip(store);
            assertEquals(store.getCapacity(), reloaded.getCapacity());
            for (int slot = 0; slot < store.getCapacity(); slot++) {
                assertTrue(GuiBuffer.isSameOutput(store.getItem(slot), reloaded.getItem(slot)),
                        describe("slot " + slot + " came back from the file as "
                                + describeItem(reloaded.getItem(slot)) + " instead of "
                                + describeItem(store.getItem(slot))));
            }
            history.add("saved and loaded");
        }

        /** The ending every screen has, whether the player asked for it or their connection did. */
        private void disconnect() {
            List<ItemStack> shown = new ArrayList<>();
            for (int slot : AREA) {
                shown.add(surface.getItem(slot));
            }

            world.releaseDetached(view, CloseReason.DISCONNECTED);
            history.add("disconnected");

            assertEquals(expected, inReach(), describe("after the connection dropped"));
            assertTrue(GuiBuffer.isEmpty(player.getCursor()), describe("the cursor was still holding "
                    + describeItem(player.getCursor()) + " after the player left"));
            for (int index = 0; index < AREA.length; index++) {
                assertTrue(GuiBuffer.isSameOutput(shown.get(index), store.getItem(index)),
                        describe("the window was showing " + describeItem(shown.get(index)) + " at slot "
                                + index + " when the player left, and the store kept "
                                + describeItem(store.getItem(index))));
            }
        }

        // -------------------------------------------------------------------------------------------------------------
        //  Counting
        // -------------------------------------------------------------------------------------------------------------

        /**
         * Every item the player can still get to. While the screen is open the container is the truth and
         * the store is a copy of it; once it is gone, the store is all there is.
         */
        private Map<Material, Integer> inReach() {
            Map<Material, Integer> counts = new EnumMap<>(Material.class);
            for (Material material : POOL) {
                counts.put(material, 0);
            }
            if (view.isClosed()) {
                for (ItemStack kept : store.getContents()) {
                    count(counts, kept);
                }
            } else {
                for (int slot = 0; slot < surface.getSize(); slot++) {
                    count(counts, surface.getItem(slot));
                }
            }
            SurfaceDouble own = player.getPlayerInventory();
            for (int slot = 0; slot < own.getSize(); slot++) {
                count(counts, own.getItem(slot));
            }
            count(counts, player.getCursor());
            for (ItemStack dropped : ground) {
                count(counts, dropped);
            }
            return counts;
        }

        private void count(Map<Material, Integer> counts, ItemStack item) {
            if (GuiBuffer.isEmpty(item) || !counts.containsKey(item.getType())) {
                return;
            }
            counts.put(item.getType(), counts.get(item.getType()) + item.getAmount());
        }

        private String describe(String what) {
            return what + "\n  the sequence was: " + history + "\n  it started with " + expected;
        }

        // -------------------------------------------------------------------------------------------------------------
        //  The gestures
        // -------------------------------------------------------------------------------------------------------------

        /** Picks a gesture the state allows, performs it, and lets the fake platform carry it out. */
        private String makeGesture(Random random) {
            String before = snapshot();
            String gesture = chooseAndPerform(random);
            if (!before.equals(snapshot())) {
                gesturesThatMoved++;
            }
            return gesture;
        }

        /**
         * Where everything is, not just how much of it there is. A count alone cannot tell a sequence
         * that moved items around from one in which every gesture was refused, and a suite that only
         * proved the second would be green forever while proving nothing.
         */
        private String snapshot() {
            StringBuilder state = new StringBuilder();
            for (int slot = 0; slot < surface.getSize(); slot++) {
                state.append(describeItem(surface.getItem(slot))).append('|');
            }
            SurfaceDouble own = player.getPlayerInventory();
            for (int slot = 0; slot < own.getSize(); slot++) {
                state.append(describeItem(own.getItem(slot))).append('|');
            }
            return state.append(describeItem(player.getCursor())).append('|').append(ground.size()).toString();
        }

        private String chooseAndPerform(Random random) {
            List<Integer> filledRegionSlots = slotsHolding(true);
            List<Integer> emptyRegionSlots = slotsHolding(false);
            List<Integer> filledOwnSlots = ownSlotsHolding();
            boolean carrying = !GuiBuffer.isEmpty(player.getCursor());

            switch (random.nextInt(12)) {
                case 0:
                    if (!carrying && !filledRegionSlots.isEmpty()) {
                        int slot = pick(random, filledRegionSlots);
                        return clickRegion(slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
                    }
                    break;
                case 1:
                    if (!carrying && !filledRegionSlots.isEmpty()) {
                        int slot = pick(random, filledRegionSlots);
                        return clickRegion(slot, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
                    }
                    break;
                case 2:
                    if (carrying && !emptyRegionSlots.isEmpty()) {
                        int slot = pick(random, emptyRegionSlots);
                        return clickRegion(slot, ClickType.LEFT, InventoryAction.PLACE_ALL);
                    }
                    break;
                case 3:
                    if (carrying && !filledRegionSlots.isEmpty()) {
                        int slot = pick(random, filledRegionSlots);
                        ItemStack held = surface.getItem(slot);
                        return held.isSimilar(player.getCursor())
                                ? clickRegion(slot, ClickType.RIGHT, InventoryAction.PLACE_ONE)
                                : clickRegion(slot, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
                    }
                    break;
                case 4:
                    if (!filledOwnSlots.isEmpty()) {
                        int slot = pick(random, filledOwnSlots);
                        return clickOwn(slot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
                    }
                    break;
                case 5:
                    if (!filledRegionSlots.isEmpty()) {
                        int slot = pick(random, filledRegionSlots);
                        return clickRegion(slot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
                    }
                    break;
                case 6: {
                    int slot = AREA[random.nextInt(AREA.length)];
                    int button = random.nextInt(9);
                    InventoryClickEvent event = clicks.clickWithHotbarKey(player, slot, ClickType.NUMBER_KEY,
                            InventoryAction.HOTBAR_SWAP, button);
                    PlatformMoves.applyClick(player, event, ground);
                    return "number key " + button + " on " + slot;
                }
                case 7: {
                    int slot = carrying || filledRegionSlots.isEmpty()
                            ? AREA[random.nextInt(AREA.length)]
                            : pick(random, filledRegionSlots);
                    return clickRegion(slot, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR);
                }
                case 8:
                    if (!filledRegionSlots.isEmpty()) {
                        int slot = pick(random, filledRegionSlots);
                        return random.nextBoolean()
                                ? clickRegion(slot, ClickType.DROP, InventoryAction.DROP_ONE_SLOT)
                                : clickRegion(slot, ClickType.CONTROL_DROP, InventoryAction.DROP_ALL_SLOT);
                    }
                    break;
                case 9:
                    if (carrying && player.getCursor().getAmount() >= 2) {
                        return drag(random);
                    }
                    break;
                case 10:
                    //not a gesture: the screen drawing itself again in the middle of one. It belongs in
                    //the sequence because it is the framework's own writing, and the framework writing
                    //over a slot the player owns is how an item disappears without anybody touching it
                    if (random.nextBoolean()) {
                        view.resync();
                        return "the screen put itself back";
                    }
                    view.refresh();
                    return "the screen rendered again";
                default:
                    break;
            }
            //whatever the state was, one of these always applies
            if (carrying && !emptyRegionSlots.isEmpty()) {
                return clickRegion(pick(random, emptyRegionSlots), ClickType.LEFT, InventoryAction.PLACE_ALL);
            }
            if (!carrying && !filledOwnSlots.isEmpty()) {
                return clickOwn(pick(random, filledOwnSlots), ClickType.LEFT, InventoryAction.PICKUP_ALL);
            }
            if (!carrying && !filledRegionSlots.isEmpty()) {
                return clickRegion(pick(random, filledRegionSlots), ClickType.LEFT, InventoryAction.PICKUP_ALL);
            }
            return clickRegion(AREA[random.nextInt(AREA.length)], ClickType.LEFT, InventoryAction.NOTHING);
        }

        private String clickRegion(int rawSlot, ClickType clickType, InventoryAction action) {
            InventoryClickEvent event = clicks.click(player, rawSlot, clickType, action);
            PlatformMoves.applyClick(player, event, ground);
            return action + " at " + rawSlot + (event.isCancelled() ? " (refused)" : "");
        }

        private String clickOwn(int ownSlot, ClickType clickType, InventoryAction action) {
            //the player's own inventory numbers its slots differently from the raw slots of a window
            int playerSlot = ownSlot >= 9 ? ownSlot - 9 : ownSlot + 27;
            InventoryClickEvent event = clicks.clickPlayerInventory(player, playerSlot, clickType, action);
            PlatformMoves.applyClick(player, event, ground);
            return action + " in the player's own slot " + ownSlot + (event.isCancelled() ? " (refused)" : "");
        }

        private String drag(Random random) {
            //a set: a client never sends the same slot twice, and a drag event that did would say a
            //stack was divided into more parts than it has slots to land in
            Set<Integer> over = new LinkedHashSet<>();
            int slots = 2 + random.nextInt(2);
            for (int index = 0; index < slots; index++) {
                over.add(random.nextInt(6) == 0 ? BUTTON_SLOT : AREA[random.nextInt(AREA.length)]);
            }
            int[] rawSlots = new int[over.size()];
            int index = 0;
            for (Integer rawSlot : over) {
                rawSlots[index++] = rawSlot;
            }
            PlatformMoves.applyDrag(player, clicks.dragEvenly(player, player.getCursor(), rawSlots));
            return "drag over " + over;
        }

        private List<Integer> slotsHolding(boolean filled) {
            List<Integer> slots = new ArrayList<>();
            for (int slot : AREA) {
                if (GuiBuffer.isEmpty(surface.getItem(slot)) != filled) {
                    slots.add(slot);
                }
            }
            return slots;
        }

        private List<Integer> ownSlotsHolding() {
            List<Integer> slots = new ArrayList<>();
            SurfaceDouble own = player.getPlayerInventory();
            for (int slot = 0; slot < own.getSize(); slot++) {
                if (!GuiBuffer.isEmpty(own.getItem(slot))) {
                    slots.add(slot);
                }
            }
            return slots;
        }

        private int pick(Random random, List<Integer> from) {
            return from.get(random.nextInt(from.size()));
        }

    }

    private static String describeItem(ItemStack item) {
        return GuiBuffer.isEmpty(item) ? "nothing" : item.getType() + " x" + item.getAmount();
    }

}
