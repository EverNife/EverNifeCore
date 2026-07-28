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
 *   # PDSection created by the Plugin [FinalJobs] authored by: EverNife
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
            storageYml.setComment(pluginPath,
                    "PDSection created by the Plugin [" + pluginName + "] authored by: " + pluginAuthor);
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
     * {@code multi-platform-accounts}, and offering a per-section choice that cannot be honoured is
     * exactly the kind of knob that misleads an admin.
     *
     * <pre>
     * accountsections:
     *   # AccountSection created by the Plugin [FinalGuilds] authored by: EverNife
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
            storageYml.setComment(pluginPath, SectionFamily.ACCOUNT.getLabel()
                    + " created by the Plugin [" + pluginName + "] authored by: " + pluginAuthor);
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
}
