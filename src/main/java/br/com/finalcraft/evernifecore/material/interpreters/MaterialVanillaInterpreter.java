package br.com.finalcraft.evernifecore.material.interpreters;

import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.util.FCItemUtils;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class MaterialVanillaInterpreter {

    private Map<Material, Boolean> isVanilla = new HashMap();
    private MethodInvoker<Boolean> isForgeBlock = null;

    public MaterialVanillaInterpreter() {
        this.isVanilla.put(Material.AIR, true);

        try {
            isForgeBlock = FCReflectionUtil.getMethod(Material.class, "isForgeBlock");
        }catch (Exception ignored) {
            //Do Nothing
        }
    }

    public boolean isVanilla(Material material) {
        Boolean vanilla = isVanilla.get(material);
        if (vanilla != null){
            return vanilla;
        }

        if (isForgeBlock != null){
            //Do a first scan based on isBlock status as it's a lot faster
            try {
                boolean materialIsFromForgeBlock = isForgeBlock.invoke(material);

                if (materialIsFromForgeBlock){
                    vanilla = false;
                }else if (material.isBlock()){
                    vanilla = true;
                }

            }catch (Exception e){

            }
        }

        if (vanilla == null){
            //As it's not a block, lets get the ItemName and check the prefix
            try {
                ItemStack itemStack = new ItemStack(material);
                String identifier = FCItemUtils.getMinecraftIdentifier(itemStack, false);
                if (identifier.startsWith("minecraft:")){
                    vanilla = true;
                }else {
                    vanilla = false;
                }
            }catch (Exception ignored){
                vanilla = false;
            }
        }

        isVanilla.put(material, vanilla);
        return vanilla;
    }

}
