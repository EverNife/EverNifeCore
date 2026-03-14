package br.com.finalcraft.evernifecore.nms.data;

import com.google.common.collect.BiMap;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    /**
     * Get all the objects in the registry
     *  Filters:
     *      'REGEX='        - Filter by regex (case-sensitive)
     *      'CONTAINS='     - Filter by contains (ignore case)
     *      'normal_string' - Filter by ignoreCase string
     * @param filters
     * @return
     */
    public List<T> getWithFilters(List<String> filters){
        List<Pattern> patterns = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        List<String> ignoreCases = new ArrayList<>();

        filters.removeIf(filterLine -> {
            if (filterLine.toUpperCase().startsWith("REGEX=")){
                patterns.add(Pattern.compile(filterLine.substring(6)));
            }else if (filterLine.toUpperCase().startsWith("CONTAINS=")){
                contains.add(filterLine.substring(9).toLowerCase());
            }else {
                ignoreCases.add(filterLine);
            }
            return true;
        });

        List<T> values = new ArrayList<>();

        outer: for (T value : getRegistryResourceLocation().values()) {

            String mcIdentifier = value.getMCIdentifier();

            for (String ignoreCase : ignoreCases) {
                if (mcIdentifier.equalsIgnoreCase(ignoreCase) || value.getMaterial().name().equalsIgnoreCase(ignoreCase)){
                    values.add(value);
                    continue outer;
                }
            }

            if (contains.size() > 0){
                String mcIdentifierLowerCase = mcIdentifier.toLowerCase();
                for (String contain : contains) {
                    if (mcIdentifierLowerCase.contains(contain)){
                        values.add(value);
                        continue outer;
                    }
                }
            }

            for (Pattern pattern : patterns) {
                if (pattern.matcher(mcIdentifier).find()) {
                    values.add(value);
                }
            }
        }

        return values;
    }
}
