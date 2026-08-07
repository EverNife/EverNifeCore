package br.com.finalcraft.evernifecore.minecraft.config;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.minecraft.autoupdater.SpigotUpdateChecker;
import br.com.finalcraft.evernifecore.minecraft.gui.ConfirmGui;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.DefaultIcons;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;

import java.util.Arrays;
import java.util.Objects;

public class McConfigManager {

    public static void initialize(ECPluginData ecPluginData){

        FCLocaleManager.loadLocale(ecPluginData,
                Arrays.asList(
                        FCBukkitUtil.class,
                        SpigotUpdateChecker.class,
                        DefaultIcons.class,
                        ConfirmGui.class
                ).stream().filter(Objects::nonNull).toArray(Class[]::new)
        );
    }

}
