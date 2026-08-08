package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.chat.ChatExpectationListener;
import br.com.finalcraft.evernifecore.minecraft.chat.ExpectedChat;
import br.com.finalcraft.evernifecore.minecraft.chat.IChatAction;
import org.bukkit.entity.Player;

public class FCChatUtil {

    /**
     * Expect a message from this player, for as long as it takes.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, 0, null, null);
    }

    /**
     * Expect a message from this player.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration Milliseconds to wait, or {@code 0} to wait indefinitely.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction, long expiration) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, expiration, null, null);
    }

    /**
     * Expect a message from this player.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration Milliseconds to wait, or {@code 0} to wait indefinitely - which leaves
     *                   onExpireAction unused, as there is no end for it to run at.
     * @param onExpireAction The action to perform when the chat expires.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction, long expiration, Runnable onExpireAction) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, expiration, onExpireAction, null);
    }

    /**
     * Expect a message from this player.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration Milliseconds to wait, or {@code 0} to wait indefinitely - which leaves
     *                   onExpireAction unused, as there is no end for it to run at.
     * @param onExpireAction The action to perform when the chat expires.
     * @param onPlayerQuitAction The action to perform when the player quits.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction, long expiration, Runnable onExpireAction, Runnable onPlayerQuitAction) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, expiration, onExpireAction, onPlayerQuitAction);
    }

}
