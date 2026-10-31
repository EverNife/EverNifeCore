package br.com.finalcraft.evernifecore.quest;

import br.com.finalcraft.evernifecore.playerdata.PDSection;
import jakarta.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player's quest progress, kept by the PLUGIN that owns the quests: the plugin declares a subclass of its own
 * ({@code public final class MyQuestSection extends QuestSection {}}) and hands it to
 * {@link QuestEngine#of(br.com.finalcraft.evernifecore.ecplugin.ECPluginData, br.com.finalcraft.evernifecore.storage.ECStorage, Class)},
 * because sections are registered by class. The state of a quest the engine does not know (removed from the config,
 * or its plugin's type not loaded) stays here untouched until a {@code reset}. Changed only by the engine, under the
 * player's lock.
 */
public abstract class QuestSection extends PDSection {

    /** The section id the engine registers a subclass under. */
    public static final String SECTION_ID = "quests";

    private Map<String, QuestState> states = new LinkedHashMap<>();
    private @Nullable String tracked;

    protected QuestSection() {
        //Jackson no-arg constructor - the framework attaches the PlayerData afterwards
    }

    /** By {@code owner:name} of the quest; the engine's own map, not a copy. */
    public Map<String, QuestState> getStates() {
        return states;
    }

    public void setStates(Map<String, QuestState> states) {
        this.states = states == null ? new LinkedHashMap<String, QuestState>() : states;
    }

    /** The quest the player tracks (its HUD and waypoints); null for none. */
    public @Nullable String getTracked() {
        return tracked;
    }

    public void setTracked(@Nullable String tracked) {
        this.tracked = tracked;
    }
}
