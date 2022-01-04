package br.com.finalcraft.evernifecore.autoupdater;

import br.com.finalcraft.evernifecore.api.events.ECFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCScheduller;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public class SpigotUpdateChecker {

    @FCLocale(lang = LocaleType.EN_US,
            text = " §a[❤Update❤] - §6[§e%plugin%§6]§a Update Available! §7[Click]",
            hover = "§bYou can disable this message by \ndisabling the UpdateChecker on \nthe config or by enabling the\nAutoDownloader feature!"
    )
    private static LocaleMessage UPDATE_IS_AVAILABLE;

    private enum UpdateResult {
        DISABLED,
        ALREADY_UPDATED,
        FAIL_TO_CHECK,
        UPDATE_AVAILABLE
    }

    public static void checkForUpdates(@NotNull JavaPlugin plugin, @NotNull String resourceId, @Nullable Config config){
        FCScheduller.runAssync(() -> {
            new SpigotUpdateChecker(plugin, resourceId, config);
        });
    }

    public static void checkForUpdates(@NotNull JavaPlugin plugin, @NotNull String resourceId){
        checkForUpdates(plugin, resourceId, null);
    }

    private static Map<String, BukkitPluginJar> MAPPED_JARS = new WeakHashMap<>();
    private void cleanOldJars(JavaPlugin plugin){
        for (File pluginFile : FileUtils.listFiles(plugin.getDataFolder().getParentFile(), new String[]{"jar"}, false)) {
            BukkitPluginJar bukkitPluginJar = MAPPED_JARS.computeIfAbsent(pluginFile.getAbsolutePath(), s -> {
                try {
                    return new BukkitPluginJar(pluginFile);
                }catch (Exception ignored){

                }
                return null;
            });
            if (bukkitPluginJar != null){
                if (bukkitPluginJar.pluginName.equalsIgnoreCase(plugin.getDescription().getName()) && !bukkitPluginJar.version.equalsIgnoreCase(plugin.getDescription().getVersion())){

                    if (currentVersion.compareTo(bukkitPluginJar.version) <= 0){
                        plugin.getLogger().warning("---------------------------------------------------------------------" +
                                "[UpdateChecker] I was going to delete the plugin [" + bukkitPluginJar.file.getAbsolutePath() + "] but its seems to be newer than the current version? Is that correct?!" +
                                "\nThis can happen if you have renamed the " + plugin.getName() + " plugin's jar name! This way the AutoUpdater will not work!" +
                                "\n" +
                                "\nYou should disable the AutoUpdater or do not manually rename this jar! Besides that, you need to manually delete older versions of this plugin!" +
                                "\n---------------------------------------------------------------------");
                        continue;
                    }

                    if (!bukkitPluginJar.file.delete()){
                        plugin.getLogger().warning("[UpdateChecker] I was not able to delete an older version of this plugin at [" + bukkitPluginJar.file.getAbsolutePath()  + "]! You will have to delete it manually!");
                    }else {
                        plugin.getLogger().warning("[UpdateChecker] Deleted an older version [" + bukkitPluginJar.version + "] of this plugin that was in the Plugins Folder!");
                    }
                }
            }
        }
    }

    private final String resourceId;
    private final boolean checkForUpdates;
    private final boolean autoDownload;
    private final String currentVersion;
    private transient String newVersion = null;

    public SpigotUpdateChecker(@NotNull JavaPlugin plugin, @NotNull String resourceId, @Nullable Config config) {
        this.resourceId = resourceId;
        this.currentVersion = plugin.getDescription().getVersion();

        if (config != null){
            checkForUpdates = config.getOrSetDefaultValue("UpdateChecker.checkForUpdates", true);
            autoDownload    = config.getOrSetDefaultValue("UpdateChecker.autoDownloadNewUpdates", false);
            config.saveIfNewDefaults();
        }else {
            checkForUpdates = true;
            autoDownload = false;
        }

        if (checkForUpdates == false){
            plugin.getLogger().warning("[UpdateChecker] Update Check is disabled!");
            return; //Don't need to do anything more!
        }

        UpdateResult updateResult = checkForUpdates(plugin);

        switch (updateResult){
            case ALREADY_UPDATED:
                plugin.getLogger().warning("[UpdateChecker] Update Check is Done! This plugins is Already Up-to-Date!");
                cleanOldJars(plugin); //Clean possible old jars of this plugin on the Plugins Directory
                return;
            case FAIL_TO_CHECK: //Error already printed in place
                return;
        }

        //If we are here, we have an update to be done!

        if (!autoDownload){ //If we are not downloading it, we need to warn staffs on join
            final String SPIGOT_URL = "https://www.spigotmc.org/resources/" + resourceId + "/";
            ECListener.register(plugin, new ECListener() {
                private final String PERMISSION = plugin.getName().toLowerCase() + ".updatechecker.warn";

                @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
                public void onPlayerMove(ECFullyLoggedInEvent event) {
                    if (event.getPlayer().isOp() || event.getPlayer().hasPermission(PERMISSION)){
                        UPDATE_IS_AVAILABLE
                                .addPlaceholder("%plugin%", plugin.getName())
                                .addLink(SPIGOT_URL)
                                .send(event.getPlayer());
                    }
                }
            });

            plugin.getLogger().info(
                    "\n" +
                            UPDATE_IS_AVAILABLE
                                    .addPlaceholder("%plugin%", plugin.getName())
                                    .getFancyText(Bukkit.getConsoleSender())
                                    .getText() +
                            "\n URL: " + SPIGOT_URL +
                            "\n"
            );
            return;
        }

        plugin.getLogger().info("[UpdateChecker] Update Check is Done! New update found, starting download!");

        downloadNewVersion(plugin);
    }

    private UpdateResult checkForUpdates(JavaPlugin plugin) {
        try {
            HttpsURLConnection connection = (HttpsURLConnection) new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openConnection();
            int timed_out = 5000;
            connection.setConnectTimeout(timed_out);
            connection.setReadTimeout(timed_out);
            this.newVersion = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();
            connection.disconnect();

            System.out.println("New Version: " + newVersion + " compare == " + this.currentVersion.compareTo(newVersion));
            if (this.currentVersion.compareTo(newVersion) > 0){
                return UpdateResult.ALREADY_UPDATED;
            }

            return UpdateResult.UPDATE_AVAILABLE;
        } catch (Exception e) {
            plugin.getLogger().warning("[UpdateChecker] Error while checking for updates:");
            e.printStackTrace();
            return UpdateResult.FAIL_TO_CHECK;
        }
    }

    private void downloadNewVersion(JavaPlugin plugin) {
        String fileName = plugin.getName() + "-" + newVersion + ".jar"; //Default naming in case the header is not present!

        FileOutputStream fos = null;
        ReadableByteChannel rbc = null;
        try {
            URLConnection con = new URL("https://api.spiget.org/v2/resources/" + resourceId + "/download").openConnection();

            String contentDisposition = con.getHeaderField("Content-Disposition");
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                fileName = contentDisposition.substring(contentDisposition.indexOf("filename=") + 9).split(Pattern.quote(" "), 2)[0];
            }

            File destFolder = new File(plugin.getDataFolder().getParentFile(), fileName);

            if (destFolder.exists()){
                plugin.getLogger().warning("[UpdateChecker] Error downloading the new update: " + newVersion + " as there is already a file named " + fileName + " under the Plugins folder. You need to manually delete all old versions of this plugins!");
                return;
            }

            plugin.getLogger().info("[UpdateChecker] Starting download of [" + fileName + "]");

            rbc = Channels.newChannel(con.getInputStream());
            fos = new FileOutputStream(new File(plugin.getDataFolder().getParentFile(), fileName));
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

            plugin.getLogger().info("[UpdateChecker] Download of the update [" + fileName + "] has finished!");
        } catch (Exception e) {
            plugin.getLogger().warning("[UpdateChecker] Error while downloading the update: " + fileName);
            e.printStackTrace();
            return;
        } finally {
            try {
                if (fos != null) fos.close();
                if (rbc != null) rbc.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}
