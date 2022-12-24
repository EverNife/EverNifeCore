package br.com.finalcraft.evernifecore.actionbar;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.util.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.version.MCVersion;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.PriorityQueue;

public class PlayerActionBarManager extends BukkitRunnable {

    private final Player player;
    private final PriorityQueue<ActionBarMessage> ACTION_BARS_PRIORITY_QUEUE = new PriorityQueue<>(3, Comparator.comparingInt(ActionBarMessage::getPriority).reversed());
    private transient boolean isRunning;
    private transient boolean terminated = false;

    public PlayerActionBarManager(Player player) {
        this.player = player;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void terminate(){
        terminated = true;
        this.cancel();
    }

    public boolean isRunning() {
        return isRunning;
    }

    private void start(){
        isRunning = true;
        this.runTaskTimerAsynchronously(EverNifeCore.instance, 0, 10);
    }

    public void addMessage(ActionBarMessage message){
        ACTION_BARS_PRIORITY_QUEUE.removeIf(innerMessage -> innerMessage.getActionBarID().equals(message.getActionBarID()));//Remove existing ActionBarMessages with the same ID
        ACTION_BARS_PRIORITY_QUEUE.offer(message);//Add this new message to the priority queue

        if (!isRunning){
            start();//Start assync runnable
        }else {
            run();//Enforce send of ActionBarMessage now
        }
    }

    @Override
    public void run() {
        if (!player.isOnline()){
            this.terminate();
            return;
        }

        while (ACTION_BARS_PRIORITY_QUEUE.peek() != null && ACTION_BARS_PRIORITY_QUEUE.peek().isTerminated()){
            ACTION_BARS_PRIORITY_QUEUE.poll();
        }

        ActionBarMessage actionBarMessage = ACTION_BARS_PRIORITY_QUEUE.peek();

        if (actionBarMessage == null){//No action bar remaining
            TextComponent baseTextComponent = new TextComponent("");
            spigot_sendMessage(player, ChatMessageType.ACTION_BAR, baseTextComponent);
            this.terminate();
            return;
        }

        //Set high priority action bar
        TextComponent baseTextComponent = new TextComponent(actionBarMessage.getActionBarText());
        spigot_sendMessage(player, ChatMessageType.ACTION_BAR, baseTextComponent);
    }

    //------------------------------------------------------------------------------------------------------------------
    // Reflection Code
    //------------------------------------------------------------------------------------------------------------------

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
