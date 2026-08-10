package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.cfg.SettingsScanner;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.ItemEngine;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.RegisteredPart;
import br.com.finalcraft.evernifecore.minecraft.itemstack.engine.StandardParts;
import br.com.finalcraft.evernifecore.minecraft.util.FCMaterialUtil;
import br.com.finalcraft.everyconfig.config.Config;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a layout class out of its yml, seeding whatever the file does not yet say.
 *
 * <p>The Java class is the DEFAULT and the file is the truth: every {@link IconData} field is written
 * to the file the first time it is seen and read back from it forever after, so the admin moves,
 * restyles, permissions or switches off any icon without the plugin knowing.</p>
 *
 * <p>Nothing here aborts a boot. A key the file got wrong costs the ONE icon it describes, and so does
 * an icon named through two channels at once: the failure is logged naming the field, the file and what
 * to do about it, and the screen opens without that icon. What throws is the class itself being
 * unusable - no {@link GuiLayout} on it, or no constructor the framework can build it with.</p>
 */
public final class LayoutScanner {

    public static final String SETTINGS = "Settings";
    public static final String LAYOUT = "Layout";
    public static final String BACKGROUND = "Background";
    public static final String QUARANTINE = "_Quarantine";

    private static final Pattern PLACEHOLDER = Pattern.compile("%[^%\\s]+%");

    private LayoutScanner() {
    }

    /** The yml a layout class is seeded into, relative to the owning plugin's data folder. */
    @Nonnull
    public static String fileNameOf(@Nonnull Class<?> type) {
        return "guis/" + type.getSimpleName() + ".yml";
    }

    /** The optional per-language overlay of a layout. The framework never creates this file. */
    @Nonnull
    public static String overlayFileNameOf(@Nonnull Class<?> type, @Nonnull String language) {
        return "guis/locale/" + language + "/" + type.getSimpleName() + ".yml";
    }

    /**
     * The overlay of {@code language}, or {@code null} when the admin did not write one. The language is
     * normalized on the way in, so a case-sensitive filesystem answers {@code pt_br} and {@code PT_BR}
     * the same way every other door of the framework does.
     */
    @Nullable
    public static Config openOverlay(@Nonnull ECPluginData plugin, @Nonnull Class<?> type,
                                     @Nullable String language) {
        if (language == null) {
            return null;
        }
        File file = new File(plugin.getMetaInfo().getDataFolder(),
                overlayFileNameOf(type, LocaleType.normalize(language)));
        return file.isFile() ? ConfigFactory.open(plugin, file) : null;
    }

    /**
     * Reads {@code type} for {@code language} - {@code null} being the base file everyone else reads.
     *
     * @throws IllegalArgumentException when the class itself is malformed
     */
    @Nonnull
    public static <T extends LayoutBase> T load(@Nonnull ECPluginData plugin, @Nonnull Class<T> type,
                                                @Nullable String language) {
        GuiLayout declaration = type.getAnnotation(GuiLayout.class);
        if (declaration == null) {
            throw new IllegalArgumentException(type.getName() + " is not a layout: it has no @GuiLayout. "
                    + "Annotate the class with @GuiLayout(title = \"...\", rows = ...) - the annotation is what "
                    + "names the window and the file the icons are seeded into.");
        }

        T instance = newInstance(type);
        Config base = ConfigFactory.open(plugin, fileNameOf(type));
        LayoutSource source = new LayoutSource(base, openOverlay(plugin, type, language));
        //the base file belongs to the copy every viewer shares: a per-language read writes nothing into
        //it, or the header, the seed and the warnings would all be whichever language loaded first
        boolean baseCopy = language == null;

        List<Field> fields = iconFields(type);
        if (baseCopy) {
            warnStaticIcons(plugin, type);
            seedSettings(base, declaration);
        }
        SettingsScanner.load(plugin, base, instance);
        applySettings(instance, plugin, type, language, declaration, source);

        for (Field field : fields) {
            IconData iconData = field.getAnnotation(IconData.class);
            String path = (iconData.background() ? BACKGROUND : LAYOUT) + "." + field.getName();
            try {
                Icon declared = iconOf(instance, field);
                IconLocale locale = localeOf(declared, iconData);
                if (baseCopy) {
                    seedIcon(base, path, declared, iconData, locale);
                }
                Icon resolved = resolveIcon(plugin, type, declaration, iconData, declared, locale, source,
                        path, field.getName());
                SlotSet slots = resolveSlots(instance, field, source, path);
                instance.putIcon(field.getName(), resolved, slots);
            } catch (Throwable failure) {
                plugin.getLog().warning(type.getSimpleName() + "." + field.getName() + ": "
                        + reasonOf(failure) + " Icon disabled. "
                        + plugin.getMetaInfo().getName() + "/" + fileNameOf(type) + ", key " + path);
            }
        }

        loadFileOnlyBackground(plugin, type, instance, source);
        if (baseCopy) {
            reportSlotDisputes(plugin, type, instance, fields);
            quarantineOrphans(plugin, base, fields);
            base.setHeader(header(instance, declaration));
            base.save();
        }
        return instance;
    }

    /** What a failure says, or what it IS when it says nothing - an NPE has no message to report. */
    @Nonnull
    private static String reasonOf(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? failure.toString() : message;
    }

    /**
     * Reports a {@code static} field carrying {@link IconData}: it is never seeded and never drawn,
     * because the file is written against an instance and a static field belongs to none.
     */
    private static void warnStaticIcons(ECPluginData plugin, Class<?> type) {
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(IconData.class) && Modifier.isStatic(field.getModifiers())) {
                    plugin.getLog().warning("The icon " + type.getSimpleName() + "." + field.getName()
                            + " is never seeded and never drawn: a static field is not an icon of the "
                            + "screen, which is read into an instance. Make it an instance field, or drop "
                            + "the @IconData.");
                }
            }
        }
    }

    /**
     * The {@link IconData} fields of the whole hierarchy, base class first and in declaration order -
     * the same order the settings of the same file are seeded in, so one class reads top to bottom.
     *
     * <p>This is the single place the layout package reflects over fields; the level-by-level walk is
     * what makes an inherited icon appear in the child's own file.</p>
     */
    @Nonnull
    public static List<Field> iconFields(@Nonnull Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> clazz = type; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            hierarchy.add(0, clazz);
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> clazz : hierarchy) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(IconData.class) && !Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /** The fields of a layout in the order a contested slot is decided in - see {@link Icon#compareForSlot}. */
    @Nonnull
    static List<Field> byDisputePriority(@Nonnull List<Field> fields) {
        List<Field> ordered = new ArrayList<>(fields);
        Collections.sort(ordered, (left, right) -> Icon.compareForSlot(
                left.getAnnotation(IconData.class).order(), left.getName(),
                right.getAnnotation(IconData.class).order(), right.getName()));
        return ordered;
    }

    /** The one sentence a contested slot is reported with, so the log and the diff cannot name two winners. */
    @Nonnull
    static String slotDisputeMessage(@Nonnull String winner, @Nonnull String loser, int slot) {
        return winner + " and " + loser + " both claim slot " + slot + ". " + winner + " wins.";
    }

    /**
     * How a claim is named in a report: the group when the icon declares one, the key otherwise.
     *
     * <p>A group is named as a whole on purpose - naming one of its members would make the text depend
     * on which member happens to come first, and the operator has to read the intention, not the
     * roster.</p>
     */
    @Nonnull
    static String claimantOf(@Nonnull Field field) {
        String group = groupOf(field);
        return group.isEmpty() ? field.getName() : "group [" + group + "]";
    }

    /** Whether two icons declare the same group, which is how a shared position stops being a dispute. */
    static boolean sharesGroup(@Nonnull Field left, @Nonnull Field right) {
        String group = groupOf(left);
        return !group.isEmpty() && group.equals(groupOf(right));
    }

    @Nonnull
    static String groupOf(@Nonnull Field field) {
        return field.getAnnotation(IconData.class).group().trim();
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Settings
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Both keys that describe the window are written, whichever type it is: an admin who turns a chest
     * into a hopper needs {@code type} to be there to change, and one who turns a hopper into a chest
     * needs {@code rows}. Which of the two the other type ignores is what the header explains.
     */
    private static void seedSettings(Config config, GuiLayout declaration) {
        config.setValueIfAbsent(SETTINGS + ".title", declaration.title());
        config.setValueIfAbsent(SETTINGS + ".type", declaration.type().name());
        config.setValueIfAbsent(SETTINGS + ".rows", declaration.rows());
        config.setValueIfAbsent(SETTINGS + ".integrateToPAPI", declaration.integrateToPAPI());
        for (FCLocale locale : declaration.locale()) {
            config.setValueIfAbsent(SETTINGS + ".Locale." + LocaleType.normalize(locale.lang()) + ".title",
                    locale.text());
        }
    }

    private static void applySettings(LayoutBase instance, ECPluginData plugin, Class<?> type,
                                      String language, GuiLayout declaration, LayoutSource source) {
        GuiType guiType = declaration.type();
        String declaredType = source.getString(SETTINGS + ".type");
        if (declaredType != null) {
            try {
                guiType = parseGuiType(declaredType);
            } catch (RuntimeException unknownType) {
                plugin.getLog().warning(type.getSimpleName() + ": " + unknownType.getMessage()
                        + " Keeping " + guiType.name() + ".");
            }
        }
        String title = source.getString(SETTINGS + ".title");
        instance.applySettings(plugin, type.getSimpleName(), language, guiType,
                rowsOf(plugin, type, guiType, declaration, source),
                source.getBoolean(SETTINGS + ".integrateToPAPI", declaration.integrateToPAPI()),
                title == null ? declaration.title() : title);

        Set<String> languages = new LinkedHashSet<>();
        for (FCLocale locale : declaration.locale()) {
            languages.add(LocaleType.normalize(locale.lang()));
        }
        languages.addAll(source.getKeys(SETTINGS + ".Locale"));
        for (String lang : languages) {
            String localizedTitle = source.getString(SETTINGS + ".Locale." + lang + ".title");
            if (localizedTitle != null) {
                instance.putTitle(lang, localizedTitle);
            }
        }
    }

    /**
     * The row count the window is built with: the file's when it describes a window this type can have,
     * the declared one otherwise.
     *
     * <p>Read and judged here, once, so a number out of range costs one line naming {@code Settings.rows}
     * instead of a warning per icon blaming icons that are fine - and so that it is caught before the
     * screen is built, where the same refusal would reach the plugin as a broken open.</p>
     */
    private static int rowsOf(ECPluginData plugin, Class<?> type, GuiType guiType, GuiLayout declaration,
                              LayoutSource source) {
        try {
            int rows = source.getInt(SETTINGS + ".rows", declaration.rows());
            guiType.sizeOf(rows);
            return rows;
        } catch (RuntimeException unusable) {
            plugin.getLog().warning(type.getSimpleName() + ", " + SETTINGS + ".rows: "
                    + reasonOf(unusable) + " Keeping the " + declaration.rows() + " the plugin declared. "
                    + plugin.getMetaInfo().getName() + "/" + fileNameOf(type));
            return declaration.rows();
        }
    }

    private static GuiType parseGuiType(String declared) {
        String wanted = declared.trim().toUpperCase(Locale.ROOT);
        for (GuiType candidate : GuiType.values()) {
            if (candidate.name().equals(wanted)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("'" + declared + "' is not a window type. "
                + didYouMean(wanted, names(GuiType.values()))
                + "The types are " + GuiType.names() + ".");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Icons
    // -----------------------------------------------------------------------------------------------------------------

    private static Icon iconOf(LayoutBase instance, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object value = field.get(instance);
        if (!(value instanceof Icon)) {
            throw new IllegalArgumentException("the field is annotated @IconData but holds "
                    + (value == null ? "null" : value.getClass().getSimpleName())
                    + ". Give it an Icon - FCItemFactory.from(Material.X)...asIcon().");
        }
        return (Icon) value;
    }

    /**
     * Writes what the file does not say yet, icon by icon - never section by section, so a version that
     * adds one icon adds one key to a file that already exists, background icons included.
     */
    private static void seedIcon(Config config, String path, Icon declared, IconData iconData,
                                 IconLocale locale) {
        config.setValueIfAbsent(path + ".Slot", SlotSet.of(iconData.slot()));

        String permission = iconData.permission().isEmpty() ? declared.getPermission() : iconData.permission();
        if (!permission.isEmpty()) {
            config.setValueIfAbsent(path + ".Permission", permission);
        }

        config.setValueIfAbsent(path + ".DisplayItem", displayItemOf(declared.getItemStack(), locale != null));
        for (String state : declared.getStateNames()) {
            IconLocale stateLocale = declared.getStateLocale(state);
            config.setValueIfAbsent(path + ".States." + state + ".DisplayItem",
                    displayItemOf(declared.getState(state), stateLocale != null));
            seedLocale(config, path + ".States." + state, stateLocale);
        }
        seedLocale(config, path, locale);
    }

    private static void seedLocale(Config config, String path, IconLocale locale) {
        if (locale == null) {
            return;
        }
        for (String lang : locale.getLanguages()) {
            config.setValueIfAbsent(path + ".Locale." + lang, locale.get(lang));
        }
    }

    /** The item-data lines of a stack, without the text ones when the text is a language block's job. */
    private static List<String> displayItemOf(ItemStack stack, boolean textLivesInLocale) {
        List<String> lines = ItemEngine.get().read(stack).getLines();
        if (!textLivesInLocale) {
            return lines;
        }
        List<String> kept = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (!IconLocale.isTextLine(line)) {
                kept.add(line);
            }
        }
        return kept;
    }

    /**
     * The icon's own language table plus whatever the annotation declares, or {@code null} when the icon
     * carries a single text.
     *
     * @throws IllegalArgumentException when the same icon is named through both channels
     */
    @Nullable
    private static IconLocale localeOf(Icon declared, IconData iconData) {
        IconLocale locale = declared.getLocale();
        if (iconData.locale().length > 0) {
            locale = locale == null ? new IconLocale() : locale.copy();
            for (FCLocale entry : iconData.locale()) {
                locale.put(entry.lang(), IconLocale.linesOf(
                        entry.text().isEmpty() ? null : entry.text(), loreOf(entry.hover())));
            }
        }
        if (locale != null && declared.hasBakedText()) {
            throw new IllegalArgumentException("it names the same icon in two places: @FCLocale and "
                    + ".displayName()/.lore(). Pick one - drop the .displayName() to keep it multi-language, "
                    + "or drop the locale = {...} to keep a single text.");
        }
        return locale;
    }

    /** A hover is one string with newlines, which is how the rest of the core writes a multi-line text. */
    private static List<String> loreOf(String hover) {
        if (hover == null || hover.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(hover.split("\n", -1));
    }

    private static Icon resolveIcon(ECPluginData plugin, Class<?> type, GuiLayout declaration,
                                    IconData iconData, Icon declared, IconLocale locale,
                                    LayoutSource source, String path, String name) {
        Icon resolved = declared.copy();
        resolved.setName(name);
        resolved.setBackground(iconData.background());
        resolved.setOrder(iconData.order());
        resolved.setLocaleOwner(plugin);
        resolved.integrateToPAPI(declaration.integrateToPAPI());

        List<String> displayItem = source.getStringList(path + ".DisplayItem");
        if (!displayItem.isEmpty()) {
            resolved.setItemStack(buildItem(displayItem));
        }

        String permission = source.getString(path + ".Permission");
        if (permission != null) {
            resolved.setPermission(permission);
        }

        for (String state : source.getKeys(path + ".States")) {
            String statePath = path + ".States." + state;
            List<String> stateItem = source.getStringList(statePath + ".DisplayItem");
            if (!stateItem.isEmpty()) {
                resolved.addState(state, buildItem(stateItem));
            }
            IconLocale stateLocale = resolveLocale(plugin, source, statePath,
                    declared.getStateLocale(state));
            if (stateLocale != null) {
                resolved.setStateLocale(state, stateLocale);
            }
        }

        resolved.setLocale(resolveLocale(plugin, source, path, locale));
        if (resolved.getStateType() != null) {
            //the yml's own keys, which the declaration-time check could not have seen
            IconStates.warnUnknownKeys(resolved.getStateType(), resolved.getStateNames(),
                    type.getSimpleName() + "." + name);
        }
        return resolved;
    }

    /** {@code declared} with the language blocks the file holds under {@code path} written over it. */
    @Nullable
    private static IconLocale resolveLocale(ECPluginData plugin, LayoutSource source, String path,
                                            IconLocale declared) {
        IconLocale locale = declared;
        for (String lang : source.getKeys(path + ".Locale")) {
            List<String> lines = source.getStringList(path + ".Locale." + lang);
            List<String> text = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (IconLocale.isTextLine(line)) {
                    text.add(line);
                } else {
                    plugin.getLog().warning("Ignoring '" + line + "' under " + path + ".Locale." + lang
                            + ": a language block only carries name: and lore:. Material, nbt and durability "
                            + "belong in DisplayItem, and a whole screen per language is guis/locale/" + lang
                            + "/.");
                }
            }
            if (locale == null) {
                locale = new IconLocale();
            }
            locale.put(lang, text);
        }
        return locale;
    }

    /**
     * Builds the stack of an item-data block, refusing a material this server does not have.
     *
     * <p>The material is checked before the transform runs so the complaint can name the line and offer
     * the nearest known name; a modded identifier ({@code minecraft:diamond_sword}) is left to the
     * server's own registry, which is the only thing that knows it.</p>
     */
    private static ItemStack buildItem(List<String> itemData) {
        for (String line : itemData) {
            String[] parts = line.split(":", 2);
            RegisteredPart part = parts.length == 2 ? ItemEngine.get().find(parts[0].trim()) : null;
            if (part == null || !StandardParts.TYPE.equals(part.getKey())) {
                continue;
            }
            String[] identifier = parts[1].trim().split(":");
            boolean bukkitIdentifier = identifier.length == 1
                    || identifier[1].matches("\\d+"); //anything else is a namespaced id only the server resolves
            if (bukkitIdentifier && FCMaterialUtil.parseMaterial(identifier[0]) == null) {
                throw new IllegalArgumentException("'" + line.trim() + "' is not a material this server has. "
                        + didYouMean(identifier[0], names(Material.values())));
            }
        }
        return FCItemFactory.from(itemData).build();
    }

    private static SlotSet resolveSlots(LayoutBase instance, Field field, LayoutSource source, String path) {
        SlotSet slots = source.getValue(path + ".Slot", SlotSet.class);
        if (slots == null) {
            slots = SlotSet.of(field.getAnnotation(IconData.class).slot());
        }
        int size = instance.getType().sizeOf(instance.getRows());
        for (int slot : slots.toArray()) {
            if (slot >= size) {
                throw new IllegalArgumentException("Slot " + slot + " of '" + slots.serialize()
                        + "' is outside a screen of " + size + " slots (0-" + (size - 1) + ").");
            }
        }
        return slots;
    }

    /**
     * Names BOTH sides of a contested slot: the loser is invisible, and only the log says why.
     *
     * <p>Two arrangements are not disputes. Only icons on the same layer contest a slot - a background
     * under content is the stacking the layers exist for, and every screen with a full backdrop would
     * report it. And icons that declare the same {@link IconData#group()} share the position on
     * purpose; which of them is on screen is the menu's call, not a mistake.</p>
     */
    private static void reportSlotDisputes(ECPluginData plugin, Class<?> type, LayoutBase instance,
                                           List<Field> fields) {
        Map<Integer, Field> ownerOfContentSlot = new LinkedHashMap<>();
        Map<Integer, Field> ownerOfBackgroundSlot = new LinkedHashMap<>();
        Set<String> reported = new LinkedHashSet<>();
        for (Field field : byDisputePriority(fields)) {
            LayoutBase.PlacedIcon placed = instance.getIcons().get(field.getName());
            if (placed == null) {
                continue;
            }
            Map<Integer, Field> ownerOfSlot = placed.getIcon().isBackground()
                    ? ownerOfBackgroundSlot : ownerOfContentSlot;
            for (int slot : placed.getSlots().toArray()) {
                Field owner = ownerOfSlot.get(slot);
                if (owner == null) {
                    ownerOfSlot.put(slot, field);
                } else if (!sharesGroup(owner, field)) {
                    String dispute = slotDisputeMessage(claimantOf(owner), claimantOf(field), slot);
                    if (reported.add(dispute)) { //a whole group against one outsider is one sentence, said once
                        plugin.getLog().warning(type.getSimpleName() + ": " + dispute + " Move one of them,"
                                + " or give both the same group = \"...\" when they are meant to share the"
                                + " slot and the menu picks which one is alive.");
                    }
                }
            }
        }
    }

    /**
     * Renders the decoration the file declares on its own.
     *
     * <p>Adding a pane to {@code Background} without touching the plugin has always worked, and it is
     * the one place a key with no Java field is a feature rather than leftovers - which is why
     * quarantine leaves that section alone.</p>
     */
    private static void loadFileOnlyBackground(ECPluginData plugin, Class<?> type, LayoutBase instance,
                                               LayoutSource source) {
        for (String key : source.getKeys(BACKGROUND)) {
            if (instance.getIcons().containsKey(key)) {
                continue;
            }
            String path = BACKGROUND + "." + key;
            try {
                List<String> displayItem = source.getStringList(path + ".DisplayItem");
                if (displayItem.isEmpty()) {
                    //no field behind the key means no default to fall back on, and building from nothing
                    //would paint whatever the factory starts out as
                    throw new IllegalArgumentException("the key says where to put an icon but never says "
                            + "which one, and no plugin field stands behind it to supply one. Give it a "
                            + "DisplayItem - a single 'type:STONE' line is enough - or delete the key.");
                }
                Icon icon = Icon.of(buildItem(displayItem)).background();
                icon.setName(key); //the yml key is all the identity a decoration with no field behind it has
                icon.setPermission(source.getString(path + ".Permission"));
                icon.setLocaleOwner(plugin);
                SlotSet slots = source.getValue(path + ".Slot", SlotSet.class);
                instance.putIcon(key, icon, slots == null ? SlotSet.EMPTY : slots);
            } catch (Throwable failure) {
                plugin.getLog().warning(type.getSimpleName() + "." + key + ": " + reasonOf(failure)
                        + " Icon disabled. " + plugin.getMetaInfo().getName() + "/" + fileNameOf(type)
                        + ", key " + path);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Quarantine - a key with no field left behind is a key the admin edits for nothing
    // -----------------------------------------------------------------------------------------------------------------

    private static void quarantineOrphans(ECPluginData plugin, Config config, List<Field> fields) {
        Set<String> declared = new LinkedHashSet<>();
        for (Field field : fields) {
            declared.add(field.getName());
        }
        if (!config.contains(LAYOUT)) {
            return;
        }
        for (String key : new ArrayList<>(config.getKeys(LAYOUT))) {
            if (declared.contains(key)) {
                continue;
            }
            config.migrateKey(LAYOUT + "." + key, QUARANTINE + "." + key);
            config.setComment(QUARANTINE + "." + key, LocalDate.now()
                    + "  no longer declared by the plugin. Kept so its customisation can be copied "
                    + "into whichever key replaced it.");
            plugin.getLog().warning("Moved " + LAYOUT + "." + key + " to " + QUARANTINE
                    + ": the plugin no longer declares it. Nothing was deleted.");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The header, which is the only documentation the admin is guaranteed to read
    // -----------------------------------------------------------------------------------------------------------------

    private static String[] header(LayoutBase instance, GuiLayout declaration) {
        List<String> lines = new ArrayList<>();
        lines.add("Generated by EverNifeCore. What you write here beats the plugin's default.");
        lines.add("");
        lines.add("  Settings.type  the window: " + GuiType.names() + ".");
        lines.add("  Settings.rows  how many rows a CHEST has, " + GuiType.MIN_CHEST_ROWS + " to "
                + GuiType.MAX_CHEST_ROWS + ". Every other type has a size of its own and ignores it.");
        lines.add("");
        lines.add("  Slot         where the icon shows up. Takes \"[10,11]\", a yaml list, or a bare number.");
        lines.add("               An empty list switches the icon off.");
        lines.add("  Permission   who sees the icon. Absent means everyone.");
        lines.add("  DisplayItem  the item, one entry per line: type: name: lore: nbt: durability: ...");
        lines.add("  States       an alternative look, picked by the plugin at runtime. Each one takes a");
        lines.add("               DisplayItem and a Locale of its own.");
        lines.add("  Locale       name and lore per language. Overrides the name:/lore: of DisplayItem.");
        lines.add("");

        Set<String> placeholders = placeholdersOf(instance, declaration);
        if (!placeholders.isEmpty()) {
            lines.add("Placeholders on this screen:");
            lines.add("  " + String.join("  ", placeholders));
            lines.add("");
        }
        return lines.toArray(new String[0]);
    }

    /** Every {@code %placeholder%} the screen's own text mentions, so the admin knows what may be moved. */
    private static Set<String> placeholdersOf(LayoutBase instance, GuiLayout declaration) {
        Set<String> found = new LinkedHashSet<>();
        collectPlaceholders(declaration.title(), found);
        for (String title : instance.getTitleByLang().values()) {
            collectPlaceholders(title, found);
        }
        for (LayoutBase.PlacedIcon placed : instance.getIcons().values()) {
            for (String line : ItemEngine.get().read(placed.getIcon().getItemStack()).getLines()) {
                collectPlaceholders(line, found);
            }
            IconLocale locale = placed.getIcon().getLocale();
            if (locale != null) {
                for (String lang : locale.getLanguages()) {
                    for (String line : locale.get(lang)) {
                        collectPlaceholders(line, found);
                    }
                }
            }
        }
        return found;
    }

    private static void collectPlaceholders(String text, Set<String> found) {
        if (text == null) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static <T extends LayoutBase> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (InvocationTargetException thrownByTheLayoutItself) {
            //The constructor exists and it RAN: what its field initializers threw is the whole news.
            //Advice about writing a no-arg constructor would bury it under a defect the class does not have
            Throwable cause = thrownByTheLayoutItself.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(type.getName() + " failed while being built: " + cause, cause);
        } catch (Exception uninstantiable) {
            throw new IllegalArgumentException(type.getName() + " cannot be built: a layout needs a public "
                    + "no-arg constructor, because the framework is what creates it - Layouts.of("
                    + type.getSimpleName() + ".class), never new.", uninstantiable);
        }
    }

    private static String[] names(Object[] constants) {
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = String.valueOf(constants[i]);
        }
        return names;
    }

    /** The closest known name, when there is one close enough to be a typo rather than a different word. */
    private static String didYouMean(String wanted, String[] candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = distance(wanted, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best != null && bestDistance <= Math.max(1, wanted.length() / 4)
                ? "Did you mean '" + best + "'? " : "";
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int substitution = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

}
