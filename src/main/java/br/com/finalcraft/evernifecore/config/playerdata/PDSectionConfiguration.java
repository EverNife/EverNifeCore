package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;

public class PDSectionConfiguration {

    private final ECPluginData pluginData;
    private final Class<? extends PDSection> pdSectionClass;
    private final boolean shouldHotLoad;

    public PDSectionConfiguration(ECPluginData pluginData, Class<? extends PDSection> pdSectionClass, boolean shouldHotLoad) {
        this.pluginData = pluginData;
        this.pdSectionClass = pdSectionClass;
        this.shouldHotLoad = shouldHotLoad;

        /**
         * The idea of this class is hold several information on how this
         * PDSection should behave.
         *
         * There will be information like:
         *  shouldHotLoad:  will be loaded as soon as PlayerData instance is created;
         *  storeDataType: UNIVERSAL, SERVER_ONLY
         *  dataStorageType: YAML, MYSQL, MIXED_MYSQL (YAML with something MYSQL)
         */
    }

    public ECPluginData getPluginData() {
        return pluginData;
    }

    public Class<? extends PDSection> getPdSectionClass() {
        return pdSectionClass;
    }

    public boolean shouldHotLoad() {
        return shouldHotLoad;
    }
}
