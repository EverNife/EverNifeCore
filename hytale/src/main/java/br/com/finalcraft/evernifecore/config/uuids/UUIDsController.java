package br.com.finalcraft.evernifecore.config.uuids;

import java.util.*;

public class UUIDsController {

    private static final UUIDHashMap UUID_HASH_MAP = new UUIDHashMap();

    public static void addOrUpdateUUIDName(UUID playerUUID, String playerName){
        if (isUUIDLinkedToName(playerUUID, playerName)) {
            return;
        }
        UUID_HASH_MAP.put(playerUUID, playerName);
    }

    public static boolean isUUIDLinkedToName(UUID playerUUID, String playerName){
        String existingPlayerName = UUID_HASH_MAP.get(playerUUID);
        UUID existingUUID = UUID_HASH_MAP.get(playerName);

        if (existingPlayerName == null || existingUUID == null){
            return false;
        }

        if (!existingPlayerName.equals(playerName)){
            return false;
        }

        if (!existingUUID.equals(playerUUID)){
            return false;
        }

        return true;
    }

    public static String getNameFromUUID(UUID playerUUID){
        return UUID_HASH_MAP.get(playerUUID);
    }

    public static String getNameFromUUID(String playerUUID){
        return UUID_HASH_MAP.get(UUID.fromString(playerUUID));
    }

    public static UUID getUUIDFromName(String playerName){
        return UUID_HASH_MAP.get(playerName);
    }

    public static String normalizeName(String unformattedPlayerName){
        return UUID_HASH_MAP.get(UUID_HASH_MAP.get(unformattedPlayerName));
    }

    public static Collection<UUID> getAllUUIDs(){
        return UUID_HASH_MAP.getAllUUIDs();
    }

    public static Collection<String> getAllNames(){
        return UUID_HASH_MAP.getAllNames();
    }

    public static Set<Map.Entry<UUID, String>> getEntrySet(){
        return UUID_HASH_MAP.entrySet();
    }

    public static UUIDHashMap getUuidHashMap() {
        return UUID_HASH_MAP;
    }

    public static final class UUIDHashMap{
        private final Map<UUID,String> storedUUIDtoName = new HashMap<>();
        private final Map<String,UUID> storedNameToUUID = new HashMap<>();

        public void put(UUID uuid, String name){
            //First, lets keep consistency between name and uuid
            String previousWrongName = storedUUIDtoName.remove(uuid);
            UUID previousWrongUUID = storedNameToUUID.remove(name.toLowerCase());
            if (previousWrongName != null) storedNameToUUID.remove(previousWrongName.toLowerCase());
            if (previousWrongUUID != null) storedUUIDtoName.remove(previousWrongUUID);

            //Add new data normally
            storedUUIDtoName.put(uuid,name.intern());
            storedNameToUUID.put(name.toLowerCase().intern(),uuid);
        }

        public UUID get(String name){
            return storedNameToUUID.get(name.toLowerCase());
        }

        public String get(UUID uuid){
            return storedUUIDtoName.get(uuid);
        }

        public boolean contains(String name){
            return storedNameToUUID.containsKey(name.toLowerCase());
        }

        public boolean contains(UUID uuid){
            return storedUUIDtoName.containsKey(uuid);
        }

        public Collection<UUID> getAllUUIDs(){
            return storedUUIDtoName.keySet();
        }

        public Collection<String> getAllNames(){
            return storedUUIDtoName.values();
        }

        public void clear(){
            storedUUIDtoName.clear();
            storedNameToUUID.clear();
        }

        public Set<Map.Entry<UUID, String>> entrySet(){
            return storedUUIDtoName.entrySet();
        }
    }
}
