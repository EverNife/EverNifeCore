package br.com.finalcraft.evernifecore.listeners.base;

import br.com.finalcraft.evernifecore.api.eventhandler.ECEventHandler;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.reflection.MethodInvoker;
import br.com.finalcraft.evernifecore.util.FCArrayUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.event.ICancellable;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface ECListener {

    public static Map<ECListener, List<EventRegistration>> MAP_OF_REGISTRATIONS = new HashMap<>();

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

    public default void unregisterAll() {
        MAP_OF_REGISTRATIONS.getOrDefault(this, new ArrayList<>())
                .forEach(EventRegistration::unregister);
    }

    public static boolean register(@Nonnull JavaPlugin pluginInstance, ECListener listener){
        Objects.requireNonNull(pluginInstance,"'pluginInstance' cannot be null when registering ECListeners!");

        try {
            String[] requiredPlugins = listener.requiredPlugins();

            if (requiredPlugins != null && requiredPlugins.length > 0){
                for (String requiredPlugin : listener.requiredPlugins()) {//Check if all required plugins are present
                    if (new Argumento(requiredPlugin).getPlugin() == null){
                        return false;
                    }
                }
            }

            Boolean canRegister = null;
            try {
                canRegister = listener.canRegister();
            }catch (Throwable e){
                pluginInstance.getLogger().atWarning().log("[ECListener] Failed to call [canRegister()] method of the ECListener: " + listener.getClass().getName());
                e.printStackTrace();
            }

            if (canRegister == null || canRegister == false){
                return false;
            }

            if (!listener.silentRegistration()){
                pluginInstance.getLogger().atInfo().log("[ECListener] Registering Listener [" + listener.getClass().getName() + "]");
            }

            ecEventHandler(pluginInstance, listener);

            //Check for locales
            FCLocaleManager.loadLocale(pluginInstance, true, listener.getClass());
            try {
                listener.onRegister();
            }catch (Throwable e){
                pluginInstance.getLogger().atWarning().log("[ECListener] Failed to call [onRegister()] method of the ECListener: " + listener.getClass().getName());
                e.printStackTrace();
                return false;
            }

            return true;
        }catch (Throwable t){
            pluginInstance.getLogger().atWarning().log("[ECListener] Failed to register Listener: " + listener.getClass().getName());
            t.printStackTrace();
        }
        return false;
    }

    public static boolean register(@Nonnull JavaPlugin pluginInstance, Class<? extends ECListener> clazz) {
        try {
            ECListener listener = clazz.getDeclaredConstructor().newInstance();
            return register(pluginInstance, listener);
        } catch (Throwable t) {
            pluginInstance.getLogger().atWarning().log("[ECListener] Failed to register Listener: [" + clazz.getName() + "] " + t.getClass().getSimpleName() + " [" + t.getMessage() + "]");
        }
        return false;
    }

    private static void ecEventHandler(JavaPlugin pluginInstance, ECListener listener){

        List<MethodInvoker> annotatedMethods = FCReflectionUtil.getMethods(listener.getClass(), method -> {
            ECEventHandler annotation = method.getAnnotation(ECEventHandler.class);
            return annotation != null;
        }).collect(Collectors.toList());

        boolean foundAnyError = false;

        List<EventRegistration> registrations = new ArrayList<>();

        for (MethodInvoker methodListener : annotatedMethods) {
            Class<?>[] parameterTypes = methodListener.get().getParameterTypes();

            if (parameterTypes.length == 0) {
                pluginInstance.getLogger().atSevere().log(String.format("[ECListener] @ECEventHandler(%s#%s) | No parameter found on this listener.. ", listener.getClass().getSimpleName(), methodListener.get().getName()));
                foundAnyError = true;
                continue;
            }

            if (parameterTypes.length > 1) {
                pluginInstance.getLogger().atSevere().log(String.format("[ECListener] @ECEventHandler(%s#%s) | More than one parameter found on this listener.. ", listener.getClass().getSimpleName(), methodListener.get().getName()));
                foundAnyError = true;
                continue;
            }

            if (!IEvent.class.isAssignableFrom(parameterTypes[0])) {
                pluginInstance.getLogger().atSevere().log(String.format("[ECListener] @ECEventHandler(%s#%s) | The parameter %s is not assignable to IEvent", listener.getClass().getSimpleName(), methodListener.get().getName(), parameterTypes[0].getSimpleName()));
                foundAnyError = true;
                continue;
            }

            ECEventHandler annotation = methodListener.get().getAnnotation(ECEventHandler.class);

            Class<IEvent> eventClass = (Class<IEvent>) parameterTypes[0];

            Consumer consumer = event -> {
                if (annotation.ignoreCancelled() && event instanceof ICancellable cancellable){
                    if (cancellable.isCancelled()){
                        return;
                    }
                }

                methodListener.invoke(listener, event);
            };

            EventRegistration registration = pluginInstance.getEventRegistry().registerGlobal(
                    annotation.priority().getValue(),
                    eventClass,
                    consumer
            );

            registrations.add(registration);
        }

        if (foundAnyError){
            pluginInstance.getLogger().atSevere().log("Found errors while registering the ECListener: " + listener.getClass().getName());
            for (EventRegistration registration : registrations) {
                registration.unregister();
            }
            throw new RuntimeException("Found errors while registering the ECListener: " + listener.getClass().getName() + " read the previous messages...");
        }

        MAP_OF_REGISTRATIONS.put(listener, registrations);
    }

}
