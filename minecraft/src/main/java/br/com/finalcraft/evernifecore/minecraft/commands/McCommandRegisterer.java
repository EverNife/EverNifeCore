package br.com.finalcraft.evernifecore.minecraft.commands;

import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.minecraft.commands.debug.*;
import br.com.finalcraft.evernifecore.minecraft.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;

public class McCommandRegisterer {

    public static void registerCommands(ECPluginData ecPluginData) {

        FinalCMDManager.registerCommand(ecPluginData, CMDBiomeInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDBlockInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDEntityInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDItemInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDProtectionTest.class);

        if (FCBukkitUtil.isForge()){
            try {
                NMSUtils.get().getOreRegistry();
                FinalCMDManager.registerCommand(ecPluginData, CMDOreDictInfo.class);
            }catch (Exception e){
                // ignored
            }
        }

    }

}
