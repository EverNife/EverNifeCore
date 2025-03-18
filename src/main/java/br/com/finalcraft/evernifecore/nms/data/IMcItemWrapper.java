package br.com.finalcraft.evernifecore.nms.data;

import org.bukkit.Material;

import java.util.Objects;

public abstract class IMcItemWrapper implements IHasMinecraftIdentifier {

    abstract public Object getMCItem();

    abstract public Material getMaterial();

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
