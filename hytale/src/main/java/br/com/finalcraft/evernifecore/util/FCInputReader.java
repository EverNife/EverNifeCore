package br.com.finalcraft.evernifecore.util;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.util.UUID;

public class FCInputReader {

    public static Integer parseInt(String input, Integer def){
        try {
            return Integer.parseInt(input);
        }catch (NumberFormatException e){
            return def;
        }
    }

    public static Integer parseInt(String input){
        return parseInt(input, null);
    }

    public static Double parseDouble(String input, Double def){
        try {
            return Double.parseDouble(input);
        }catch (NumberFormatException e){
            return def;
        }
    }

    public static Double parseDouble(String input){
        return parseDouble(input, null);
    }

    public static UUID parseUUID(String uuid){
        if (uuid == null || uuid.isEmpty() || uuid.length() != 36) return null;
        try {
            return UUID.fromString(uuid);
        }catch (Exception ignored){
            return null;
        }
    }

    public static Item parseItem(String item){
        return Item.getAssetMap().getAsset(item);
    }

}
