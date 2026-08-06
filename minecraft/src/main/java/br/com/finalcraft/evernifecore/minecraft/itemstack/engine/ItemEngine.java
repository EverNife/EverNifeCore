package br.com.finalcraft.evernifecore.minecraft.itemstack.engine;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.ItemDataPart;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartAmount;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartComponents;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartCustomModelData;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartDurability;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartEnchantment;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartItemflags;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartLore;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartMaterial;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartNBT;
import br.com.finalcraft.evernifecore.minecraft.itemdatapart.datapart.ItemDataPartName;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The one place that knows how items and item-data text turn into each other.
 *
 * <p>An engine is bound to an {@link ItemRuntime}, negotiated once when it is built: every part is
 * either active here or refused here, with a reason, and nothing re-decides that later. Reading,
 * transforming and materializing are all derived from the same parts, written once - which is why
 * the key of a concept, and the shape of its value, cannot drift between the two directions.</p>
 *
 * <p>Resolution is lazy on purpose. Third-party plugins load classes of this library before the
 * server has finished starting, so paying the probe in a static initializer is how the old system
 * came to be undefined on a bare JVM. The first caller pays it; a test installs one instead.</p>
 */
public final class ItemEngine {

    private static volatile ItemEngine current;

    /** The engine of this JVM, probing the machine on first use. */
    @Nonnull
    public static ItemEngine get() {
        ItemEngine engine = current;
        if (engine == null) {
            synchronized (ItemEngine.class) {
                if (current == null) {
                    current = bootstrap(ItemRuntime.probe());
                }
                engine = current;
            }
        }
        return engine;
    }

    /**
     * Installs an engine over a runtime of the caller's choosing, replacing whatever is there.
     *
     * <p>This is the door a test rig owns: it is how a suite stands on a 1.12.2 server it is not
     * running, or on a JVM with no server at all, without a single reflection hack in the item
     * path. Call {@link #uninstall()} to hand the JVM back.</p>
     */
    @Nonnull
    public static ItemEngine install(@Nonnull ItemRuntime runtime) {
        synchronized (ItemEngine.class) {
            current = bootstrap(runtime);
            return current;
        }
    }

    /** Drops the installed engine, so the next {@link #get()} probes the real machine again. */
    public static void uninstall() {
        synchronized (ItemEngine.class) {
            current = null;
        }
    }

    /** An engine carrying the parts this library ships with, over {@code runtime}. */
    @Nonnull
    public static ItemEngine bootstrap(@Nonnull ItemRuntime runtime) {
        ItemEngine engine = new ItemEngine(runtime);

        engine.register(PartRegistration.of(StandardParts.TYPE, ItemRequirement.base(),
                new String[]{"id", "material", "identifier"}, ItemDataPartMaterial::new));
        engine.register(PartRegistration.of(StandardParts.DURABILITY,
                ItemRequirement.base().with(ItemProbe.ITEM_META),
                new String[]{"damage", "subid"}, ItemDataPartDurability::new));
        engine.register(PartRegistration.of(StandardParts.AMOUNT, ItemRequirement.base(),
                new String[]{"number"}, ItemDataPartAmount::new));
        engine.register(PartRegistration.of(StandardParts.CUSTOM_MODEL_DATA,
                ItemRequirement.atLeast(MCDetailedVersion.v1_14_R1).with(ItemProbe.ITEM_META),
                new String[]{}, ItemDataPartCustomModelData::new)
                .orWrite("On older servers, write it through the 'nbt:' hatch: nbt:{CustomModelData:1042}"));
        engine.register(PartRegistration.of(StandardParts.HIDE_FLAGS,
                ItemRequirement.atLeast(MCDetailedVersion.v1_8_R1).with(ItemProbe.ITEM_META),
                new String[]{"itemflag", "hideflag", "flag", "itemflags", "flags"},
                ItemDataPartItemflags::new));
        engine.register(PartRegistration.of(StandardParts.NAME,
                ItemRequirement.base().with(ItemProbe.ITEM_META),
                new String[]{"text", "title"}, ItemDataPartName::new));
        engine.register(PartRegistration.of(StandardParts.LORE,
                ItemRequirement.base().with(ItemProbe.ITEM_META),
                new String[]{"description"}, ItemDataPartLore::new));
        engine.register(PartRegistration.of(StandardParts.NBT,
                ItemRequirement.base().with(ItemProbe.NBT, ItemProbe.SNBT_IO),
                new String[]{"rawnbt"}, ItemDataPartNBT::new));
        engine.register(PartRegistration.of(StandardParts.COMPONENTS,
                ItemRequirement.base().with(ItemProbe.COMPONENTS),
                new String[]{}, ItemDataPartComponents::new)
                .orWrite("Components arrived in 1.20.5; below it the same data lives under 'nbt:'."));
        engine.register(PartRegistration.of(StandardParts.ENCHANT,
                ItemRequirement.atLeast(MCDetailedVersion.v1_13_R1).with(ItemProbe.ENCHANT_REGISTRY),
                new String[]{}, ItemDataPartEnchantment::new)
                .orWrite("On older servers, write it through the 'nbt:' hatch:"
                        + " nbt:{ench:[{id:16,lvl:5}]}"));

        return engine;
    }

    private final ItemRuntime runtime;
    private final List<RegisteredPart> parts = new ArrayList<>();
    private boolean refusalsReported = false;

    private ItemEngine(@Nonnull ItemRuntime runtime) {
        this.runtime = runtime;
    }

    @Nonnull
    public ItemRuntime getRuntime() {
        return runtime;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The registry - it keeps what it refused, and why
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Adds a part to this engine, negotiating its requirement against the runtime once, here.
     *
     * @throws IllegalArgumentException when a spelling is already taken or is a reserved shape
     */
    public void register(@Nonnull PartRegistration registration) {
        for (String spelling : registration.getSpellings()) {
            if (spelling.isEmpty()) {
                throw new IllegalArgumentException("An item-data key cannot be empty. Give the part a key "
                        + "like 'name' or 'lore' - it is what a line writes before the ':'.");
            }
            if (spelling.charAt(0) == '!') {
                throw new IllegalArgumentException("The key '" + spelling + "' starts with '!', which is "
                        + "reserved to mean 'unset this' in a future release. Drop the '!' from the key.");
            }
            if (spelling.indexOf(':') >= 0) {
                throw new IllegalArgumentException("The key '" + spelling + "' contains ':', which is what "
                        + "separates a key from its value. Drop the ':' from the key.");
            }
            RegisteredPart taken = find(spelling);
            if (taken != null) {
                throw new IllegalArgumentException("The key '" + spelling + "' is already answered by the "
                        + "part '" + taken.getKey() + "'. Pick another spelling for '"
                        + registration.getKey() + "'.");
            }
        }
        parts.add(new RegisteredPart(registration, runtime));
    }

    /** The part a spelling reaches - active or refused - or {@code null} when nothing answers to it. */
    @Nullable
    public RegisteredPart find(@Nullable String spelling) {
        for (RegisteredPart part : parts) {
            if (part.answersTo(spelling)) {
                return part;
            }
        }
        return null;
    }

    /** The part the key of {@code line} reaches, or {@code null} when the line names nothing known. */
    @Nullable
    public RegisteredPart findByLine(@Nullable String line) {
        return line == null ? null : find(keyOf(line));
    }

    /** Every part, in the order a read emits them. */
    @Nonnull
    public List<RegisteredPart> getParts() {
        return Collections.unmodifiableList(parts);
    }

    /** Every spelling a line may use, for the message that follows an unknown one. */
    @Nonnull
    public String getKnownSpellings() {
        List<String> spellings = new ArrayList<>();
        for (RegisteredPart part : parts) {
            spellings.add(part.getKey());
        }
        return String.join(", ", spellings);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Reading: item -> text
    // -----------------------------------------------------------------------------------------------------------------

    /** What {@code item} says about itself here, next to whatever went unanswered. */
    @Nonnull
    @SuppressWarnings("unchecked")
    public ItemDescription read(@Nonnull ItemStack item) {
        List<String> lines = new ArrayList<>();
        List<PartRefusal> refusals = new ArrayList<>();
        List<PartFailure> failures = new ArrayList<>();

        for (RegisteredPart registered : parts) {
            if (!registered.isActive()) {
                refusals.add(registered.getRefusal());
                continue;
            }
            try {
                ItemDataPart<Object> part = (ItemDataPart<Object>) registered.getPart();
                Object value = part.extract(item);
                if (value != null) {
                    for (String argument : part.format(value)) {
                        lines.add(registered.getKey() + ":" + argument);
                    }
                }
            } catch (Exception | LinkageError defect) {
                //a bug in the part, not a gap in the runtime: the difference the old blanket catch erased
                PartFailure failure = new PartFailure(registered.getKey(), defect);
                failures.add(failure);
                warn(failure.describe());
            }
        }

        reportRefusalsOnce(refusals);
        return new ItemDescription(lines, refusals, failures);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Writing: text -> item
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Turns a block of item-data lines into the edits it asks for, reporting the lines it could not
     * read instead of throwing on the first one.
     *
     * <p>Reading happens here and not at build time on purpose: a mistake in a file is worth
     * knowing about where the file was opened, not three calls later.</p>
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public ParsedBlock parse(@Nonnull List<String> lines) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<ItemEdit> edits = new ArrayList<>();
        List<ItemLineProblem> problems = new ArrayList<>();

        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String key = keyOf(line);
            String argument = argumentOf(line);

            RegisteredPart registered = find(key);
            if (registered == null) {
                problems.add(new ItemLineProblem(line, "'" + key + "' is not an item-data key. "
                        + "The keys this server knows are " + getKnownSpellings() + "."));
                continue;
            }
            if (!registered.isActive()) {
                //kept as an edit so materialize answers with the capability, not with silence
                edits.add(ItemEdit.ofPart(registered.getKey(), argument));
                continue;
            }
            try {
                ItemDataPart<Object> part = (ItemDataPart<Object>) registered.getPart();
                Object parsed = part.parse(argument);
                Object previous = values.get(registered.getKey());
                values.put(registered.getKey(), previous == null ? parsed : part.merge(previous, parsed));
            } catch (ItemLineException badValue) {
                problems.add(new ItemLineProblem(line, badValue.getMessage()));
            }
        }

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            edits.add(ItemEdit.ofPart(entry.getKey(), entry.getValue()));
        }
        for (ItemLineProblem problem : problems) {
            warn("Item data " + problem);
        }
        return new ParsedBlock(edits, problems);
    }

    /** Applies a block of lines onto {@code base}, which is what a config value turns into. */
    @Nonnull
    public ItemStack transform(@Nonnull ItemStack base, @Nonnull List<String> lines) {
        ParsedBlock block = parse(lines);
        return materialize(ItemBase.of(base), block.getEdits(), block.getProblems()).getItemStack();
    }

    /**
     * Runs a recipe, in the one order that keeps an item whole.
     *
     * <p>Priority is the whole order: the type change leads, so everything after it lands on the new
     * item instead of being copied field by field onto it, and every tag write trails, so a metadata
     * write can never land on top of one. The old builder had to reconcile three representations of
     * the same item because those two rules were nowhere.</p>
     */
    @Nonnull
    public BuiltItem materialize(@Nonnull ItemBase base, @Nonnull List<ItemEdit> edits) {
        return materialize(base, edits, Collections.<ItemLineProblem>emptyList());
    }

    @Nonnull
    public BuiltItem materialize(@Nonnull ItemBase base, @Nonnull List<ItemEdit> edits,
                                 @Nonnull List<ItemLineProblem> problems) {
        List<RefusedEdit> refused = new ArrayList<>();
        List<ItemEdit> runnable = new ArrayList<>();

        for (ItemEdit edit : edits) {
            String refusal = edit.refusalIn(this);
            if (refusal == null) {
                runnable.add(edit);
            } else {
                refused.add(new RefusedEdit(edit.getName(), refusal));
            }
        }

        Collections.sort(runnable, new Comparator<ItemEdit>() {
            @Override
            public int compare(ItemEdit left, ItemEdit right) {
                return Integer.compare(left.priorityIn(ItemEngine.this), right.priorityIn(ItemEngine.this));
            }
        });

        List<ItemLineProblem> allProblems = new ArrayList<>(problems);
        ItemStack item = base.resolve();
        for (ItemEdit edit : runnable) {
            try {
                item = edit.applyTo(item, this);
            } catch (ItemLineException badValue) {
                //the value cannot be what it says it is here; the rest of the recipe still stands
                allProblems.add(new ItemLineProblem(edit.getName(), badValue.getMessage()));
            }
        }

        reportRefusedOnce(refused);
        return new BuiltItem(item, refused, allProblems);
    }

    /**
     * Puts what {@code key} should end up as into a recipe.
     *
     * @param merge whether this joins what the key already asks for, or replaces it - the difference
     *              between {@code addItemFlags}, which piles up, and {@code lore}, which is a setter
     */
    @SuppressWarnings("unchecked")
    public void stage(@Nonnull List<ItemEdit> edits, @Nonnull String key, @Nonnull Object value,
                      boolean merge) {
        for (int index = 0; index < edits.size(); index++) {
            ItemEdit staged = edits.get(index);
            if (!(staged instanceof ItemEdit.PartEdit) || !staged.getName().equals(key)) {
                continue;
            }
            Object previous = ((ItemEdit.PartEdit) staged).getValue();
            RegisteredPart registered = find(key);
            Object combined = merge && registered != null && registered.isActive()
                    ? ((ItemDataPart<Object>) registered.getPart()).merge(previous, value)
                    : value;
            edits.set(index, ItemEdit.ofPart(key, combined));
            return;
        }
        edits.add(ItemEdit.ofPart(key, value));
    }

    /** Adds a tag write to a recipe, joining the one already there so the batch stays single. */
    public void stageNbt(@Nonnull List<ItemEdit> edits, @Nonnull Consumer<ReadWriteNBT> editor) {
        for (int index = 0; index < edits.size(); index++) {
            ItemEdit staged = edits.get(index);
            if (staged instanceof ItemEdit.NbtEdit) {
                edits.set(index, ((ItemEdit.NbtEdit) staged).andThen(editor));
                return;
            }
        }
        edits.add(ItemEdit.ofNbt("nbt edit", editor));
    }

    /** What {@code key} is already set to in a recipe, or {@code null} when it says nothing about it. */
    @Nullable
    public Object staged(@Nonnull List<ItemEdit> edits, @Nonnull String key) {
        for (ItemEdit staged : edits) {
            if (staged instanceof ItemEdit.PartEdit && staged.getName().equals(key)) {
                return ((ItemEdit.PartEdit) staged).getValue();
            }
        }
        return null;
    }

    /** One concept off an item, or {@code null} when the item has none or this runtime cannot ask. */
    @Nullable
    public Object extract(@Nonnull ItemStack item, @Nonnull String key) {
        RegisteredPart registered = find(key);
        return registered == null || !registered.isActive() ? null : registered.getPart().extract(item);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Comparing
    // -----------------------------------------------------------------------------------------------------------------

    public boolean isSimilar(@Nullable ItemStack base, @Nullable ItemStack other) {
        return isSimilar(base, other, Collections.<String>emptyList(), true, false);
    }

    /**
     * Whether two items say the same thing, part by part, each part answering with the very value
     * it reads and writes.
     *
     * @param exceptKeys     keys to skip entirely
     * @param compareAmount  whether a different stack size makes them different
     * @param considerNbt    whether the tag takes part, which costs a full read of both items
     */
    public boolean isSimilar(@Nullable ItemStack base, @Nullable ItemStack other,
                             @Nonnull Collection<String> exceptKeys, boolean compareAmount,
                             boolean considerNbt) {
        if (base == null || other == null) {
            return false;
        }
        for (RegisteredPart registered : parts) {
            if (!registered.isActive() || exceptKeys.contains(registered.getKey())) {
                continue;
            }
            if (!compareAmount && StandardParts.AMOUNT.equals(registered.getKey())) {
                continue;
            }
            boolean tagPart = StandardParts.NBT.equals(registered.getKey())
                    || StandardParts.COMPONENTS.equals(registered.getKey());
            if (tagPart && !considerNbt) {
                continue;
            }
            if (!registered.getPart().matches(base, other)) {
                return false;
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Line shape
    // -----------------------------------------------------------------------------------------------------------------

    /** The key side of {@code key:value}, trimmed. */
    @Nonnull
    public static String keyOf(@Nonnull String line) {
        int separator = line.indexOf(':');
        return (separator < 0 ? line : line.substring(0, separator)).trim();
    }

    /** The value side of {@code key:value}, trimmed - never null, so a bare key means an empty value. */
    @Nonnull
    public static String argumentOf(@Nonnull String line) {
        int separator = line.indexOf(':');
        return separator < 0 ? "" : line.substring(separator + 1).trim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Saying it once
    // -----------------------------------------------------------------------------------------------------------------

    private void reportRefusalsOnce(List<PartRefusal> refusals) {
        if (refusalsReported || refusals.isEmpty()) {
            return;
        }
        refusalsReported = true;
        List<String> named = new ArrayList<>();
        for (PartRefusal refusal : refusals) {
            named.add(refusal.toString());
        }
        warn("This runtime (" + runtime.describe() + ") cannot work with these item-data keys: "
                + String.join("; ", named) + ". Lines naming them are left out instead of being guessed at.");
    }

    private void reportRefusedOnce(List<RefusedEdit> refused) {
        if (refusalsReported || refused.isEmpty()) {
            return;
        }
        refusalsReported = true;
        warn("This runtime (" + runtime.describe() + ") could not apply " + refused
                + ". The item was built without them.");
    }

    /** The log exists only after the plugin boots, and item work happens before that too. */
    private static void warn(String message) {
        try {
            EverNifeCore.getLog().warning(message);
        } catch (Exception notBootedYet) {
            System.out.println("[EverNifeCore] " + message);
        }
    }

}
