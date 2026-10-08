package br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NbtApiException;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableItemNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import de.tr7zw.changeme.nbtapi.utils.MinecraftVersion;
import de.tr7zw.changeme.nbtapi.utils.nmsmappings.ClassWrapper;
import de.tr7zw.changeme.nbtapi.utils.nmsmappings.ReflectionMethod;
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
 * <p>Every write goes through {@link #modifyBatch}, one round trip per materialization, and hands back
 * the stack that carries it: the write always lands in a server-backed item, so a plain Bukkit stack
 * is never asked to carry a tag through its metadata.</p>
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

    /**
     * Every staged change to {@code item}, applied in a single round trip.
     *
     * @return the stack that carries the change - the caller continues with it, never with {@code item}.
     *         A server-backed item is edited in place and comes back as itself; any other is first copied
     *         into a server-backed one and left as it was.
     */
    @Nonnull
    public ItemStack modifyBatch(@Nonnull ItemStack item, @Nonnull Consumer<ReadWriteNBT> editor) {
        if (componentSide) {
            if (!MinecraftVersion.isAtLeastVersion(MinecraftVersion.MC1_20_R4)) {
                throw new NbtApiException("Item components only exist from 1.20.5 on.");
            }
            ReadWriteNBT whole = NBT.itemStackToNBT(item);
            editor.accept(whole.getOrCreateCompound("components"));
            return NBT.itemStackFromNBT(whole);
        }
        ItemStack backed = serverBacked(item);
        NBT.modify(backed, (Consumer<ReadWriteItemNBT>) nbt -> editor.accept(nbt));
        return backed;
    }

    /**
     * {@code item} as a stack whose tag lives in the server's own item, where a write lands whole.
     *
     * <p>Written onto a plain Bukkit stack, a tag can only travel through its {@code ItemMeta}, and the
     * 1.7.10 metadata keeps only the keys it knows - every custom one is dropped on the way.</p>
     */
    @Nonnull
    private static ItemStack serverBacked(@Nonnull ItemStack item) {
        if (ClassWrapper.CRAFT_ITEMSTACK.getClazz().isInstance(item)) {
            return item;
        }
        Object handle = ReflectionMethod.ITEMSTACK_NMSCOPY.run(null, item);
        return (ItemStack) ReflectionMethod.ITEMSTACK_BUKKITMIRROR.run(null, handle);
    }

    /** A detached copy of the item's tag, which is what a value read out of an item is made of. */
    @Nonnull
    public ReadWriteNBT snapshot(@Nonnull ItemStack item) {
        //only a String may leave the scoped read: the library throws on any ReadableNBT returned
        //from it, even a detached one. The tag is rebuilt from the text on the outside.
        return NBT.parseNBT(read(item, ReadableNBT::toString));
    }

    /** Text into a tag, the leaf form the {@code nbt:} and {@code components:} lines carry. */
    @Nonnull
    public static ReadWriteNBT parse(@Nonnull String snbt) {
        return NBT.parseNBT(snbt);
    }

}
