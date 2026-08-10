package br.com.finalcraft.evernifecore.minecraft.gui.view;

import br.com.finalcraft.evernifecore.minecraft.inventory.ItemStore;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

/**
 * What a slot is about to hold, worked out from the gesture instead of read afterwards.
 *
 * <p>A store that judges its own updates has to be asked while the click can still be cancelled, and at
 * that moment nothing has moved yet - the server applies a click once every listener has had its say.
 * So the framework says what the gesture means: {@code PICKUP_HALF} on a stack of five leaves two,
 * {@code PLACE_ONE} on an empty slot leaves one.</p>
 *
 * <p>Actions are matched by NAME. A constant would tie this class to a version that has it, and the
 * whole point of the gui framework's click layer is that a 1.7.10 server and a 1.21 server go through
 * the same code. A name nobody here recognises answers {@link #UNKNOWN}, which is not a guess: a store
 * that vets its updates refuses what the framework cannot describe, because letting an undescribed
 * gesture through is exactly how the vetting would be worked around.</p>
 */
final class SlotOutcome {

    /** The gesture leaves this slot alone - dropping from the cursor, cloning, a click that does nothing. */
    static final SlotOutcome UNCHANGED = new SlotOutcome(false, true, null);

    /** The framework cannot say what this gesture does to the slot. */
    static final SlotOutcome UNKNOWN = new SlotOutcome(false, false, null);

    private final boolean known;
    private final boolean unchanged;
    private final ItemStack item;

    private SlotOutcome(boolean known, boolean unchanged, ItemStack item) {
        this.known = known;
        this.unchanged = unchanged;
        this.item = item;
    }

    /** The slot ends up holding {@code item}, or nothing when it is {@code null}. */
    static SlotOutcome of(@Nullable ItemStack item) {
        return new SlotOutcome(true, false, GuiBuffer.isEmpty(item) ? null : item.clone());
    }

    /**
     * What {@code actionName} leaves at a slot holding {@code current}, for a viewer whose cursor holds
     * {@code cursor} and whose pressed hotbar slot holds {@code hotbar}.
     */
    static SlotOutcome afterClick(String actionName, @Nullable ItemStack current, @Nullable ItemStack cursor,
                                  @Nullable ItemStack hotbar) {
        if (actionName == null) {
            return UNKNOWN;
        }
        int held = amountOf(current);
        switch (actionName) {
            case "NOTHING":
            case "DROP_ALL_CURSOR":
            case "DROP_ONE_CURSOR":
            case "CLONE_STACK":
                return UNCHANGED;
            case "PICKUP_ALL":
            case "DROP_ALL_SLOT":
                return of(null);
            case "MOVE_TO_OTHER_INVENTORY":
                //the platform moves as much of the stack as fits somewhere else, and how much that is
                //exists only after the click has been applied
                return UNKNOWN;
            case "PICKUP_HALF":
                //the cursor takes the bigger half of an odd stack
                return of(sized(current, held / 2));
            case "PICKUP_ONE":
            case "DROP_ONE_SLOT":
                return of(sized(current, held - 1));
            case "PICKUP_SOME":
                //as much as the cursor still has room for
                return of(sized(current, held - Math.min(held, room(cursor))));
            case "PLACE_ALL":
                return of(joined(current, cursor, amountOf(cursor)));
            case "PLACE_ONE":
                return of(joined(current, cursor, 1));
            case "PLACE_SOME":
                //as much of the cursor as the slot still has room for
                return of(joined(current, cursor, room(current)));
            case "SWAP_WITH_CURSOR":
                return of(cursor);
            case "HOTBAR_SWAP":
                return of(hotbar);
            default:
                return UNKNOWN;
        }
    }

    /** Whether the slot's next content is known. {@link #UNCHANGED} is not "known" - it is "no change". */
    boolean isKnown() {
        return known;
    }

    boolean isUnchanged() {
        return unchanged;
    }

    /** What the slot ends up holding, or {@code null} for an empty slot. Only meaningful when known. */
    @Nullable
    ItemStack getItem() {
        return item == null ? null : item.clone();
    }

    @Override
    public String toString() {
        if (unchanged) {
            return "SlotOutcome{unchanged}";
        }
        if (!known) {
            return "SlotOutcome{unknown}";
        }
        return "SlotOutcome{" + (item == null ? "empty" : item.getType() + " x" + item.getAmount()) + "}";
    }

    /** {@code base} grown by up to {@code amount} of {@code added}, or {@code added} on an empty slot. */
    private static ItemStack joined(ItemStack base, ItemStack added, int amount) {
        if (GuiBuffer.isEmpty(added) || amount <= 0) {
            return base;
        }
        if (GuiBuffer.isEmpty(base)) {
            return sized(added, Math.min(amount, amountOf(added)));
        }
        if (!base.isSimilar(added)) {
            return base;
        }
        return sized(base, amountOf(base) + Math.min(amount, amountOf(added)));
    }

    /** How much more of the same item {@code item} would still take. */
    private static int room(ItemStack item) {
        return GuiBuffer.isEmpty(item) ? 0 : Math.max(0, ItemStore.maxStackSizeOf(item) - amountOf(item));
    }

    private static int amountOf(ItemStack item) {
        return GuiBuffer.isEmpty(item) ? 0 : item.getAmount();
    }

    private static ItemStack sized(ItemStack item, int amount) {
        if (GuiBuffer.isEmpty(item) || amount <= 0) {
            return null;
        }
        ItemStack sized = item.clone();
        sized.setAmount(amount);
        return sized;
    }

}
