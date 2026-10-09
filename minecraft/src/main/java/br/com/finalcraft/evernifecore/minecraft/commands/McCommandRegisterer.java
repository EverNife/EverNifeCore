package br.com.finalcraft.evernifecore.minecraft.commands;

import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.minecraft.commands.debug.*;

public class McCommandRegisterer {

    public static void registerCommands(ECPluginData ecPluginData) {

        FinalCMDManager.registerCommand(ecPluginData, CMDBiomeInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDBlockInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDEntityInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDItemInfo.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDProtectionTest.class);
        FinalCMDManager.registerCommand(ecPluginData, CMDGuiLayout.class);

        //every served revision has an item dictionary: Forge's OreDictionary or the item tags
        if (NMSUtils.isSupported()){
            FinalCMDManager.registerCommand(ecPluginData, CMDOreDictInfo.class);
        }

    }

}
