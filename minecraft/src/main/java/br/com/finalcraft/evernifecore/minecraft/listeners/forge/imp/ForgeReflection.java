package br.com.finalcraft.evernifecore.minecraft.listeners.forge.imp;

import br.com.finalcraft.evernifecore.minecraft.listeners.forge.ForgeRegistration;
import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.FieldAccessor;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;
import br.com.finalcraft.everylibs.reflection.lookup.ClassLookup;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * By-name access to the Forge side of a hybrid server, for adapters that must never name a Forge type.
 *
 * <p>Every lookup here starts from a string, so this module compiles against no Forge artifact and its
 * bytecode carries no Forge descriptor. That is what lets one adapter serve eras that disagree on
 * types: {@code MinecraftForge.EVENT_BUS} is a {@code cpw.mods.fml.common.eventhandler.EventBus} on
 * 1.7.10 and a {@code net.minecraftforge.eventbus.api.IEventBus} from 1.16.5 on, and a by-name read
 * hands back either one as an opaque handle for the bus to receive later. NeoForge renamed the whole
 * tree, so its bus is reached under its own names.</p>
 *
 * <p>Every entry point answers one of two things: the result, or an {@link IllegalStateException}
 * naming the class and member the route wanted. That covers the by-name step that comes back empty
 * and the one that throws alike - see {@link #byName(String, Supplier)}. The refusal is thrown at call
 * time, never at class-initialization time, so a server that lacks the type loses this route and
 * nothing else.</p>
 */
final class ForgeReflection {

    private static final String MINECRAFT_FORGE = "net.minecraftforge.common.MinecraftForge";
    private static final String EVENT_BUS = "EVENT_BUS";
    private static final String MODERN_EVENT_BUS = "net.minecraftforge.eventbus.api.IEventBus";
    private static final String MODERN_SUBSCRIBE_EVENT = "net.minecraftforge.eventbus.api.SubscribeEvent";
    private static final String MODERN_EVENT_PRIORITY = "net.minecraftforge.eventbus.api.EventPriority";
    private static final String FML_COMMON_HANDLER = "cpw.mods.fml.common.FMLCommonHandler";
    private static final String NEOFORGE = "net.neoforged.neoforge.common.NeoForge";
    private static final String NEOFORGE_EVENT_BUS = "net.neoforged.bus.api.IEventBus";

    private ForgeReflection() {

    }

    /**
     * @return Forge's main event bus, as whatever type this era declares it to be.
     * @throws IllegalStateException if this runtime does not have it, or if reaching it throws.
     */
    static Object defaultEventBus() {
        return mainEventBusOf(MINECRAFT_FORGE);
    }

    /**
     * @return NeoForge's main event bus, the one its game events are posted on.
     * @throws IllegalStateException if this runtime does not have it, or if reaching it throws.
     */
    static Object neoForgeEventBus() {
        return mainEventBusOf(NEOFORGE);
    }

    private static Object mainEventBusOf(String home) {
        Class<?> owner = requireClass(home);
        String target = home + "." + EVENT_BUS;
        FieldAccessor<Object> eventBus = byName(target,
                () -> FCReflectionUtil.getFields().<Object>getField(owner, EVENT_BUS));
        if (eventBus == null) {
            throw new IllegalStateException(home + " is on this server but declares no field named '"
                    + EVENT_BUS + "'. Hand the bus you already hold to "
                    + "registerListener(plugin, listener, eventBus) instead of asking for the default one.");
        }
        return byName(target, eventBus::get);
    }

    /**
     * @return FML's own bus on 1.7.10 - a second bus beside {@code MinecraftForge.EVENT_BUS}, where
     * {@code TickEvent} and FML's {@code PlayerEvent} family (logged in, logged out, respawn,
     * changed dimension) are posted. From 1.8 on FML hands out {@code MinecraftForge.EVENT_BUS} itself.
     * @throws IllegalStateException if this runtime does not have it, or if reaching it throws.
     */
    static Object fmlEventBus() {
        Object handler = method(FML_COMMON_HANDLER, "instance", 0).invoke(null);
        return method(FML_COMMON_HANDLER, "bus", 0).invoke(handler);
    }

    /**
     * @return whether {@code bus} is one of the buses Forge hands out from 1.16.5 on, and {@code false}
     * on a runtime that does not carry that interface.
     * @throws IllegalStateException if the interface is on this runtime but cannot be loaded, or if
     * asking throws instead of answering. This answer picks the route the adapter takes, so a
     * {@code false} invented by a failed lookup would skip a registration in silence and nothing would
     * ever say why.
     */
    static boolean isModernEventBus(Object bus) {
        return isBusOfType(MODERN_EVENT_BUS, bus);
    }

    /**
     * @return whether {@code bus} is a NeoForge bus, and {@code false} on a runtime that does not carry
     * that interface.
     * @throws IllegalStateException under the same conditions as {@link #isModernEventBus(Object)}.
     */
    static boolean isNeoForgeEventBus(Object bus) {
        return isBusOfType(NEOFORGE_EVENT_BUS, bus);
    }

    private static boolean isBusOfType(String busInterface, Object bus) {
        ClassLookup eventBus = byName(busInterface,
                () -> FCReflectionUtil.getClasses().lookupClass(busInterface));
        if (eventBus.isUnlinkable()) {
            throw new IllegalStateException(busInterface + " is on this server but could not be loaded,"
                    + " so no bus here can be told apart as one of its kind and registering on it would be"
                    + " skipped without a word. Report the server brand and version - the chained cause is"
                    + " what the server threw while loading it.", eventBus.getLinkageError());
        }
        return eventBus.isFound() && eventBus.getType().isInstance(bus);
    }

    /**
     * Subscribes {@code handler} to {@code eventType} on a modern Forge bus, with the priority and the
     * cancelled-event choice {@code subscribeEvent} declares. The bus is handed a ready consumer, so it
     * generates no class of its own that would have to see the listener's classloader. The handler is
     * the key the bus files it under: {@code unregister(handler)} takes it off.
     *
     * @param subscribeEvent the {@code net.minecraftforge.eventbus.api.SubscribeEvent} on the method
     *                       the handler stands for
     * @throws IllegalStateException if a class or member this needs is not on this runtime, or if the
     *                               lookup throws instead of answering.
     */
    static void addModernListener(Object bus, Annotation subscribeEvent, Class<?> eventType, Consumer<?> handler) {
        Object priority = method(MODERN_SUBSCRIBE_EVENT, "priority", 0).invoke(subscribeEvent);
        Object receiveCanceled = method(MODERN_SUBSCRIBE_EVENT, "receiveCanceled", 0).invoke(subscribeEvent);

        Class<?> busType = requireClass(MODERN_EVENT_BUS);
        Class<?> priorityType = requireClass(MODERN_EVENT_PRIORITY);
        String target = MODERN_EVENT_BUS + ".addListener";
        MethodInvoker<Object> addListener = byName(target, () -> FCReflectionUtil.getMethods().<Object>getMethod(
                busType, "addListener", priorityType, boolean.class, Class.class, Consumer.class));
        if (addListener == null) {
            throw new IllegalStateException(MODERN_EVENT_BUS + " is on this server but declares no"
                    + " addListener(EventPriority, boolean, Class, Consumer). This server runs a build of it"
                    + " that EverNifeCore does not speak to - report the server brand and version.");
        }
        addListener.invoke(bus, priority, receiveCanceled, eventType, handler);
    }

    /**
     * Registers {@code listener} on a NeoForge bus through the bus's own {@code register(Object)}.
     *
     * @throws IllegalStateException if the bus interface or that member is not on this runtime, or if
     *                               the lookup throws instead of answering.
     */
    static void registerOnNeoForgeBus(Object bus, Object listener) {
        method(NEOFORGE_EVENT_BUS, "register", 1).invoke(bus, listener);
    }

    /**
     * The handle for a listener registered as itself on each of {@code buses}: undoing it calls each bus's own
     * {@code unregister(Object)}, the member every Forge and NeoForge event bus declares for exactly this. Every bus
     * is attempted even when one of them fails; the first failure is then thrown, carrying the others as
     * suppressed.
     */
    static ForgeRegistration unregisterFrom(List<Object> buses, Object listener) {
        List<Object> reached = new ArrayList<>(buses);
        return ForgeRegistration.once(() -> {
            RuntimeException first = null;
            for (Object bus : reached) {
                try {
                    unregisterMethod(bus).invoke(bus, listener);
                } catch (RuntimeException failure) {
                    if (first == null) {
                        first = failure;
                    } else {
                        first.addSuppressed(failure);
                    }
                }
            }
            if (first != null) {
                throw first;
            }
        });
    }

    private static MethodInvoker<Object> unregisterMethod(Object bus) {
        if (isNeoForgeEventBus(bus)) {
            //resolved on the interface NeoForge publishes: the class behind it sits in a named module
            //and nothing says its package is open to a plugin
            return method(NEOFORGE_EVENT_BUS, "unregister", 1);
        }
        Class<?> busType = bus.getClass();
        MethodInvoker<Object> invoker = byName(busType.getName() + ".unregister",
                () -> FCReflectionUtil.getMethods().<Object>getMethod(busType, "unregister", Object.class));
        if (invoker == null) {
            throw new IllegalStateException(busType.getName() + " declares no unregister(Object), so a listener"
                    + " registered on it cannot be taken off and stays until the server restarts. Report the"
                    + " server brand and version.");
        }
        return invoker;
    }

    /**
     * @param parameterCount how many arguments the caller is going to pass - checked here because a
     *                       by-name lookup would otherwise bind to an overload taking something else
     *                       and only fail deep inside the invocation.
     * @throws IllegalStateException if the class, the method, or that arity is not on this runtime, or
     *                               if the lookup throws instead of answering.
     */
    static MethodInvoker<Object> method(String className, String methodName, int parameterCount) {
        Class<?> owner = requireClass(className);
        MethodInvoker<Object> invoker = byName(className + "." + methodName,
                () -> FCReflectionUtil.getMethods().<Object>getMethod(owner, methodName));
        if (invoker == null) {
            throw new IllegalStateException(className + " is on this server but declares no method named '"
                    + methodName + "'. This server runs a build of it that EverNifeCore does not speak to -"
                    + " report the server brand and version.");
        }
        int declared = invoker.getMethod().getParameterCount();
        if (declared != parameterCount) {
            throw new IllegalStateException(className + "." + methodName + " takes " + declared
                    + " parameters on this server and EverNifeCore calls it with " + parameterCount
                    + ". This server runs a build of it that EverNifeCore does not speak to - report the"
                    + " server brand and version.");
        }
        return invoker;
    }

    /**
     * @throws IllegalStateException if the class is not on this server, is on it but cannot be loaded,
     *                               or the lookup throws instead of answering - each with its own message.
     */
    static Class<?> requireClass(String className) {
        ClassLookup resolved = byName(className, () -> FCReflectionUtil.getClasses().lookupClass(className));
        if (resolved.isUnlinkable()) {
            throw new IllegalStateException(className + " is on this server but could not be loaded. Nothing on"
                    + " this route works without it. Report the server brand and version - the chained cause"
                    + " is what the server threw while loading it.", resolved.getLinkageError());
        }
        if (resolved.isAbsent()) {
            throw new IllegalStateException(className + " is not on this server. Nothing on this route works"
                    + " without it; ask ForgeListener.isAvailable() before getting this far.");
        }
        return resolved.getType();
    }

    /**
     * Runs one by-name step, so a step that throws is refused the same way a step that comes back empty
     * is: an {@link IllegalStateException} naming {@code target}, carrying what actually went wrong.
     *
     * <p>{@link RuntimeException} and {@link LinkageError} are what is caught, and {@link Throwable} is
     * not. A hybrid rewrites reflection through a classloader of its own, which can fail to link a type
     * in the middle of a lookup - and a {@code LinkageError} is not an {@code Exception}, so catching
     * one does not catch the other. An {@code OutOfMemoryError} or a {@code StackOverflowError} is the
     * JVM failing rather than this server being strange, and it keeps its own type so the
     * {@code catch (Throwable)} fences around this route cannot file it as a quirky hybrid.</p>
     */
    private static <T> T byName(String target, Supplier<T> step) {
        try {
            return step.get();
        } catch (RuntimeException | LinkageError failure) {
            throw new IllegalStateException("Reaching " + target + " by name threw " + failure + " instead"
                    + " of answering. Something this server does not have comes back absent instead, so"
                    + " what failed is the by-name step itself. Report the server brand and version: the"
                    + " chained cause is what the server threw while resolving this name, so a type named"
                    + " in it is one the server tripped on, not the one asked for here.", failure);
        }
    }

}
