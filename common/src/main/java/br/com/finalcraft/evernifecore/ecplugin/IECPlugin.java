package br.com.finalcraft.evernifecore.ecplugin;

public interface IECPlugin {

    public default ECPluginData getPluginData(){
        return ECPluginManager.getOrCreateECorePluginData(this);
    }

    public default void onReload() {
        
    }
}
