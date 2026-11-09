package br.com.finalcraft.evernifecore.player.state;

import java.util.Objects;

/**
 * How a holder holds a slot of {@link HeldPlayerState}: whether what the player had also lands in their
 * {@link PlayerStateBackup}, to survive a server stop, and what the holder does when the player quits ({@link QuitPolicy},
 * {@link QuitPolicy#giveBack()} unless told otherwise).
 */
public final class HoldOptions {

    private static final HoldOptions MEMORY = new HoldOptions(false, QuitPolicy.giveBack());
    private static final HoldOptions PERSISTENT = new HoldOptions(true, QuitPolicy.giveBack());

    private final boolean persistent;
    private final QuitPolicy quitPolicy;

    private HoldOptions(boolean persistent, QuitPolicy quitPolicy) {
        this.persistent = persistent;
        this.quitPolicy = quitPolicy;
    }

    /** What the player had stays in memory: a server stop mid-hold loses it, except an inventory whose give-back was refused. */
    public static HoldOptions memory() {
        return MEMORY;
    }

    /** What the player had also lands in their data, and the next join gives it back if nothing else did. */
    public static HoldOptions persistent() {
        return PERSISTENT;
    }

    /** These options with {@code policy} on the quit. A {@link QuitPolicy#keepFor window} needs {@link #persistent()}. */
    public HoldOptions onQuit(QuitPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (policy.getWindow() != null && !persistent) {
            throw new IllegalArgumentException("A reconnection window of " + policy.getWindow() + " cannot be kept in memory: the next"
                    + " join gives back what the player's data holds. Use HoldOptions.persistent().onQuit(...), or"
                    + " QuitPolicy.delegate() to decide on the quit.");
        }
        return new HoldOptions(persistent, policy);
    }

    public boolean isPersistent() {
        return persistent;
    }

    public QuitPolicy getQuitPolicy() {
        return quitPolicy;
    }
}
