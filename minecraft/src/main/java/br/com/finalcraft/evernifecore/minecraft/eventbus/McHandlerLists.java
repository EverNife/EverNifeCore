package br.com.finalcraft.evernifecore.minecraft.eventbus;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.base.ECEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bukkit's own answer to "which HandlerList does a listener of this event class register into",
 * computed the way the server computes it and cached per class.
 *
 * <p>The plugin manager finds the list by walking up from the listened class to the first one that
 * DECLARES a static {@code getHandlerList}, invoking it and casting the value. Whatever that walk
 * returns is where {@code registerEvent} puts the listener - so it is also what an EC event's
 * {@code getHandlers()} must return for dispatch to reach them, and what the Bukkit audience must
 * read to know whether anyone listens. Resolving all three through this one function is what keeps
 * them from ever disagreeing, whatever list an event's author chose to hand back.</p>
 */
public final class McHandlerLists {

    private McHandlerLists() {
    }

    //ClassValue instead of a map keyed by Class, for both: the entry lives and dies with the class
    //itself, so a plugin whose classloader goes away does not leave its event classes pinned here.
    private static final ClassValue<AtomicBoolean> WARNED = new ClassValue<AtomicBoolean>() {
        @Override
        protected AtomicBoolean computeValue(Class<?> eventType) {
            return new AtomicBoolean();
        }
    };

    private static final ClassValue<HandlerList> REGISTRATION_LIST = new ClassValue<HandlerList>() {
        @Override
        protected HandlerList computeValue(Class<?> eventType) {
            return resolve(eventType);
        }
    };

    /**
     * The list Bukkit registers a listener of {@code eventType} into: the {@code getHandlerList} of the
     * first class up its chain that declares one. An EC event that declares none resolves to the
     * family fallback ({@link ECEvent#getHandlerList()}), which is where Bukkit itself would land.
     */
    public static HandlerList registrationListOf(Class<?> eventType) {
        return REGISTRATION_LIST.get(eventType);
    }

    private static HandlerList resolve(Class<?> eventType) {
        for (Class<?> level = eventType; level != null && level != Event.class && level != Object.class; level = level.getSuperclass()) {
            Method method;
            try {
                method = level.getDeclaredMethod("getHandlerList");
            } catch (NoSuchMethodException notDeclaredHere) {
                continue;
            }
            //the first declaring class decides, exactly like the plugin manager: a wrong shape here is
            //a wrong shape for Bukkit too, so it is reported and the family fallback stands in
            if (!Modifier.isStatic(method.getModifiers())) {
                warnOnce(eventType, level, "it is not static");
                break;
            }
            try {
                method.setAccessible(true);
                Object list = method.invoke(null);
                if (list instanceof HandlerList) {
                    return (HandlerList) list;
                }
                warnOnce(eventType, level, "it returned " + (list == null ? "null" : list.getClass().getName()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce(eventType, level, "invoking it failed: " + e);
            }
            break;
        }
        return ECEvent.getHandlerList();
    }

    private static void warnOnce(Class<?> eventType, Class<?> declaringType, String problem) {
        if (!WARNED.get(eventType).compareAndSet(false, true)) {
            return;
        }
        EverNifeCore.getLog().severe("[ECEventBus] {} declares getHandlerList but {}. Bukkit finds that method by"
                        + " name up the hierarchy, invokes it static and expects a HandlerList back - a Bukkit"
                        + " listener of {} would fail to register. Make it a public static getHandlerList() that"
                        + " returns ECEvent.getHandlerListOf({}.class), or remove it and share the family list."
                        + " Until then the family list stands in for it here.",
                declaringType.getName(), problem, eventType.getSimpleName(), declaringType.getSimpleName());
    }

}
