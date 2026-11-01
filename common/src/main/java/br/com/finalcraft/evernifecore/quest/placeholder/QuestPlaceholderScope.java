package br.com.finalcraft.evernifecore.quest.placeholder;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.quest.QuestEngine;
import br.com.finalcraft.evernifecore.quest.QuestId;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Which engines one placeholder prefix sees, and how it names their quests. {@link #everyEngine()} sees every quest by
 * its full id ({@code finalquests:mine-iron}); {@link #ownedBy} sees one plugin's, by the bare name its own commands take
 * ({@code mine-iron}).
 */
public final class QuestPlaceholderScope {

    private static final QuestPlaceholderScope EVERY_ENGINE = new QuestPlaceholderScope(null);

    private final @Nullable ECPluginData owner;

    private QuestPlaceholderScope(@Nullable ECPluginData owner) {
        this.owner = owner;
    }

    /** Every quest of every engine, named by its full id. */
    public static QuestPlaceholderScope everyEngine() {
        return EVERY_ENGINE;
    }

    /** Only the quests of {@code owner}'s engine, named by their bare name. */
    public static QuestPlaceholderScope ownedBy(ECPluginData owner) {
        return new QuestPlaceholderScope(Objects.requireNonNull(owner, "owner"));
    }

    /** The engines this scope reads now; an owner that has not opened its engine yet reads none. */
    public Collection<QuestEngine> engines() {
        if (owner == null) {
            return QuestEngine.all();
        }
        QuestEngine engine = QuestEngine.ofIfPresent(owner);
        return engine == null ? Collections.<QuestEngine>emptyList() : Collections.singletonList(engine);
    }

    /** The quest this scope calls {@code text}; null when it is no valid id here. */
    public @Nullable QuestId find(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            if (owner != null) {
                return text.indexOf(':') >= 0 ? null : QuestId.of(owner.getMetaInfo().getName(), text.trim());
            }
            return text.indexOf(':') > 0 ? QuestId.parse(text.trim(), "") : null;
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    /** The engine that holds {@code id} in this scope; null when none is open. */
    public @Nullable QuestEngine engineOf(QuestId id) {
        for (QuestEngine engine : engines()) {
            if (engine.getOwner().getMetaInfo().getName().equalsIgnoreCase(id.getOwner())) {
                return engine;
            }
        }
        return null;
    }

    /** The quest's name in this scope. */
    public String idOf(QuestId id) {
        return owner == null ? id.toString() : id.getName();
    }
}
