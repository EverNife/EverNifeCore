package br.com.finalcraft.evernifecore.integration.worldedit.masks;

import br.com.finalcraft.evernifecore.integration.worldedit.CustomMask;
import com.sk89q.worldedit.Vector;
import org.bukkit.Material;
import org.bukkit.block.Block;

public class CMaskOnlyAir extends CustomMask {

    @Override
    public boolean test(Vector vector) {

        if (clipboard.getBlock(vector).isAir()){
            return false;
        }

        Vector insideOrigin = clipboard.getOrigin().ceil();
        Vector distanceRelative = vector.subtract(insideOrigin);
        Vector blockRelativeToPlayer = targetLocation.add(distanceRelative);

        Block block = world.getBlockAt(blockRelativeToPlayer.getBlockX(),blockRelativeToPlayer.getBlockY(),blockRelativeToPlayer.getBlockZ());

        return block.getType() == Material.AIR;
    }


}
