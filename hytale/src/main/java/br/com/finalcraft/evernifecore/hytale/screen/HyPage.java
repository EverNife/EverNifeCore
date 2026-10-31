package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.screen.CloseReason;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import jakarta.annotation.Nullable;

/**
 * A page the core opens through the player's {@code PageManager}. The server calls {@code build},
 * {@code handleDataEvent} and {@code onDismiss} on the player's world thread; so does everything here.
 *
 * <p>{@code onDismiss} is not only the escape key: the server calls it on every teardown - a page opened over this one,
 * {@code close()}, a reset. {@link #dismissedAs} is how a page tells those apart.</p>
 */
abstract class HyPage extends InteractiveCustomUIPage<HyPageEvent> {

    /** Why the next {@code onDismiss} happens, when the core caused it; {@code null} means the player did. */
    private volatile CloseReason closingReason;

    HyPage(@Nullable PlayerRef playerRef, CustomPageLifetime lifetime) {
        super(playerRef, lifetime, HyPageEvent.CODEC);
    }

    /** Sends {@code ui} as an update of what is on screen, keeping the rest. World thread. */
    void push(UICommandBuilder ui) {
        sendUpdate(ui, false);
    }

    /** Takes the page off the player's screen; the server answers with {@code onDismiss}. World thread. */
    void closeNow() {
        close();
    }

    /** Records that the core, not the player, is about to dismiss this page, and why. Any thread. */
    void markClosing(CloseReason reason) {
        this.closingReason = reason;
    }

    /** What the core said when it closed this page, or {@code null} when the player dismissed it. */
    @Nullable
    CloseReason dismissedAs() {
        return closingReason;
    }

}
