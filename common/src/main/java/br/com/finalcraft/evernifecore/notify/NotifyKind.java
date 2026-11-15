package br.com.finalcraft.evernifecore.notify;

import java.util.Locale;

/**
 * What a notification is about. The kind picks, in the sender's {@link NotifyProfile}, the chain of channels a
 * {@link Notification} without its own goes through and how long it stays.
 */
public enum NotifyKind {
    /** A step forward: a counter moved, an objective advanced. */
    PROGRESS,
    /** Something finished: a quest, a goal, a level. */
    COMPLETE,
    /** The state a player keeps an eye on (the tracked quest); stays until cleared. */
    TRACKED,
    /** A plain message. */
    INFO,
    /** An action was refused. */
    DENY;

    /** The lowercase name the {@code notify} keys of a plugin's config use. */
    public String getKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
