package br.com.finalcraft.evernifecore.minecraft.config;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.minecraft.autoupdater.SpigotUpdateChecker;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.DefaultIcons;
import br.com.finalcraft.evernifecore.minecraft.guis.LayoutManager;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;

import java.util.Arrays;
import java.util.Objects;

public class McConfigManager {

    public static void initialize(ECPluginData ecPluginData){

        FCLocaleManager.loadLocale(ecPluginData,
                Arrays.asList(
                        FCBukkitUtil.class,
                        SpigotUpdateChecker.class,
                        DefaultIcons.class
                ).stream().filter(Objects::nonNull).toArray(Class[]::new)
        );

        LayoutManager.initialize();//This uses some locales. Must be called after FCLocaleManager;
    }

}
