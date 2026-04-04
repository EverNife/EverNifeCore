package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.config.ConfigManager;

public class GenericCooldown extends Cooldown {

    public GenericCooldown(String identifier) {
        super(identifier);
    }

    public GenericCooldown(String identifier, long timeStart, long timeDuration, boolean persist) {
        super(identifier, timeStart, timeDuration, persist);
    }

    @Override
    public void handleSaveIfPermanent() {
        ConfigManager.getCooldowns().setValue("AllCooldowns." + this.getIdentifier(),this);
        ConfigManager.getCooldowns().saveAsync();
    }

    @Override
    public void handleStopIfPermanent() {
        ConfigManager.getCooldowns().setValue("AllCooldowns." + this.getIdentifier(),null);
        ConfigManager.getCooldowns().saveAsync();
    }

}
