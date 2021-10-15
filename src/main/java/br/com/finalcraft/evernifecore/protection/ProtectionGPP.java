package br.com.finalcraft.evernifecore.protection;

import net.kaikk.mc.gpp.Claim;
import net.kaikk.mc.gpp.GriefPreventionPlus;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class ProtectionGPP {

    public static boolean canPvP(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        return canPvP(player);
    }

    public static boolean canPvP(Player player) {
        Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(player.getLocation(), true, (Claim)null);
        return claim == null;
    }

    public static boolean isProtected(Player player, Block b) {
        Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(b.getLocation(), true, (Claim)null);
        if (claim != null) {
            return claim.canBreak(player, b.getType()) == null;
        } else {
            return false;
        }
    }

    public static boolean isInsideWilderness(Player player){
        Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(player.getLocation());
        if (claim == null) {
            return true;
        }
        return false;
    }

    public static boolean isInsideSelfClaimOrWilderness(Player player){
        Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(player.getLocation());
        if (claim == null || claim.getOwnerID().toString().equalsIgnoreCase(player.getUniqueId().toString())) {
            return true;
        }
        return false;
    }

    public static boolean isInsideSelfClaim(Player player){
        Claim claim = GriefPreventionPlus.getInstance().getDataStore().getClaimAt(player.getLocation());
        if (claim != null && claim.getOwnerID().toString().equalsIgnoreCase(player.getUniqueId().toString())) {
            return true;
        }
        return false;
    }

}
