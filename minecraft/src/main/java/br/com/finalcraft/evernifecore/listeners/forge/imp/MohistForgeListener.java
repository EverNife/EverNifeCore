package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import lombok.extern.log4j.Log4j2;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Log4j2
public class MohistForgeListener implements IForgeListener, ECListener {

    private static final String BUKKIT_HOOK_FORGE_EVENT = "com.mohistmc.api.event.BukkitHookForgeEvent";
    private static final String GET_EVENT = "getEvent";
    // Modern +1.16.5 Forge, then old 1.12.2 Forge
    private static final String[] SUBSCRIBE_EVENT = {
            "net.minecraftforge.eventbus.api.SubscribeEvent",
            "net.minecraftforge.fml.common.eventhandler.SubscribeEvent"
    };

    private final Class<? extends Annotation> subscribeEvent;
    private final MethodInvoker<Object> getEvent;

    public MohistForgeListener() {
        this.subscribeEvent = resolveSubscribeEvent();
        this.getEvent = ForgeReflection.method(BUKKIT_HOOK_FORGE_EVENT, GET_EVENT, 0);

        ECPluginData ecPluginData = EverNifeCore.getEcPluginData();
        ECListener.register(ecPluginData, this);
        hookBukkitBridge(ecPluginData);
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        registerListener(plugin, listener); // Mohist does not use BUS to register
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {

        for (Method declaredMethod : listener.getClass().getDeclaredMethods()) {
            declaredMethod.setAccessible(true);

            if (declaredMethod.isAnnotationPresent(subscribeEvent)){
                try {
                    Class forgeEvent = declaredMethod.getParameters()[0].getType();
                    this.addEventHandler(forgeEvent, event -> {
                        try {
                            declaredMethod.invoke(listener, event);
                        } catch (Throwable e) {
                            plugin.getLogger().severe("Error while invoking ForgeEvent " + forgeEvent.getSimpleName() + " on " + listener.getClass().getSimpleName());
                            e.printStackTrace();
                        }
                    });
                }catch (Throwable e){
                    plugin.getLogger().severe("Failed to register ForgeEvent listener for method: " + declaredMethod.getName());
                    e.printStackTrace();
                }
            }
        }

    }

    private static Class<? extends Annotation> resolveSubscribeEvent() {
        Class<?> resolved = FCReflectionUtil.getClasses().getFirstClass(SUBSCRIBE_EVENT);
        if (resolved == null) {
            throw new IllegalStateException("None of " + String.join(", ", SUBSCRIBE_EVENT) + " could be loaded"
                    + " on this server, so no method can be recognized as a Forge handler and this route is"
                    + " dead. This server runs a Forge version that EverNifeCore does not speak to - report"
                    + " the server brand and version.");
        }
        return resolved.asSubclass(Annotation.class);
    }

    /**
     * Mohist mirrors every Forge event into Bukkit as a single wrapper event, so this whole inbound
     * route is one Bukkit handler. It is registered programmatically rather than through an
     * {@code @EventHandler} method because naming the wrapper in a signature is what would compile a
     * Forge-side type into this module.
     */
    private void hookBukkitBridge(ECPluginData ecPluginData) {
        Class<? extends Event> wrapper = ForgeReflection.requireClass(BUKKIT_HOOK_FORGE_EVENT).asSubclass(Event.class);
        Plugin pluginInstance = (Plugin) ecPluginData.getPlugin();

        EventExecutor executor = (ignoredListener, event) -> {
            //HandlerLists are shared among native events, so the type is what decides who hears what
            if (wrapper.isInstance(event)) {
                onBukkitHookForgeEvent(event);
            }
        };

        Bukkit.getServer().getPluginManager()
                .registerEvent(wrapper, this, EventPriority.MONITOR, executor, pluginInstance, false);
    }

    //------------------------------------------------------------------------------------------------------------------
    //  Create Listener for the Core Events
    //------------------------------------------------------------------------------------------------------------------

    private Map<Class<?>, List<Consumer<?>>> eventHandlers = new LinkedHashMap<>();
    private Map<Class<?>, List<Consumer<?>>> eventHandlerCache = new LinkedHashMap<>();

    private void onBukkitHookForgeEvent(Event event) {
        Object forgeEvent = getEvent.invoke(event);
        Class<?> eventClass = forgeEvent.getClass();

        List<Consumer<?>> consumers = eventHandlerCache.get(eventClass);
        if (consumers == null) {
            consumers = new ArrayList<>();
            for (Map.Entry<Class<?>, List<Consumer<?>>> entry : eventHandlers.entrySet()) {
                if (entry.getKey().isAssignableFrom(eventClass)) {
                    consumers.addAll(entry.getValue());
                }
            }
            eventHandlerCache.put(eventClass, consumers);
        }

        // Call all consumers
        for (int i = 0; i < consumers.size(); i++) {
            Consumer consumer = consumers.get(i);
            try {
                consumer.accept(forgeEvent);
            } catch (Throwable e) {
                log.error("[ForgeListener] Error while invoking ForgeEvent " + eventClass.getSimpleName() + " on consumer " + consumer.getClass().getName(), e);
            }
        }
    }

    public <T> void addEventHandler(Class<T> clazz, Consumer<T> consumer){
        List<Consumer<?>> consumers = eventHandlers.computeIfAbsent(clazz, k -> new ArrayList<>());
        consumers.add(consumer);

        // Clear cache, because handler mapping has changed
        eventHandlerCache.clear();
    }

}
