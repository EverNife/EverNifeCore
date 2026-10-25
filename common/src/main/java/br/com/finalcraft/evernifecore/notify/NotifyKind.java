package br.com.finalcraft.evernifecore.notify;

import java.util.Locale;

/**
 * What a notification is about. The kind picks the chain of channels a {@link Notification} without its own goes
 * through ({@code Settings.Notify.chains.<kind>}) and how long it stays ({@code Settings.Notify.durations.<kind>}).
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

    /** The lowercase name the settings keys use. */
    public String getKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
