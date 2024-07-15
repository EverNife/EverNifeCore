package br.com.finalcraft.evernifecore.minecraft.worlddata.manager.config;

import br.com.finalcraft.evernifecore.config.Config;
import br.com.finalcraft.evernifecore.minecraft.region.RegionPos;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class ServerConfigData {

    private final File mainFolder;
    private final Map<String, Map<RegionPos, Config>> CONFIG_MAP = new LinkedHashMap<>();

    public ServerConfigData(File mainFolder) {
        this.mainFolder = mainFolder;
    }

    public Map<String, Map<RegionPos, Config>> getConfigMap() {
        return CONFIG_MAP;
    }

    public @Nullable Map<RegionPos, Config> getRegionMap(String worldName){
        return CONFIG_MAP.get(worldName);
    }

    public @Nullable Config setConfigData(String worldName, RegionPos regionPos, @Nullable Config config){
        Map<RegionPos, Config> regionMap = config == null
                ? CONFIG_MAP.get(worldName)
                : CONFIG_MAP.computeIfAbsent(worldName, s -> new HashMap<>());

        if (regionMap == null){//This can be null only when 'value' is null as well, so lets early return
            return null;
        }

        if (config == null){
            return regionMap.remove(regionPos);
        }else {
            return regionMap.put(regionPos, config);
        }
    }

    public @Nullable Config getConfigData(String worldName, RegionPos regionPos){
        Map<RegionPos, Config> regionMap = CONFIG_MAP.get(worldName);

        if (regionMap == null){
            return null;
        }

        return regionMap.get(regionPos);
    }

    public Config getOrCreateConfigData(String worldName, RegionPos regionPos){
        Map<RegionPos, Config> regionMap = CONFIG_MAP.computeIfAbsent(worldName, s -> new LinkedHashMap<>());

        Config config = regionMap.computeIfAbsent(regionPos, r ->
                new Config(new File(mainFolder, worldName + File.separator + "r." + regionPos.getX() + "." + regionPos.getZ() + ".yml"))
                        .enableSmartCache()
        );
        return config;
    }

    public List<Config> getAllConfigs(){
        List<Config> allConfigs = new ArrayList<>();
        for (Map<RegionPos, Config> regionMap : CONFIG_MAP.values()) {
            allConfigs.addAll(regionMap.values());
        }
        return allConfigs;
    }

}
