package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
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

    /** Where a key stands between what the plugin declares and what the file says. */
    public enum Origin {
        /** Read from the file as the plugin declares it. */
        DECLARED,
        /** Not in the file yet: it will be seeded on the next save. */
        TO_BE_SEEDED,
        /** The plugin no longer declares it. */
        NO_LONGER_DECLARED,
        /** A decoration the file added by itself. */
        FILE_ONLY
    }

    /** A slot list nothing can read: where it goes is unknown, and that is a defect, not the empty list that switches an icon off. */
    public static final class UnreadableSlots {

        private final String key;
        private final String reason;
        private final String path;

        UnreadableSlots(String key, String reason, String path) {
            this.key = key;
            this.reason = reason;
            this.path = path;
        }

        @Nonnull
        public String getKey() {
            return key;
        }

        /** What the deepest failure said. */
        @Nonnull
        public String getReason() {
            return reason;
        }

        /** The node to fix in {@link LayoutDiff#getFileName()}. */
        @Nonnull
        public String getPath() {
            return path;
        }

        @Override
        public String toString() {
            return key + " has an unreadable slot list at " + path + ": " + reason;
        }
    }

    /** One slot two claims of the same layer want, in the order the screen resolves them. */
    public static final class SlotDispute {

        private final String winner;
        private final String loser;
        private final int slot;

        SlotDispute(String winner, String loser, int slot) {
            this.winner = winner;
            this.loser = loser;
            this.slot = slot;
        }

        /** The key that keeps the slot, or {@code [group]} for an icon that declares one: a group is named as a whole. */
        @Nonnull
        public String getWinner() {
            return winner;
        }

        /** The key that loses the slot, or {@code [group]}. */
        @Nonnull
        public String getLoser() {
            return loser;
        }

        public int getSlot() {
            return slot;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SlotDispute && toString().equals(other.toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public String toString() {
            return winner + " and " + loser + " both claim slot " + slot + ". " + winner + " wins.";
        }
    }

    /** One key and what became of it. */
    public static final class Entry {

        private final String key;
        private final Verdict verdict;
        private final String section;
        private final SlotSet slots;
        private final boolean fromOverlay;
        private final Origin origin;
        private final int stateCount;
        private final Set<String> languages;
        private final String permission;
        private final String group;

        Entry(String key, Verdict verdict, String section, SlotSet slots, boolean fromOverlay, Origin origin,
              int stateCount, Set<String> languages, String permission, String group) {
            this.key = key;
            this.verdict = verdict;
            this.section = section;
            this.slots = slots;
            this.fromOverlay = fromOverlay;
            this.origin = origin;
            this.stateCount = stateCount;
            this.languages = languages;
            this.permission = permission;
            this.group = group;
        }

        static Entry bare(String key, Verdict verdict, String section, SlotSet slots, boolean fromOverlay, Origin origin, String group) {
            return new Entry(key, verdict, section, slots, fromOverlay, origin, 0, Collections.emptySet(), null, group);
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

        /** Where the key stands between the plugin and the file. */
        @Nonnull
        public Origin getOrigin() {
            return origin;
        }

        /** How many {@code States} the file gives this key. */
        public int getStateCount() {
            return stateCount;
        }

        /** The languages the file's {@code Locale} block covers for this key. */
        @Nonnull
        public Set<String> getLanguages() {
            return languages;
        }

        /** The permission the file asks for this key, or {@code null}. */
        @Nullable
        public String getPermission() {
            return permission;
        }

        /**
         * The group this key shares its position with on purpose, or an empty string. Three icons over
         * one slot look like a defect until the report says they were meant to.
         */
        @Nonnull
        public String getGroup() {
            return group;
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
    private final List<UnreadableSlots> unreadableSlots;
    private final List<SlotDispute> slotDisputes;

    private LayoutDiff(String layoutName, String fileName, String language, boolean overlayPresent,
                       List<Entry> entries, List<UnreadableSlots> unreadableSlots, List<SlotDispute> slotDisputes) {
        this.layoutName = layoutName;
        this.fileName = fileName;
        this.language = language;
        this.overlayPresent = overlayPresent;
        this.entries = entries;
        this.unreadableSlots = unreadableSlots;
        this.slotDisputes = slotDisputes;
    }

    /** The confrontation for {@code type}, reading the plugin's own files. */
    @Nonnull
    public static LayoutDiff of(@Nonnull ECPluginData plugin, @Nonnull Class<? extends Layout> type,
                                @Nullable String language) {
        Config base = ConfigFactory.open(plugin, LayoutScanner.fileNameOf(type));
        Config overlay = LayoutScanner.openOverlay(plugin, type, language);
        return of(type, new LayoutSource(base, overlay), language);
    }

    /** The confrontation against files already open - the form a test uses. */
    @Nonnull
    public static LayoutDiff of(@Nonnull Class<? extends Layout> type, @Nonnull LayoutSource source,
                                @Nullable String language) {
        List<Field> fields = LayoutScanner.iconFields(type);
        Map<String, Field> fieldOfKey = new LinkedHashMap<>();
        for (Field field : fields) {
            fieldOfKey.put(field.getName(), field);
        }

        String fileName = LayoutScanner.fileNameOf(type);
        List<Entry> entries = new ArrayList<>();
        List<UnreadableSlots> unreadable = new ArrayList<>();
        Map<String, SlotSet> claimedSlots = new LinkedHashMap<>();

        for (Map.Entry<String, Field> declared : fieldOfKey.entrySet()) {
            String key = declared.getKey();
            IconData iconData = declared.getValue().getAnnotation(IconData.class);
            String section = iconData.background() ? LayoutScanner.BACKGROUND : LayoutScanner.LAYOUT;
            String path = section + "." + key;
            if (!source.contains(path)) {
                entries.add(Entry.bare(key, Verdict.NEW, section, null, false, Origin.TO_BE_SEEDED, iconData.group()));
                continue;
            }
            SlotSet slots = slotsOf(source, path, unreadable, key);
            Verdict verdict = slots != null && slots.isEmpty() ? Verdict.SILENCED : Verdict.MATCHED;
            String permission = source.getString(path + ".Permission");
            entries.add(new Entry(key, verdict, section, slots, source.isFromOverlay(path), Origin.DECLARED,
                    source.getKeys(path + ".States").size(), source.getKeys(path + ".Locale"),
                    permission == null || permission.isEmpty() ? null : permission, iconData.group()));
            if (slots != null) {
                claimedSlots.put(key, slots);
            }
        }
        //the claims are confronted in the order the SCREEN resolves them, never in declaration order:
        //a report that named the other winner is worse than no report at all
        List<SlotDispute> disputes = disputesOf(fields, claimedSlots);

        for (String key : source.getKeys(LayoutScanner.LAYOUT)) {
            if (!fieldOfKey.containsKey(key)) {
                entries.add(Entry.bare(key, Verdict.ORPHAN, LayoutScanner.LAYOUT, null,
                        source.isFromOverlay(LayoutScanner.LAYOUT + "." + key), Origin.NO_LONGER_DECLARED, ""));
            }
        }
        //Decoration the file added on its own is a feature, not leftovers: it renders, so it matches
        for (String key : source.getKeys(LayoutScanner.BACKGROUND)) {
            if (!fieldOfKey.containsKey(key)) {
                String path = LayoutScanner.BACKGROUND + "." + key;
                SlotSet slots = slotsOf(source, path, unreadable, key);
                entries.add(Entry.bare(key, slots != null && slots.isEmpty() ? Verdict.SILENCED
                        : Verdict.MATCHED, LayoutScanner.BACKGROUND, slots, source.isFromOverlay(path), Origin.FILE_ONLY, ""));
            }
        }

        return new LayoutDiff(type.getSimpleName(), fileName, language,
                source.hasOverlay(), entries, unreadable, disputes);
    }

    /**
     * Where the file puts {@code path}, or {@code null} when it holds a slot list nothing can read -
     * which is reported as the defect it is, never as the empty list that switches an icon off.
     */
    @Nullable
    private static SlotSet slotsOf(LayoutSource source, String path, List<UnreadableSlots> unreadable, String key) {
        try {
            return source.getValue(path + ".Slot", SlotSet.class);
        } catch (RuntimeException failure) {
            unreadable.add(new UnreadableSlots(key, reasonOf(failure), path + ".Slot"));
            return null;
        }
    }

    /** What a failure says, following it down to the one that actually knows - a read that fails deep
     *  inside a codec is wrapped by every layer on the way out. */
    private static String reasonOf(Throwable failure) {
        Throwable deepest = failure;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }
        String message = deepest.getMessage();
        return message == null ? deepest.toString() : message;
    }

    /** The slots more than one key claims, as the screen would resolve them: same layer, different groups. */
    private static List<SlotDispute> disputesOf(List<Field> fields, Map<String, SlotSet> claimedSlots) {
        Set<SlotDispute> disputes = new LinkedHashSet<>(); //a whole group against one outsider is one dispute
        Map<Integer, Field> ownerOfContentSlot = new LinkedHashMap<>();
        Map<Integer, Field> ownerOfBackgroundSlot = new LinkedHashMap<>();
        for (Field field : LayoutScanner.byDisputePriority(fields)) {
            SlotSet slots = claimedSlots.get(field.getName());
            if (slots == null) {
                continue;
            }
            //a background and the content above it are two layers, not two claims to one slot
            Map<Integer, Field> ownerOfSlot = field.getAnnotation(IconData.class).background()
                    ? ownerOfBackgroundSlot : ownerOfContentSlot;
            for (int slot : slots.toArray()) {
                Field owner = ownerOfSlot.get(slot);
                if (owner == null) {
                    ownerOfSlot.put(slot, field);
                } else if (!LayoutScanner.sharesGroup(owner, field)) {
                    disputes.add(new SlotDispute(claimOf(owner), claimOf(field), slot));
                }
            }
        }
        return new ArrayList<>(disputes);
    }

    private static String claimOf(Field field) {
        String group = LayoutScanner.groupOf(field);
        return group.isEmpty() ? field.getName() : "[" + group + "]";
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
    public List<UnreadableSlots> getUnreadableSlots() {
        return Collections.unmodifiableList(unreadableSlots);
    }

    @Nonnull
    public List<SlotDispute> getSlotDisputes() {
        return Collections.unmodifiableList(slotDisputes);
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
