package br.com.finalcraft.evernifecore.commands;

import br.com.finalcraft.evernifecore.commands.debug.*;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.misc.*;
import br.com.finalcraft.evernifecore.nms.util.NMSUtils;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandRegisterer {

    public static void registerCommands(JavaPlugin pluginInstance) {

        FinalCMDManager.registerCommand(pluginInstance, CoreCommand.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDCooldown.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDSvInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetName.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDGetUUID.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDBiomeInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDBlockInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDEntityInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDItemInfo.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDECLocale.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDProtectionTest.class);
        FinalCMDManager.registerCommand(pluginInstance, CMDECDynamicCommand.class);
        if (FCBukkitUtil.isForge()){
            try {
                NMSUtils.get().getOreRegistry();
                FinalCMDManager.registerCommand(pluginInstance, CMDOreDictInfo.class);
            }catch (Exception e){
                // ignored
            }
        }

        //FinalCMDManager.registerCommand(pluginInstance, CMDEcoTest.class);
    }

}
