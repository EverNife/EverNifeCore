package br.com.finalcraft.evernifecore.playerdata;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyImportReport;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyMigrationMetadata;
import br.com.finalcraft.evernifecore.playerdata.storage.legacy.LegacyPlayerDataImporter;

import java.io.File;
import java.util.Locale;

/**
 * The one-time legacy YAML import of a first boot: detects the pending import, runs it on the
 * first server tick (after every plugin registered its sections + legacyYaml adapters) and then
 * releases the {@code ready} gate that is holding the logins.
 */
final class LegacyBootstrap {

    private final PlayerController controller;

    LegacyBootstrap(PlayerController controller) {
        this.controller = controller;
    }

    /**
     * Trigger: legacy {@code PlayerData/*.yml} files exist AND the progress file does not declare the
     * migration finished. To force a re-migration, delete the generated artifacts (the progress file
     * and the consolidated {@code __LegacyData_V2} archive) and restore the {@code PlayerData/*.yml}
     * files - the import then runs again and skips whatever already reached the backend.
     *
     * <p>Deliberately blind to how many rows the backend already holds: a legitimate second run
     * happens over a populated collection (the plugin owning a root key may only be installed on a
     * later boot), so counting rows would answer the wrong question - and answer it with an I/O
     * round-trip on every single boot.</p>
     */
    boolean isImportPending(File legacyFolder) {
        boolean hasYmlFiles = hasYmlFiles(legacyFolder);
        LegacyMigrationMetadata metadata = LegacyMigrationMetadata.load(LegacyMigrationMetadata.fileOf(legacyFolder));
        return !metadata.isComplete() && hasYmlFiles;
    }

    private static boolean hasYmlFiles(File legacyFolder) {
        File[] ymlFiles = legacyFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        return ymlFiles != null && ymlFiles.length > 0;
    }

    /**
     * First-tick continuation of a bootstrap with a pending import: binds every section
     * known so far (the import routes each legacyYaml adapter through its resolved
     * binding), runs the importer, does the normal load and releases the {@code ready} gate that is
     * holding the logins.
     */
    void runImportThenStart(File legacyFolder) {
        try {
            //register the framework's own cooldown rows first, so the local row's legacyYaml("Cooldown")
            //adapter is among the bindings the importer scans - start() runs only AFTER the import here
            controller.registerBuiltinSectionsOnce();
            for (PDSectionConfiguration<?> configuration : PlayerController.getConfiguredPDSections().values()) {
                controller.bindSection(configuration); //no player loaded yet -> bind only, no hot-load
            }
            LegacyImportReport report = new LegacyPlayerDataImporter(legacyFolder,
                    controller.playerDataBinding(), controller.sectionBindings()).run();
            if (report.hasFailures()) {
                EverNifeCore.getLog().warning(report.format());
            } else {
                EverNifeCore.getLog().info(report.format());
            }
        } catch (Throwable importFailure) {
            EverNifeCore.getLog().severe("The legacy PlayerData import failed unexpectedly - the server will continue"
                    + " WITHOUT the legacy data (the remaining .yml files were not touched). Fix the"
                    + " cause and restart; already-imported players are skipped by idempotency.");
            importFailure.printStackTrace();
        }

        try {
            controller.start();            //sections already bound -> runs only the hot-load
            controller.completeReady();    //release the logins that were held
        } catch (Throwable bootFailure) {
            EverNifeCore.getLog().severe("Failed to load the players after the legacy import!");
            bootFailure.printStackTrace();
            controller.failReady(bootFailure);
        }
    }
}
