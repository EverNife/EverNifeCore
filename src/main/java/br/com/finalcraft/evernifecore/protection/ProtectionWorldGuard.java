package br.com.finalcraft.evernifecore.protection;

import br.com.finalcraft.evernifecore.protection.worldguard.WGFlags;
import br.com.finalcraft.evernifecore.protection.worldguard.WGPlatform;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class ProtectionWorldGuard {

    public static boolean canPvP(Player player) {
        return WGPlatform.getInstance()
                .getApplicableRegions(player.getLocation())
                .testState(WGPlatform.getInstance().wrapPlayer(player), WGFlags.PVP);
    }

    public static boolean canBreak(Player player, Block block){
        return WGPlatform.getInstance()
                .getApplicableRegions(block.getLocation())
                .testState(WGPlatform.getInstance().wrapPlayer(player), WGFlags.BLOCK_BREAK);
    }

    public static boolean canBuild(Player player, Block block){
        return WGPlatform.getInstance()
                .getApplicableRegions(block.getLocation())
                .testState(WGPlatform.getInstance().wrapPlayer(player), WGFlags.BUILD);
    }

}
