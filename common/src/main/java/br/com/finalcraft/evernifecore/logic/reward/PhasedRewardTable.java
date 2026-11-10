package br.com.finalcraft.evernifecore.logic.reward;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link RewardTable} per phase of an occasion - a raid's stage, an arena's wave, a race's position - read from a map
 * whose keys say which phases each table pays:
 * <pre>{@code
 * 5: [...]          # exactly phase 5
 * 4-10: [...]       # 4 to 10, both included
 * 10+: [...]        # 10 and up
 * every 5: [...]    # 5, 10, 15...
 * default: [...]    # any phase no other key takes
 * }</pre>
 * The most specific key wins: exact, then the narrowest range, then the highest {@code N+}, then the largest
 * {@code every N}, then {@code default}. Two keys of one kind that match a phase must nest (a range inside another,
 * {@code every 10} inside {@code every 5}); otherwise the load refuses them. Immutable; any thread.
 */
public final class PhasedRewardTable {

    private static final Pattern EVERY = Pattern.compile("every\\s+(\\d+)");
    private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
    private static final Pattern FROM = Pattern.compile("(\\d+)\\s*\\+");
    private static final Pattern EXACT = Pattern.compile("\\d+");

    private final RewardOccasion occasion;
    private final Map<Key, RewardTable> tables;

    private PhasedRewardTable(RewardOccasion occasion, Map<Key, RewardTable> tables) {
        this.occasion = occasion;
        this.tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
    }

    /**
     * Reads the map of phase keys, each table as {@link RewardTable#parse} reads one; nothing (null) pays no phase.
     *
     * @throws TypeRegistryException for a key that is no phase, two keys of one kind that match a phase without nesting
     *                               (naming both and a phase they share), or a table that cannot be read (naming its key)
     */
    public static PhasedRewardTable parse(ECPluginData caller, RewardOccasion occasion, JsonNode node) throws TypeRegistryException {
        Map<Key, RewardTable> tables = new LinkedHashMap<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return new PhasedRewardTable(occasion, tables);
        }
        if (!node.isObject()) {
            throw new TypeRegistryException("The rewards of '" + occasion.getId() + "' are a map of " + occasion.getPhase() + " to steps"
                    + " (5: [...], 4-10: [...], 10+: [...], every 5: [...], default: [...]) - this one is " + node + ".");
        }
        List<Key> keys = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> fields = node.fields(); fields.hasNext(); ) {
            Map.Entry<String, JsonNode> field = fields.next();
            Key key = Key.parse(occasion, field.getKey());
            for (Key other : keys) {
                Integer shared = key.clashesWith(other);
                if (shared != null) {
                    throw new TypeRegistryException("The rewards of '" + occasion.getId() + "' have the keys '" + other.written + "' and '"
                            + key.written + "', which both take " + occasion.getPhase() + " " + shared + " and neither is inside the other."
                            + " " + key.wayOut());
                }
            }
            keys.add(key);
            try {
                tables.put(key, RewardTable.parse(caller, occasion, field.getValue()));
            } catch (TypeRegistryException refused) {
                throw new TypeRegistryException("Under '" + field.getKey() + "' of the rewards of '" + occasion.getId() + "': " + refused.getMessage());
            }
        }
        return new PhasedRewardTable(occasion, tables);
    }

    public RewardOccasion getOccasion() {
        return occasion;
    }

    /** The table of the most specific key that takes {@code phase}; {@link RewardTable#EMPTY} when no key does. */
    public RewardTable getTable(int phase) {
        Key key = keyOf(phase);
        return key == null ? RewardTable.EMPTY : tables.get(key);
    }

    /** The key, as written, whose table pays {@code phase}; null when none takes it. */
    public @Nullable String getKey(int phase) {
        Key key = keyOf(phase);
        return key == null ? null : key.written;
    }

    private @Nullable Key keyOf(int phase) {
        Key best = null;
        for (Key key : tables.keySet()) {
            if (key.matches(phase) && (best == null || key.beats(best))) {
                best = key;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return "PhasedRewardTable{" + occasion.getId() + ", " + tables + "}";
    }

    /** One key; kinds in the order of precedence, the earlier winning. */
    private static final class Key {
        private enum Kind { EXACT, RANGE, FROM, EVERY, DEFAULT }

        private final String written;
        private final Kind kind;
        private final int from;
        private final int to;

        private Key(String written, Kind kind, int from, int to) {
            this.written = written;
            this.kind = kind;
            this.from = from;
            this.to = to;
        }

        static Key parse(RewardOccasion occasion, String written) throws TypeRegistryException {
            String key = written.trim().toLowerCase(Locale.ROOT);
            try {
                if (key.equals("default")) {
                    return new Key(written, Kind.DEFAULT, 0, Integer.MAX_VALUE);
                }
                Matcher every = EVERY.matcher(key);
                if (every.matches() && Integer.parseInt(every.group(1)) > 0) {
                    return new Key(written, Kind.EVERY, Integer.parseInt(every.group(1)), Integer.MAX_VALUE);
                }
                Matcher range = RANGE.matcher(key);
                if (range.matches() && Integer.parseInt(range.group(1)) <= Integer.parseInt(range.group(2))) {
                    return new Key(written, Kind.RANGE, Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)));
                }
                Matcher from = FROM.matcher(key);
                if (from.matches()) {
                    return new Key(written, Kind.FROM, Integer.parseInt(from.group(1)), Integer.MAX_VALUE);
                }
                if (EXACT.matcher(key).matches()) {
                    return new Key(written, Kind.EXACT, Integer.parseInt(key), Integer.parseInt(key));
                }
            } catch (NumberFormatException tooLarge) {
                //falls through to the refusal below
            }
            throw new TypeRegistryException("The rewards of '" + occasion.getId() + "' have the key '" + written + "', which names no "
                    + occasion.getPhase() + ": write 5, 4-10 (smaller first), 10+, every 5 (above 0) or default.");
        }

        boolean matches(int phase) {
            if (kind == Kind.EVERY) {
                return phase > 0 && phase % from == 0;
            }
            return phase >= from && phase <= to;
        }

        /** Whether this key is more specific than {@code other}, both matching one phase. */
        boolean beats(Key other) {
            if (kind != other.kind) {
                return kind.ordinal() < other.kind.ordinal();
            }
            switch (kind) {
                case RANGE:
                    return (long) to - from < (long) other.to - other.from;
                case FROM:
                case EVERY:
                    return from > other.from;
                default:
                    return false;
            }
        }

        /** A phase both keys take when they are of one kind and neither is inside the other; null when they may stand together. */
        @Nullable Integer clashesWith(Key other) {
            if (kind != other.kind) {
                return null;
            }
            switch (kind) {
                case RANGE:
                    boolean overlap = Math.max(from, other.from) <= Math.min(to, other.to);
                    boolean same = from == other.from && to == other.to;
                    boolean nested = !same && ((from <= other.from && to >= other.to) || (other.from <= from && other.to >= to));
                    return overlap && !nested ? Math.max(from, other.from) : null;
                case EVERY:
                    if (from != other.from && (from % other.from == 0 || other.from % from == 0)) {
                        return null;
                    }
                    long lcm = (long) from / gcd(from, other.from) * other.from;
                    return lcm > Integer.MAX_VALUE ? null : (int) lcm;
                default:
                    return from == other.from ? from : null;
            }
        }

        String wayOut() {
            switch (kind) {
                case RANGE:
                    return "Put one range inside the other (the narrower wins), or split them so they share no value.";
                case EVERY:
                    return "Make one a multiple of the other (every 10 inside every 5 - the larger wins), or keep one.";
                default:
                    return "They are the same key: merge their steps under one.";
            }
        }

        private static int gcd(int a, int b) {
            return b == 0 ? a : gcd(b, a % b);
        }

        @Override
        public String toString() {
            return written;
        }
    }
}
