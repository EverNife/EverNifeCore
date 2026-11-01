package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.npc.NpcRef;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What a session carries besides its tree: the NPC it is held with, the node it starts on, and any attribute the
 * starter adds. Every attribute reaches each {@code ExecutionContext} of the session as {@code getAttribute(key, type)},
 * the NPC under {@link #NPC} and the session itself under {@link #SESSION}. Filled by the starter before the session
 * opens; read-only afterwards.
 */
public final class DialogueContext {

    /** The attribute key of the {@link NpcRef} the player talks to. */
    public static final String NPC = "npc";
    /** The attribute key of the {@link DialogueSession} itself. */
    public static final String SESSION = DialogueSession.class.getName();

    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private @Nullable String node;
    private boolean frozen;

    DialogueContext() {
    }

    /** Starts on {@code node} instead of the dialogue's entry. */
    public DialogueContext node(@Nullable String node) {
        requireOpen();
        this.node = node == null ? null : node.toLowerCase(Locale.ROOT);
        return this;
    }

    /** The NPC the player talks to: {@code near-npc} and the screen may read it. */
    public DialogueContext npc(@Nullable NpcRef npc) {
        return attribute(NPC, npc);
    }

    public DialogueContext attribute(String key, @Nullable Object value) {
        requireOpen();
        if (value == null) {
            attributes.remove(Objects.requireNonNull(key, "key"));
        } else {
            attributes.put(Objects.requireNonNull(key, "key"), value);
        }
        return this;
    }

    /** The node the session starts on; null = the dialogue's entry. */
    public @Nullable String getNode() {
        return node;
    }

    public @Nullable NpcRef getNpc() {
        return getAttribute(NPC, NpcRef.class);
    }

    public <V> @Nullable V getAttribute(String key, Class<V> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    void freeze() {
        frozen = true;
    }

    private void requireOpen() {
        if (frozen) {
            throw new IllegalStateException("The session already started: its context is read-only. Fill it in the consumer handed to"
                    + " Dialogues.start.");
        }
    }
}
