package br.com.finalcraft.evernifecore.minecraft.loader.imp;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import jakarta.annotation.Nonnull;
import org.bukkit.plugin.java.JavaPlugin;

public class McECPluginExtractor implements IECPluginExtractor {

    @Override
    public String getPluginName(Object javaPlugin) {
        return ((JavaPlugin) javaPlugin).getName();
    }

    @Override
    public boolean isJavaPlugin(Object plugin) {
        return plugin instanceof JavaPlugin;
    }

    @Override
    public Object getProvidingPlugin(@Nonnull Class<?> clazz) {
        return JavaPlugin.getProvidingPlugin(clazz);
    }

    @Override
    public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
        return new McPluginMetaInfo((JavaPlugin) javaPlugin);
    }
}
