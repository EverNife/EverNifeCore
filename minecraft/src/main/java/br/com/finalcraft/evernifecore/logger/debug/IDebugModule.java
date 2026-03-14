package br.com.finalcraft.evernifecore.logger.debug;

import br.com.finalcraft.evernifecore.config.yaml.section.ConfigSection;
import br.com.finalcraft.evernifecore.logger.ECLogger;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;
import java.util.logging.Level;

public interface IDebugModule<P extends Plugin, DM extends IDebugModule>  {

    public String getName();

    public default String getComment(){
        return null;
    }

    public boolean isEnabled();

    public void setEnabled(boolean enabled);

    public default boolean isEnabledByDefault(){
        return true;
    }

    public default boolean onConfigLoad(ConfigSection section){
        return section.getOrSetDefaultValue("DebugModules." + getName(), isEnabledByDefault(), getComment());
    }

    public P getPlugin();

    public ECLogger<DM> getLog();

    public default void logModule(Level level, String msg) {
        this.getLog().logModule((DM) this, level, msg);
    }

    public default void logModule(Level level, Supplier<String> supplier) {
        this.getLog().logModule((DM) this, level, supplier);
    }

    public default void debugModule(String message, Object... params) {
        this.getLog().debugModule((DM) this, message, params);
    }

    public default void debugModule(Supplier<String> supplier) {
        this.getLog().debugModule((DM) this, supplier);
    }

    public default void infoModule(String message, Object... params) {
        this.getLog().infoModule((DM) this, message, params);
    }

    public default void infoModule(Supplier<String> supplier) {
        this.getLog().infoModule((DM) this, supplier);
    }

    public default void warningModule(String message, Object... params) {
        this.getLog().warningModule((DM) this, message, params);
    }

    public default void warningModule(Supplier<String> supplier) {
        this.getLog().warningModule((DM) this, supplier);
    }

    public default void severeModule(String message, Object... params) {
        this.getLog().severeModule((DM) this, message, params);
    }

    public default void severeModule(Supplier<String> supplier) {
        this.getLog().severeModule((DM) this, supplier);
    }

}
