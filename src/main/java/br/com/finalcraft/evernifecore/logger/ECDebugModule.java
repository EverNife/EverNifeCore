package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;

public enum ECDebugModule implements IDebugModule {
    TESTE,
    ;

    private boolean enabled = true;

    @Override
    public String getName() {
        return this.name();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean onConfigLoad(ConfigSection section) {
        return false;
    }
}
