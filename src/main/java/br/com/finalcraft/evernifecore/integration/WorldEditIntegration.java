package br.com.finalcraft.evernifecore.integration;

import br.com.finalcraft.evernifecore.integration.worldedit.CustomMask;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.io.Closer;
import com.sk89q.worldedit.world.registry.WorldData;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class WorldEditIntegration {

    public static WorldEditPlugin worldEditPlugin = null;

    public static boolean apiLoaded = false;

    public static void initialize(){
        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")){
            worldEditPlugin = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
            apiLoaded = true;
        }
    }

    public static boolean pasteSchematic(File schematic, Location targetLocation, CustomMask customMask){

        com.sk89q.worldedit.world.World world = new BukkitWorld(targetLocation.getWorld());
        Vector newOrigin = new Vector(targetLocation.getBlockX(), targetLocation.getBlockY(), targetLocation.getBlockZ());

        EditSession editSession = worldEditPlugin.getWorldEdit().getEditSessionFactory().getEditSession(world, -1);
        editSession.setBlockChangeLimit(-1);
        editSession.enableQueue();

        // Read the schematic and paste it into the world
        try(Closer closer = Closer.create()) {

            FileInputStream fis = closer.register(new FileInputStream(schematic));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ClipboardReader reader = ClipboardFormat.SCHEMATIC.getReader(bis);

            WorldData worldData = world.getWorldData();
            Clipboard clipboard = reader.read(worldData);
            ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard, worldData);

            // Build operation
            BlockTransformExtent extent = new BlockTransformExtent(clipboardHolder.getClipboard(), clipboardHolder.getTransform(), editSession.getWorld().getWorldData().getBlockRegistry());
            ForwardExtentCopy copy = new ForwardExtentCopy(extent, clipboard.getRegion(), clipboard.getOrigin(), editSession, newOrigin);
            copy.setTransform(clipboardHolder.getTransform());

            if (customMask != null){
                customMask.setupFor(clipboard,targetLocation.getWorld(),newOrigin);
                copy.setSourceMask(customMask);
            }

            Operations.completeLegacy(copy);
        } catch(MaxChangedBlocksException e) {
            worldEditPlugin.getLogger().warning("exceeded the block limit while restoring schematic, limit in exception: " + e.getBlockLimit() + ", limit passed by EverNifeCore: -1");
            return false;
        } catch(IOException e) {
            worldEditPlugin.getLogger().warning("An error occured while restoring schematic, enable debug to see the complete stacktrace");
            return false;
        }
        editSession.flushQueue();
        return true;
    }

}
