package br.com.finalcraft.evernifecore.nms.data;

import org.bukkit.Material;

import java.util.Objects;

public abstract class IMcBlockWrapper implements IHasMinecraftIdentifier {

    abstract public Object getMCBlock();

    abstract public Material getMaterial();

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
