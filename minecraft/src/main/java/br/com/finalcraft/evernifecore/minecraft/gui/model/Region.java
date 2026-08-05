package br.com.finalcraft.evernifecore.minecraft.gui.model;

/**
 * A named area of a gui, with the layer its writes land on and the {@link ClickPolicy} that rules
 * clicks inside it.
 *
 * <p>Layer is depth, not order of application: a background written on layer {@link #LAYER_BACKGROUND}
 * stays underneath whatever content writes on top of it, whichever was declared first, and clearing
 * the content uncovers the background instead of blanking the slot.</p>
 */
public final class Region {

    /** Where a background writes. Anything painted over it uses a higher layer. */
    public static final int LAYER_BACKGROUND = 0;
    /** Where an ordinary icon writes. */
    public static final int LAYER_CONTENT = 100;

    private final String name;
    private final SlotSet slots;
    private final int layer;
    private final ClickPolicy policy;

    public Region(String name, SlotSet slots, int layer, ClickPolicy policy) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A region needs a name - it is how the layout, the click "
                    + "router and the logs refer to it.");
        }
        if (slots == null) {
            throw new IllegalArgumentException("Region [" + name + "] needs a SlotSet.");
        }
        this.name = name;
        this.slots = slots;
        this.layer = layer;
        this.policy = policy == null ? ClickPolicy.DENY_ALL : policy;
    }

    public Region(String name, SlotSet slots) {
        this(name, slots, LAYER_CONTENT, ClickPolicy.DENY_ALL);
    }

    public String getName() {
        return name;
    }

    public SlotSet getSlots() {
        return slots;
    }

    public int getLayer() {
        return layer;
    }

    public ClickPolicy getPolicy() {
        return policy;
    }

    /** This region with its slots measured against a real window. */
    public Region resolve(GuiGeometry geometry) {
        SlotSet resolved = slots.resolve(geometry);
        return resolved == slots ? this : new Region(name, resolved, layer, policy);
    }

    @Override
    public String toString() {
        return "Region{" + name + ", layer=" + layer + ", slots=" + slots + "}";
    }

}
