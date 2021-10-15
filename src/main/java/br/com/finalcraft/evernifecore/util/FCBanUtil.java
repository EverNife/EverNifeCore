package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;

import java.util.Date;

public class FCBanUtil {

    public static void addBan(String playerName, String banReason, Date date, String source){
        playerName = UUIDsController.normalizeName(playerName);
        if (banReason == null || banReason.isEmpty()) banReason = "HAMMER_HAS_SPOKEN";
        if (source == null || source.isEmpty()) source = "CONSOLE";
        Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, banReason, null, source);
    }

    public static boolean removeBan(String playerName){
        BanEntry banEntry = getBanEntry(playerName);
        if (banEntry != null) {
            Bukkit.getBanList(BanList.Type.NAME).pardon(banEntry.getTarget());
            try {
                PlayerData playerData = PlayerController.getPlayerData(playerName);
                if (playerData != null){
                    Bukkit.getBanList(BanList.Type.NAME).pardon(playerData.getPlayerName()); //Minecraft is bugged man!
                    Bukkit.getBanList(BanList.Type.NAME).pardon(playerData.getUniqueId().toString()); //Minecraft is bugged man!
                }
            }catch (Exception ignored){
            }
            return true;
        }
        return false;
    }

    public static Boolean isBanned(String playerName) {
        return getBanEntry(playerName) != null;
    }

    public static BanEntry getBanEntry(String name) {
        for (BanEntry banEntry : Bukkit.getServer().getBanList(BanList.Type.NAME).getBanEntries()) {
            if (banEntry.getTarget().equalsIgnoreCase(name)) {
                return banEntry;
            }
        }
        return null;
    }

}
