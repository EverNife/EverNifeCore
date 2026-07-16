package br.com.finalcraft.evernifecore.minecraft.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.loader.EverNifeCoreBukkitPlugin;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;

/**
 * Sends an Adventure {@link Component} to a Bukkit {@link CommandSender}, picking the transport by
 * <em>runtime capability</em> rather than by server version - so a 1.7.10 server that backports
 * modern chat is served the same rich path as a stock 1.8+ one.
 * <p>
 * Order of preference for a player:
 * <ol>
 *   <li>{@code player.spigot().sendMessage(BaseComponent[])} when that method resolves. It renders
 *       every feature FancyText emits (colour, hex, hover text/item, click) and is present from
 *       1.7.10 (and its backports) through the latest Spigot/Paper, so it is the most portable path.
 *       Bungee's chat classes ({@code BaseComponent}, {@code ComponentSerializer}) come from the
 *       server and re-serialise our components in that server's own JSON dialect, never bundled here.</li>
 *   <li>The adventure-platform bridge ({@link BukkitAudiences}) when the BaseComponent method is
 *       absent - it self-selects a native facet and degrades to plain text as a last resort.</li>
 * </ol>
 * Console (and any non-player sender) receives legacy section-coloured text, never raw component JSON.
 */
public class FCMinecraftAdventureUtil {

    // player.spigot() and Player$Spigot.sendMessage(BaseComponent[]), or null when the server does
    // not expose the BaseComponent chat API. Resolved reflectively because the method is missing on
    // servers old enough (or trimmed enough) not to carry the Spigot chat backport.
    private static final MethodInvoker<?> METHOD_SPIGOT;
    private static final MethodInvoker<?> METHOD_SEND_BASECOMPONENTS;

    // adventure-platform bridge; built lazily and only used when the BaseComponent path is missing.
    private static BukkitAudiences adventure;

    static {
        MethodInvoker<?> spigot = null;
        MethodInvoker<?> sendBaseComponents = null;
        try {
            Class<?> craftPlayer = FCReflectionUtil.getClasses().getFirstClass(
                    "org.bukkit.craftbukkit." + MCVersion.getCurrent().name() + ".entity.CraftPlayer",
                    "org.bukkit.craftbukkit.entity.CraftPlayer" // Paper 1.20.5+ dropped the version segment
            );
            Class<?> baseComponent = FCReflectionUtil.getClasses().getClass("net.md_5.bungee.api.chat.BaseComponent");

            if (craftPlayer != null && baseComponent != null) {
                spigot = FCReflectionUtil.getMethods().getMethod(craftPlayer, "spigot");
                if (spigot != null) {
                    Class<?> baseComponentArray = Array.newInstance(baseComponent, 0).getClass();
                    sendBaseComponents = FCReflectionUtil.getMethods()
                            .getMethod(spigot.getMethod().getReturnType(), "sendMessage", baseComponentArray);
                }
            }
        } catch (Throwable ignored) {
            // Any resolution hiccup means the capability is unavailable; fall back to adventure-platform.
        }
        METHOD_SPIGOT = spigot;
        METHOD_SEND_BASECOMPONENTS = sendBaseComponents;
    }

    private static boolean hasBaseComponentChat() {
        return METHOD_SPIGOT != null && METHOD_SEND_BASECOMPONENTS != null;
    }

    /**
     * Sends a component to a sender using the best transport available on this server.
     */
    public static void sendMessage(CommandSender sender, Component component) {
        if (sender instanceof Player && hasBaseComponentChat()) {
            sendViaSpigot((Player) sender, component);
        } else if (sender instanceof Player) {
            getAdventure().sender(sender).sendMessage(component);
        } else {
            // Console and other non-player senders: section-coloured text (the server turns it into
            // ANSI), never the component's raw JSON.
            sender.sendMessage(FCColorUtil.componentToString(component));
        }
    }

    /**
     * Sends via {@code player.spigot().sendMessage(BaseComponent[])}. Each newline becomes its own
     * chat line, because an old client renders a single {@code BaseComponent[]} as one line and would
     * otherwise collapse the message.
     */
    private static void sendViaSpigot(Player player, Component component) {
        try {
            Object spigot = METHOD_SPIGOT.invoke(player);
            for (Component line : FCComponentUtil.splitNewlines(component)) {
                BaseComponent[] baseComponents = FCComponentUtil.toBaseComponents(line);
                // Pass the array as a single argument (sendMessage takes one BaseComponent[] parameter).
                METHOD_SEND_BASECOMPONENTS.invoke(spigot, (Object) baseComponents);
            }
        } catch (Throwable e) {
            EverNifeCore.getLog().severe("Failed to send message via player.spigot().sendMessage(BaseComponent[]); falling back to plain text");
            e.printStackTrace();
            player.sendMessage(FCColorUtil.componentToString(component));
        }
    }

    public static BukkitAudiences getAdventure() {
        if (adventure == null) {
            adventure = BukkitAudiences.create(EverNifeCoreBukkitPlugin.instance);
        }
        return adventure;
    }
}
