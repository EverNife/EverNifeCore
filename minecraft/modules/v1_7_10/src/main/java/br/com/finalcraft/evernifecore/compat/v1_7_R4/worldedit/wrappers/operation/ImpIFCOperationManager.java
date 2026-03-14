package br.com.finalcraft.evernifecore.compat.v1_7_R4.worldedit.wrappers.operation;

import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.operation.IFCOperationManager;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import org.bukkit.World;

public class ImpIFCOperationManager extends IFCOperationManager {

    @Override
    public void forwardExtentCopy(World world, IFCCuboidRegion region, FCBlockArrayClipboard clipboard) {
        com.sk89q.worldedit.world.World bukkitWorld = new BukkitWorld(world);
        EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(bukkitWorld, -1);
        ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region.getHandle(), clipboard.getHandle(), region.getHandle().getMinimumPoint());
        try {
            Operations.completeLegacy(copy);
        } catch (MaxChangedBlocksException e) {
            e.printStackTrace();//As edit session is -1, this should never happen, but whatever
        }
    }
}
