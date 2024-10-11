package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.chatmenuapi.listeners.CMListener;
import br.com.finalcraft.evernifecore.chatmenuapi.listeners.expectedchat.ExpectedChat;
import br.com.finalcraft.evernifecore.chatmenuapi.menu.ChatMenuAPI;
import org.bukkit.entity.Player;

public class FCChatUtil {

    /**
     * Expect a player's to chat a message.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     */
    public static ExpectedChat expectPlayerChat(Player player, CMListener.IChatAction chatAction) {
        return ChatMenuAPI.getChatListener().expectPlayerChat(player, chatAction, 0, null, null);
    }

    /**
     * Expect a player's to chat a message.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration The time in milliseconds the wait for the chat.
     */
    public static ExpectedChat expectPlayerChat(Player player, CMListener.IChatAction chatAction, long expiration) {
        return ChatMenuAPI.getChatListener().expectPlayerChat(player, chatAction, expiration, null, null);
    }

    /**
     * Expect a player's to chat a message.
     *
     * @param player The player to expect a chat from.
     * @param chatAction The action to perform when the player chats.
     * @param expiration The time in milliseconds the wait for the chat.
     * @param onExpireAction The action to perform when the chat expires.
     */
    public static ExpectedChat expectPlayerChat(Player player, CMListener.IChatAction chatAction, long expiration, Runnable onExpireAction) {
        return ChatMenuAPI.getChatListener().expectPlayerChat(player, chatAction, expiration, onExpireAction, null);
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
    public static ExpectedChat expectPlayerChat(Player player, CMListener.IChatAction chatAction, long expiration, Runnable onExpireAction, Runnable onPlayerQuitAction) {
        return ChatMenuAPI.getChatListener().expectPlayerChat(player, chatAction, expiration, onExpireAction, onPlayerQuitAction);
    }

}
