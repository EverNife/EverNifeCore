package br.com.finalcraft.evernifecore.minecraft.actionbar;

import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public class McActionBarHelper {

    private static final MethodInvoker method_spigot;
    private static final MethodInvoker method_sendmessage;

    static {
        String CRAFT_PLAYER_CLASS = "org.bukkit.craftbukkit." + MCVersion.getCurrent().name() + ".entity.CraftPlayer";
        method_spigot = FCReflectionUtil.getMethods(FCReflectionUtil.getClass(CRAFT_PLAYER_CLASS),
                method -> {
                    return method.toString().equals("public org.bukkit.entity.Player$Spigot " + CRAFT_PLAYER_CLASS + ".spigot()");
                }).findFirst().get();
        method_sendmessage = FCReflectionUtil.getMethods(method_spigot.get().getReturnType(),
                method -> {
                    return method.toString().equals("public void org.bukkit.entity.Player$Spigot.sendMessage(net.md_5.bungee.api.ChatMessageType,net.md_5.bungee.api.chat.BaseComponent)");
                }).findFirst().get();
    }

    public static void spigot_sendMessage(Player player, ChatMessageType messageType, BaseComponent baseComponent){
        Object spigot = method_spigot.invoke(player);
        try {
            //I need to execute the method by hand!
            method_sendmessage.get().invoke(
                    spigot,
                    messageType,
                    baseComponent
            );
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

}
