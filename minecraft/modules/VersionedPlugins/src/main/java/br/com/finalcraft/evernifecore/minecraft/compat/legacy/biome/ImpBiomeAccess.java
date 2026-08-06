package br.com.finalcraft.evernifecore.minecraft.compat.legacy.biome;

import br.com.finalcraft.evernifecore.minecraft.biome.BiomeAccess;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Biome as the enum it is up to 1.20, so {@code values()} and {@code name()} compile to the
 * class-shaped opcodes an old server expects.
 */
public final class ImpBiomeAccess implements BiomeAccess {

    @Override
    public List<String> biomeNames() {
        List<String> names = new ArrayList<String>();
        for (Biome biome : Biome.values()) {
            names.add(biome.name());
        }
        return names;
    }

    @Override
    public String nameAt(Block block) {
        return block.getBiome().name();
    }
}
