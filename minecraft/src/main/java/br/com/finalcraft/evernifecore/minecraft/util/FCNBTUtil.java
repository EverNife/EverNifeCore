package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.runtime.NbtDoor;
import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The one place a plugin reads and writes nbt: items, entities, tile entities, the data a plugin keeps on a
 * block or a chunk, files and SNBT text.
 *
 * <p>Every read and write on a live object is scoped: {@code read*} hands the tag to a function and returns what
 * it answered, {@code modify*} hands it to an editor and applies every change in one round trip. A tag never
 * leaves its scope - returning it, or a piece of it, from a {@code read*} function throws - so nothing holds a
 * stale view of an object that changed meanwhile.</p>
 *
 * <p>Items are the exception that returns a stack: see {@link #modifyItem}.</p>
 */
public final class FCNBTUtil {

    private FCNBTUtil() {
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Text
    // -----------------------------------------------------------------------------------------------------------------

    /** A new tag with nothing in it, to build and then write somewhere or print as SNBT. */
    @Nonnull
    public static ReadWriteNBT createEmpty() {
        return NBT.createNBTObject();
    }

    /**
     * SNBT text into a tag - the format {@code toString()} of any tag prints, braces included:
     * {@code {quest_id:"find_the_key",stage:2}}.
     *
     * @throws de.tr7zw.changeme.nbtapi.NbtApiException when the text is not SNBT
     */
    @Nonnull
    public static ReadWriteNBT parse(@Nonnull String snbt) {
        return NBT.parseNBT(snbt);
    }

    /** Whether {@code nbt} is absent or has no keys. */
    public static boolean isEmpty(@Nullable ReadableNBT nbt) {
        return nbt == null || nbt.getKeys().isEmpty();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Items
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The item's tag as it is now, to read several values without a scope each. It is a read-only copy: a later
     * change to the item does not show in it.
     */
    @Nonnull
    public static ReadableNBT readItem(@Nonnull ItemStack item) {
        return NbtDoor.custom().view(item);
    }

    /** What {@code reader} answers from the item's tag. From 1.20.5 on the tag is the item's custom data. */
    public static <T> T readItem(@Nonnull ItemStack item, @Nonnull Function<ReadableNBT, T> reader) {
        return NbtDoor.custom().read(item, reader);
    }

    /**
     * Applies every change {@code editor} makes to the item's tag, in one round trip.
     *
     * <p>Continue with the returned stack, never with {@code item}. A stack the server owns - one taken from an
     * inventory - is edited in place and comes back as itself, so the change is already on the player's item.
     * A plain {@code new ItemStack(...)} is copied into a server-owned stack that carries the change, and is left
     * as it was: below 1.20.5 a plain stack can only carry a tag through its metadata, which on 1.7.10 drops every
     * key it does not know.</p>
     *
     * @return the stack that carries the change
     */
    @Nonnull
    public static ItemStack modifyItem(@Nonnull ItemStack item, @Nonnull Consumer<ReadWriteNBT> editor) {
        return NbtDoor.custom().modifyBatch(item, editor);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Entities, players included
    // -----------------------------------------------------------------------------------------------------------------

    /** What {@code reader} answers from the entity's whole tag, the one the server saves it with. */
    public static <T> T readEntity(@Nonnull Entity entity, @Nonnull Function<ReadableNBT, T> reader) {
        return NBT.get(entity, reader);
    }

    /** Writes the entity's whole tag back with every change {@code editor} made, which the entity then loads. */
    public static void modifyEntity(@Nonnull Entity entity, @Nonnull Consumer<ReadWriteNBT> editor) {
        NBT.modify(entity, editor);
    }

    /** What {@code reader} answers from the data plugins keep on the entity. Needs 1.14 or newer. */
    public static <T> T readEntityData(@Nonnull Entity entity, @Nonnull Function<ReadableNBT, T> reader) {
        return NBT.getPersistentData(entity, reader);
    }

    /** Edits the data plugins keep on the entity, saved with it. Needs 1.14 or newer. */
    public static void modifyEntityData(@Nonnull Entity entity, @Nonnull Consumer<ReadWriteNBT> editor) {
        NBT.modifyPersistentData(entity, editor);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Tile entities (chests, signs, spawners...)
    // -----------------------------------------------------------------------------------------------------------------

    /** What {@code reader} answers from the tile entity's tag. */
    public static <T> T readTileEntity(@Nonnull BlockState tile, @Nonnull Function<ReadableNBT, T> reader) {
        return NBT.get(tile, reader);
    }

    /** Writes the tile entity's tag back with every change {@code editor} made. */
    public static void modifyTileEntity(@Nonnull BlockState tile, @Nonnull Consumer<ReadWriteNBT> editor) {
        NBT.modify(tile, editor);
    }

    /** What {@code reader} answers from the data plugins keep on the tile entity. Needs 1.14 or newer. */
    public static <T> T readTileEntityData(@Nonnull BlockState tile, @Nonnull Function<ReadableNBT, T> reader) {
        return NBT.getPersistentData(tile, reader);
    }

    /** Edits the data plugins keep on the tile entity, saved with it. Needs 1.14 or newer. */
    public static void modifyTileEntityData(@Nonnull BlockState tile, @Nonnull Consumer<ReadWriteNBT> editor) {
        NBT.modifyPersistentData(tile, editor);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Plugin data on any block and on chunks, kept in the chunk's persistent data (1.16.4 or newer)
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * What {@code reader} answers from the data kept for this block position, any block, tile entity or not.
     * An empty tag when nothing was kept. Needs 1.16.4 or newer.
     */
    public static <T> T readBlockData(@Nonnull Block block, @Nonnull Function<ReadableNBT, T> reader) {
        return NBT.readAndGetChunkPDC(block, reader);
    }

    /** Edits the data kept for this block position; a position left empty is removed. Needs 1.16.4 or newer. */
    public static void modifyBlockData(@Nonnull Block block, @Nonnull Consumer<ReadWriteNBT> editor) {
        NBT.modifyChunkPDC(block, editor);
    }

    /** What {@code reader} answers from the data plugins keep on the chunk. Needs 1.16.4 or newer. */
    public static <T> T readChunkData(@Nonnull Chunk chunk, @Nonnull Function<ReadableNBT, T> reader) {
        return NBT.readAndGetChunkPDC(chunk, reader);
    }

    /** Edits the data plugins keep on the chunk, saved with it. Needs 1.16.4 or newer. */
    public static void modifyChunkData(@Nonnull Chunk chunk, @Nonnull Consumer<ReadWriteNBT> editor) {
        NBT.modifyChunkPDC(chunk, editor);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Files
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * A gzipped nbt file - a player's {@code .dat}, {@code level.dat} - as a tag; a missing file reads as empty.
     *
     * @throws UncheckedIOException when the file exists and cannot be read
     */
    @Nonnull
    public static ReadWriteNBT readFile(@Nonnull File file) {
        try {
            return NBT.readFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the nbt file " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Writes {@code nbt} to {@code file}, gzipped, replacing what was there.
     *
     * @throws UncheckedIOException when the file cannot be written
     */
    public static void writeFile(@Nonnull File file, @Nonnull ReadWriteNBT nbt) {
        try {
            NBT.writeFile(file, nbt);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the nbt file " + file.getAbsolutePath(), e);
        }
    }
}
