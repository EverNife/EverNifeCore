package br.com.finalcraft.evernifecore.material.interpreters;

import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.version.MCVersion;
import com.google.common.collect.Sets;
import org.bukkit.Material;

import java.util.HashSet;

public class MaterialHeadInterpreter {

    private final HashSet<Material> HEAD_MATERIALS = MCVersion.isLowerEquals(MCVersion.v1_12)
            ? Sets.newHashSet(FCInputReader.parseMaterial("SKULL"), FCInputReader.parseMaterial("SKULL_ITEM"))
            : Sets.newHashSet(Material.PLAYER_HEAD, Material.ZOMBIE_HEAD, Material.CREEPER_HEAD, Material.DRAGON_HEAD, Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL);

    public boolean isHead(Material material){
        return HEAD_MATERIALS.contains(material);
    }

}
