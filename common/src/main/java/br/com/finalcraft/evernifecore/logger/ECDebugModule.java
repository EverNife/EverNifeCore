package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.PlatformId;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;

import java.util.Arrays;

public enum ECDebugModule implements IDebugModule {
    HYTALE_FPLAYER("Logs related to the HytaleFPlayer implementation and its methods.", true, PlatformId.HYTALE),
    ARG_PARSER("Logs related to the CommandSystem '@Arg' Context checks.", true),
    CONTEXTUAL_ARG_PARSER("Logs related to the CommandSystem '@Arg.Contextual' Context checks.", true),
    SV_WORLD_DATA("Logs what every SVWorldDataManager block store preloads and flushes.", true),
    COMMAND_REGISTRY("Logs every command path removed, and every alias overridden, by a 'commands/<PluginName>.yml' file.", true),
    ;

    private final String comment;
    private final boolean enabledByDefault;
    private final String[] platforms;
    private boolean enabled;

    /** @param platforms the platforms this module exists on; none listed means every platform. */
    ECDebugModule(String comment, boolean enabledByDefault, String... platforms) {
        this.comment = comment;
        this.enabledByDefault = enabledByDefault;
        this.platforms = platforms;
        this.enabled = enabledByDefault;
    }

    @Override
    public boolean isAvailable(IPlatform platform) {
        return platforms.length == 0 || Arrays.asList(platforms).contains(platform.getPlatformProviderId());
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
