package br.com.finalcraft.evernifecore.minecraft.gui.model;

/**
 * What a click is trying to DO, independent of the platform enum that named it.
 *
 * <p>The mapping is done from the action's NAME, never from a constant: referencing a constant that
 * a 1.7.10 server does not carry would kill this class in its initializer and take the whole gui
 * framework with it. An action nobody recognises answers {@link #UNKNOWN}, and unknown is always
 * denied.</p>
 */
public enum ClickKind {

    /** The click changes nothing. */
    NOTHING,
    /** Taking items out of the slot (every {@code PICKUP_*}). */
    TAKE,
    /** Putting items into the slot (every {@code PLACE_*}). */
    PLACE,
    /** Exchanging the slot's content with the cursor. */
    SWAP,
    /** Dropping from the slot or from the cursor (every {@code DROP_*}). */
    DROP,
    /** Creative middle-click duplication. */
    CLONE,
    /** The number-key exchange with the hotbar (every {@code HOTBAR_*}). */
    HOTBAR,
    /** Shift-click sending the item to the other inventory. */
    MOVE_TO_OTHER_INVENTORY,
    /** Double-click gathering every matching stack onto the cursor. */
    COLLECT_TO_CURSOR,
    /** A drag across slots. Drags carry no action of their own, so they get this kind. */
    DRAG,
    /** Anything this version names and this framework does not recognise. */
    UNKNOWN;

    /**
     * Classifies an {@code InventoryAction} name. A name from a future version that matches none of
     * the known prefixes answers {@link #UNKNOWN}, which denies.
     */
    public static ClickKind ofAction(String actionName) {
        if (actionName == null) {
            return UNKNOWN;
        }
        if (actionName.equals("NOTHING")) {
            return NOTHING;
        }
        if (actionName.startsWith("PICKUP_")) {
            return TAKE;
        }
        if (actionName.startsWith("PLACE_")) {
            return PLACE;
        }
        if (actionName.equals("SWAP_WITH_CURSOR")) {
            return SWAP;
        }
        if (actionName.startsWith("DROP_")) {
            return DROP;
        }
        if (actionName.equals("CLONE_STACK")) {
            return CLONE;
        }
        if (actionName.startsWith("HOTBAR_")) {
            return HOTBAR;
        }
        if (actionName.equals("MOVE_TO_OTHER_INVENTORY")) {
            return MOVE_TO_OTHER_INVENTORY;
        }
        if (actionName.equals("COLLECT_TO_CURSOR")) {
            return COLLECT_TO_CURSOR;
        }
        return UNKNOWN;
    }

}
