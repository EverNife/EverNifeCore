package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Throwaway conditions, costs and effects that record what was done to them. */
final class LogicFixtures {

    private static final ObjectMapper JSON = new ObjectMapper();

    private LogicFixtures() {
    }

    static JsonNode node(String singleQuotedJson) {
        try {
            return JSON.readTree(singleQuotedJson.replace('\'', '"'));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** A condition that passes or fails by a fixed answer, with the needs it is given. */
    static Condition fixed(String name, boolean passes, EventSource... needs) {
        return new Condition() {
            @Override
            public ConditionResult test(ExecutionContext ctx) {
                return ConditionResult.of(passes, FancyText.of(name + " is not met"));
            }

            @Override
            public Set<EventSource> getNeeds() {
                return needs.length == 0 ? Collections.<EventSource>emptySet() : new LinkedHashSet<>(Arrays.asList(needs));
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    /** A cost that writes "take X", "refund X" into {@code journal}; take answers {@code takes}, refund throws when told. */
    static final class JournalCost implements Cost {
        final String name;
        final List<String> journal;
        boolean affordable = true;
        boolean takes = true;
        boolean refundThrows;

        JournalCost(String name, List<String> journal) {
            this.name = name;
            this.journal = journal;
        }

        @Override
        public ConditionResult test(ExecutionContext ctx) {
            return ConditionResult.of(affordable, FancyText.of("cannot afford " + name));
        }

        @Override
        public boolean take(ExecutionContext ctx) {
            journal.add("take " + name);
            return takes;
        }

        @Override
        public void refund(ExecutionContext ctx) {
            journal.add("refund " + name);
            if (refundThrows) {
                throw new IllegalStateException("the bank is closed");
            }
        }

        @Override
        public FancyText describe(ExecutionContext ctx) {
            return FancyText.of("1 " + name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** An effect that records the player name (or "-") each time it applies, optionally only while a predicate holds. */
    static final class RecordingEffect implements Effect {
        final String name;
        final List<String> applied = new CopyOnWriteArrayList<>();
        final List<Thread> threads = new CopyOnWriteArrayList<>();
        Predicate<ExecutionContext> failsWhen = ctx -> false;
        boolean holdable;
        boolean sender;

        RecordingEffect(String name) {
            this.name = name;
        }

        @Override
        public void apply(ExecutionContext ctx) {
            if (failsWhen.test(ctx)) {
                throw new IllegalStateException(name + " broke");
            }
            applied.add(ctx.getPlayer() == null ? "-" : ctx.getPlayer().getName());
            threads.add(Thread.currentThread());
        }

        @Override
        public boolean isHoldable() {
            return holdable;
        }

        @Override
        public boolean acceptsSender() {
            return sender;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
