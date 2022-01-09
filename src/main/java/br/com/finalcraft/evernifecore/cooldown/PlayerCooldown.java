package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

import java.util.UUID;

public class PlayerCooldown extends Cooldown {

    private final UUID uuid;

    public PlayerCooldown(String identifier, UUID uuid) {
        super(identifier);
        this.uuid = uuid;
    }

    public PlayerCooldown(Cooldown cooldown, UUID uuid) {
        super(cooldown.identifier, cooldown.timeStart, cooldown.timeDuration, cooldown.persist);
        this.uuid = uuid;
    }

    public PlayerData getPlayerData() {
        return PlayerController.getPlayerData(uuid);
    }

    @Override
    public void handleSaveIfPermanent() {
        PlayerData playerData = getPlayerData();
        Config config = playerData.getConfig();
        config.setValue("Cooldown." + this.getIdentifier(), this);
        playerData.setRecentChanged();
    }

    @Override
    public void handleStopIfPermanent() {
        PlayerData playerData = getPlayerData();
        Config config = playerData.getConfig();
        config.setValue("Cooldown." + this.getIdentifier(), null);
        playerData.setRecentChanged();
    }
}
