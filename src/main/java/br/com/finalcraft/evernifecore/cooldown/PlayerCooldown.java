package br.com.finalcraft.evernifecore.cooldown;

import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;

import java.util.UUID;

public class PlayerCooldown extends Cooldown {

    private final UUID uuid;

    public PlayerCooldown(String identifier, UUID uuid) {
        super(identifier);
        this.uuid = uuid;
    }

    public PlayerData getPlayerData() {
        return PlayerController.getPlayerData(uuid);
    }

    @Override
    public Cooldown setDuration(long timeDuration) {
        getPlayerData().setRecentChanged();
        return super.setDuration(timeDuration);
    }

    @Override
    public Cooldown setPersist(boolean persist) {
        getPlayerData().setRecentChanged();
        return super.setPersist(persist);
    }

    @Override
    public void start() {
        getPlayerData().setRecentChanged();
        super.start();
    }

    @Override
    public void handleSaveIfPermanent() {
        //The save occurs inside PlayerData class
    }

    @Override
    public void handleStopIfPermanent() {
        //The save occurs inside PlayerData class
    }
}
