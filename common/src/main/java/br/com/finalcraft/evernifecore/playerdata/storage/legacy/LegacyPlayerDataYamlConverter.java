package br.com.finalcraft.evernifecore.playerdata.storage.legacy;

import br.com.finalcraft.everyconfig.config.Config;
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
 * <p>This handles ONLY the base block. Every other root key - the legacy {@code Cooldown:} block
 * included - is migrated by the section it belongs to, through the {@code legacyYaml(rootKey, adapter)}
 * binding routing in {@link LegacyPlayerDataImporter}. The {@code Cooldown:} block is claimed by the
 * framework's own {@code PlayerCooldownsLocal} row, so it migrates like any other section and its file
 * is archived once done.</p>
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

        //Only the base block is converted here; the Cooldown: block (and every other section key) is
        //migrated by its own section's legacyYaml adapter - for Cooldown, PlayerCooldownsLocal.
        return new PlayerData(uuid, name, firstSeen, lastSeen, lastSaved);
    }
}
