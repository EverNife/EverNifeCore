package br.com.finalcraft.evernifecore.nms.data;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public abstract class IMcBlockWrapper implements IHasMinecraftIdentifier {

    abstract public Object getMCBlock();

    public boolean hasItemStack(){
        //Some blocks don't have ItemStacks, like WALL_SIGN
        IMcItemWrapper possibleItem = NMSUtils.get().getItemRegistry().getObject(this.getMCIdentifier());
        return possibleItem != null;
    }

    public ItemStack getItemStack(){
        //Some blocks don't have ItemStacks, like WALL_SIGN
        IMcItemWrapper possibleItem = NMSUtils.get().getItemRegistry().getObject(this.getMCIdentifier());
        return possibleItem == null ? null : possibleItem.getItemStack();
    }

    @Override
    public int hashCode() {
        return getMCIdentifier() == null ? 0 : getMCIdentifier().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IMcBlockWrapper) {
            return Objects.equals(getMCBlock(), ((IMcBlockWrapper) obj).getMCBlock());
        }
        return false;
    }
}
