package br.com.finalcraft.evernifecore.hytale.loader.imp;

import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.IPluginMetaInfo;
import br.com.finalcraft.evernifecore.hytale.util.FCJavaPluginUtil;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import jakarta.annotation.Nonnull;

public class HyECPluginExtractor implements IECPluginExtractor {

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
        return FCJavaPluginUtil.getProvidingPlugin(clazz);
    }

    @Override
    public IPluginMetaInfo getPluginMetaInfo(Object javaPlugin) {
        return new HyPluginMetaInfo((JavaPlugin) javaPlugin);
    }
}
