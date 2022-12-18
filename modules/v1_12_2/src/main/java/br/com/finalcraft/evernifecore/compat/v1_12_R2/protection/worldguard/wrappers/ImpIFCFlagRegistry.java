package br.com.finalcraft.evernifecore.compat.v1_12_R2.protection.worldguard.wrappers;

import br.com.finalcraft.evernifecore.protection.worldguard.IFCFlagRegistry;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ImpIFCFlagRegistry implements IFCFlagRegistry {

    @Override
    public void register(Flag<?> flag) {
        WorldGuardPlugin.inst().getFlagRegistry().register(flag);
    }

    @Override
    public @Nullable Flag<?> get(String name) {
        return WorldGuardPlugin.inst().getFlagRegistry().get(name);
    }

    @Override
    public List<Flag<?>> getAll() {
        List<Flag<?>> flags = new ArrayList<>();
        iterator().forEachRemaining(flag -> flags.add(flag));
        return flags;
    }

    @Override
    public int size() {
        return WorldGuardPlugin.inst().getFlagRegistry().size();
    }

    @NotNull
    @Override
    public Iterator<Flag<?>> iterator() {
        return WorldGuardPlugin.inst().getFlagRegistry().iterator();
    }

    @Override
    public Object getHandle() {
        return WorldGuardPlugin.inst().getFlagRegistry();
    }
}
