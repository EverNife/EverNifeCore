package br.com.finalcraft.evernifecore.minecraft.protection;

import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.WGFlags;
import br.com.finalcraft.evernifecore.minecraft.protection.worldguard.WGPlatform;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** WorldGuard's verdict; without WorldGuard installed nothing it would deny is denied. */
public class ProtectionWorldGuard {

    public static boolean canPvP(Player player) {
        if (!WGPlatform.isPresent()) return true;
        return WGPlatform.getInstance()
                .getApplicableRegions(player.getLocation())
                .testState(WGPlatform.getInstance().wrapPlayer(player), WGFlags.PVP);
    }

    public static boolean canBreak(Player player, Block block){
        if (!WGPlatform.isPresent()) return true;
        return WGPlatform.getInstance()
                .getApplicableRegions(block.getLocation())
                .testState(WGPlatform.getInstance().wrapPlayer(player), WGFlags.BUILD);
    }

    public static boolean canBuild(Player player, Block block){
        if (!WGPlatform.isPresent()) return true;
        return WGPlatform.getInstance()
                .getApplicableRegions(block.getLocation())
                .testState(WGPlatform.getInstance().wrapPlayer(player), WGFlags.BUILD);
    }

}
