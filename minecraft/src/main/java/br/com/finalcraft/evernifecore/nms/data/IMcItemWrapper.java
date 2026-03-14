package br.com.finalcraft.evernifecore.nms.data;

import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public abstract class IMcItemWrapper implements IHasMinecraftIdentifier {

    abstract public Object getMCItem();

    public ItemStack getItemStack(){
        return NMSUtils.get().asBukkitItemStack(getMCItem()).clone();
    }

    @Override
    public int hashCode() {
        return getMCIdentifier() == null ? 0 : getMCIdentifier().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IMcItemWrapper) {
            return Objects.equals(getMCItem(), ((IMcBlockWrapper) obj).getMCBlock());
        }
        return false;
    }

}
