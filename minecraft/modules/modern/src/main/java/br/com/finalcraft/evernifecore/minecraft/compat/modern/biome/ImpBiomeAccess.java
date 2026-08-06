package br.com.finalcraft.evernifecore.minecraft.compat.modern.biome;

import br.com.finalcraft.evernifecore.minecraft.biome.BiomeAccess;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Biome as the interface it is from 1.21 on. The source is the legacy one verbatim - 1.21 kept a
 * static {@code values()} and inherits {@code name()} from {@code OldEnum} - and only the API this
 * module compiles against differs, which is what gives these calls the interface-shaped opcodes.
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
