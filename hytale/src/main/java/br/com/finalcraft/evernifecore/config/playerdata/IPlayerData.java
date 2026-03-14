package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;

import java.util.UUID;

public interface IPlayerData {

    public PlayerData getPlayerData();

    public String getName();

    public UUID getUniqueId();

    public boolean isPlayerOnline();

    public FPlayer getPlayer();

    public Config getConfig();

    public long getFirstSeen();

    public long getLastSeen();

    public long getLastSaved();

    public PlayerCooldown getCooldown(String identifier);

    public <T extends PDSection> T getPDSection(Class<T> pdSectionClass);

}
