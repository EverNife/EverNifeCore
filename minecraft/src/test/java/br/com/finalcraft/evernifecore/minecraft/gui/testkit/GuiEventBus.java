package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import br.com.finalcraft.evernifecore.minecraft.chat.ChatExpectationListener;
import br.com.finalcraft.evernifecore.minecraft.chat.ExpectedChat;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiListener;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Delivers a platform event straight to the framework's own listeners.
 *
 * <p>There is no plugin manager doing the routing on purpose: what is under test is what a listener
 * does with an event, not whether Bukkit can deliver one.</p>
 *
 * <p>Chat is the exception to "just call the method": a real server raises
 * {@code AsyncPlayerChatEvent} off the main thread, and code that answers it has to hop back before
 * it may draw anything. {@link #typeInChat(Player, String)} therefore delivers from a thread of its
 * own, so that hop is exercised rather than assumed.</p>
 */
public final class GuiEventBus {

    private final GuiListener listener = new GuiListener();

    public GuiListener getListener() {
        return listener;
    }

    public void fireOpen(InventoryView view) {
        listener.onInventoryOpen(new InventoryOpenEvent(view));
    }

    public void fireClose(InventoryView view) {
        listener.onInventoryClose(new InventoryCloseEvent(view));
    }

    /** Both listeners a leaving player concerns, in the order a server registered them. */
    public void fireQuit(Player player) {
        PlayerQuitEvent event = new PlayerQuitEvent(player, "");
        listener.onPlayerQuit(event);
        ChatExpectationListener.get().onPlayerQuit(event);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Chat, which never arrives on the main thread
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * The player types {@code message}, delivered off the main thread to whoever was waiting for it.
     *
     * @return the event, so a test can read whether the message was swallowed or reached public chat
     */
    public AsyncPlayerChatEvent typeInChat(Player player, String message) {
        AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(true, player, message, new HashSet<Player>());
        offTheMainThread(() -> ChatExpectationListener.get().onPlayerChat(event));
        return event;
    }

    /**
     * Runs out the clock on what {@code player} is being waited for, the way the expiration the core
     * scheduled does: off the main thread, and only while the wait has not already settled.
     *
     * @return whether anything was still being waited for - a timeout nobody was waiting on proves
     *         nothing about what a timeout does
     */
    public boolean expireChatWait(Player player) {
        List<ExpectedChat> waiting = new ArrayList<>(
                ChatExpectationListener.get().getChatListeners().get(player.getUniqueId()));
        for (ExpectedChat expectation : waiting) {
            if (expectation.wasConsumed() || expectation.wasCancelled()
                    || expectation.getOnExpireAction() == null) {
                continue;
            }
            offTheMainThread(expectation.getOnExpireAction());
            return true;
        }
        return false;
    }

    /**
     * Runs {@code body} on a thread that is not the main one and waits for it, so whatever it handed
     * to the main thread is already queued by the time the caller moves the clock.
     */
    public void offTheMainThread(Runnable body) {
        Thread thread = new Thread(body, "gui-test-off-main");
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the off-main work to finish", interrupted);
        }
    }

}
