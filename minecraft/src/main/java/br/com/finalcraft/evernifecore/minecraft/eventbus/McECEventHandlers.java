package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.api.events.base.IECEvent;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventHandler;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The Bukkit half of {@link ECEventHandler}. A handler that names a Bukkit event carries no
 * {@code @EventHandler}, so the server's own scan never sees it; this registers it programmatically,
 * with an executor and a priority of its own.
 */
public final class McECEventHandlers {

    private McECEventHandlers() {

    }

    /**
     * Registers with the server every {@link ECEventHandler} method of {@code listener} that names a
     * Bukkit event. A method naming an {@link IECEvent} belongs to the event bus and is left alone.
     *
     * @throws RuntimeException when a method names something that is no event at all - and then
     *                          nothing of {@code listener} stays registered, this route included
     */
    public static void register(ECPluginData ecPluginData, ECListener listener) {
        List<MethodInvoker<?>> annotatedMethods = FCReflectionUtil.getMethods()
                .getMethods(listener.getClass(), method -> method.getAnnotation(ECEventHandler.class) != null)
                .collect(Collectors.toList());

        if (annotatedMethods.isEmpty()) {
            return;
        }

        Plugin pluginInstance = (Plugin) ecPluginData.getPlugin();
        PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        boolean foundAnyError = false;

        for (MethodInvoker<?> invoker : annotatedMethods) {
            Method method = invoker.getMethod();
            Class<?>[] parameterTypes = method.getParameterTypes();

            if (parameterTypes.length != 1) {
                ecPluginData.getLog().severe("[ECListener] @ECEventHandler(" + describe(listener, method)
                        + ") takes " + parameterTypes.length + " parameters. A handler takes exactly one: "
                        + "the event it wants to hear.");
                foundAnyError = true;
                continue;
            }

            Class<?> parameterType = parameterTypes[0];

            if (IECEvent.class.isAssignableFrom(parameterType)) {
                //Asked BEFORE the Bukkit type, because an ECEvent is both: registering it here as well
                //would deliver it twice, once from the bus and once from the mirror the bus feeds.
                continue;
            }

            if (!Event.class.isAssignableFrom(parameterType)) {
                ecPluginData.getLog().severe("[ECListener] @ECEventHandler(" + describe(listener, method)
                        + ") names " + parameterType.getName() + ", which is neither an IECEvent nor an "
                        + "org.bukkit.event.Event. A handler parameter has to be one of the two: an "
                        + "IECEvent is delivered by the EverNifeCore bus, a Bukkit event by the server.");
                foundAnyError = true;
                continue;
            }

            ECEventHandler annotation = method.getAnnotation(ECEventHandler.class);
            short priority = annotation.priorityValue() != -1
                    ? annotation.priorityValue()
                    : annotation.priority().getValue();
            Class<? extends Event> eventType = parameterType.asSubclass(Event.class);

            EventExecutor executor = (ignoredListener, event) -> {
                //the filter Bukkit's own generated executor applies: HandlerLists are shared among
                //native events too, so the parameter type is what decides who hears what
                if (!eventType.isInstance(event)) {
                    return;
                }
                invoker.invoke(listener, event);
            };

            pluginManager.registerEvent(eventType, listener, toBukkitPriority(priority), executor,
                    pluginInstance, annotation.ignoreCancelled());
        }

        if (foundAnyError) {
            //everything this listener had registered goes, the @EventHandler methods included: half a
            //listener answering events is worse than one that plainly did not load
            HandlerList.unregisterAll(listener);
            throw new RuntimeException("The ECListener " + listener.getClass().getName() + " has an "
                    + "@ECEventHandler method that could not be registered (the messages above name "
                    + "each one). Nothing of this listener stayed registered.");
        }
    }

    /**
     * The EC priority as the nearest Bukkit one, cutting the EC scale at the midpoints between its own
     * steps. {@code MONITOR} is unreachable on purpose: it is Bukkit's "watch, never change" slot, and
     * an EC handler asking for LAST is asking to be last among handlers, not to stop being one.
     */
    public static EventPriority toBukkitPriority(short priority) {
        //FIRST=-21844, EARLY=-10922, NORMAL=0, LATE=10922, LAST=21844
        if (priority < -16383) {
            return EventPriority.LOWEST;
        }
        if (priority < -5461) {
            return EventPriority.LOW;
        }
        if (priority <= 5461) {
            return EventPriority.NORMAL;
        }
        if (priority <= 16383) {
            return EventPriority.HIGH;
        }
        return EventPriority.HIGHEST;
    }

    private static String describe(ECListener listener, Method method) {
        return listener.getClass().getSimpleName() + "#" + method.getName();
    }

}
