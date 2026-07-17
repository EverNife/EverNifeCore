package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.config.ConfigManager;
import br.com.finalcraft.everyconfig.config.Config;

/**
 * A cooldown owned by no one in particular, stored in this server's {@code Cooldowns.yml} under
 * {@code AllCooldowns.<identifier>} - the route behind {@link Cooldown#of(String)}.
 */
public class GenericCooldown extends Cooldown {

    public GenericCooldown(String identifier) {
        super(identifier);
    }

    public GenericCooldown(String identifier, CooldownEntry entry) {
        super(identifier, entry);
    }

    public GenericCooldown(String identifier, long timeStart, long timeDuration, boolean persist) {
        super(identifier, timeStart, timeDuration, persist);
    }

    /** This route's row is a {@code Cooldowns.yml} key, present exactly while the cooldown is persistent. */
    @Override
    protected void onMutated() {
        Config cooldowns = ConfigManager.getCooldowns();
        cooldowns.setValue("AllCooldowns." + this.getIdentifier(), isPersistent() ? this : null);
        cooldowns.saveAsync();
    }

}
