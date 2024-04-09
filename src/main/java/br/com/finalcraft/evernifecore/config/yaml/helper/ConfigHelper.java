package br.com.finalcraft.evernifecore.config.yaml.helper;

import br.com.finalcraft.evernifecore.config.Config;
import org.bukkit.plugin.Plugin;
import org.simpleyaml.configuration.comments.format.YamlCommentFormat;
import org.simpleyaml.configuration.file.YamlFile;
import org.simpleyaml.configuration.implementation.SimpleYamlImplementation;
import org.simpleyaml.configuration.implementation.api.QuoteStyle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ConfigHelper {

//    private static final SimpleYamlImplementation simpleYamlImplementation = new SimpleYamlImplementation();
    public static YamlFile createYamlFile(File file){
        SimpleYamlImplementation simpleYamlImplementation = new SimpleYamlImplementation();

        YamlFile yamlFile = new YamlFile(simpleYamlImplementation);
        yamlFile.setConfigurationFile(file);

        yamlFile.setCommentFormat(YamlCommentFormat.PRETTY);

        yamlFile.options().quoteStyleDefaults().setDefaultQuoteStyle(QuoteStyle.PLAIN);
        yamlFile.options().quoteStyleDefaults().setQuoteStyle(List.class, QuoteStyle.DOUBLE);
        yamlFile.options().quoteStyleDefaults().setQuoteStyle(String.class, QuoteStyle.DOUBLE);

        simpleYamlImplementation.getDumperOptions().setSplitLines(false);
        simpleYamlImplementation.getDumperOptions().setProcessComments(true);
        simpleYamlImplementation.getLoaderOptions().setProcessComments(true);

        return yamlFile;
    }

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
                    configList.addAll(getAllConfings(innerFile, recursive));
                }
            }
        }

        return configList;
    }

    /**
     * It copies a file from the plugin's jar to a folder on the server
     *
     * @param plugin The plugin instance.
     * @param assetPath The asset path from the plugin you want to copy.
     *                  Example: "assets/config.yml"
     * @param targetFile The File where to place the asset to.
     * @return A file object
     */
    public static File copyAsset(Plugin plugin, String assetPath, File targetFile) throws IOException {
        if (!targetFile.exists()){
            targetFile.getParentFile().mkdirs();
            InputStream inputStream = plugin.getResource(assetPath);
            Files.copy(inputStream, targetFile.getAbsoluteFile().toPath(), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
        }
        return targetFile;
    }

}
