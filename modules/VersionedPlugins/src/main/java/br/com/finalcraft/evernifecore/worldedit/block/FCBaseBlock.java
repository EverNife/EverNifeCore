package br.com.finalcraft.evernifecore.worldedit.block;

import com.sk89q.jnbt.CompoundTag;
import jakarta.annotation.Nullable;

public abstract class FCBaseBlock {

    public abstract Object getHandle();

    public abstract @Nullable CompoundTag getNbtData();

    public boolean hasNbtData() {
        return getNbtData() != null;
    }

    public abstract int getLegacyId();

    public abstract int getLegacyMetadata();

    public abstract boolean shouldPlaceFinal();

    public abstract boolean shouldPlaceLast();

}
