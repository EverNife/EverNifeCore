package br.com.finalcraft.evernifecore.api.common.providers.extractors;

import br.com.finalcraft.evernifecore.ecplugin.IPluginData;
import jakarta.annotation.Nonnull;

public interface IECPluginExtractor {

    public String getPluginName(Object javaPlugin);

    public boolean isJavaPlugin(Object plugin);

    public default void validateJavaPlugin(Object plugin){
        if (!isJavaPlugin(plugin)){
            throw new IllegalArgumentException(String.format("The provided plugin [%s] is not valid!", plugin.getClass().getName()));
        }
    }

    public Object getProvidingPlugin(@Nonnull Class<?> clazz);

    public IPluginData getPluginData(Object javaPlugin);

}
