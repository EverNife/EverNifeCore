package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

/**
 * The canonical key of every part the engine ships with - the one spelling a read emits.
 *
 * <p>Aliases exist so a file written years ago still loads, but they are absorbed on parse and
 * never written back. Code that has to name a key names it from here, so a rename is one edit.</p>
 */
public final class StandardParts {

    public static final String TYPE = "type";
    public static final String AMOUNT = "amount";
    public static final String DURABILITY = "durability";
    public static final String NAME = "name";
    public static final String LORE = "lore";
    public static final String HIDE_FLAGS = "hideflags";
    public static final String CUSTOM_MODEL_DATA = "CustomModelData";
    public static final String ENCHANT = "enchant";
    public static final String NBT = "nbt";
    public static final String COMPONENTS = "components";

    private StandardParts() {

    }

}
