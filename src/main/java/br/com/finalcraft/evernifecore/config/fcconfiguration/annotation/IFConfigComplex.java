package br.com.finalcraft.evernifecore.config.fcconfiguration.annotation;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;

public interface IFConfigComplex {

    public default void onConfigSavePre(ConfigSection section){
        //Code run before saving the config
    }

    public default void onConfigSavePost(ConfigSection section){
        //Code run after saving the config
    }

    public default void onConfigLoadPre(ConfigSection section){
        //Code run before loading the config
    }

    public default void onConfigLoadPost(ConfigSection section){
        //Code run after loading the config
    }

}
