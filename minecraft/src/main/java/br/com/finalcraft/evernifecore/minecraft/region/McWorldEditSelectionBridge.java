package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.minecraft.worldedit.WEPlatform;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;

/** Reads a player's WorldEdit selection as a region shape; what to do with it is the calling plugin's call. */
public final class McWorldEditSelectionBridge {

    private McWorldEditSelectionBridge() {
    }

    /**
     * The player's complete WorldEdit selection as a shape - a polygon keeps its Y span, any other selection is the box
     * of its extremes; null without WorldEdit, or while the selection is incomplete.
     */
    public static @Nullable Shape selectionOf(Player player) {
        return WEPlatform.isPresent() ? WEPlatform.getInstance().getSelection(player) : null;
    }
}
