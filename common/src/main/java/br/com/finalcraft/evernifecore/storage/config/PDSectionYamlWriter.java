package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.everyconfig.config.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Appends the auto-generated {@code pdsections.<plugin>.<sectionId>} entry to
 * storage.yml on the first registration of a PDSection, through the EveryConfig
 * {@link Config} (with comment support):
 *
 * <pre>
 * pdsections:
 *   # PDSection created by EverNife on the Plugin: FinalJobs
 *   finaljobs:
 *     jobs:
 *       # A player's job level and progress
 *       # Recommended Backend Types: localfile | mysql | mongo
 *       storage-backend-id: localfile
 * </pre>
 *
 * <p>The key is the section's stable id, not its class name, so renaming the class leaves the
 * admin's choice where it is.</p>
 */
public final class PDSectionYamlWriter {

    private PDSectionYamlWriter() {
    }

    /**
     * The one-line credit above a plugin's entries, in the order it reads: who wrote it, then what it
     * is part of. The plugin name comes last because that is the word the admin is scanning the file
     * for, and bracketing it mid-sentence buried it.
     */
    private static String authorship(SectionFamily family, String pluginName, String pluginAuthor) {
        return family.getLabel() + " created by " + pluginAuthor + " on the Plugin: " + pluginName;
    }

    /**
     * Writes the entry if absent. The {@code storage-backend-id} value is the developer's
     * default (or the global default); the {@code Recommended Backend Types} comment lists
     * the developer's suggestions (or all declared backend ids when none), preceded by the
     * developer's description when there is one.
     *
     * @return true when the entry was written (file saved); false when it already existed
     */
    public static boolean ensureEntry(Config storageYml,
                                      String pluginKey, String pluginName, String pluginAuthor,
                                      String sectionId, String description,
                                      String backendValue,
                                      List<String> suggestedBackends,
                                      Collection<String> allBackendIds) {
        String pluginPath = "pdsections." + pluginKey;
        String sectionPath = pluginPath + "." + sectionId;

        if (storageYml.contains(sectionPath + ".storage-backend-id")) {
            return false;
        }

        List<String> possible = suggestedBackends == null || suggestedBackends.isEmpty()
                ? new ArrayList<>(allBackendIds)
                : suggestedBackends;

        boolean newPluginEntry = !storageYml.contains(pluginPath);

        storageYml.setValue(sectionPath + ".storage-backend-id", backendValue);
        if (newPluginEntry) {
            storageYml.setComment(pluginPath, authorship(SectionFamily.PLAYER, pluginName, pluginAuthor));
        }
        String recommended = "Recommended Backend Types: " + String.join(" | ", possible);
        storageYml.setComment(sectionPath + ".storage-backend-id",
                description == null || description.isEmpty()
                        ? recommended
                        : description + "\n" + recommended);

        storageYml.save();
        return true;
    }

    /**
     * Writes the {@code accountsections.<plugin>.<sectionId>} entry if absent. It carries no
     * {@code storage-backend-id}: the whole account family lives on the one backend configured under
     * {@code network}, so a per-section choice could not be honoured.
     *
     * <pre>
     * accountsections:
     *   # AccountSection created by EverNife on the Plugin: FinalGuilds
     *   finalguilds:
     *     achievements:
     *       # Achievements shared by every linked identity
     *       collection: acs_finalguilds_achievements
     * </pre>
     *
     * @return true when the entry was written (file saved); false when it already existed
     */
    public static boolean ensureAccountEntry(Config storageYml,
                                             String pluginKey, String pluginName, String pluginAuthor,
                                             String sectionId, String description,
                                             String collectionValue) {
        String pluginPath = SectionFamily.ACCOUNT.getYamlBlock() + "." + pluginKey;
        String sectionPath = pluginPath + "." + sectionId;

        if (storageYml.contains(sectionPath + ".collection")) {
            return false;
        }

        boolean newPluginEntry = !storageYml.contains(pluginPath);

        storageYml.setValue(sectionPath + ".collection", collectionValue);
        if (newPluginEntry) {
            storageYml.setComment(pluginPath, authorship(SectionFamily.ACCOUNT, pluginName, pluginAuthor));
        }
        String shared = "Shared by every identity linked to the account."
                + " Optional: cache: { policy: TTL, ttlSeconds: 300 }";
        storageYml.setComment(sectionPath + ".collection",
                description == null || description.isEmpty()
                        ? shared
                        : description + "\n" + shared);

        storageYml.save();
        return true;
    }

    /**
     * Writes the {@code network.server-cooldowns} entry if absent, so the one collection the framework
     * owns on the network backend is visible to the admin, renameable out of a name collision, and on
     * the boot report.
     *
     * <pre>
     * network:
     *   storage-backend-id: networkdata
     *   server-cooldowns:
     *     collection: ec_server_cooldowns
     * </pre>
     *
     * <p>It gets {@code collection} and {@code cache} but no {@code storage-backend-id}: the rows belong
     * to the network family, which moves as one.
     *
     * @return true when the entry was written (file saved); false when it already existed
     */
    public static boolean ensureServerCooldownsEntry(Config storageYml, String collectionValue) {
        String entryPath = "network.server-cooldowns";
        if (storageYml.contains(entryPath + ".collection")) {
            return false;
        }

        storageYml.setValue(entryPath + ".collection", collectionValue);
        storageYml.setComment(entryPath, String.join("\n",
                "Network-wide cooldowns owned by no player - Cooldown.network(id).",
                "Rename 'collection' if another plugin already claimed the name.",
                "Optional: cache: { policy: TTL, ttlSeconds: 300 }, which bounds how stale",
                "another server's write may look here when the backend has no change feed."));

        storageYml.save();
        return true;
    }
}
