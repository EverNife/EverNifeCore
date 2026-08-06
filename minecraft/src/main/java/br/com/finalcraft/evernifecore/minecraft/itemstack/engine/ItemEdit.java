package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * One intention a recipe remembers instead of performing.
 *
 * <p>Nothing here touches an item. An edit knows what it wants, what a runtime must offer for it to
 * be possible, and when in the order it belongs - and the engine decides, once, whether it runs.</p>
 */
public abstract class ItemEdit {

    /** A part edit: the key names the concept, and the part behind it does the work. */
    @Nonnull
    public static ItemEdit ofPart(@Nonnull String key, @Nonnull Object value) {
        return new PartEdit(key, value);
    }

    /** An edit written in Bukkit metadata terms, for the shapes no item-data key covers. */
    @Nonnull
    public static ItemEdit ofMeta(@Nonnull String name, @Nonnull ItemRequirement requirement,
                                  int priority, @Nonnull Consumer<ItemMeta> editor) {
        return new MetaEdit(name, requirement, priority, editor);
    }

    /** A free write into the item's tag. Runs last, with every other tag write, in one round trip. */
    @Nonnull
    public static ItemEdit ofNbt(@Nonnull String name, @Nonnull Consumer<ReadWriteNBT> editor) {
        return new NbtEdit(name, editor);
    }

    /** The general shape, for the few edits that are neither a key nor plain metadata. */
    @Nonnull
    public static ItemEdit ofStack(@Nonnull String name, @Nonnull ItemRequirement requirement,
                                   int priority, @Nonnull UnaryOperator<ItemStack> operation) {
        return new StackEdit(name, requirement, priority, operation);
    }

    /** What a refusal calls this edit: the part key, or the builder call that staged it. */
    @Nonnull
    public abstract String getName();

    /** Why {@code engine} will not run this edit, or {@code null} when it will. */
    @Nullable
    abstract String refusalIn(@Nonnull ItemEngine engine);

    abstract int priorityIn(@Nonnull ItemEngine engine);

    @Nonnull
    abstract ItemStack applyTo(@Nonnull ItemStack item, @Nonnull ItemEngine engine);

    // -----------------------------------------------------------------------------------------------------------------
    //  The three shapes
    // -----------------------------------------------------------------------------------------------------------------

    static final class PartEdit extends ItemEdit {

        private final String key;
        private final Object value;

        PartEdit(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        Object getValue() {
            return value;
        }

        @Nonnull
        @Override
        public String getName() {
            return key;
        }

        @Nullable
        @Override
        String refusalIn(@Nonnull ItemEngine engine) {
            RegisteredPart registered = engine.find(key);
            if (registered == null) {
                return "there is no item-data key named '" + key + "'. The keys this server knows are "
                        + engine.getKnownSpellings();
            }
            return registered.isActive() ? null : registered.getRefusal().getReason();
        }

        @Override
        int priorityIn(@Nonnull ItemEngine engine) {
            RegisteredPart registered = engine.find(key);
            return registered == null ? ItemDataPart.PRIORITY_NORMAL : registered.getPriority();
        }

        @Nonnull
        @Override
        @SuppressWarnings("unchecked")
        ItemStack applyTo(@Nonnull ItemStack item, @Nonnull ItemEngine engine) {
            ItemDataPart<Object> part = (ItemDataPart<Object>) engine.find(key).getPart();
            return part.apply(value, item);
        }
    }

    static final class MetaEdit extends ItemEdit {

        private final String name;
        private final ItemRequirement requirement;
        private final int priority;
        private final Consumer<ItemMeta> editor;

        MetaEdit(String name, ItemRequirement requirement, int priority, Consumer<ItemMeta> editor) {
            this.name = name;
            this.requirement = requirement;
            this.priority = priority;
            this.editor = editor;
        }

        @Nonnull
        @Override
        public String getName() {
            return name;
        }

        @Nullable
        @Override
        String refusalIn(@Nonnull ItemEngine engine) {
            return requirement.explain(engine.getRuntime());
        }

        @Override
        int priorityIn(@Nonnull ItemEngine engine) {
            return priority;
        }

        @Nonnull
        @Override
        ItemStack applyTo(@Nonnull ItemStack item, @Nonnull ItemEngine engine) {
            ItemMeta meta = item.getItemMeta();
            editor.accept(meta);
            item.setItemMeta(meta);
            return item;
        }
    }

    static final class StackEdit extends ItemEdit {

        private final String name;
        private final ItemRequirement requirement;
        private final int priority;
        private final UnaryOperator<ItemStack> operation;

        StackEdit(String name, ItemRequirement requirement, int priority,
                  UnaryOperator<ItemStack> operation) {
            this.name = name;
            this.requirement = requirement;
            this.priority = priority;
            this.operation = operation;
        }

        @Nonnull
        @Override
        public String getName() {
            return name;
        }

        @Nullable
        @Override
        String refusalIn(@Nonnull ItemEngine engine) {
            return requirement.explain(engine.getRuntime());
        }

        @Override
        int priorityIn(@Nonnull ItemEngine engine) {
            return priority;
        }

        @Nonnull
        @Override
        ItemStack applyTo(@Nonnull ItemStack item, @Nonnull ItemEngine engine) {
            return operation.apply(item);
        }
    }

    static final class NbtEdit extends ItemEdit {

        private final String name;
        private final Consumer<ReadWriteNBT> editor;

        NbtEdit(String name, Consumer<ReadWriteNBT> editor) {
            this.name = name;
            this.editor = editor;
        }

        /** The two writes as one, so a recipe with many of them still costs a single round trip. */
        @Nonnull
        NbtEdit andThen(@Nonnull Consumer<ReadWriteNBT> next) {
            Consumer<ReadWriteNBT> first = this.editor;
            return new NbtEdit(name, nbt -> {
                first.accept(nbt);
                next.accept(nbt);
            });
        }

        @Nonnull
        @Override
        public String getName() {
            return name;
        }

        @Nullable
        @Override
        String refusalIn(@Nonnull ItemEngine engine) {
            return ItemRequirement.base().with(ItemProbe.NBT).explain(engine.getRuntime());
        }

        @Override
        int priorityIn(@Nonnull ItemEngine engine) {
            return ItemDataPart.PRIORITY_VERY_LATE;
        }

        @Nonnull
        @Override
        ItemStack applyTo(@Nonnull ItemStack item, @Nonnull ItemEngine engine) {
            NbtDoor.custom().modifyBatch(item, editor);
            return item;
        }
    }

}
