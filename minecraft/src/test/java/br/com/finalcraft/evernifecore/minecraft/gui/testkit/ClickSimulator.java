package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthetic clicks and drags, built the way a server builds them and delivered to the framework's
 * listener.
 *
 * <p>Slots are addressed the way the protocol addresses them: {@code raw}. Raw slot {@code n} of a
 * 27-slot screen is inside the screen, raw slot {@code 30} is in the player's own inventory, and the
 * two do <b>not</b> share a numbering - which is the whole reason the framework is not allowed to
 * read {@code getSlot()}. {@link #clickPlayerInventory(PlayerDouble, int, ClickType, InventoryAction)}
 * takes the player-inventory index and turns it into the raw slot, so a test can aim at the exact
 * collision.</p>
 */
public final class ClickSimulator {

    private final GuiEventBus events;

    ClickSimulator(GuiEventBus events) {
        this.events = events;
    }

    /** A click on the screen itself, at a raw (0-based) slot of the gui. */
    public InventoryClickEvent click(PlayerDouble player, int rawSlot, ClickType clickType, InventoryAction action) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenView(),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);
        events.getListener().onInventoryClick(event);
        return event;
    }

    /**
     * A number-key click: the same gesture as {@link #click}, plus the hotbar slot the key names.
     *
     * <p>{@code hotbarButton} is an index into the player's own hotbar, {@code 0..8}, which is what the
     * protocol sends and what {@code getHotbarButton()} answers - it is not a slot of either
     * container.</p>
     */
    public InventoryClickEvent clickWithHotbarKey(PlayerDouble player, int rawSlot, ClickType clickType,
                                                  InventoryAction action, int hotbarButton) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenView(),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action, hotbarButton);
        events.getListener().onInventoryClick(event);
        return event;
    }

    /** The everyday case: a plain left click that picks nothing up. */
    public InventoryClickEvent leftClick(PlayerDouble player, int rawSlot) {
        return click(player, rawSlot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
    }

    /**
     * A click on the player's own inventory. {@code playerSlot} is the index inside that inventory,
     * so {@code 0} is the slot the player sees as their first - and the raw slot it produces is far
     * past the end of the screen.
     */
    public InventoryClickEvent clickPlayerInventory(PlayerDouble player, int playerSlot, ClickType clickType,
                                                    InventoryAction action) {
        int rawSlot = player.getOpenView().getTopInventory().getSize() + playerSlot;
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenView(),
                InventoryType.SlotType.CONTAINER, rawSlot, clickType, action);
        events.getListener().onInventoryClick(event);
        return event;
    }

    /** A drag over {@code rawSlots}, each receiving one item. */
    public InventoryDragEvent drag(PlayerDouble player, ItemStack dragged, int... rawSlots) {
        Map<Integer, ItemStack> added = new LinkedHashMap<>();
        for (int rawSlot : rawSlots) {
            added.put(rawSlot, dragged);
        }
        InventoryDragEvent event = new InventoryDragEvent(player.getOpenView(), null, dragged, false, added);
        events.getListener().onInventoryDrag(event);
        return event;
    }

    /**
     * An even drag: the stack is divided between the slots of {@code rawSlots} that can take it, and
     * what is left stays on the cursor.
     *
     * <p>The map a server hands the event holds what each slot would END with, not what it receives -
     * {@code getNewItems()} on a slot already holding two of the same item reads four, not two - so a
     * test that means to divide a stack has to build it that way.</p>
     *
     * <p>A slot already holding something ELSE is left out of the event entirely, and the share is
     * divided between the rest. A client never drags a stack into a slot that cannot merge with it, and
     * an event that said it did would be saying the item in the way had turned into the dragged one.</p>
     */
    public InventoryDragEvent dragEvenly(PlayerDouble player, ItemStack dragged, int... rawSlots) {
        InventoryView view = player.getOpenView();
        List<Integer> accepting = new ArrayList<>();
        for (int rawSlot : rawSlots) {
            ItemStack held = view.getItem(rawSlot);
            if (held == null || held.getType() == Material.AIR || held.isSimilar(dragged)) {
                accepting.add(rawSlot);
            }
        }

        int share = accepting.isEmpty() ? 0 : dragged.getAmount() / accepting.size();
        Map<Integer, ItemStack> ending = new LinkedHashMap<>();
        for (int rawSlot : accepting) {
            ItemStack held = view.getItem(rawSlot);
            ItemStack result = dragged.clone();
            result.setAmount(share + (held == null ? 0 : held.getAmount()));
            ending.put(rawSlot, result);
        }

        int leftOver = dragged.getAmount() - share * accepting.size();
        ItemStack cursor = null;
        if (leftOver > 0) {
            cursor = dragged.clone();
            cursor.setAmount(leftOver);
        }
        InventoryDragEvent event = new InventoryDragEvent(view, cursor, dragged, false, ending);
        events.getListener().onInventoryDrag(event);
        return event;
    }

}
