package br.com.finalcraft.evernifecore.nms.data;

import com.google.common.collect.BiMap;
import org.bukkit.Material;

public abstract class IMCMaterialRegistry<T extends IHasMinecraftIdentifier> {

    private final BiMap<String, T> registryResourceLocation;
    private final BiMap<Material, T> registryMaterial;

    public IMCMaterialRegistry(BiMap<String, T> registryResourceLocation, BiMap<Material, T> registryMaterial) {
        this.registryResourceLocation = registryResourceLocation;
        this.registryMaterial = registryMaterial;
    }

    public T getObject(String resourceLocation){
        return registryResourceLocation.get(resourceLocation);
    }

    public T getObject(Material material){
        return registryMaterial.get(material);
    }

    public String getResourceLocation(Material material){
        T t = registryMaterial.get(material);
        if (t == null){
            return null;
        }
        return t.getMCIdentifier();
    }

    public String getResourceLocation(T object){
        return registryResourceLocation.inverse().get(object);
    }

    public String getResourceLocationFromHandle(Object handle){
        return registryResourceLocation.inverse().get(wrap(handle));
    }

    public BiMap<String, T> getRegistryResourceLocation() {
        return registryResourceLocation;
    }

    public BiMap<Material, T> getRegistryMaterial() {
        return registryMaterial;
    }

    public abstract T wrap(Object handle);

}
