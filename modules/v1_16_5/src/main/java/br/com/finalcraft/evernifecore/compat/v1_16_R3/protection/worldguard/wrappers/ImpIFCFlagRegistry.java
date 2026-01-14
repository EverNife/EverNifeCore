package br.com.finalcraft.evernifecore.compat.v1_16_R3.protection.worldguard.wrappers;

import br.com.finalcraft.evernifecore.protection.worldguard.IFCFlagRegistry;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Iterator;
import java.util.List;

public class ImpIFCFlagRegistry implements IFCFlagRegistry {

    @Override
    public void register(Flag<?> flag) {
        WorldGuard.getInstance().getFlagRegistry().register(flag);
    }

    @Override
    public @Nullable Flag<?> get(String name) {
        return WorldGuard.getInstance().getFlagRegistry().get(name);
    }

    @Override
    public List<Flag<?>> getAll() {
        return WorldGuard.getInstance().getFlagRegistry().getAll();
    }

    @Override
    public int size() {
        return WorldGuard.getInstance().getFlagRegistry().size();
    }

    @Nonnull
    @Override
    public Iterator<Flag<?>> iterator() {
        return WorldGuard.getInstance().getFlagRegistry().iterator();
    }

    @Override
    public Object getHandle() {
        return WorldGuard.getInstance().getFlagRegistry();
    }
}
