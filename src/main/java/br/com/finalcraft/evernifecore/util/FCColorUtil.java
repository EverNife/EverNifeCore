package br.com.finalcraft.evernifecore.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class FCColorUtil {

    public static ChatColor getRandomColor(){
        List<ChatColor> colors = new ArrayList<>();
        for (ChatColor value : ChatColor.values()) {
            if (value.isColor()){
                colors.add(value);
            }
        }
        return colors.get(FCBukkitUtil.getRandom().nextInt(colors.size()));
    }

}
