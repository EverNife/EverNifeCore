package br.com.finalcraft.evernifecore.dialogue;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.event.ActionEvent;
import br.com.finalcraft.evernifecore.action.event.ActionEvents;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.dialogue.event.DialogueChoiceEvent;
import br.com.finalcraft.evernifecore.dialogue.event.DialogueEndedEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.CustomVariables;
import br.com.finalcraft.evernifecore.logic.Effect;
import br.com.finalcraft.evernifecore.logic.Effects;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenKey;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenRequest;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.time.ECClock;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * One player inside one dialogue, in memory only - a reboot ends every session. Shows each node on whatever answers
 * {@link ScreenKey#DIALOGUE}, waits {@link Dialogue#getTimeout()} for an answer (re-armed by every pick), asks the
 * {@code fail-when} conditions when one of their sources fires, and ends once, with the first {@link DialogueEndCause}
 * that reaches it. Started by {@link Dialogues#start}.
 */
public final class DialogueSession {

    private final FPlayer player;
    private final Dialogue dialogue;
    private final Caller caller;
    private final DialogueContext context;
    private final Instant startedAt;
    private final List<ECEventSubscription<?>> failWhenWatches = new CopyOnWriteArrayList<>();
    private volatile DialogueNode node;
    private volatile @Nullable DialogueEndCause endCause;
    private volatile @Nullable Screen<?> screen;
    private long timeoutGeneration;

    DialogueSession(FPlayer player, Dialogue dialogue, Caller caller, DialogueContext context, DialogueNode first) {
        this.player = Objects.requireNonNull(player, "player");
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.caller = Objects.requireNonNull(caller, "caller");
        this.context = context;
        this.node = first;
        this.startedAt = ECClock.now();
    }

    public FPlayer getPlayer() {
        return player;
    }

    public Dialogue getDialogue() {
        return dialogue;
    }

    /** Who started it: the plugin and the origin ({@code npc:citizens:12}, {@code region-enter:...}, an effect's caller). */
    public Caller getCaller() {
        return caller;
    }

    /** The node the player is on now. */
    public DialogueNode getNode() {
        return node;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public DialogueContext getContext() {
        return context;
    }

    /** Null while the session is open. */
    public @Nullable DialogueEndCause getEndCause() {
        return endCause;
    }

    public boolean isOpen() {
        return endCause == null;
    }

    /** The screen showing the current node, as {@link ScreenKey#DIALOGUE} answered it; null before the first one is built. */
    public @Nullable Screen<?> getScreen() {
        return screen;
    }

    /** A context for this player under the session's caller, carrying every attribute of {@link #getContext()} and the session. */
    public ExecutionContext.Builder newExecutionContext() {
        ExecutionContext.Builder builder = ExecutionContext.builder(caller).player(player);
        context.getAttributes().forEach(builder::attribute);
        return builder.attribute(DialogueContext.SESSION, this);
    }

    /** Opens on the first node: called once by {@link Dialogues#start} after the start was accepted. */
    void open() {
        watchFailWhen();
        armTimeout();
        enter(node);
    }

    /**
     * The player picks {@code optionId} on the current node: a {@link DialogueChoiceEvent} that may cancel it, the
     * option's gate paid, the answer {@link DialogueNode#getRemember() remembered}, the effects run, then the talk moves
     * on - to the {@code goto}, to the end, or stays. A cancelled or denied pick charges nothing and leaves the node as it
     * was. Thread: the renderer's click thread (effects dispatch as {@link Effects#apply} decides).
     */
    public ChoiceResult choose(String optionId) {
        DialogueNode current = node;
        if (!isOpen()) {
            return new ChoiceResult(ChoiceResult.Outcome.CLOSED, null);
        }
        DialogueOption option = current.getOption(optionId);
        if (option == null) {
            return new ChoiceResult(ChoiceResult.Outcome.UNKNOWN_OPTION, null);
        }
        //a condition with no source of its own is asked here, at the player's pace, instead of on a timer
        if (fails(condition -> condition.getNeeds().isEmpty())) {
            end(DialogueEndCause.FAIL_WHEN);
            return new ChoiceResult(ChoiceResult.Outcome.CLOSED, null);
        }
        DialogueChoiceEvent event = ECEventBus.global().postIfListened(DialogueChoiceEvent.class, () -> new DialogueChoiceEvent(this, current, option));
        if (event != null && event.isCancelled()) {
            refresh();
            return new ChoiceResult(ChoiceResult.Outcome.CANCELLED, null);
        }
        ExecutionContext ctx = newExecutionContext().build();
        GateResult paid = option.getGate().pay(ctx);
        if (!paid.isPassed()) {
            refresh();
            return new ChoiceResult(ChoiceResult.Outcome.DENIED, paid);
        }
        remember(current, option, ctx);
        for (Effect effect : option.getEffects()) {
            Effects.apply(ctx, effect);
        }
        if (!isOpen() || node != current) {
            return new ChoiceResult(ChoiceResult.Outcome.CHOSEN, paid); //an effect ended or moved the talk itself
        }
        armTimeout();
        if (option.isEnd()) {
            end(DialogueEndCause.END);
        } else if (option.getGoto() != null) {
            enter(dialogue.getNode(option.getGoto()));
        } else {
            refresh();
        }
        return new ChoiceResult(ChoiceResult.Outcome.CHOSEN, paid);
    }

    /**
     * Ends the session with {@code cause}. Idempotent: the first cause wins and posts one {@link DialogueEndedEvent};
     * later calls do nothing. The screen goes too, unless the player is reading the last line of a terminal node, or the
     * window already went. Thread: any.
     */
    public void end(DialogueEndCause cause) {
        synchronized (this) {
            if (endCause != null) {
                return;
            }
            endCause = Objects.requireNonNull(cause, "cause");
            timeoutGeneration++;
        }
        failWhenWatches.forEach(ECEventSubscription::unsubscribe);
        failWhenWatches.clear();
        Dialogues.forget(this);
        boolean readingLastLine = cause == DialogueEndCause.END && node.isTerminal();
        if (cause != DialogueEndCause.CLOSED && cause != DialogueEndCause.REPLACED && cause != DialogueEndCause.PLAYER_LEFT && !readingLastLine) {
            ScreenView view = currentView();
            if (view != null) {
                view.closeNextTick();
            }
        }
        ECEventBus.global().postIfListened(DialogueEndedEvent.class, () -> new DialogueEndedEvent(this, cause));
    }

    /** Draws the current node again - after a state the options read changed. */
    public void refresh() {
        ScreenView view = currentView();
        if (view != null) {
            view.refresh();
        }
    }

    /** The open view of the player when it shows this session's current node. */
    @Nullable ScreenView currentView() {
        ScreenView view = Screens.getOpenView(player);
        Screen<?> shown = screen;
        return view != null && shown != null && view.getScreen() == shown && !view.isClosed() ? view : null;
    }

    private void enter(DialogueNode next) {
        node = next;
        if (!next.getEffectsOnEnter().isEmpty()) {
            ExecutionContext ctx = newExecutionContext().build();
            for (Effect effect : next.getEffectsOnEnter()) {
                Effects.apply(ctx, effect);
            }
            if (!isOpen() || node != next) {
                return;
            }
        }
        present();
        if (next.isTerminal()) {
            end(DialogueEndCause.END);
        }
    }

    private void present() {
        Screen<?> next;
        try {
            next = Screens.build(player, ScreenKey.DIALOGUE, ScreenRequest.of(dialogue.getOwner()).payload(this));
        } catch (RuntimeException refused) {
            EverNifeCore.getLog().warning("The dialogue {} could not be shown to {}: {}", dialogue.getId(), player.getName(), refused.getMessage());
            end(DialogueEndCause.CLOSED);
            return;
        }
        ScreenView showing = currentView();
        screen = next;
        if (showing != null) {
            ScreenNavigation.replace(showing, next);
            return;
        }
        Screens.open(player, next).whenComplete((view, refused) -> {
            if (refused != null && screen == next) {
                end(DialogueEndCause.CLOSED); //nobody can see it: the open already logged why
            }
        });
    }

    private void remember(DialogueNode current, DialogueOption option, ExecutionContext ctx) {
        if (current.getRemember() == null) {
            return;
        }
        CustomVariables.CustomVariable<?> variable = CustomVariables.find(current.getRemember()).orElse(null);
        if (variable != null && variable.getType() == String.class) {
            @SuppressWarnings("unchecked")
            CustomVariables.CustomVariable<String> text = (CustomVariables.CustomVariable<String>) variable;
            text.set(ctx, option.getId());
        } else {
            //validated at build: the plugin that defined it closed since
            EverNifeCore.getLog().warning("The dialogue {} could not remember '{}' in {}: that variable is no longer defined.",
                    dialogue.getId(), option.getId(), current.getRemember());
        }
    }

    private void armTimeout() {
        Duration timeout = dialogue.getTimeout();
        if (timeout.isZero()) {
            return;
        }
        long generation;
        synchronized (this) {
            generation = ++timeoutGeneration;
        }
        ECScheduler.scheduleAsync(() -> {
            synchronized (this) {
                if (generation != timeoutGeneration) {
                    return;
                }
            }
            end(DialogueEndCause.TIMEOUT);
        }, timeout.toMillis());
    }

    private void watchFailWhen() {
        Set<EventSource> sources = new LinkedHashSet<>();
        for (Condition condition : dialogue.getFailWhen()) {
            sources.addAll(condition.getNeeds());
        }
        for (EventSource source : sources) {
            ActionEvents.Pair pair = ActionEvents.of(source);
            if (pair != null) {
                //listening holds the source needed, so the platform raises it while the session lives
                failWhenWatches.add(ECEventBus.global().subscribe(pair.getPost(), this::onSource));
            }
        }
    }

    private void onSource(ActionEvent<?> event) {
        FPlayer actor = event.getPlayer();
        if (actor == null || !actor.getUniqueId().equals(player.getUniqueId()) || !isOpen()) {
            return;
        }
        if (fails(condition -> condition.getNeeds().contains(event.getSource()))) {
            end(DialogueEndCause.FAIL_WHEN);
        }
    }

    private boolean fails(Predicate<Condition> asked) {
        ExecutionContext ctx = null;
        for (Condition condition : dialogue.getFailWhen()) {
            if (!asked.test(condition)) {
                continue;
            }
            if (ctx == null) {
                ctx = newExecutionContext().build();
            }
            ConditionResult result = Conditions.test(condition, ctx);
            if (!result.isPassed()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "DialogueSession{" + player.getName() + " in " + dialogue.getId() + " at " + node.getId() + (isOpen() ? "" : ", " + endCause) + "}";
    }

    /** What a pick did. */
    public static final class ChoiceResult {

        public enum Outcome {
            /** Paid, effects run, the talk moved on. */
            CHOSEN,
            /** The gate did not pass: nothing was charged, {@link #getGateResult()} holds every failure. */
            DENIED,
            /** A {@link DialogueChoiceEvent} listener cancelled it. */
            CANCELLED,
            /** The current node has no option with that id. */
            UNKNOWN_OPTION,
            /** The session is no longer open (or a {@code fail-when} ended it now). */
            CLOSED
        }

        private final Outcome outcome;
        private final @Nullable GateResult gateResult;

        ChoiceResult(Outcome outcome, @Nullable GateResult gateResult) {
            this.outcome = outcome;
            this.gateResult = gateResult;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public boolean isChosen() {
            return outcome == Outcome.CHOSEN;
        }

        /** The gate's answer for {@link Outcome#CHOSEN} and {@link Outcome#DENIED}; null otherwise. */
        public @Nullable GateResult getGateResult() {
            return gateResult;
        }

        /** Every failure of a denied pick; empty otherwise. */
        public List<ConditionResult> getFailures() {
            return gateResult == null || gateResult.isPassed() ? Collections.<ConditionResult>emptyList() : gateResult.getFailures();
        }

        @Override
        public String toString() {
            return outcome + (getFailures().isEmpty() ? "" : " " + getFailures());
        }
    }
}
