package br.com.finalcraft.evernifecore.listeners.base;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.platoverride.eclistener.IECBaseListener;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import jakarta.annotation.Nonnull;

import java.util.Objects;

public interface ECListener extends IECBaseListener {

    public default String[] requiredPlugins(){
        return FCArrayUtil.toArray();
    }

    public default boolean canRegister(){
        return true;
    }

    public default boolean silentRegistration(){
        return false;
    }

    public default void onRegister(){

    }

    public default void unregisterThis() {
        EverNifeCore.getPlatform().unregisterECListener(this);
    }

    public static boolean register(@Nonnull ECPluginData ecPluginData, ECListener listener){
        Objects.requireNonNull(ecPluginData,"'ecPluginData' cannot be null when registering ECListeners!");

        try {
            String[] requiredPlugins = listener.requiredPlugins();

            if (requiredPlugins != null && requiredPlugins.length > 0){
                for (String requiredPlugin : requiredPlugins) { // Register only when every required plugin is present
                    if (!EverNifeCore.getPlatform().isPluginLoaded(requiredPlugin)){
                        return false;
                    }
                }
            }

            Boolean canRegister = null;
            try {
                canRegister = listener.canRegister();
            }catch (Throwable e){
                ecPluginData.getLog().warning("[ECListener] Failed to call [canRegister()] method of the ECListener: " + listener.getClass().getName());
                e.printStackTrace();
            }

            if (canRegister == null || canRegister == false){
                return false;
            }

            if (!listener.silentRegistration()){
                ecPluginData.getLog().info("[ECListener] Registering Listener [" + listener.getClass().getName() + "]");
            }

            EverNifeCore.getPlatform().registerECListener(ecPluginData, listener);

            //Check for locales
            FCLocaleManager.loadLocale(ecPluginData, true, listener.getClass());
            try {
                listener.onRegister();
            }catch (Throwable e){
                //The listener is already registered on the platform; an onRegister() failure is the
                //listener author's bug and must not unregister it or abort the flow.
                ecPluginData.getLog().warning("[ECListener] Listener [" + listener.getClass().getName() + "] failed on onRegister() but remains registered");
                e.printStackTrace();
            }

            return true;
        }catch (Throwable t){
            ecPluginData.getLog().warning("[ECListener] Failed to register Listener: " + listener.getClass().getName());
            t.printStackTrace();
        }
        return false;
    }

    public static boolean register(@Nonnull ECPluginData ecPluginData, Class<? extends ECListener> clazz) {
        try {
            ECListener listener = clazz.getDeclaredConstructor().newInstance();
            return register(ecPluginData, listener);
        } catch (Throwable t) {
            ecPluginData.getLog().warning("[ECListener] Failed to register Listener: [" + clazz.getName() + "] " + t.getClass().getSimpleName() + " [" + t.getMessage() + "]");
        }
        return false;
    }

}
