package br.com.finalcraft.evernifecore.quest;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** What a journal and a pool read about a quest: category, icon, order, visibility, tags and a product's own extras. Immutable. */
public final class QuestMeta {

    public enum Visibility {
        VISIBLE,
        /** Listed only once started. */
        HIDDEN,
        /** Not listed until available. */
        SECRET;

        /** Whether a list (the quest command, the journal) shows a quest of this visibility that stands at {@code status}. */
        public boolean lists(QuestStatus status) {
            switch (this) {
                case HIDDEN:
                    return status != QuestStatus.LOCKED && status != QuestStatus.AVAILABLE && status != QuestStatus.OFFERED;
                case SECRET:
                    return status != QuestStatus.LOCKED;
                default:
                    return true;
            }
        }
    }

    public static final QuestMeta NONE = new QuestMeta("", null, 0, Visibility.VISIBLE, Collections.<String>emptySet(),
            Collections.<String, JsonNode>emptyMap());

    private final String category;
    private final @Nullable String icon;
    private final int order;
    private final Visibility visibility;
    private final Set<String> tags;
    private final Map<String, JsonNode> extra;

    public QuestMeta(String category, @Nullable String icon, int order, Visibility visibility, Set<String> tags, Map<String, JsonNode> extra) {
        this.category = category;
        this.icon = icon;
        this.order = order;
        this.visibility = visibility;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        this.extra = Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    public String getCategory() {
        return category;
    }

    /** The item id a screen draws the quest with ({@code minecraft:iron_ore}); null for the screen's default. */
    public @Nullable String getIcon() {
        return icon;
    }

    public int getOrder() {
        return order;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    /** What a pool draws by ({@code from-tag}). */
    public Set<String> getTags() {
        return tags;
    }

    /** A product's own keys, kept as written. */
    public Map<String, JsonNode> getExtra() {
        return extra;
    }

    /** The extra {@code key} as an integer, {@code fallback} when absent or not a number. */
    public int getExtra(String key, int fallback) {
        JsonNode value = extra.get(key);
        return value != null && value.canConvertToInt() ? value.asInt() : fallback;
    }
}
