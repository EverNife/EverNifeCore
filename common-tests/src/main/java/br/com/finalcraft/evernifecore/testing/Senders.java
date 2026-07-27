package br.com.finalcraft.evernifecore.testing;

import java.util.UUID;

/**
 * The two kinds of sender a command test needs: a console (no uuid, so {@code isPlayer()} is
 * false) and a player. Both capture every message they receive as legacy-formatted text, so a test
 * asserts on the exact string a command produced without a chat renderer.
 */
public final class Senders {

    private Senders() {
    }

    /** A console-like sender named {@code "Console"}. */
    public static TestCommandSender console() {
        return new TestCommandSender("Console");
    }

    public static TestCommandSender console(String name) {
        return new TestCommandSender(name);
    }

    /** A player with a random uuid. */
    public static TestFPlayerSender player(String name) {
        return new TestFPlayerSender(name);
    }

    public static TestFPlayerSender player(String name, UUID uniqueId) {
        return new TestFPlayerSender(name, uniqueId);
    }
}
