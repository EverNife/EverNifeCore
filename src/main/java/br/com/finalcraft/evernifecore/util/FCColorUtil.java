package br.com.finalcraft.evernifecore.util;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FCColorUtil {

    public static ChatColor getRandomColor(){
        List<ChatColor> colors = new ArrayList<>();
        for (ChatColor value : ChatColor.values()) {
            if (value.isColor()){
                colors.add(value);
            }
        }
        return colors.get(FCMathUtil.getRandom().nextInt(colors.size()));
    }

    public static String decolorfy(@Nullable String text){
        if (text == null) return null;
        //TODO do something better
        return text.replace("§", "&");
    }

    public static String colorfy(@Nullable String text){
        if (text == null) return null;
        String colored = ChatColor.translateAlternateColorCodes('&',text);

        String[] split = colored.split("\n",-1);
        if (split.length > 1){ //In case of '\n' lets repeat colors on each new '\n'
            List<String> result = new ArrayList<>();
            result.add(split[0]);
            for (int i = 1; i < split.length; i++) {
                String previousLine = result.get(i - 1);
                String previousColor = ChatColor.getLastColors(previousLine);

                String nextLine = split[i];
                if (!nextLine.startsWith(previousColor)){//Only if the new line doesn't already have the color, apply it
                    nextLine = previousColor + nextLine;
                }

                result.add(nextLine);
            }
            return String.join("\n", result);
        }

        return colored;
    }

    //Returns a new List<String> with the colored strings
    public static List<String> colorfy(@Nullable List<String> text){
        if (text == null) return null;
        return new ArrayList<>(
                Arrays.asList(
                        FCColorUtil.colorfy(String.join("\n",text)).split("\n",-1)
                )
        );
    }

    public static String stripColor(@Nullable final String input){
        return ChatColor.stripColor(input);
    }

    public static List<String> stripColor(@Nullable final List<String> text){
        if (text == null) return null;
        for (int i = 0; i < text.size(); i++) {
            text.set(i, stripColor(text.get(i)));
        }
        return text;
    }

}
