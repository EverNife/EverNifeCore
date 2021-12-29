package br.com.finalcraft.evernifecore.config.playerdata;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import org.bukkit.entity.Player;

import java.util.UUID;

public interface IPlayerData {

    public PlayerData getPlayerData();

    public String getPlayerName();

    public UUID getUniqueId();

    public boolean isPlayerOnline();

    public Player getPlayer();

    public Config getConfig();

    public long getLastSeen();

    public PlayerCooldown getCooldown(String identifier);

    public <T extends PDSection> T getPDSection(Class<? extends T> pdSectionClass);

}
