package br.com.finalcraft.evernifecore.config.uuids;

import br.com.finalcraft.evernifecore.config.ConfigManager;

import java.util.*;

public class UUIDsController {

    private static final UUIDHashMap uuidHashMap = new UUIDHashMap();

    public static void loadUUIDs(){
        if (ConfigManager.getPlayerUUIDs().contains("StoredUUIDs")){
            for (String stringUUID : ConfigManager.getPlayerUUIDs().getKeys("StoredUUIDs")){
                UUID playerUUID = UUID.fromString(stringUUID);
                String playerName = ConfigManager.getPlayerUUIDs().getString("StoredUUIDs." + stringUUID);
                uuidHashMap.put(playerUUID,playerName);
            }
        }
    }

    public static void addUUIDName(UUID playerUUID, String playerName){
        if (uuidHashMap.contains(playerUUID)){
            return;
        }
        ConfigManager.getPlayerUUIDs().setValue("StoredUUIDs." + playerUUID.toString(),playerName);
        ConfigManager.getPlayerUUIDs().save();
        uuidHashMap.put(playerUUID,playerName);
    }

    public static String getNameFromUUID(UUID playerUUID){
        return uuidHashMap.get(playerUUID);
    }

    public static String getNameFromUUID(String playerUUID){
        return uuidHashMap.get(UUID.fromString(playerUUID));
    }

    public static UUID getUUIDFromName(String playerName){
        return uuidHashMap.get(playerName);
    }

    public static String normalizeName(String unformattedPlayerName){
        return uuidHashMap.get(uuidHashMap.get(unformattedPlayerName));
    }

    public static Collection<UUID> getAllUUIDs(){
        return uuidHashMap.getAllUUIDs();
    }

    public static Collection<String> getAllNames(){
        return uuidHashMap.getAllNames();
    }

    public static Set<Map.Entry<UUID, String>> getEntrySet(){
        return uuidHashMap.entrySet();
    }

    public static UUIDHashMap getUuidHashMap() {
        return uuidHashMap;
    }

    public static final class UUIDHashMap{
        private final Map<UUID,String> storedUUIDtoName = new HashMap<UUID, String>();
        private final Map<String,UUID> storedNameToUUID = new HashMap<String, UUID>();

        private void put(UUID uuid, String name){
            storedUUIDtoName.put(uuid,name);
            storedNameToUUID.put(name.toLowerCase(),uuid);
        }

        private UUID get(String name){
            return storedNameToUUID.get(name.toLowerCase());
        }

        private String get(UUID uuid){
            return storedUUIDtoName.get(uuid);
        }

        private boolean contains(String name){
            return storedNameToUUID.containsKey(name.toLowerCase());
        }

        private boolean contains(UUID uuid){
            return storedUUIDtoName.containsKey(uuid);
        }

        public Collection<UUID> getAllUUIDs(){
            return storedUUIDtoName.keySet();
        }

        public Collection<String> getAllNames(){
            return storedUUIDtoName.values();
        }

        public Set<Map.Entry<UUID, String>> entrySet(){
            return storedUUIDtoName.entrySet();
        }
    }
}
