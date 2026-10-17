package br.com.finalcraft.evernifecore.minecraft.worldedit;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.format.IFCClipboardManager;
import br.com.finalcraft.evernifecore.minecraft.worldedit.operation.IFCOperationManager;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.world.World;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public abstract class WEPlatform {

    private static final WEPlatform INSTANCE;

    static {
        WEPlatform instance = null;
        Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (worldEdit != null) {
            String weVersion = worldEdit.getDescription().getVersion();
            String apiClassname = weVersion.startsWith("6")
                    ? "v1_7_R4" //WorldEdit 6.*
                    : "v1_16_R3"; //WorldEdit 7.*
            try {
                instance = (WEPlatform) Class.forName("br.com.finalcraft.evernifecore.minecraft.compat." + apiClassname + ".worldedit.ImpWEPlatform").newInstance();
            } catch (Throwable e) {
                System.out.println("[EverNifeCore] WorldEdit " + weVersion + " is present but its adapter could not be loaded:");
                e.printStackTrace();
            }
        }
        INSTANCE = instance;
    }

    /** Whether WorldEdit was installed when this class first loaded; false means {@link #getInstance()} throws. */
    public static boolean isPresent() {
        return INSTANCE != null;
    }

    public static WEPlatform getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("WorldEdit is not installed, its adapter failed to load, or EverNifeCore touched WEPlatform"
                    + " before WorldEdit enabled - check WEPlatform.isPresent() before calling getInstance().");
        }
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

    /**
     * The WorldEdit world every copy, paste and restore of the core works on: the Bukkit plugin's world, or on 1.7.10 with
     * WorldEdit Forge in mods/ the mod's world, the only one there that reads and writes tile entities ({@link WorldEditForge}).
     */
    public abstract World adaptWorld(org.bukkit.World world);

    /**
     * The player's WorldEdit selection in the world they stand in: a polygonal selection with its Y span, anything else
     * (cuboid, sphere, cylinder, convex) as the box of its extremes; null while the selection is incomplete.
     */
    public abstract @Nullable Shape getSelection(Player player);
}
