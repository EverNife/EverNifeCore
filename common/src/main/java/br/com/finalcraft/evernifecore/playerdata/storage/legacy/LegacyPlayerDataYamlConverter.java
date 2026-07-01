package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.cooldown.GenericCooldown;
import br.com.finalcraft.evernifecore.cooldown.PlayerCooldown;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;

import java.util.Objects;
import java.util.UUID;

/**
 * Converts the BASE block of a legacy per-player YAML file
 * ({@code plugins/EverNifeCore/PlayerData/&lt;player&gt;.yml}, ECore 2.x) into the new
 * storage entity {@link PlayerData}.
 *
 * <p>Legacy layout (written by the old {@code PlayerData.savePlayerData()}):</p>
 * <pre>
 * PlayerData:
 *   Username: Petrus
 *   UUID: 068117bc-...
 *   firstSeen: 1600000000000
 *   lastSeen:  1700000000000
 *   lastSaved: 1700000000001
 * </pre>
 *
 * <p>The legacy {@code Cooldown:} block is NOT imported: the player-cooldown flow was removed
 * from PlayerData and will be redesigned as its own storage entity. The block stays intact in
 * the archived YAML file, so a future importer can still read it.</p>
 *
 * <p>PDSection blocks (any other root key) are handled by the binding routing
 * {@code legacyYaml(rootKey, adapter)} in {@link LegacyPlayerDataImporter}.</p>
 */
public final class LegacyPlayerDataYamlConverter {

    private LegacyPlayerDataYamlConverter() {
    }

    public static PlayerData convertBase(Config legacyConfig) {
        String name = Objects.requireNonNull(legacyConfig.getString("PlayerData.Username"),
                "Missing 'PlayerData.Username'!");
        UUID uuid = Objects.requireNonNull(legacyConfig.getUUID("PlayerData.UUID"),
                "Missing or invalid 'PlayerData.UUID'!");

        long now = System.currentTimeMillis();
        long firstSeen = legacyConfig.getLong("PlayerData.firstSeen", now);
        long lastSeen = legacyConfig.getLong("PlayerData.lastSeen", now);
        long lastSaved = legacyConfig.getLong("PlayerData.lastSaved", lastSeen);

        PlayerData playerData = new PlayerData(uuid, name, firstSeen, lastSeen, lastSaved);

        //Player cooldown was DECOUPLED from PlayerData (pending rewrite as its own storage entity).
        //The legacy 'Cooldown:' block is intentionally NOT imported here; the original reader is kept
        //commented as a marker so a future cooldown importer knows exactly where it used to live:
        //for (String cooldownKey : legacyConfig.getKeys("Cooldown")) {
        //    String base = "Cooldown." + cooldownKey + ".";
        //    String identifier = legacyConfig.getString(base + "identifier", cooldownKey);
        //    long timeStart = legacyConfig.getLong(base + "timeStart", 0L);
        //    long timeDuration = legacyConfig.getLong(base + "timeDuration", 0L);
        //    //only persistent cooldowns were ever written to the player's legacy file
        //    PlayerCooldown playerCooldown = new PlayerCooldown(
        //            new GenericCooldown(identifier, timeStart, timeDuration, true), uuid);
        //    playerData.getCooldownHashMap().put(identifier, playerCooldown);
        //}

        return playerData;
    }
}
