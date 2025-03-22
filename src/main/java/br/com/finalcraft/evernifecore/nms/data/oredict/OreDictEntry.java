package br.com.finalcraft.evernifecore.nms.data.oredict;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import lombok.Data;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@Data
public class OreDictEntry {

    private final String oreName;

    /**
     * Get all ItemStacks that are registered with this OreDictEntry
     *
     * This can't be cached because it may change when CraftTweaker is present.
     *
     * @return List of ItemStacks of this ORE
     */
    public List<ItemStack> getItemStacks() {
        return NMSUtils.get().getOreRegistry().getOreItemStacks(oreName);
    }
}
