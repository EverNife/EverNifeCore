package br.com.finalcraft.evernifecore.material.interpreters;

import br.com.finalcraft.evernifecore.util.FCInputReader;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.version.MCVersion;
import com.google.common.collect.Sets;
import org.bukkit.Material;

import java.util.HashSet;

public class MaterialSignInterpreter {

    private final Class SIGN_DATA_CLASS_LEGACY = MCVersion.isLowerEquals(MCVersion.v1_12) ? FCReflectionUtil.getClass("org.bukkit.material.Sign") : null;
    private final Class SIGN_DATA_CLASS_MODERN = !MCVersion.isLowerEquals(MCVersion.v1_12) ? FCReflectionUtil.getClass("org.bukkit.block.data.type.Sign") : null;
    private final Class WAALL_SIGN_DATA_CLASS_MODERN = !MCVersion.isLowerEquals(MCVersion.v1_12) ? FCReflectionUtil.getClass("org.bukkit.block.data.type.WallSign") : null;

    private HashSet<Material> SIGN_MATERIALS_1_12_BELLOW = Sets.newHashSet(
            FCInputReader.parseMaterial("SIGN"),
            FCInputReader.parseMaterial("SIGN_POST"),
            FCInputReader.parseMaterial("WALL_SIGN")
    );

    public boolean isSign(Material material){
        if (MCVersion.isLowerEquals(MCVersion.v1_12)){
            return SIGN_MATERIALS_1_12_BELLOW.contains(material) || material.getData() == SIGN_DATA_CLASS_LEGACY;
        }else {
            //Field isLegacy() is only present on post 1.13 MC version
            if (!material.isLegacy()){
                return material.data == SIGN_DATA_CLASS_MODERN || material.data == WAALL_SIGN_DATA_CLASS_MODERN; //getData() willl throw exeption on modern materials! So lets use the public reference!
            }else {
                return material.getData() == SIGN_DATA_CLASS_LEGACY;
            }
        }
    }

}
