package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class FancyTextManager {

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
                    return method.toString().equals("public void org.bukkit.entity.Player$Spigot.sendMessage(net.md_5.bungee.api.chat.BaseComponent[])");
                }).findFirst().get();
    }

    private static void spigot_sendMessage(Player player, BaseComponent[] textComponenArray){
        Object spigot = method_spigot.invoke(player);
        try {
            //I need to execute the method by hand!
            method_sendmessage.get().invoke(
                    spigot,
                    (Object)textComponenArray //MANUAL FIX FOR https://stackoverflow.com/questions/48577435/why-do-i-get-java-lang-illegalargumentexception-wrong-number-of-arguments-while
            );
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public static void send(FancyText fancyText, CommandSender... commandSenders){

        if (fancyText.fancyFormatter != null){
            send(fancyText.fancyFormatter, commandSenders);
            return;
        }

        List<List<TextComponent>> textComponentList2D = null;
        for (CommandSender sender : commandSenders) {
            if(sender instanceof Player){
                Player player = (Player) sender;
                if (textComponentList2D == null) textComponentList2D = get2DListOfTextComponents(fancyText);
                for (List<TextComponent> textComponentList : textComponentList2D) {
                    TextComponent[] textComponenArray = textComponentList.toArray(new TextComponent[0]);
                    spigot_sendMessage(player, textComponenArray);
                }
            }else {
                sender.sendMessage(fancyText.text);
            }
        }

    }

    public static void send(FancyFormatter fancyFormatter, CommandSender... commandSenders){

        if (fancyFormatter.hasPlaceholders() == false){
            List<List<TextComponent>> textComponentList2D = get2DListOfTextComponents(fancyFormatter.getFancyTextList().toArray(new FancyText[0]));
            for (CommandSender sender : commandSenders) {
                if(sender instanceof Player){
                    Player player = (Player) sender;
                    for (List<TextComponent> textComponentList : textComponentList2D) {
                        TextComponent[] textComponenArray = textComponentList.toArray(new TextComponent[0]);
                        spigot_sendMessage(player, textComponenArray);
                    }
                }else {
                    StringBuilder stringBuilder = new StringBuilder();
                    for (FancyText aFancyText : fancyFormatter.getFancyTextList()) {
                        stringBuilder.append(aFancyText.text);
                    }
                    sender.sendMessage(stringBuilder.toString());
                }
            }
            return;
        }

        if (fancyFormatter.complexPlaceholder == true){
            for (CommandSender sender : commandSenders) {
                FancyFormatter formatterClone = fancyFormatter.clone();
                final boolean isPlayer = sender instanceof Player;
                final PlayerData playerData = isPlayer ? PlayerController.getPlayerData((Player) sender) : null;
                for (Map.Entry<String, Object> entry : formatterClone.mapOfPlaceholders.entrySet()) {
                    String placeholder = entry.getKey();
                    String value;
                    if (isPlayer && entry.getValue() instanceof Function){
                        value = String.valueOf(((Function<PlayerData, Object>)entry.getValue()).apply(playerData));
                    }else {
                        value = String.valueOf(entry.getValue());
                    }
                    formatterClone.replace(placeholder, value);
                }

                if(isPlayer){
                    Player player = (Player) sender;
                    for (List<TextComponent> textComponentList : get2DListOfTextComponents(formatterClone.getFancyTextList().toArray(new FancyText[0]))) {
                        TextComponent[] textComponenArray = textComponentList.toArray(new TextComponent[0]);
                        spigot_sendMessage(player, textComponenArray);
                    }
                }else {
                    StringBuilder stringBuilder = new StringBuilder();
                    for (FancyText aFancyText : fancyFormatter.getFancyTextList()) {
                        stringBuilder.append(aFancyText.text);
                    }
                    sender.sendMessage(stringBuilder.toString());
                }
            }
            return;
        }

        FancyFormatter formatterClone = fancyFormatter.clone();
        for (Map.Entry<String, Object> entry : fancyFormatter.mapOfPlaceholders.entrySet()) {
            String placeholder = entry.getKey();
            String value = String.valueOf(entry.getValue());
            formatterClone.replace(placeholder, value);
        }

        for (CommandSender sender : commandSenders) {
            if(sender instanceof Player){
                Player player = (Player) sender;
                for (List<TextComponent> textComponentList : get2DListOfTextComponents(formatterClone.getFancyTextList().toArray(new FancyText[0]))) {
                    TextComponent[] textComponenArray = textComponentList.toArray(new TextComponent[0]);
                    spigot_sendMessage(player, textComponenArray);
                }
            }else {
                StringBuilder stringBuilder = new StringBuilder();
                for (FancyText aFancyText : fancyFormatter.getFancyTextList()) {
                    stringBuilder.append(aFancyText.text);
                }
                sender.sendMessage(stringBuilder.toString());
            }
        }

    }

    private static List<List<TextComponent>> get2DListOfTextComponents(FancyText... fancyTexts){
        List<List<TextComponent>> textComponent2DList = new ArrayList<>();
        int id = 0;
        String previousColor = "§r";
        for (FancyText aFancyText : fancyTexts) {
            for (TextComponent textComponent : aFancyText.getTextComponents(previousColor)) {
                if (textComponent2DList.size() <= id){
                    textComponent2DList.add(new ArrayList<>());
                }
                if (textComponent == null){
                    id++;
                    continue;
                }
                textComponent2DList.get(id).add(textComponent);
            }
            previousColor = aFancyText.getLastTextColor();
        }
        if (textComponent2DList.size() <= id){
            textComponent2DList.add(new ArrayList<>());
        }
        return textComponent2DList;
    }
}
