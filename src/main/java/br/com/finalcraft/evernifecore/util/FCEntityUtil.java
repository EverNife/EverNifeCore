package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.everforgelib.api.customnpcs.IScriptLivingBase;
import br.com.finalcraft.everforgelib.api.customnpcs.IScriptPlayer;
import br.com.finalcraft.evernifecore.util.reflection.MethodInvoker;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class FCEntityUtil {

    private static MethodInvoker getBukkitEntity = null;
    private static MethodInvoker getMCEntity = null;
    public static LivingEntity getBukkitEntity(IScriptLivingBase scriptLivingBase){
        if (getBukkitEntity == null){
            getMCEntity = FCReflectionUtil.getMethod(IScriptLivingBase.class, "getMCEntity");
            Class clazzEntityLivingBase = getMCEntity.get().getReturnType();
            getBukkitEntity = FCReflectionUtil.getMethod(clazzEntityLivingBase, "getBukkitEntity");
        }
        //return scriptLivingBase.getMCEntity().getBukkitEntity();
        Object mcEntity = getMCEntity.invoke(scriptLivingBase);
        return (LivingEntity) getBukkitEntity.invoke(mcEntity);
    }

    public static Player getBukkitEntity(IScriptPlayer scriptPlayer){
        return Bukkit.getPlayer(scriptPlayer.getUUID());
    }

}
