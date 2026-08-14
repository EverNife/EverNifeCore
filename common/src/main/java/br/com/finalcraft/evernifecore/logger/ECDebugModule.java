package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;

public enum ECDebugModule implements IDebugModule {
    HYTALE_FPLAYER("Logs related to the HytaleFPlayer implementation and its methods.", true),
    ARG_PARSER("Logs related to the CommandSystem '@Arg' Context checks.", true),
    CONTEXTUAL_ARG_PARSER("Logs related to the CommandSystem '@Arg.Contextual' Context checks.", true),
    SV_WORLD_DATA("Logs what every SVWorldDataManager block store preloads and flushes.", true),
    COMMAND_REGISTRY("Logs every command path removed, and every alias overridden, by a 'commands/<PluginName>.yml' file.", true),
    ;

    private final String comment;
    private final boolean enabledByDefault;
    private boolean enabled;

    ECDebugModule(String comment, boolean enabledByDefault) {
        this.comment = comment;
        this.enabledByDefault = enabledByDefault;
        this.enabled = enabledByDefault;
    }

    @Override
    public ECPluginData getPluginData() {
        return EverNifeCore.instance.getEcPluginData();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String getComment() {
        return comment;
    }
}
