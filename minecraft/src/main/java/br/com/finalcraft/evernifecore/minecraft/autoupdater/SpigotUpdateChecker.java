package br.com.finalcraft.evernifecore.minecraft.autoupdater;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.everyconfig.config.Config;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventHandler;
import br.com.finalcraft.evernifecore.eventbus.ECEventPriority;
import br.com.finalcraft.evernifecore.minecraft.McPermissionNodes;
import br.com.finalcraft.evernifecore.scheduler.FCScheduler;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

public class SpigotUpdateChecker {

    @FCLocale(lang = LocaleType.EN_US,
            text = " §a[❤Update❤] - §6[§e${plugin}§6]§a Update Available!",
            hover = "§bClick here to Open Download Link!\n\n - You can disable this message by \ndisabling the UpdateChecker on \nthe config or by enabling the\nAutoDownloader feature!"
    )
    private static LocaleMessage UPDATE_IS_AVAILABLE;

    private enum UpdateResult {
        DISABLED,
        ALREADY_UPDATED,
        FAIL_TO_CHECK,
        UPDATE_AVAILABLE
    }

    public static void checkForUpdates(@Nonnull JavaPlugin plugin, @Nonnull String resourceId, @Nullable Config config){
        SpigotUpdateChecker spigotUpdateChecker = new SpigotUpdateChecker(plugin, resourceId, config);
        if (spigotUpdateChecker.checkForUpdates == false){
            plugin.getLogger().info("[UpdateChecker] Update Check is disabled!");
            return; //Don't need to do anything more!
        }

        ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(plugin);
        FCScheduler.runAsync(() -> {
            spigotUpdateChecker.execute(ecPluginData);
        });
    }

    public static void checkForUpdates(@Nonnull JavaPlugin plugin, @Nonnull String resourceId){
        checkForUpdates(plugin, resourceId, null);
    }

    private static Map<String, BukkitPluginJar> MAPPED_JARS = new WeakHashMap<>();

    private final String resourceId;
    private final boolean checkForUpdates;
    private final boolean autoDownload;
    private final String currentVersion;
    private transient String newVersion = null;

    public SpigotUpdateChecker(@Nonnull JavaPlugin plugin, @Nonnull String resourceId, @Nullable Config config) {
        this.resourceId = resourceId;
        this.currentVersion = plugin.getDescription().getVersion();

        if (config != null){
            checkForUpdates = config.getOrSetValueIfAbsent(
                    "UpdateChecker.checkForUpdates",
                    true,
                    "If '" + plugin.getName() + "' should check for updates on the Spigot platform."
            );
            autoDownload    = config.getOrSetValueIfAbsent(
                    "UpdateChecker.autoDownloadNewUpdates",
                    false,
                    "If '" + plugin.getName() + "' should automatically download and install the new update."
            );
            config.setComment("UpdateChecker","-----------------------\nAutomatic Update System\n-----------------------");
            if (config.hasNewSeededDefaults()) {
                config.save();
                config.clearNewSeededDefaults();
            }
        }else {
            checkForUpdates = true;
            autoDownload = false;
        }

    }

    public void execute(@Nonnull ECPluginData ecPlugin){
        JavaPlugin plugin = (JavaPlugin) ecPlugin.getPlugin();
        UpdateResult updateResult = checkForUpdates(plugin);

        switch (updateResult){
            case ALREADY_UPDATED:
                plugin.getLogger().info("[UpdateChecker] Update Check Completed! " + plugin.getName() + " is Already Up-to-Date!");
                cleanOldJars(plugin); //Clean possible old jars of this plugin on the Plugins Directory
                return;
            case FAIL_TO_CHECK: //Error already printed in place
                return;
        }

        //If we are here, we have an update to be done!

        if (autoDownload){
            plugin.getLogger().info("[UpdateChecker] Update Check Completed! Found a new update for " + plugin.getName() + "! Download Started!");
            downloadNewVersion(plugin);
            return;
        }

        //If we are not downloading it, we need to warn staffs on join
        final String SPIGOT_URL = "https://www.spigotmc.org/resources/" + resourceId + "/";
        ecPlugin.setUpdateLink(SPIGOT_URL);
        ECListener.register(ecPlugin, new ECListener() {
            private final String PLUGIN_NAME = plugin.getName();
            private final String PERMISSION = McPermissionNodes.UPDATECHECK_PERMISSION_TEMPLATE.replace("%plugin%",PLUGIN_NAME.toLowerCase());
            @ECEventHandler(priority = ECEventPriority.LAST)
            public void onPlayerLogin(ECPlayerFullyLoggedInEvent event) {
                Player player = event.getPlayer().getDelegate(Player.class);
                new BukkitRunnable(){
                    @Override
                    public void run() {
                        UPDATE_IS_AVAILABLE
                                .addPlaceholder("plugin", PLUGIN_NAME)
                                .setClickLink(SPIGOT_URL)
                                .sendIf(player.isOp() || player.hasPermission(PERMISSION), event.getPlayer());
                    }
                }.runTaskLater(plugin, 10);
            }
        });

        plugin.getLogger().info("\n" +
                "\n[UpdateChecker] Update Check Completed! Found a new update for " + plugin.getName() + "! You can download it here [" + SPIGOT_URL + "] !" +
                "\n");
    }

    private UpdateResult checkForUpdates(JavaPlugin plugin) {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openConnection();
            int timed_out = 5000;
            connection.setConnectTimeout(timed_out);
            connection.setReadTimeout(timed_out);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                this.newVersion = reader.readLine(); //may be null on an empty response
            }

            if (this.isNewerOrEqualNextVersion(newVersion)){
                return UpdateResult.ALREADY_UPDATED;
            }

            return UpdateResult.UPDATE_AVAILABLE;
        } catch (Exception e) {
            plugin.getLogger().warning("[UpdateChecker] Error while checking for updates:");
            e.printStackTrace();
            return UpdateResult.FAIL_TO_CHECK;
        } finally {
            if (connection != null) connection.disconnect();
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
                plugin.getLogger().severe("[UpdateChecker] Error downloading the new update: " + newVersion + " as there is already a file named " + fileName + " under the Plugins folder. You need to manually delete all old versions of this plugins!");
                return;
            }

            rbc = Channels.newChannel(con.getInputStream());
            fos = new FileOutputStream(new File(plugin.getDataFolder().getParentFile(), fileName));
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

            plugin.getLogger().info("[UpdateChecker] Download of the update [" + fileName + "] completed! This update will be applied on the next server restart!");
        } catch (Exception e) {
            plugin.getLogger().severe("[UpdateChecker] Error while downloading the update: " + fileName);
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

    private void cleanOldJars(JavaPlugin plugin){
        //listed with the JDK rather than commons-io: that library is on the plugin classpath of some
        //server builds and not others, and reaching for it here threw NoClassDefFoundError on Paper
        //1.16.5 for what one FilenameFilter does
        File[] pluginFiles = plugin.getDataFolder().getParentFile()
                .listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (pluginFiles == null){
            return;
        }
        for (File pluginFile : pluginFiles) {
            BukkitPluginJar bukkitPluginJar = MAPPED_JARS.computeIfAbsent(pluginFile.getAbsolutePath(), s -> {
                try {
                    return new BukkitPluginJar(pluginFile);
                }catch (Exception ignored){

                }
                return null;
            });
            if (bukkitPluginJar != null){
                if (bukkitPluginJar.pluginName.equalsIgnoreCase(plugin.getDescription().getName()) && !bukkitPluginJar.version.equalsIgnoreCase(plugin.getDescription().getVersion())){

                    if (!this.isNewerOrEqualNextVersion(bukkitPluginJar.version)){
                        plugin.getLogger().severe("----------------------------- [UpdateChecker] -----------------------------" +
                                "\nI was going to delete the plugin [" + bukkitPluginJar.file.getAbsolutePath() + "] but it seems to be newer than the current version? Is that correct?!" +
                                "\nThis can happen if you have renamed the " + plugin.getName() + " plugin's jar name! This way the AutoUpdater will not work!" +
                                "\n" +
                                "\nYou should disable the AutoUpdater or do not manually rename the jar! Besides that, you need to manually delete older versions of this plugin!" +
                                "\"----------------------------- [UpdateChecker] -----------------------------");
                        continue;
                    }

                    if (!bukkitPluginJar.file.delete()){
                        plugin.getLogger().severe("[UpdateChecker] I was not able to delete an older version of this plugin at [" + bukkitPluginJar.file.getAbsolutePath()  + "]! You will have to delete it manually!");
                    }else {
                        plugin.getLogger().info("[UpdateChecker] Deleted an older version [" + bukkitPluginJar.version + "] of this plugin that was in the plugins folder!");
                    }
                }
            }
        }
    }

    private boolean isNewerOrEqualNextVersion(String nextVersion) {
        if (nextVersion == null) {
            return false; //no known remote version -> not a newer-or-equal version
        }
        try {
            String[] parts1 = this.currentVersion.split("\\.");
            String[] parts2 = nextVersion.split("\\.");
            int length = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < length; i++) {
                int currentV1 = (i < parts1.length) ? Integer.parseInt(parts1[i]) : 0;
                int newV1 = (i < parts2.length) ? Integer.parseInt(parts2[i]) : 0;
                if (currentV1 != newV1) {
                    return currentV1 > newV1;
                }
            }
            return true;
        }catch (Exception e){
            EverNifeCore.getLog().warning("[UpdateChecker] Error while comparing versions: %s and %s", currentVersion, nextVersion);
            e.printStackTrace();
        }
        return currentVersion.compareTo(nextVersion) >= 0;
    }
}
