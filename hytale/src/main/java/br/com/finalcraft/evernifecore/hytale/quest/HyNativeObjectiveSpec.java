package br.com.finalcraft.evernifecore.hytale.quest;

import br.com.finalcraft.evernifecore.quest.Objective;
import br.com.finalcraft.evernifecore.quest.ObjectiveFilter;
import br.com.finalcraft.evernifecore.quest.QuestDefinition;
import br.com.finalcraft.evernifecore.quest.QuestParams;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * A quest's {@code hytale-native} map: {@code {markers, npc-actions, spawns, asset}}, every switch off by default.
 * {@code markers} puts a native map marker on each active objective with a place; {@code npc-actions} mirrors the native
 * objective {@code asset} an NPC role starts and advances into the quest; {@code spawns} cancels that native objective -
 * and with it what its spawn beacon brought - when the quest ends without completing. Immutable, any thread.
 */
public final class HyNativeObjectiveSpec {

    /** The objective types the native objectives express. */
    public static final List<String> MAPPED = List.of("break-block", "place-block", "kill", "pickup", "craft", "interact", "reach-location");

    private static final List<String> KEYS = List.of("markers", "npc-actions", "spawns", "asset");
    private static final String CORE_PREFIX = "evernifecore:";

    private final boolean markers;
    private final boolean npcActions;
    private final boolean spawns;
    private final @Nullable String asset;

    private HyNativeObjectiveSpec(boolean markers, boolean npcActions, boolean spawns, @Nullable String asset) {
        this.markers = markers;
        this.npcActions = npcActions;
        this.spawns = spawns;
        this.asset = asset;
    }

    /**
     * The map of {@code definition}; null when it has none or every switch is off.
     *
     * @throws IllegalArgumentException naming the quest and the fix, for a key it does not read or a switch that needs an asset
     */
    public static @Nullable HyNativeObjectiveSpec of(QuestDefinition definition) {
        JsonNode node = definition.getHytaleNative();
        if (node == null) {
            return null;
        }
        String where = "Quest '" + definition.getId() + "'";
        if (!node.isObject()) {
            throw new IllegalArgumentException(where + ": hytale-native is a map, e.g. {markers: true} - this one is " + node + ".");
        }
        for (Iterator<String> keys = node.fieldNames(); keys.hasNext(); ) {
            String key = keys.next();
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException(where + ": hytale-native has no key '" + key + "'. It reads: " + KEYS + ".");
            }
        }
        HyNativeObjectiveSpec spec = new HyNativeObjectiveSpec(node.path("markers").asBoolean(false), node.path("npc-actions").asBoolean(false),
                node.path("spawns").asBoolean(false), node.hasNonNull("asset") ? node.get("asset").asText() : null);
        if ((spec.npcActions || spec.spawns) && spec.asset == null) {
            throw new IllegalArgumentException(where + ": hytale-native npc-actions and spawns follow a native objective, so they need its id -"
                    + " write asset: \"<Pack>:<ObjectiveId>\", the objective the NPC role starts.");
        }
        return spec.markers || spec.npcActions || spec.spawns ? spec : null;
    }

    /**
     * The registration check: a quest with a switch on and an objective the native objectives cannot express is refused,
     * naming the objective, its type and what maps.
     *
     * @throws IllegalArgumentException that teaches the way out
     */
    public static void require(QuestDefinition definition) {
        if (of(definition) == null) {
            return;
        }
        for (Objective objective : definition.getObjectives()) {
            String type = typeOf(objective);
            if (!MAPPED.contains(type)) {
                throw new IllegalArgumentException("Quest '" + definition.getId() + "', objective '" + objective.getId() + "': hytale-native cannot"
                        + " express the type '" + type + "'; turn off markers/npc-actions for this quest or move the objective to one it maps: "
                        + String.join(", ", MAPPED) + ".");
            }
        }
    }

    /** The objective's type key, without the core's prefix; read before the type itself is, when it is still pending. */
    static String typeOf(Objective objective) {
        String key;
        ObjectiveFilter filter = objective.getFilter(QuestParams.of(Collections.<String, String>emptyMap()));
        if (filter != null) {
            key = filter.getType().getKey();
        } else if (objective.getPendingType() != null) {
            key = objective.getPendingType();
        } else {
            JsonNode raw = objective.getRawFilter();
            key = raw == null ? "" : raw.path("type").asText("");
        }
        key = key.trim().toLowerCase(Locale.ROOT);
        return key.startsWith(CORE_PREFIX) ? key.substring(CORE_PREFIX.length()) : key;
    }

    public boolean isMarkers() {
        return markers;
    }

    public boolean isNpcActions() {
        return npcActions;
    }

    public boolean isSpawns() {
        return spawns;
    }

    /** The native objective {@code npc-actions}/{@code spawns} follow; null when only markers are on. */
    public @Nullable String getAsset() {
        return asset;
    }

    @Override
    public String toString() {
        return "HyNativeObjectiveSpec{markers=" + markers + ", npcActions=" + npcActions + ", spawns=" + spawns + ", asset=" + asset + "}";
    }
}
