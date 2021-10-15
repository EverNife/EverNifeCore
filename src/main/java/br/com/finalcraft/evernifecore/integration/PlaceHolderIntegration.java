package br.com.finalcraft.evernifecore.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class PlaceHolderIntegration {

    private static Boolean apiLoaded = null;

    private static boolean isApiLoaded(){
        if (apiLoaded == null){
            apiLoaded = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? true : false;
        }
        return apiLoaded;
    }

    public static String parsePlaceholders(Player player, String textToParse){
        if (!apiLoaded){
            return ChatColor.translateAlternateColorCodes('&',textToParse);
        }
        return PlaceholderAPI.setPlaceholders(player,textToParse);
    }

    public static String getString(Player player, String placeholder, String def){
        String theString = getString(player, placeholder);
        return theString.isEmpty() ? def : theString;
    }

    public static String getString(Player player, String placeholder){
        if (!apiLoaded){
            return placeholder;
        }
        return PlaceholderAPI.setPlaceholders(player,placeholder);
    }

    public static long getLong(Player player, String placeholder){
        if (!apiLoaded){
            return 0L;
        }
        try {
            long aLong = Long.parseLong(PlaceholderAPI.setPlaceholders(player, placeholder));
            return aLong;
        }catch (NumberFormatException error){
            error.printStackTrace();
        }
        return 0L;
    }

}
