package br.com.finalcraft.evernifecore.protection;

import net.kaikk.mc.gpp.Claim;
import net.kaikk.mc.gpp.GriefPreventionPlus;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class ProtectionGPP {

    public static boolean canBreak(Player player, Block block) {
        Claim claimAtBlock = getClaimAt(block.getLocation());
        return claimAtBlock == null || claimAtBlock.canBreak(player, block.getType()) == null;
    }

    public static boolean canBuild(Player player, Block block) {
        Claim claimAtBlock = getClaimAt(block.getLocation());
        return claimAtBlock == null || claimAtBlock.canBuild(player, block.getType()) == null;
    }

    public static Claim getClaimAt(Location location){
        return GriefPreventionPlus.getInstance().getDataStore().getClaimAt(location);
    }

    public static boolean isInsideWilderness(Player player){
        return getClaimAt(player.getLocation()) == null;
    }

    public static boolean isInsideSelfClaim(Player player){
        Claim claim = getClaimAt(player.getLocation());
        return claim != null && claim.getOwnerID().equals(player.getUniqueId());
    }

    public static boolean isInsideSelfClaimOrWilderness(Player player){
        Claim claim = getClaimAt(player.getLocation());
        return claim == null || claim.getOwnerID().equals(player.getUniqueId());
    }

}
