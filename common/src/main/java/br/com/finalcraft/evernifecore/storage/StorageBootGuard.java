package br.com.finalcraft.evernifecore.storage;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;


/**
 * Turns a storage-unavailable boot into an admin-readable stop. It is the ONLY place that reads the
 * stop-the-server setting, so the policy has one home for the core, for a reload, and for any plugin
 * that opts in later.
 */
public final class StorageBootGuard {

    private StorageBootGuard() {
    }

    /**
     * Logs the report and, on a BOOT with the setting on, stops the server. Never throws - the
     * caller keeps owning the exception, so the platform still unwinds and disables the plugin.
     *
     * @param reloading true when a live storage is still serving (a failed reload never stops anything)
     */
    public static void onStorageUnavailable(StorageUnavailableException failure, boolean reloading) {
        boolean stopping = !reloading && ECSettings.STOP_SERVER_IF_STORAGE_IS_UNREACHABLE;

        //stack traces FIRST, banner LAST: the banner is what must stay on the admin's screen
        for (StorageInitFailure each : failure.getFailures()) {
            EverNifeCore.getLog().severe("Storage backend '{}' failed to initialize:", each.getBackendName());
            each.getCause().printStackTrace();
        }
        for (String line : StorageBootReport.render(failure, stopping, reloading)) {
            //the rendered report quotes backend names and driver messages; a '{}' in one of them must
            //reach the console as text, not be read as a placeholder
            EverNifeCore.getLog().severe("{}", line);
        }

        if (stopping) {
            EverNifeCore.getPlatform().shutdown("EverNifeCore could not contact "
                    + failure.getFailures().size() + " storage backend(s) - see the report above");
        }
    }
}
