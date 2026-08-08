package br.com.finalcraft.evernifecore.minecraft.chat;

import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Routes chat messages to whoever asked to be notified about the next thing a player types.
 *
 * <p>Registered on first use rather than at startup, so a server that never waits for chat never
 * pays for a chat listener.
 */
public class ChatExpectationListener implements Listener {

    private static ChatExpectationListener instance;

    public static ChatExpectationListener get() {
        if (instance == null) {
            instance = new ChatExpectationListener();
        }
        return instance;
    }

    private final Multimap<UUID, ExpectedChat> chatListeners = Multimaps.synchronizedMultimap(HashMultimap.create());

    private ChatExpectationListener() {
        Bukkit.getPluginManager().registerEvents(this, EverNifeCoreBukkitPlugin.instance);
    }

    public Multimap<UUID, ExpectedChat> getChatListeners() {
        return chatListeners;
    }

    public boolean hasAnyExpectedChat(Player player) {
        return !chatListeners.get(player.getUniqueId()).isEmpty();
    }

    /**
     * @param expiration         milliseconds to wait, or {@code 0} to wait indefinitely - a wait with
     *                           no end has nothing to schedule, so {@code onExpireAction} never runs.
     * @param onExpireAction     run once the wait elapses, if it does. Also what makes the
     *                           expiration schedulable at all: without it nothing is scheduled, and
     *                           the expectation is dropped lazily instead - the next time that
     *                           player chats, and never if they do not.
     * @param onPlayerQuitAction run if the player leaves while still being waited on.
     */
    public ExpectedChat expectPlayerChat(Player player, IChatAction chatAction, long expiration, @Nullable Runnable onExpireAction, @Nullable Runnable onPlayerQuitAction) {
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("Cannot wait for chat for a null/offline player.");
        }
        if (chatAction == null) {
            throw new IllegalArgumentException("Cannot call null function.");
        }

        AtomicReference<ScheduledFuture<?>> possibleFuture = new AtomicReference<>();

        ExpectedChat expectedChat = new ExpectedChat(player, chatAction, expiration, onExpireAction, onPlayerQuitAction, possibleFuture);

        //a delay of zero fires on the same tick, ending the very wait it was meant to bound
        if (expiration > 0 && onExpireAction != null) {
            ScheduledFuture<?> future = FCScheduler.getScheduler().schedule(() -> {
                if (expectedChat.wasConsumed() || expectedChat.wasCancelled()) {
                    return;
                }

                expectedChat.getOnExpireAction().run();
            }, expiration, TimeUnit.MILLISECONDS);
            possibleFuture.set(future);
        }

        chatListeners.put(player.getUniqueId(), expectedChat);

        return expectedChat;
    }

    /**
     * Calls {@code expectedChat} off and stops routing to it right away.
     *
     * <p>{@link ExpectedChat#cancel()} on its own only marks it: it stays registered until the next
     * message that player sends sweeps it, and until then it is still offered every message - so a
     * caller that has finished with a wait unregisters it here.</p>
     */
    public void stopExpecting(ExpectedChat expectedChat) {
        if (expectedChat == null) {
            return;
        }

        expectedChat.cancel();
        chatListeners.remove(expectedChat.getPlayer().getUniqueId(), expectedChat);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();

        Collection<ExpectedChat> expectations = new ArrayList<>(chatListeners.get(player.getUniqueId()));

        for (ExpectedChat expectedChat : expectations) {

            if (expectedChat.wasCancelled() || expectedChat.wasConsumed() || expectedChat.hasExpired()) {
                chatListeners.remove(player.getUniqueId(), expectedChat);
                continue;
            }

            IChatAction.ActionResult actionResult = expectedChat.getChatAction().onChat(e.getMessage());

            switch (actionResult) {
                case SUCCESS_AND_CONSUME:
                    settle(player, expectedChat);
                    e.setCancelled(true);
                    return;   //the message is gone; nobody else gets a look at it

                case SUCCESS:
                    settle(player, expectedChat);
                    continue;

                case CONSUME_AND_CONTINUE:
                    e.setCancelled(true);
                    return;   //the message was claimed by a wait that is still on; nobody else sees it

                case IGNORE_CURRENT_MESSAGE:
                    //still waiting, maybe the next message is the one
                    continue;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Collection<ExpectedChat> expectedChats = chatListeners.removeAll(e.getPlayer().getUniqueId());

        for (ExpectedChat expectedChat : expectedChats) {
            expectedChat.cancel();

            if (expectedChat.getOnPlayerQuitAction() != null) {
                expectedChat.getOnPlayerQuitAction().run();
            }
        }
    }

    private void settle(Player player, ExpectedChat expectedChat) {
        chatListeners.remove(player.getUniqueId(), expectedChat);
        expectedChat.setConsumed(true);

        if (expectedChat.getFuture().get() != null) {
            expectedChat.getFuture().get().cancel(false);
        }
    }

}
