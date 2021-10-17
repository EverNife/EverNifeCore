package br.com.finalcraft.evernifecore.listeners.base;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ECListener extends Listener {

    public default String[] requiredPlugins(){
        return null;
    }

    public default boolean canRegister(){
        return true;
    }

    public default void onRegister(){
        //Do Nothing
    }

    public static boolean register(@NotNull Plugin pluginInstance, Class<? extends ECListener> clazz){
        try {
            ECListener listener = (ECListener) clazz.newInstance();

            String[] requiredPlugins = listener.requiredPlugins();

            if (requiredPlugins != null && requiredPlugins.length > 0){
                for (String requiredPlugin : listener.requiredPlugins()) {//Check if all required plugins are present
                    if (!Bukkit.getPluginManager().isPluginEnabled(requiredPlugin)){
                        return false;
                    }
                }
            }

            Boolean canRegister = null;
            try {
                canRegister = listener.canRegister();
            }catch (Exception e){
                pluginInstance.getLogger().warning("[ECListener] Failed to call [canRegister()] method of the ECListener: " + clazz.getName());
                e.printStackTrace();
            }

            if (canRegister == null || canRegister == false){
                return false;
            }

            pluginInstance.getLogger().info("[ECListener] Registering Listener [" + listener.getClass().getName() + "]");
            try {
                listener.onRegister();
            }catch (Exception e){
                pluginInstance.getLogger().warning("[ECListener] Failed to call [onRegister()] method of the ECListener: " + clazz.getName());
                e.printStackTrace();
            }
            Bukkit.getServer().getPluginManager().registerEvents(listener, pluginInstance);
            return true;
        } catch (NoClassDefFoundError nfe) {
            pluginInstance.getLogger().warning("[ECListener] Failed to register Listener: [" + clazz.getName() + "] NoClassDefFoundError [" + nfe.getMessage() + "]");
        } catch (Exception e) {
            pluginInstance.getLogger().warning("[ECListener] Failed to register Listener: " + clazz.getName());
            e.printStackTrace();
        }
        return false;
    }

    public static String[] toArray(String... strings){
        return strings;
    }

}
