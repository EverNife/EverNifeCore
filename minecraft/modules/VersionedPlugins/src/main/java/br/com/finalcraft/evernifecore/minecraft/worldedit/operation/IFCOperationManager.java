package br.com.finalcraft.evernifecore.minecraft.worldedit.operation;

import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import org.bukkit.World;

public abstract class IFCOperationManager {

    public abstract void forwardExtentCopy(World world, IFCCuboidRegion region, FCBlockArrayClipboard clipboard);


}
