package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.minecraft.chat.ChatExpectationListener;
import br.com.finalcraft.evernifecore.minecraft.chat.ExpectedChat;
import br.com.finalcraft.evernifecore.minecraft.chat.IChatAction;
import org.bukkit.entity.Player;

public class FCChatUtil {

    /**
     * Expect a player's to chat a message.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, 0, null, null);
    }

    /**
     * Expect a player's to chat a message.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration The time in milliseconds the wait for the chat.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction, long expiration) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, expiration, null, null);
    }

    /**
     * Expect a player's to chat a message.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration The time in milliseconds the wait for the chat.
     * @param onExpireAction The action to perform when the chat expires.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction, long expiration, Runnable onExpireAction) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, expiration, onExpireAction, null);
    }

    /**
     * Expect a player's to chat a message.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration The time in milliseconds the wait for the chat.
     * @param onExpireAction The action to perform when the chat expires.
     * @param onPlayerQuitAction The action to perform when the player quits.
     */
    public static ExpectedChat expectPlayerChat(Player player, IChatAction chatAction, long expiration, Runnable onExpireAction, Runnable onPlayerQuitAction) {
        return ChatExpectationListener.get().expectPlayerChat(player, chatAction, expiration, onExpireAction, onPlayerQuitAction);
    }

}
