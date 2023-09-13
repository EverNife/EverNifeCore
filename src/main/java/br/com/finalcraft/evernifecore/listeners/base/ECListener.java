package br.com.finalcraft.evernifecore.listeners.base;

import br.com.finalcraft.evernifecore.listeners.forge.ForgeEventHandler;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface ECListener extends Listener {

    public default String[] requiredPlugins(){
        return FCArrayUtil.toArray();
    }

    public default boolean canRegister(){
        return true;
    }

    public default boolean silentRegistration(){
        return false;
    }

    public default Object[] getForgeEventBuses(){
        return null; //On newer forge version there are more than one Forge BUS
    }

    public default void onRegister(){
        //Do Nothing
    }

    public static boolean register(@NotNull Plugin pluginInstance, ECListener listener){
        try {
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
            }catch (Throwable e){
                pluginInstance.getLogger().warning("[ECListener] Failed to call [canRegister()] method of the ECListener: " + listener.getClass().getName());
                e.printStackTrace();
            }

            if (canRegister == null || canRegister == false){
                return false;
            }

            if (!listener.silentRegistration()){
                pluginInstance.getLogger().info("[ECListener] Registering Listener [" + listener.getClass().getName() + "]");
            }

            //Check for locales
            FCLocaleManager.loadLocale(pluginInstance, true, listener.getClass());
            try {
                listener.onRegister();
            }catch (Throwable e){
                pluginInstance.getLogger().warning("[ECListener] Failed to call [onRegister()] method of the ECListener: " + listener.getClass().getName());
                e.printStackTrace();
                return false;
            }
            Bukkit.getServer().getPluginManager().registerEvents(listener, pluginInstance);

            List<Method> forgeMethods = Arrays.stream(listener.getClass().getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(ForgeEventHandler.class))
                    .collect(Collectors.toList());

            if (forgeMethods.size() > 0){
                //TODO Implement, for each Major Modding Version, a way to register ForgeEvents thought the BukkitPlatform.
                // [ ] 1.7.10
                // [ ] 1.12.2
                // [ ] 1.16.5
            }

            return true;
        }catch (Throwable t){
            pluginInstance.getLogger().warning("[ECListener] Failed to register Listener: " + listener.getClass().getName());
            t.printStackTrace();
        }
        return false;
    }

    public static boolean register(@NotNull Plugin pluginInstance, Class<? extends ECListener> clazz) {
        try {
            ECListener listener = clazz.getDeclaredConstructor().newInstance();
            return register(pluginInstance, listener);
        } catch (Throwable t) {
            pluginInstance.getLogger().warning("[ECListener] Failed to register Listener: [" + clazz.getName() + "] " + t.getClass().getSimpleName() + " [" + t.getMessage() + "]");
        }
        return false;
    }

}
