package br.com.finalcraft.evernifecore.config.fcconfiguration.annotation;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;

public interface FConfigComplex {

    public default void onConfigSavePre(ConfigSection section){

    }

    public default void onConfigSavePost(ConfigSection section){

    }

    public default void onConfigLoadPre(ConfigSection section){

    }

    public default void onConfigLoadPost(ConfigSection section){

    }

}
