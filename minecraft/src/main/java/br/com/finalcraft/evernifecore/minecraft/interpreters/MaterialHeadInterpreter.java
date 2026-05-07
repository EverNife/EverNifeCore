package br.com.finalcraft.evernifecore.minecraft.interpreters;

import br.com.finalcraft.evernifecore.minecraft.util.FCMaterialUtil;
import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import com.google.common.collect.Sets;
import org.bukkit.Material;

import java.util.HashSet;

public class MaterialHeadInterpreter {

    private final HashSet<Material> HEAD_MATERIALS = MCVersion.isLowerEquals(MCVersion.v1_12)
            ? Sets.newHashSet(FCMaterialUtil.parseMaterial("SKULL"), FCMaterialUtil.parseMaterial("SKULL_ITEM"))
            : Sets.newHashSet(Material.PLAYER_HEAD, Material.ZOMBIE_HEAD, Material.CREEPER_HEAD, Material.DRAGON_HEAD, Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL);

    public boolean isHead(Material material){
        return HEAD_MATERIALS.contains(material);
    }

}
