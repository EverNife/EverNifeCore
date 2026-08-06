package br.com.finalcraft.evernifecore.minecraft.biome;

import org.bukkit.block.Block;

import java.util.List;

/**
 * Reads biomes without naming {@code org.bukkit.block.Biome} at the call site.
 *
 * <p>Biome is an enum up to 1.20 and an interface from 1.21 on. The call opcode for the two shapes
 * differs and is frozen when the class is compiled, so one artifact cannot serve both servers: each
 * era ships its own implementation and {@link #get()} hands back the one this server can run.</p>
 */
public interface BiomeAccess {

    /**
     * The name of every biome this server knows.
     */
    List<String> biomeNames();

    /**
     * The name of the biome at {@code block}.
     */
    String nameAt(Block block);

    /**
     * Resolved once, when this interface's holder is first touched, never per call.
     */
    static BiomeAccess get() {
        return BiomeAccessHolder.INSTANCE;
    }
}
