package br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit;

import br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit.wrappers.clipboard.ImpFCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit.wrappers.clipboard.format.IFCClipboardFormat_Schematic;
import br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit.wrappers.operation.ImpIFCOperationManager;
import br.com.finalcraft.evernifecore.minecraft.compat.v1_7_R4.worldedit.wrappers.region.ImpIFCCuboidRegion;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.worldedit.WEPlatform;
import br.com.finalcraft.evernifecore.minecraft.worldedit.block.FCBaseBlock;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.FCBlockArrayClipboard;
import br.com.finalcraft.evernifecore.minecraft.worldedit.clipboard.format.IFCClipboardManager;
import br.com.finalcraft.evernifecore.minecraft.worldedit.region.IFCCuboidRegion;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.PolygonShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import jakarta.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ImpWEPlatform extends WEPlatform {

    public ImpWEPlatform() {
        super(new IFCClipboardManager(
                        new IFCClipboardFormat_Schematic(),
                        null
                ),
                new ImpIFCOperationManager()
        );
    }

    @Override
    public IFCCuboidRegion createCuboidRegion(BlockPos pos1, BlockPos pos2) {
        return new ImpIFCCuboidRegion(new CuboidRegion(
                new Vector(pos1.getX(), pos1.getY(), pos1.getZ()),
                new Vector(pos2.getX(), pos2.getY(), pos2.getZ())
        ));
    }

    @Override
    public FCBlockArrayClipboard createBlockArrayClipboard(IFCCuboidRegion region) {
        return new ImpFCBlockArrayClipboard(
                new BlockArrayClipboard(
                        region.getHandle()
                )
        );
    }

    public void setBlock(World world, BlockPos blockPos, FCBaseBlock blockBase, boolean placeFast) throws WorldEditException {
        world.setBlock(new Vector(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                (BaseBlock) blockBase.getHandle(),
                placeFast
        );
    }

    @Override
    public @Nullable Shape getSelection(Player player) {
        WorldEditPlugin worldEdit = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
        Region selection;
        try {
            selection = worldEdit.getSession(player).getSelection(BukkitUtil.getLocalWorld(player.getWorld()));
        } catch (IncompleteRegionException incomplete) {
            return null;
        }
        if (selection instanceof Polygonal2DRegion) {
            Polygonal2DRegion polygon = (Polygonal2DRegion) selection;
            List<BlockPos> points = new ArrayList<>();
            for (BlockVector2D point : polygon.getPoints()) {
                points.add(BlockPos.of(point.getBlockX(), polygon.getMinimumY(), point.getBlockZ()));
            }
            return PolygonShape.of(points, polygon.getMinimumY(), polygon.getMaximumY());
        }
        Vector min = selection.getMinimumPoint();
        Vector max = selection.getMaximumPoint();
        return CuboidShape.of(BlockPos.of(min.getBlockX(), min.getBlockY(), min.getBlockZ()),
                BlockPos.of(max.getBlockX(), max.getBlockY(), max.getBlockZ()));
    }
}
