package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import com.google.common.collect.Iterables;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Utility class to handle Adventure Component sending with fallback to legacy Bungee API
 * for older Minecraft versions that don't support Adventure natively.
 */
public class FCMinecraftAdventureUtil {

    private static final MethodInvoker method_spigot;
    private static final MethodInvoker method_sendmessage;
    private static final BukkitAudiences adventure = BukkitAudiences.create(EverNifeCoreBukkitPlugin.instance);

    static {
        String CRAFT_PLAYER_CLASS = "org.bukkit.craftbukkit." + MCVersion.getCurrent().name() + ".entity.CraftPlayer";
        String CRAFT_PLAYER_CLASS_PAPER_POST_1_21 = "org.bukkit.craftbukkit.entity.CraftPlayer";

        String PLAYER_SPIGOT_METHOD_CONTRACT_1 = "public org.bukkit.entity.Player$Spigot " + CRAFT_PLAYER_CLASS + ".spigot()";
        String PLAYER_SPIGOT_METHOD_CONTRACT_2 = "public org.bukkit.entity.Player$Spigot " + CRAFT_PLAYER_CLASS_PAPER_POST_1_21 + ".spigot()";

        method_spigot = FCReflectionUtil.getMethods(FCReflectionUtil.getClass(CRAFT_PLAYER_CLASS),
                method -> {
                    return method.toString().equals(PLAYER_SPIGOT_METHOD_CONTRACT_1) || method.toString().equals(PLAYER_SPIGOT_METHOD_CONTRACT_2);
                }).findFirst().get();

        method_sendmessage = FCReflectionUtil.getMethods(method_spigot.get().getReturnType(),
                method -> {
                    return method.toString().equals("public void org.bukkit.entity.Player$Spigot.sendMessage(net.md_5.bungee.api.chat.BaseComponent[])");
                }).findFirst().get();
    }

    /**
     * Sends an Adventure Component to a CommandSender.
     * Uses native Adventure support if available, otherwise falls back to Bungee API.
     *
     * @param sender The CommandSender to send the message to
     * @param component The Adventure Component to send
     */
    public static void sendMessage(CommandSender sender, Component component) {
        if (sender instanceof Player) {
            getAdventure().sender(sender).sendMessage(component);
//            sendMessageLegacy((Player) sender, component);
        } else {
            // For console, send plain text
            sender.sendMessage(GsonComponentSerializer.gson().serialize(component));
        }
    }

    /**
     * Sends a message using legacy Bungee API via reflection.
     * This is used for Minecraft versions that don't have native Adventure support.
     *
     * @param player The player to send the message to
     * @param component The Adventure Component to send
     */
    private static void sendMessageLegacy(Player player, Component component) {
        if (method_spigot == null || method_sendmessage == null) {
            // Reflection failed, fallback to plain text
            player.sendMessage(GsonComponentSerializer.gson().serialize(component));
            return;
        }

        try {
            // Convert Adventure Component to Bungee BaseComponent[]

            for (Component splitNewline : splitNewlines(component)) {
                BaseComponent[] bungeeComponents = adventureToBungee(splitNewline);

                // Call player.spigot().sendMessage(BaseComponent[])
                Object spigot = method_spigot.invoke(player);
                method_sendmessage.get().invoke(
                        spigot,
                        (Object) bungeeComponents // Cast to Object to avoid varargs ambiguity
                );
            }

        } catch (IllegalAccessException | InvocationTargetException e) {
            EverNifeCore.getLog().severe("Failed to call Call player.spigot().sendMessage(BaseComponent[])");
            e.printStackTrace();
            // Fallback to plain text on error
            player.sendMessage(GsonComponentSerializer.gson().serialize(component));
        }
    }

    /**
     * Converts an Adventure Component to Bungee BaseComponent array.
     * Uses JSON serialization as an intermediate format.
     *
     * @param component The Adventure Component to convert
     * @return The equivalent Bungee BaseComponent array
     */
    private static BaseComponent[] adventureToBungee(Component component) {
        // Serialize Adventure Component to JSON
//        String json = GsonComponentSerializer.gson().serialize(component);
//
//        System.out.println("Serialize Adventure Component to JSON: " + json);

        BaseComponent[] bungeeComponents = BungeeComponentSerializer.get().serialize(component);
//
//        BaseComponent[] parse = ComponentSerializer.parse(json);
//
//        System.out.println("Parsed Bungee BaseComponent[]: " + bungeeComponents.getClass().getName());
//        System.out.println("Parsed Bungee BaseComponent[]: " + bungeeComponents.length);
//        System.out.println("Parsed Bungee BaseComponent[]: " + bungeeComponents[0].getClass().getName());

        // Deserialize JSON to Bungee BaseComponent[]
        return bungeeComponents;
    }

    public static Iterable<Component> splitNewlines(Component message) {
        System.out.println("splitNewlines: " + message.getClass().getName());
        if (message instanceof net.kyori.adventure.text.TextComponent && message.style().isEmpty() && !message.children().isEmpty() && ((TextComponent) message).content().isEmpty()) {
            LinkedList<List<Component>> split = new LinkedList<>();
            split.add(new ArrayList<>());

            for (Component child : message.children()) {
                if (Component.newline().equals(child)) {
                    split.add(new ArrayList<>());
                } else {
                    Iterator<Component> splitChildren = splitNewlines(child).iterator();
                    if (splitChildren.hasNext()) {
                        split.getLast().add(splitChildren.next());
                    }
                    while (splitChildren.hasNext()) {
                        split.add(new ArrayList<>());
                        split.getLast().add(splitChildren.next());
                    }
                }
            }

            return Iterables.transform(split, input -> {
                switch (input.size()) {
                    case 0:
                        return Component.empty();
                    case 1:
                        return input.get(0);
                    default:
                        return Component.join(JoinConfiguration.separator(Component.empty()), input);
                }
            });
        }

        return Collections.singleton(message);
    }

    public static BukkitAudiences getAdventure() {
        return adventure;
    }
}
