package br.com.finalcraft.evernifecore.minecraft.region.selection;

import br.com.finalcraft.evernifecore.api.platoverride.item.NativeItem;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** {@link SelectionTool} for a Bukkit plugin that brings its own tool item. */
public final class McSelectionTool {

    private McSelectionTool() {
    }

    /** {@link SelectionTool#start} with {@code tool} as the selection tool: it is handed out as given, plus the selection mark. */
    public static SelectionSession start(Player player, SelectionSpec spec, ItemStack tool, Consumer<Shape> onConfirm) {
        return SelectionTool.start(FCBukkitUtil.adapt(player), spec.withTool(spec.getTool().withNativeItem(new NativeItem(tool))), onConfirm);
    }
}
