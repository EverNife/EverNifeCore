package br.com.finalcraft.evernifecore.storage.config;

import br.com.finalcraft.everyconfig.config.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Appends the auto-generated {@code pdsections.<Plugin>.<SectionClass>} entry to
 * storage.yml on the first registration of a PDSection, through the EveryConfig
 * {@link Config} (with comment support):
 *
 * <pre>
 * pdsections:
 *   # PDSection created by the Plugin [FinalJobs] authored by: EverNife
 *   FinalJobs:
 *     JobsPDSection:
 *       # Recommended Backend Types: localfile | mysql | mongo
 *       storage-backend-id: localfile
 * </pre>
 */
public final class PDSectionYamlWriter {

    private PDSectionYamlWriter() {
    }

    /**
     * Writes the entry if absent. The {@code storage-backend-id} value is the developer's
     * default (or the global default); the {@code Recommended Backend Types} comment lists
     * the developer's suggestions (or all declared backend ids when none).
     *
     * @return true when the entry was written (file saved); false when it already existed
     */
    public static boolean ensureEntry(Config storageYml,
                                      String pluginName, String pluginAuthor, String sectionName,
                                      String backendValue,
                                      List<String> suggestedBackends,
                                      Collection<String> allBackendIds) {
        String pluginPath = "pdsections." + pluginName;
        String sectionPath = pluginPath + "." + sectionName;

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
        storageYml.setComment(sectionPath + ".storage-backend-id",
                "Recommended Backend Types: " + String.join(" | ", possible));

        storageYml.save();
        return true;
    }
}
