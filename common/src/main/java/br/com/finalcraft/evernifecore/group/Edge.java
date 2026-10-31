package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.notify.NotifyKind;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The player's way into or out of a group ({@link Group#join}, {@link Group#leave}): an optional {@link Gate} paid on
 * the way through, and notices sent once the player crossed. Code, never stored: the owner sets it on every enable.
 * Immutable: every call returns a copy.
 *
 * <pre>{@code
 * Edge.gate(Gate.of(...)).notify(EdgeAudience.MEMBERS, "&a{player} joined {group}")
 * }</pre>
 */
public final class Edge {

    /** One notice: who gets it and the text, resolved by {@code ExecutionContext.resolve} ({@code {player}}, {@code {group}}, %placeholders%). */
    public static final class Notice {
        private final EdgeAudience audience;
        private final String text;

        Notice(EdgeAudience audience, String text) {
            this.audience = audience;
            this.text = text;
        }

        public EdgeAudience getAudience() {
            return audience;
        }

        public String getText() {
            return text;
        }
    }

    private static final Edge SILENT = new Edge(null, Collections.<Notice>emptyList(), NotifyKind.INFO);

    private final @Nullable Gate gate;
    private final List<Notice> notices;
    private final NotifyKind kind;

    private Edge(@Nullable Gate gate, List<Notice> notices, NotifyKind kind) {
        this.gate = gate;
        this.notices = notices;
        this.kind = kind;
    }

    /** No gate, no notice. */
    public static Edge silent() {
        return SILENT;
    }

    /** A gate paid on the way through: a denial keeps the player where they were and explains why. */
    public static Edge gate(Gate gate) {
        return new Edge(Objects.requireNonNull(gate, "gate"), Collections.<Notice>emptyList(), NotifyKind.INFO);
    }

    /** This edge plus a notice to {@code to} once a player crossed it. */
    public Edge notify(EdgeAudience to, String text) {
        List<Notice> more = new ArrayList<>(notices);
        more.add(new Notice(Objects.requireNonNull(to, "to"), Objects.requireNonNull(text, "text")));
        return new Edge(gate, Collections.unmodifiableList(more), kind);
    }

    /** The kind of the notices, which picks their notify chain; {@link NotifyKind#INFO} by default (chat). */
    public Edge via(NotifyKind kind) {
        return new Edge(gate, notices, Objects.requireNonNull(kind, "kind"));
    }

    public @Nullable Gate getGate() {
        return gate;
    }

    public List<Notice> getNotices() {
        return notices;
    }

    public NotifyKind getKind() {
        return kind;
    }
}
