package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.listeners.forge.IForgeListener;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import com.mohistmc.api.event.BukkitHookForgeEvent;
import lombok.extern.log4j.Log4j2;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Log4j2
public class MohistForgeListener implements IForgeListener, ECListener {

    private static Class SubscribeEventClass;
    private static final MethodInvoker<Object> BukkitHookForgeEvent_getEvent = FCReflectionUtil.getMethods().getMethod(
            BukkitHookForgeEvent.class,
            "getEvent"
    );
    static {
        try {
            // Modern +1.16.5 Forge
            SubscribeEventClass = Class.forName("net.minecraftforge.eventbus.api.SubscribeEvent");
        } catch (Throwable e1) {
            try {
                // Old 1.12.2 Forge
                SubscribeEventClass = Class.forName("net.minecraftforge.fml.common.eventhandler.SubscribeEvent");
            } catch (Throwable e2) {
                throw new RuntimeException("Failed to find @SubscribeEvent class for MohistForgeListener", e2);
            }
        }
    }

    public MohistForgeListener() {
        ECListener.register(EverNifeCore.getEcPluginData(), this);
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener, Object... eventBus) {
        registerListener(plugin, listener); // Mohist does not use BUS to register
    }

    @Override
    public void registerListener(Plugin plugin, ECListener listener) {

        for (Method declaredMethod : listener.getClass().getDeclaredMethods()) {
            declaredMethod.setAccessible(true);

            if (declaredMethod.isAnnotationPresent(SubscribeEventClass)){
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

    //------------------------------------------------------------------------------------------------------------------
    //  Create Listener for the Core Events
    //------------------------------------------------------------------------------------------------------------------

    private Map<Class<?>, List<Consumer<?>>> eventHandlers = new LinkedHashMap<>();
    private Map<Class<?>, List<Consumer<?>>> eventHandlerCache = new LinkedHashMap<>();

    @EventHandler(ignoreCancelled = false, priority = EventPriority.MONITOR)
    public void onBukkitHookForgeEvent(BukkitHookForgeEvent event) {
        Object forgeEvent = BukkitHookForgeEvent_getEvent.invoke(event);
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
