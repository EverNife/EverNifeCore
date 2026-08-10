package br.com.finalcraft.evernifecore.minecraft.gui.model;

/**
 * A named area of a gui and the {@link ClickPolicy} that rules clicks inside it.
 *
 * <p>The name is how a click is attributed to an area; the slots are what the window has to be
 * measured against before they are indexes.</p>
 */
public final class Region {

    private final String name;
    private final SlotSet slots;
    private final ClickPolicy policy;

    public Region(String name, SlotSet slots, ClickPolicy policy) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A region needs a name - it is how a click is told which "
                    + "area it landed in.");
        }
        if (slots == null) {
            throw new IllegalArgumentException("Region [" + name + "] needs a SlotSet. Slots.box(2, 2, 5, 8) "
                    + "is the rectangular form, and Slots.of(11, 12, 13) names raw slots.");
        }
        this.name = name;
        this.slots = slots;
        this.policy = policy == null ? ClickPolicy.DENY_ALL : policy;
    }

    public Region(String name, SlotSet slots) {
        this(name, slots, ClickPolicy.DENY_ALL);
    }

    public String getName() {
        return name;
    }

    public SlotSet getSlots() {
        return slots;
    }

    public ClickPolicy getPolicy() {
        return policy;
    }

    @Override
    public String toString() {
        return "Region{" + name + ", slots=" + slots + "}";
    }

}
