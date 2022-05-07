package br.com.finalcraft.evernifecore.config.yaml.section;

import br.com.finalcraft.evernifecore.config.Config;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ConfigSection{

    private final Config config;
    private final String path;
    private transient String lastPathIndex = null; //Only populated when requested!

    public ConfigSection(Config config, String path) {
        this.config = config;
        this.path = path;
    }

    public Config getConfig() {
        return config;
    }

    public String getSectionKey() {
        if (lastPathIndex == null){
            //Calculate last path index
            int lastDot = path.lastIndexOf(".");
            this.lastPathIndex = path.substring(Math.max(lastDot, 0));
        }
        return lastPathIndex;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "ConfigSection{" +
                "configFile=" + (config.getTheFile() == null ? null : config.getTheFile().getAbsolutePath()) +
                ", path='" + path + "'}'";
    }

    private String concatSubPath(@Nullable String subPath){
        return subPath == null ? this.path : this.path + "." + subPath;
    }

    // ------------------------------------------------------------------------------------------------------------------
    //      Basic YamlFile Functions
    // ------------------------------------------------------------------------------------------------------------------

    public ConfigSection getConfigSection(String subPath){
        return config.getConfigSection(concatSubPath(subPath));
    }

    public Set<String> getKeys() {
        return config.getKeys(this.path);
    }

    public Set<String> getKeys(String subPath) {
        return config.getKeys(concatSubPath(subPath));
    }

    public Set<String> getKeys(String subPath, boolean deep) {
        return config.getKeys(concatSubPath(subPath), deep);
    }

    public boolean contains() {
        return config.contains(this.path);
    }

    public boolean contains(String subPath) {
        return config.contains(concatSubPath(subPath));
    }

    public Object getValue(String path) {
        return config.getValue(path);
    }


    public void setValue(Object value) {
        config.setValue(this.path, value);
    }
    public void setValue(String subPath, Object value) {
        config.setValue(concatSubPath(subPath), value);
    }

    public void setDefaultValue(String subPath, Object value) {
        config.setDefaultValue(concatSubPath(subPath), value);
    }

    public void clear(){
        config.setValue(this.path, null);
    }

    // ------------------------------------------------------------------------------------------------------------------
    //      Basic Java Elements Getters
    // ------------------------------------------------------------------------------------------------------------------

    public String getString(@Nullable String subPath) {
        return config.getString(concatSubPath(subPath));
    }

    public String getString(@Nullable String subPath, String def) {
        return config.getString(concatSubPath(subPath),def);
    }

    public Boolean getBoolean(@Nullable String subPath) {
        return config.getBoolean(concatSubPath(subPath));
    }
    public Boolean getBoolean(@Nullable String subPath, Boolean def) {
        return config.getBoolean(concatSubPath(subPath),def);
    }

    public Integer getInt(@Nullable String subPath) {
        return config.getInt(concatSubPath(subPath));
    }
    public Integer getInt(@Nullable String subPath, Integer def) {
        return config.getInt(concatSubPath(subPath),def);
    }

    public Long getLong(@Nullable String subPath) {
        return config.getLong(concatSubPath(subPath), null);
    }
    public Long getLong(@Nullable String subPath, Long def) {
        return config.getLong(concatSubPath(subPath), def);
    }

    public Double getDouble(@Nullable String subPath) {
        return config.getDouble(concatSubPath(subPath));
    }
    public Double getDouble(@Nullable String subPath, double def) {
        return config.getDouble(concatSubPath(subPath),def);
    }

    public UUID getUUID(@Nullable String subPath) {
        return config.getUUID(concatSubPath(subPath));
    }

    public UUID getUUID(@Nullable String subPath, UUID def) {
        return config.getUUID(concatSubPath(subPath), def);
    }

    public List getList(@Nullable String subPath) {
        return config.getList(concatSubPath(subPath));
    }
    public List getList(@Nullable String subPath, List<String> def) {
        return config.getList(concatSubPath(subPath), def);
    }

    public List<String> getStringList(@Nullable String subPath) {
        return config.getStringList(concatSubPath(subPath));
    }
    public List<String> getStringList(@Nullable String subPath, List<String> def) {
        return config.getStringList(concatSubPath(subPath), def);
    }

    // ------------------------------------------------------------------------------------------------------------------
    //      Basic Java Elements GetOrDefault
    // ------------------------------------------------------------------------------------------------------------------

    public <D> @NotNull List<D> getOrSetDefaultValue(@Nullable String subPath, @NotNull List<D> def) {
        return config.getOrSetDefaultValue(concatSubPath(subPath), def);
    }

    public <D> @NotNull D getOrSetDefaultValue(@Nullable String subPath, @NotNull D def) {
        return config.getOrSetDefaultValue(concatSubPath(subPath), def);
    }

    // ------------------------------------------------------------------------------------------------------------------
    //      Loadable System
    // ------------------------------------------------------------------------------------------------------------------

    public <L> @Nullable L getLoadable(@Nullable String subPath, Class<L> loadableClass) {
        return config.getLoadable(concatSubPath(subPath), loadableClass);
    }

    public <L> @NotNull List<L> getLoadableList(@Nullable String subPath, Class<? extends L> loadableClass) {
        return config.getLoadableList(concatSubPath(subPath), loadableClass);
    }
}
