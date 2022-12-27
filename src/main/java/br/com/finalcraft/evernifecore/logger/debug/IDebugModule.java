package br.com.finalcraft.evernifecore.logger.debug;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;

public interface IDebugModule {

    public String getName();

    public default String getComment(){
        return null;
    }

    public boolean isEnabled();

    public void setEnabled(boolean enabled);

    public default boolean onConfigLoad(ConfigSection section){
        return section.getOrSetDefaultValue("DebugModule." + getName(), true, getComment());
    }

}
