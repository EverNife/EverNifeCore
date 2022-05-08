package br.com.finalcraft.evernifecore.config.yaml.helper;

import br.com.finalcraft.evernifecore.config.Config;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ConfigHelper {

    /**
     * It returns a list of all the configs in a directory
     *
     * @param directory The directory to search in.
     * @param recursive If true, it will search all sub-folders for config files.
     * @return A list of all the configs in the directory.
     */
    public static List<Config> getAllConfings(File directory, boolean recursive){
        if (directory == null) throw new IllegalArgumentException("Directory to search can't be null!");
        if (directory.isFile()) throw new IllegalArgumentException("Directory to search must be a FOLDER not a FILE!");

        List<Config> configList = new ArrayList<>();

        if (!directory.exists()) return configList;

        File[] files = directory.listFiles();
        if (files != null){
            for (File innerFile : files) {
                if (innerFile.isFile()){
                    if (innerFile.getName().endsWith(".yml")){
                        configList.add(new Config(innerFile));
                    }
                }else if (recursive){
                    configList.addAll(getAllConfings(directory, recursive));
                }
            }
        }

        return configList;
    }

    /**
     * It copies a file from the plugin's jar to a folder on the server
     *
     * @param plugin The plugin instance.
     * @param assetName The name of the asset you want to copy.
     * @param targetFolder The folder you want to copy the asset to.
     * @return A file object
     */
    public static File copyAsset(Plugin plugin, String assetName, File targetFolder) throws IOException {
        File file = new File(targetFolder, assetName);
        if (!file.exists()){
            file.mkdirs();
            InputStream inputStream = plugin.getResource(assetName);
            Files.copy(inputStream, file.getAbsoluteFile().toPath(), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
        }
        return file;
    }

}
