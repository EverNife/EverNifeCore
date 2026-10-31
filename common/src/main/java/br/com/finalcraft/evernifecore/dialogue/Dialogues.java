package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.action.UsageHold;
import br.com.finalcraft.evernifecore.action.event.InteractNpcActionEvent;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.dialogue.event.DialogueStartedEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.locale.LocaleOverlay;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.npc.NpcRef;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.event.RegionCrossedEvent;
import br.com.finalcraft.evernifecore.screen.ScreenCloseEvent;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The dialogues of one plugin, and every open session. Definitions live in the plugin's own files: {@link #register}
 * keeps them in memory until the plugin registers them again or shuts down ({@link #closeOwnedBy}, called by the core).
 * A player is in at most one session; starting another ends the first with {@link DialogueEndCause#REPLACED}.
 *
 * <p>The core listens only while it matters: to quits and screen closes while a session is open, to NPC uses while a
 * registered dialogue has an {@code npc:} trigger, to region crossings (holding {@link EventSource#MOVE}) while one has
 * a {@code region-enter:} trigger.</p>
 */
public final class Dialogues {

    private static final Map<String, Dialogues> OWNED = new ConcurrentHashMap<>();
    private static final Map<UUID, DialogueSession> SESSIONS = new ConcurrentHashMap<>();

    private static @Nullable ECEventSubscription<ECPlayerQuitEvent> quitWatch;
    private static @Nullable ECEventSubscription<ScreenCloseEvent> closeWatch;
    private static @Nullable ECEventSubscription<InteractNpcActionEvent.Post> npcWatch;
    private static @Nullable ECEventSubscription<RegionCrossedEvent> regionWatch;
    private static @Nullable UsageHold moveHold;

    private final ECPluginData owner;
    private final Map<String, Dialogue> dialogues = Collections.synchronizedMap(new LinkedHashMap<String, Dialogue>());

    private Dialogues(ECPluginData owner) {
        this.owner = owner;
    }

    /** The dialogues of {@code owner}, created on first use. */
    public static Dialogues of(ECPluginData owner) {
        return OWNED.computeIfAbsent(key(owner), name -> new Dialogues(owner));
    }

    public static @Nullable Dialogues ofIfPresent(ECPluginData owner) {
        return OWNED.get(key(owner));
    }

    /** Ends {@code owner}'s sessions with {@link DialogueEndCause#OWNER_DISABLED} and drops its dialogues; its shutdown. */
    public static void closeOwnedBy(ECPluginData owner) {
        Dialogues closed = OWNED.remove(key(owner));
        if (closed == null) {
            return;
        }
        for (Dialogue dialogue : closed.getAll()) {
            endSessionsOf(dialogue, DialogueEndCause.OWNER_DISABLED);
            LocaleOverlay overlay = LocaleOverlay.ofIfPresent(owner);
            if (overlay != null) {
                overlay.unregister(dialogue.getOverlayId());
            }
        }
        followTriggers();
    }

    public ECPluginData getOwner() {
        return owner;
    }

    /**
     * Keeps {@code dialogue} and puts its texts in the owner's {@link LocaleOverlay}. A dialogue of the same name is
     * replaced, and the sessions of the old one end with {@link DialogueEndCause#OWNER_DISABLED}.
     *
     * @throws IllegalArgumentException when the dialogue belongs to another plugin
     */
    public void register(Dialogue dialogue) {
        if (!key(dialogue.getOwner()).equals(key(owner))) {
            throw new IllegalArgumentException("The dialogue " + dialogue.getId() + " belongs to " + dialogue.getOwner().getMetaInfo().getName()
                    + ", so it registers in Dialogues.of(" + dialogue.getOwner().getMetaInfo().getName() + "), not in the dialogues of "
                    + owner.getMetaInfo().getName() + ".");
        }
        LocaleOverlay.of(owner).register(dialogue.getOverlayId(), dialogue.overlayTree(), "speaker", "text");
        Dialogue previous = dialogues.put(dialogue.getId().getName(), dialogue);
        if (previous != null) {
            endSessionsOf(previous, DialogueEndCause.OWNER_DISABLED);
        }
        followTriggers();
    }

    public Optional<Dialogue> find(String name) {
        return Optional.ofNullable(name == null ? null : dialogues.get(name.toLowerCase(Locale.ROOT)));
    }

    /** In registration order. */
    public List<Dialogue> getAll() {
        synchronized (dialogues) {
            return Collections.unmodifiableList(new ArrayList<>(dialogues.values()));
        }
    }

    /** The registered dialogue {@code id} names, of whichever plugin. */
    public static Optional<Dialogue> find(DialogueId id) {
        Dialogues registry = OWNED.get(id.getOwner());
        return registry == null ? Optional.<Dialogue>empty() : registry.find(id.getName());
    }

    /** Every registered dialogue, of every plugin. */
    public static List<Dialogue> all() {
        List<Dialogue> all = new ArrayList<>();
        for (Dialogues registry : OWNED.values()) {
            all.addAll(registry.getAll());
        }
        return all;
    }

    /** The session {@code player} is in; null when none is open. */
    public static @Nullable DialogueSession sessionOf(FPlayer player) {
        return SESSIONS.get(player.getUniqueId());
    }

    public static Collection<DialogueSession> getSessions() {
        return Collections.unmodifiableCollection(new ArrayList<>(SESSIONS.values()));
    }

    /**
     * Starts {@code dialogue} for {@code player} on behalf of {@code caller}; {@code attributes} fills the session's
     * {@link DialogueContext} (the NPC, another starting node...). Posts the cancellable {@link DialogueStartedEvent}:
     * cancelled, nothing changes and this answers null. Otherwise the session the player was in ends with
     * {@link DialogueEndCause#REPLACED} and the first node is shown. Thread: any; the screen opens on the renderer's.
     *
     * @throws IllegalStateException    when the dialogue is not the one registered under its id
     * @throws IllegalArgumentException when the starting node is not in the dialogue
     */
    public static @Nullable DialogueSession start(FPlayer player, Dialogue dialogue, Caller caller, @Nullable Consumer<DialogueContext> attributes) {
        if (find(dialogue.getId()).orElse(null) != dialogue) {
            throw new IllegalStateException("The dialogue " + dialogue.getId() + " is not registered (or another one was registered under"
                    + " its id since). Register it first - Dialogues.of(plugin).register(dialogue) - then start the instance registered.");
        }
        DialogueContext context = new DialogueContext();
        if (attributes != null) {
            attributes.accept(context);
        }
        context.freeze();
        String first = context.getNode() != null ? context.getNode() : dialogue.getEntryNode();
        DialogueNode node = dialogue.getNode(first);
        if (node == null) {
            throw new IllegalArgumentException("The dialogue " + dialogue.getId() + " has no node '" + first + "' to start on. Nodes defined: "
                    + String.join(", ", dialogue.getNodes().keySet()) + ".");
        }
        DialogueSession session = new DialogueSession(player, dialogue, caller, context, node);
        DialogueStartedEvent started = ECEventBus.global().postIfListened(DialogueStartedEvent.class, () -> new DialogueStartedEvent(session));
        if (started != null && started.isCancelled()) {
            return null;
        }
        DialogueSession previous;
        synchronized (Dialogues.class) {
            previous = SESSIONS.put(player.getUniqueId(), session);
            followSessions();
        }
        if (previous != null) {
            previous.end(DialogueEndCause.REPLACED);
        }
        session.open();
        return session;
    }

    /** {@code session} ended: it stops being the player's. */
    static void forget(DialogueSession session) {
        synchronized (Dialogues.class) {
            SESSIONS.remove(session.getPlayer().getUniqueId(), session);
            followSessions();
        }
    }

    private static void endSessionsOf(Dialogue dialogue, DialogueEndCause cause) {
        for (DialogueSession session : getSessions()) {
            if (session.getDialogue() == dialogue) {
                session.end(cause);
            }
        }
    }

    private static String key(ECPluginData owner) {
        return owner.getMetaInfo().getName().toLowerCase(Locale.ROOT);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Listening only while it matters
    // -----------------------------------------------------------------------------------------------------------------

    private static void followSessions() {
        if (!SESSIONS.isEmpty() && quitWatch == null) {
            quitWatch = ECEventBus.global().subscribe(ECPlayerQuitEvent.class, event -> {
                DialogueSession session = SESSIONS.get(event.getPlayerData().getUniqueId());
                if (session != null) {
                    session.end(DialogueEndCause.PLAYER_LEFT);
                }
            });
            closeWatch = ECEventBus.global().subscribe(ScreenCloseEvent.class, Dialogues::onScreenClosed);
        } else if (SESSIONS.isEmpty() && quitWatch != null) {
            quitWatch.unsubscribe();
            quitWatch = null;
            if (closeWatch != null) {
                closeWatch.unsubscribe();
                closeWatch = null;
            }
        }
    }

    //the session's own screen went away; a screen it replaced itself, node after node, is not its current one
    private static void onScreenClosed(ScreenCloseEvent event) {
        DialogueSession session = SESSIONS.get(event.getView().getViewerId());
        if (session != null && session.getScreen() == event.getView().getScreen()) {
            session.end(DialogueEndCause.CLOSED);
        }
    }

    private static synchronized void followTriggers() {
        boolean npcs = false;
        boolean regions = false;
        for (Dialogues registry : OWNED.values()) {
            for (Dialogue dialogue : registry.getAll()) {
                for (DialogueTrigger trigger : dialogue.getTriggers()) {
                    npcs |= trigger.getNpc() != null;
                    regions |= trigger.getRegion() != null;
                }
            }
        }
        if (npcs && npcWatch == null) {
            //listening to the event is what holds interact-npc needed: the providers raise it only while someone hears it
            npcWatch = ECEventBus.global().subscribe(InteractNpcActionEvent.Post.class, Dialogues::onNpcUsed);
        } else if (!npcs && npcWatch != null) {
            npcWatch.unsubscribe();
            npcWatch = null;
        }
        if (regions && regionWatch == null) {
            moveHold = EventSourceUsage.get().acquire(EventSource.MOVE);
            regionWatch = ECEventBus.global().subscribe(RegionCrossedEvent.class, Dialogues::onRegionsCrossed);
        } else if (!regions && regionWatch != null) {
            regionWatch.unsubscribe();
            regionWatch = null;
            if (moveHold != null) {
                moveHold.release();
                moveHold = null;
            }
        }
    }

    private static void onNpcUsed(InteractNpcActionEvent.Post event) {
        FPlayer player = event.getPlayer();
        NpcRef npc = event.getContext().getEntity().getNpc();
        if (player == null || npc == null || SESSIONS.containsKey(player.getUniqueId())) {
            return;
        }
        for (Dialogues registry : OWNED.values()) {
            for (Dialogue dialogue : registry.getAll()) {
                if (dialogue.getTriggers().contains(DialogueTrigger.npc(npc))) {
                    start(player, dialogue, Caller.of(registry.owner, "npc:" + npc), context -> context.npc(npc));
                    return;
                }
            }
        }
    }

    private static void onRegionsCrossed(RegionCrossedEvent event) {
        FPlayer player = event.getPlayer();
        if (SESSIONS.containsKey(player.getUniqueId())) {
            return;
        }
        for (Region region : event.getEntered()) {
            DialogueTrigger trigger = DialogueTrigger.regionEnter(region.getId());
            for (Dialogues registry : OWNED.values()) {
                for (Dialogue dialogue : registry.getAll()) {
                    if (dialogue.getTriggers().contains(trigger)) {
                        start(player, dialogue, Caller.of(registry.owner, "region-enter:" + region.getId()), null);
                        return;
                    }
                }
            }
        }
    }
}
