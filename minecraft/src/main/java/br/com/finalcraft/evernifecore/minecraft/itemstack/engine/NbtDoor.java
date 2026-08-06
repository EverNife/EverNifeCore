package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import jakarta.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The one way into an item's tag, so the rest of the engine never names the nbt library.
 *
 * <p>It hides the split 1.20.5 introduced: below it an item carries free nbt, from it on the same
 * calls address {@code minecraft:custom_data} only. That is deliberate and it is the whole point -
 * {@code nbt:} means custom data on every version, so a block of lines written on one server means
 * the same thing on another. The typed component side has its own key and its own door.</p>
 *
 * <p>Every write goes through {@link #modifyBatch}, one round trip per materialization: the setter
 * at a time path this replaces re-applied the whole tag on every single set.</p>
 */
public final class NbtDoor {

    private static final NbtDoor LIVE = new NbtDoor(false);
    private static final NbtDoor COMPONENTS = new NbtDoor(true);

    /** Item nbt, which is custom data from 1.20.5 on. */
    @Nonnull
    public static NbtDoor custom() {
        return LIVE;
    }

    /** The typed component side, which only exists from 1.20.5 on. */
    @Nonnull
    public static NbtDoor components() {
        return COMPONENTS;
    }

    private final boolean componentSide;

    private NbtDoor(boolean componentSide) {
        this.componentSide = componentSide;
    }

    public <T> T read(@Nonnull ItemStack item, @Nonnull Function<ReadableNBT, T> reader) {
        //the casts pick between the Function and Consumer overloads, which a bare lambda cannot
        if (componentSide) {
            return NBT.getComponents(item, (Function<ReadableNBT, T>) nbt -> reader.apply(nbt));
        }
        return NBT.get(item, (Function<ReadableItemNBT, T>) nbt -> reader.apply(nbt));
    }

    /** Every staged change to {@code item}, applied in a single round trip. */
    public void modifyBatch(@Nonnull ItemStack item, @Nonnull Consumer<ReadWriteNBT> editor) {
        if (componentSide) {
            NBT.modifyComponents(item, (Consumer<ReadWriteNBT>) nbt -> editor.accept(nbt));
        } else {
            NBT.modify(item, (Consumer<ReadWriteItemNBT>) nbt -> editor.accept(nbt));
        }
    }

    /** A detached copy of the item's tag, which is what a value read out of an item is made of. */
    @Nonnull
    public ReadWriteNBT snapshot(@Nonnull ItemStack item) {
        return read(item, nbt -> NBT.parseNBT(nbt.toString()));
    }

    /** Text into a tag, the leaf form the {@code nbt:} and {@code components:} lines carry. */
    @Nonnull
    public static ReadWriteNBT parse(@Nonnull String snbt) {
        return NBT.parseNBT(snbt);
    }

}
