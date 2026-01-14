package br.com.finalcraft.evernifecore.integration.worldedit;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Mask2D;
import org.bukkit.World;

import javax.annotation.Nullable;

public abstract class CustomMask implements Mask {

    public Clipboard clipboard;
    public World world;
    public Vector targetLocation;

    public void setupFor(Clipboard clipboard, World world, Vector targetLocation){
        this.clipboard = clipboard;
        this.world = world;
        this.targetLocation = targetLocation;
    }

    @Nullable
    @Override
    public Mask2D toMask2D() {
        return null;
    }

}
