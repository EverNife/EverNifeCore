package br.com.finalcraft.evernifecore.worldedit;

import br.com.finalcraft.evernifecore.minecraft.vector.BlockPos;
import br.com.finalcraft.evernifecore.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.worldedit.clipboard.format.IFCClipboardManager;
import br.com.finalcraft.evernifecore.worldedit.operation.IFCOperationManager;
import br.com.finalcraft.evernifecore.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;

public abstract class WEPlatform {

    private static final WEPlatform INSTANCE;

    static {
        String wgVersion = Bukkit.getPluginManager().getPlugin("WorldEdit").getDescription().getVersion();
        String apiClassname = wgVersion.startsWith("6")
                ? "v1_7_R4" //WorldEdit 6.*
                : "v1_16_R3"; //WorldEdit 7.*

        WEPlatform wgPlatform = null;
        try {
            wgPlatform = (WEPlatform) Class.forName("br.com.finalcraft.evernifecore.compat." + apiClassname + ".worldedit.ImpWEPlatform").newInstance();
        } catch (Throwable e) {
            System.out.println("[EverNifeCore] You do not have WorldEdit present or you did not waited for EverNifeCore to be fully StartedUp!");
            e.printStackTrace();
        }
        INSTANCE = wgPlatform;
    }

    public static WEPlatform getInstance(){
        return INSTANCE;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Body
    // -----------------------------------------------------------------------------------------------------------------

    private final IFCClipboardManager clipboardManager;
    private final IFCOperationManager operationManager;

    public WEPlatform(IFCClipboardManager clipboardManager, IFCOperationManager operationManager) {
        this.clipboardManager = clipboardManager;
        this.operationManager = operationManager;
    }

    public IFCClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    public IFCOperationManager getOperationManager() {
        return operationManager;
    }

    public abstract IFCCuboidRegion createCuboidRegion(BlockPos pos1, BlockPos pos2);

    public abstract FCBlockArrayClipboard createBlockArrayClipboard(IFCCuboidRegion region);

    public abstract void setBlock(World world, BlockPos blockPos, FCBaseBlock blockBase, boolean placeFast) throws WorldEditException;
}
