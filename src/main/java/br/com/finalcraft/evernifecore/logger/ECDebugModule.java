package br.com.finalcraft.evernifecore.logger;

import br.com.finalcraft.evernifecore.logger.debug.IDebugModule;

public enum ECDebugModule implements IDebugModule {
    LOCALIZATION,
    PLAYER_DATA,
    ARG_PARSER,
    SVDATA_MANAGER(null, true),
    ;

    private final String comment;
    private final boolean enabledByDefault;
    private boolean enabled = true;

    ECDebugModule() {
        this.comment = null;
        this.enabledByDefault = false;
    }

    ECDebugModule(String comment) {
        this.comment = comment;
        this.enabledByDefault = false;
    }

    ECDebugModule(String comment, boolean enabledByDefault) {
        this.comment = comment;
        this.enabledByDefault = enabledByDefault;
    }

    @Override
    public String getName() {
        return this.name();
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
