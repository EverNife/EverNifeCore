package br.com.finalcraft.evernifecore.listeners.base;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.api.platoverride.eclistener.IECBaseListener;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECListenerWatch;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
        EverNifeCore.getEventBus().unregister(this);
        ECListenerRegistry.forget(this);
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
            ECListenerRegistry.track(ecPluginData.getMetaInfo().getName(), listener);

            //Deliver framework-agnostic IECEvents to the @ECEventHandler methods of this listener.
            EverNifeCore.getEventBus().register(listener);

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

    /**
     * Keeps {@code listener} registered only while somebody listens to what it produces: it is
     * registered through {@link #register(ECPluginData, ECListener)} when the first listener of any of
     * {@code produced} appears - on the bus or on the platform - and {@link #unregisterThis()
     * unregistered} when the last one leaves. What it saves is the platform work the listener would do
     * for nobody: a producer hooked on a hot native event costs nothing until it is asked for. The
     * watch belongs to {@code ecPluginData} and is dropped with the rest of that plugin's on shutdown.
     *
     * @return the watch, for a producer that wants to read the presence or stop following it early
     */
    @SafeVarargs
    public static ECListenerWatch registerWhileListened(@Nonnull ECPluginData ecPluginData, ECListener listener, Class<? extends IECEvent>... produced) {
        Objects.requireNonNull(ecPluginData, "'ecPluginData' cannot be null when registering an on-demand ECListener!");
        Objects.requireNonNull(listener, "'listener' cannot be null when registering an on-demand ECListener!");
        Objects.requireNonNull(produced, "'produced' cannot be null: name the event types the listener produces!");

        //The bus runs one presence pass at a time, so the two callbacks never overlap; the flag is
        //what makes a registration that was refused (canRegister, a missing plugin) or that blew up
        //retry the next time somebody listens, instead of standing as registered while nothing is.
        AtomicBoolean registered = new AtomicBoolean();
        return EverNifeCore.getEventBus().watchListeners(ecPluginData, Arrays.asList(produced),
                () -> {
                    if (!registered.compareAndSet(false, true)) {
                        return;
                    }
                    boolean done = false;
                    try {
                        done = register(ecPluginData, listener);
                    } finally {
                        if (!done) {
                            registered.set(false);
                        }
                    }
                },
                () -> {
                    if (registered.compareAndSet(true, false)) {
                        listener.unregisterThis();
                    }
                });
    }

    /**
     * Unregisters every {@link ECListener} this plugin registered through {@link #register}. This is
     * the default late-shutdown cleanup wired by the bootstrap layer; it is safe to call more than once.
     */
    public static void unregisterAll(@Nonnull ECPluginData ecPluginData) {
        Objects.requireNonNull(ecPluginData, "'ecPluginData' cannot be null when unregistering ECListeners!");
        for (ECListener listener : ECListenerRegistry.drain(ecPluginData.getMetaInfo().getName())) {
            listener.unregisterThis();
        }
    }

    /** A read-only snapshot of the listeners currently registered by this plugin through {@link #register}. */
    public static Set<ECListener> getRegistered(@Nonnull ECPluginData ecPluginData) {
        Objects.requireNonNull(ecPluginData, "'ecPluginData' cannot be null!");
        return Collections.unmodifiableSet(ECListenerRegistry.snapshot(ecPluginData.getMetaInfo().getName()));
    }

}
