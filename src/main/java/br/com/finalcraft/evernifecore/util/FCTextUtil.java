package br.com.finalcraft.evernifecore.util;

import me.tom.sparse.spigot.chat.menu.ChatMenuAPI;
import me.tom.sparse.spigot.chat.util.TextUtil;

public class FCTextUtil {

    private static final int MAX_WIDTH = 320;

    //Fit the String to a single line
    public static String fitLine(String lineToFit, boolean dotsOnEnd){
        int text_widht = ChatMenuAPI.getWidth(lineToFit);
        if (text_widht < MAX_WIDTH) return lineToFit;
        return lineToFit;
    }

    public static String alignCenter(String stringToAlign){
        int text_widht = ChatMenuAPI.getWidth(stringToAlign);
        int side_widht = (int) (MAX_WIDTH - text_widht) / 2;
        String sideString = TextUtil.generateWidth(' ', side_widht, false);

        return sideString + stringToAlign;// + sideString;//Dont need to append second half of SPACES
    }

    public static String alignCenter(String stringToAlign, String borderFill){
        int text_widht = ChatMenuAPI.getWidth(stringToAlign);
        int side_widht = (int) Math.floor((MAX_WIDTH - text_widht) / 2D) ;
        String sideString = generateWidth(borderFill, side_widht, false);
        String result = sideString + "§r" + stringToAlign + "§r" + sideString;
        return result;
    }

    public static String straightLineOf(String string){
        return generateWidth(string, MAX_WIDTH, false);
    }

    public static String generateWidth(String string, int width, boolean canExceed) {
        int stringWidth = ChatMenuAPI.getWidth(string);
        if (stringWidth < 0) throw new IllegalStateException("String without any size cannot be used as argument in generateWidth() str: [" +  string + "]");
        int count = (int) (canExceed ? Math.round(width / (double) stringWidth) : Math.floor(width / (double) stringWidth));
        return repeatString(string, count);
    }

    public static String repeatString(String character, int count) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            stringBuilder.append(character);
        }
        return stringBuilder.toString();
    }
}
