package br.com.finalcraft.evernifecore.config.yaml.section;

import br.com.finalcraft.evernifecore.config.Config;
import org.simpleyaml.configuration.comments.CommentType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ConfigSection{

    private final Config config;
    private String path;
    private transient String lastPathIndex = null; //Only populated when requested!

    public ConfigSection(Config config, String path) {
        this.config = config;
        this.path = path == null ? "" : path;
    }

    public Config getConfig() {
        return config;
    }

    public String getSectionKey() {
        if (lastPathIndex == null){
            //Calculate last path index
            int lastDot = path.lastIndexOf(".");
            this.lastPathIndex = path.substring(Math.max(lastDot + 1, 0));
        }
        return lastPathIndex;
    }

    public ConfigSection getFatherSection() {
        return new ConfigSection(this.config, path.substring(0, Math.max(0, path.lastIndexOf("."))));
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "ConfigSection{" +
                "configFile=" + (config.getTheFile() == null ? null : config.getTheFile().getAbsolutePath()) +
                ", path='" + path + "'}";
    }

    private String concatSubPath(@Nullable String subPath){
        if (subPath == null || subPath.isEmpty()){
            return this.path;
        }

        if (this.path.isEmpty()){
            return subPath;
        }

        return this.path + "." + subPath;
    }

    public void setCustomKeyIndex(String customKeyIndex) {
        this.lastPathIndex = customKeyIndex;
        this.path = this.path.isEmpty()
                ? customKeyIndex
                : this.path + "." + customKeyIndex;
    }

    public ConfigSection getParentSection(){
        if (this.path.isEmpty()){
            return this;
        }
        return new ConfigSection(config, path.substring(0, Math.max(0, path.lastIndexOf("."))));
    }

    // ------------------------------------------------------------------------------------------------------------------
    //      Comment System Functions
    // ------------------------------------------------------------------------------------------------------------------

    public void setComment(@Nullable String subPath, @Nonnull String comment, @Nonnull CommentType type) {
        config.setComment(concatSubPath(subPath), comment, type);
    }

    public void setComment(@Nullable String subPath, @Nonnull String comment) {
        config.setComment(concatSubPath(subPath), comment);
    }

    public String getComment(@Nullable String subPath, @Nonnull CommentType type) {
        return config.getComment(concatSubPath(subPath), type);
    }

    public String getComment(@Nullable String subPath) {
        return config.getComment(concatSubPath(subPath));
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

    public Set<ConfigSection> getKeysSections() {
        return config.getKeysSections(this.path);
    }

    public Set<ConfigSection> getKeysSections(String subPath) {
        return config.getKeysSections(concatSubPath(subPath));
    }

    public Set<ConfigSection> getKeysSections(String subPath, boolean deep) {
        return config.getKeysSections(concatSubPath(subPath), deep);
    }

    public boolean contains() {
        return config.contains(this.path);
    }

    public boolean contains(String subPath) {
        return config.contains(concatSubPath(subPath));
    }

    public Object getValue(String subPath) {
        return config.getValue(concatSubPath(subPath));
    }


    public void setValue(Object value) {
        config.setValue(this.path, value);
    }
    public void setValue(String subPath, Object value) {
        config.setValue(concatSubPath(subPath), value);
    }
    public void setValue(String subPath, Object value, String comment) {
        config.setValue(concatSubPath(subPath), value, comment);
    }

    public void setDefaultValue(String subPath, Object value) {
        config.setDefaultValue(concatSubPath(subPath), value);
    }
    public void setDefaultValue(String subPath, Object value, String comment) {
        config.setDefaultValue(concatSubPath(subPath), value, comment);
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

    public boolean getBoolean(@Nullable String subPath) {
        return config.getBoolean(concatSubPath(subPath));
    }
    public boolean getBoolean(@Nullable String subPath, boolean def) {
        return config.getBoolean(concatSubPath(subPath),def);
    }

    public int getInt(@Nullable String subPath) {
        return config.getInt(concatSubPath(subPath));
    }
    public int getInt(@Nullable String subPath, int def) {
        return config.getInt(concatSubPath(subPath),def);
    }

    public long getLong(@Nullable String subPath) {
        return config.getLong(concatSubPath(subPath));
    }
    public long getLong(@Nullable String subPath, long def) {
        return config.getLong(concatSubPath(subPath), def);
    }

    public double getDouble(@Nullable String subPath) {
        return config.getDouble(concatSubPath(subPath));
    }
    public double getDouble(@Nullable String subPath, double def) {
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

    public <D> @Nonnull List<D> getOrSetDefaultValue(@Nullable String subPath, @Nonnull List<D> def) {
        return config.getOrSetDefaultValue(concatSubPath(subPath), def);
    }

    public <D> @Nonnull List<D> getOrSetDefaultValue(@Nullable String subPath, @Nonnull List<D> def, @Nullable String comment) {
        return config.getOrSetDefaultValue(concatSubPath(subPath), def, comment);
    }

    public <D> @Nonnull D getOrSetDefaultValue(@Nullable String subPath, @Nonnull D def) {
        return config.getOrSetDefaultValue(concatSubPath(subPath), def);
    }

    public <D> @Nonnull D getOrSetDefaultValue(@Nullable String subPath, @Nonnull D def, @Nullable String comment) {
        return config.getOrSetDefaultValue(concatSubPath(subPath), def, comment);
    }

    // ------------------------------------------------------------------------------------------------------------------
    //      Loadable System
    // ------------------------------------------------------------------------------------------------------------------

    public <L> @Nullable L getLoadable(@Nullable String subPath, @Nonnull Class<L> loadableClass) {
        return config.getLoadable(concatSubPath(subPath), loadableClass);
    }

    public <L> @Nullable L getLoadable(@Nullable String subPath, @Nonnull L loadableDefault) {
        return config.getLoadable(concatSubPath(subPath), loadableDefault);
    }

    public <L> @Nonnull List<L> getLoadableList(@Nullable String subPath, @Nonnull Class<? extends L> loadableClass) {
        return config.getLoadableList(concatSubPath(subPath), loadableClass);
    }

    public <L> @Nonnull List<L> getLoadableList(@Nullable String subPath, @Nonnull List<L> loadableListDefault) {
        return config.getLoadableList(concatSubPath(subPath), loadableListDefault);
    }
}
