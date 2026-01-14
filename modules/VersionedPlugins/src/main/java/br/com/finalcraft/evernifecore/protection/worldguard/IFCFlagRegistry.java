package br.com.finalcraft.evernifecore.protection.worldguard;

import com.sk89q.worldguard.protection.flags.Flag;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Keeps track of registered flags.
 *
 * This is a wrapper for https://github.com/EngineHub/WorldGuard/blob/master/worldguard-core/src/main/java/com/sk89q/worldguard/protection/flags/registry/FlagRegistry.java
 *
 * As this is not present on WorldGuard 6.1 for MC 1.7.10, we need to create our own version of it!
 */
public interface IFCFlagRegistry extends Iterable<Flag<?>> {

    /**
     * Register a new flag.
     *
     * @param flag The flag
     */
    void register(Flag<?> flag);

    /**
     * Get af flag by its name.
     *
     * @param name The name
     * @return The flag, if it has been registered
     */
    @Nullable
    Flag<?> get(String name);

    /**
     * Get all flags
     *
     * @return All flags
     */
    List<Flag<?>> getAll();

    /**
     * Get the number of registered flags.
     *
     * @return The number of registered flags
     */
    int size();

    //Only present on non MCVersion > 1.7.10
    public Object getHandle();
}