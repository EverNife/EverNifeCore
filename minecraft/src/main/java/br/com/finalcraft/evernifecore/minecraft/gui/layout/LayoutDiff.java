package br.com.finalcraft.evernifecore.minecraft.gui.layout;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.everyconfig.config.Config;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the plugin declares against what the file says, key by key.
 *
 * <p>It answers the question nobody could answer without reading the source: the admin edited a key
 * and nothing changed on screen - is the key still used, was it renamed, or is it simply switched
 * off? Four verdicts cover it, and {@link Verdict#SILENCED} is the one that matters: a key that
 * exists, that the admin is editing, and that has no effect at all.</p>
 *
 * <p>This is a value, not a command: it is computed against any {@link Config}, in memory included,
 * which is what lets a test assert on the same classification an operator reads.</p>
 */
public final class LayoutDiff {

    /** What became of one key. */
    public enum Verdict {
        /** Declared in Java and present in the file. */
        MATCHED,
        /** Declared in Java, absent from the file - it will be seeded on the next save. */
        NEW,
        /** In the file, no longer declared in Java - it goes to quarantine. */
        ORPHAN,
        /** In both, but with no effect on screen: an empty slot list. */
        SILENCED
    }

    /** One key and what became of it. */
    public static final class Entry {

        private final String key;
        private final Verdict verdict;
        private final String section;
        private final SlotSet slots;
        private final boolean fromOverlay;
        private final String detail;

        Entry(String key, Verdict verdict, String section, SlotSet slots, boolean fromOverlay, String detail) {
            this.key = key;
            this.verdict = verdict;
            this.section = section;
            this.slots = slots;
            this.fromOverlay = fromOverlay;
            this.detail = detail;
        }

        @Nonnull
        public String getKey() {
            return key;
        }

        @Nonnull
        public Verdict getVerdict() {
            return verdict;
        }

        /** {@code Layout} or {@code Background}. */
        @Nonnull
        public String getSection() {
            return section;
        }

        /** Where the key puts the icon, or {@code null} when the file holds no readable slot list. */
        @Nullable
        public SlotSet getSlots() {
            return slots;
        }

        /** Whether the language overlay, and not the base file, is what answers for this key. */
        public boolean isFromOverlay() {
            return fromOverlay;
        }

        /** What else is worth knowing about this key - states, permission - or an empty string. */
        @Nonnull
        public String getDetail() {
            return detail;
        }

        @Override
        public String toString() {
            return verdict + " " + key + (slots == null ? "" : " " + slots.serialize());
        }

    }

    private final String layoutName;
    private final String fileName;
    private final String language;
    private final boolean overlayPresent;
    private final List<Entry> entries;
    private final List<String> warnings;

    private LayoutDiff(String layoutName, String fileName, String language, boolean overlayPresent,
                       List<Entry> entries, List<String> warnings) {
        this.layoutName = layoutName;
        this.fileName = fileName;
        this.language = language;
        this.overlayPresent = overlayPresent;
        this.entries = entries;
        this.warnings = warnings;
    }

    /** The confrontation for {@code type}, reading the plugin's own files. */
    @Nonnull
    public static LayoutDiff of(@Nonnull ECPluginData plugin, @Nonnull Class<? extends LayoutBase> type,
                                @Nullable String language) {
        Config base = ConfigFactory.open(plugin, LayoutScanner.fileNameOf(type));
        Config overlay = LayoutScanner.openOverlay(plugin, type, language);
        return of(type, new LayoutSource(base, overlay), language);
    }

    /** The confrontation against files already open - the form a test uses. */
    @Nonnull
    public static LayoutDiff of(@Nonnull Class<? extends LayoutBase> type, @Nonnull LayoutSource source,
                                @Nullable String language) {
        Map<String, String> sectionOfField = new LinkedHashMap<>();
        for (Field field : LayoutScanner.iconFields(type)) {
            IconData iconData = field.getAnnotation(IconData.class);
            sectionOfField.put(field.getName(),
                    iconData.background() ? LayoutScanner.BACKGROUND : LayoutScanner.LAYOUT);
        }

        List<Entry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<Integer, String> ownerOfSlot = new LinkedHashMap<>();

        for (Map.Entry<String, String> declared : sectionOfField.entrySet()) {
            String key = declared.getKey();
            String path = declared.getValue() + "." + key;
            if (!source.contains(path)) {
                entries.add(new Entry(key, Verdict.NEW, declared.getValue(), null, false,
                        "will be seeded on the next save"));
                continue;
            }
            SlotSet slots = slotsOf(source, path, warnings, key);
            Verdict verdict = slots != null && slots.isEmpty() ? Verdict.SILENCED : Verdict.MATCHED;
            entries.add(new Entry(key, verdict, declared.getValue(), slots,
                    source.isFromOverlay(path), detailOf(source, path)));
            if (slots != null) {
                reportDisputes(key, slots, ownerOfSlot, warnings);
            }
        }

        for (String key : source.getKeys(LayoutScanner.LAYOUT)) {
            if (!sectionOfField.containsKey(key)) {
                entries.add(new Entry(key, Verdict.ORPHAN, LayoutScanner.LAYOUT, null,
                        source.isFromOverlay(LayoutScanner.LAYOUT + "." + key),
                        "the plugin no longer declares it"));
            }
        }
        //Decoration the file added on its own is a feature, not leftovers: it renders, so it matches
        for (String key : source.getKeys(LayoutScanner.BACKGROUND)) {
            if (!sectionOfField.containsKey(key)) {
                String path = LayoutScanner.BACKGROUND + "." + key;
                SlotSet slots = slotsOf(source, path, warnings, key);
                entries.add(new Entry(key, slots != null && slots.isEmpty() ? Verdict.SILENCED
                        : Verdict.MATCHED, LayoutScanner.BACKGROUND, slots, source.isFromOverlay(path),
                        "declared by the file only"));
            }
        }

        return new LayoutDiff(type.getSimpleName(), LayoutScanner.fileNameOf(type), language,
                source.hasOverlay(), entries, warnings);
    }

    @Nullable
    private static SlotSet slotsOf(LayoutSource source, String path, List<String> warnings, String key) {
        try {
            return source.getValue(path + ".Slot", SlotSet.class);
        } catch (RuntimeException unreadable) {
            warnings.add(key + " has an unreadable slot list: " + unreadable.getMessage());
            return null;
        }
    }

    private static String detailOf(LayoutSource source, String path) {
        List<String> detail = new ArrayList<>();
        Set<String> states = source.getKeys(path + ".States");
        if (!states.isEmpty()) {
            detail.add(states.size() + " state" + (states.size() == 1 ? "" : "s"));
        }
        Set<String> languages = source.getKeys(path + ".Locale");
        if (!languages.isEmpty()) {
            detail.add(String.join("/", languages));
        }
        String permission = source.getString(path + ".Permission");
        if (permission != null && !permission.isEmpty()) {
            detail.add("permission " + permission);
        }
        return String.join(", ", detail);
    }

    private static void reportDisputes(String key, SlotSet slots, Map<Integer, String> ownerOfSlot,
                                       List<String> warnings) {
        for (int slot : slots.toArray()) {
            String owner = ownerOfSlot.get(slot);
            if (owner == null) {
                ownerOfSlot.put(slot, key);
            } else {
                warnings.add(owner + " and " + key + " both claim slot " + slot + ". " + owner + " wins.");
            }
        }
    }

    @Nonnull
    public String getLayoutName() {
        return layoutName;
    }

    /** The base file, relative to the plugin's data folder. */
    @Nonnull
    public String getFileName() {
        return fileName;
    }

    /** The language this diff was read for, or {@code null} for the base file alone. */
    @Nullable
    public String getLanguage() {
        return language;
    }

    public boolean hasOverlay() {
        return overlayPresent;
    }

    @Nonnull
    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Nonnull
    public List<Entry> getEntries(@Nonnull Verdict verdict) {
        List<Entry> matching = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.getVerdict() == verdict) {
                matching.add(entry);
            }
        }
        return matching;
    }

    @Nonnull
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /** The keys the overlay answers for - what a language changed about this screen. */
    @Nonnull
    public Set<String> getOverriddenByOverlay() {
        Set<String> overridden = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (entry.isFromOverlay()) {
                overridden.add(entry.getKey());
            }
        }
        return overridden;
    }

}
