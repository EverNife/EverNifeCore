package br.com.finalcraft.evernifecore.api.common.player;

import br.com.finalcraft.evernifecore.api.common.game.FLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BaseFPlayerEqualsTest {

    /**
     * Minimal concrete BaseFPlayer whose only meaningful state is the delegate; the FPlayer
     * methods unrelated to identity are left unsupported on purpose.
     */
    static class TestFPlayer extends BaseFPlayer<String> {
        TestFPlayer(String delegate) {
            super(delegate);
        }

        @Override public String getName() { return getDelegate(); }
        @Override public java.util.UUID getUniqueId() { throw new UnsupportedOperationException(); }
        @Override public void sendMessage(net.kyori.adventure.text.Component component) { }
        @Override public boolean hasPermission(String permission) { return false; }
        @Override public boolean isOnline() { return false; }
        @Override public FLocation getLocation() { return null; }
        @Override public boolean teleportTo(FLocation targetLocation) { return false; }
    }

    @Test
    void sameDelegateIsEqualAndSameHashCode() {
        String delegate = "player-a";
        TestFPlayer one = new TestFPlayer(delegate);
        TestFPlayer two = new TestFPlayer(delegate);

        assertEquals(one, two);
        assertEquals(one.hashCode(), two.hashCode());
    }

    @Test
    void differentDelegatesAreNotEqual() {
        TestFPlayer one = new TestFPlayer("player-a");
        TestFPlayer two = new TestFPlayer("player-b");

        assertNotEquals(one, two);
    }
}
