package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.FieldAccessor;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;

import java.util.function.Supplier;

/**
 * By-name access to the Forge side of a hybrid server, for adapters that must never name a Forge type.
 *
 * <p>Every lookup here starts from a string, so this module compiles against no Forge artifact and its
 * bytecode carries no Forge descriptor. That is what lets one adapter serve eras that disagree on
 * types: {@code MinecraftForge.EVENT_BUS} is a {@code cpw.mods.fml.common.eventhandler.EventBus} on
 * 1.7.10 and a {@code net.minecraftforge.eventbus.api.IEventBus} from 1.16.5 on, and a by-name read
 * hands back either one as an opaque handle for the bus to receive later.</p>
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

    private ForgeReflection() {

    }

    /**
     * @return Forge's main event bus, as whatever type this era declares it to be.
     * @throws IllegalStateException if this runtime does not have it, or if reaching it throws.
     */
    static Object defaultEventBus() {
        Class<?> minecraftForge = requireClass(MINECRAFT_FORGE);
        String target = MINECRAFT_FORGE + "." + EVENT_BUS;
        FieldAccessor<Object> eventBus = byName(target,
                () -> FCReflectionUtil.getFields().<Object>getField(minecraftForge, EVENT_BUS));
        if (eventBus == null) {
            throw new IllegalStateException(MINECRAFT_FORGE + " is on this server but declares no field named '"
                    + EVENT_BUS + "'. Hand the bus you already hold to "
                    + "registerListener(plugin, listener, eventBus) instead of asking for the default one.");
        }
        return byName(target, eventBus::get);
    }

    /**
     * @return whether {@code bus} is one of the buses Forge hands out from 1.16.5 on, and {@code false}
     * on a runtime where that interface does not resolve - absent and unlinkable alike, which is one
     * answer here because the lookup reports both as absent.
     * @throws IllegalStateException if asking throws instead of answering. This answer picks the route
     * the adapter takes, so a {@code false} invented by a failed lookup would skip a registration in
     * silence and nothing would ever say why.
     */
    static boolean isModernEventBus(Object bus) {
        Class<?> modernEventBus = byName(MODERN_EVENT_BUS,
                () -> FCReflectionUtil.getClasses().getClass(MODERN_EVENT_BUS));
        return modernEventBus != null && modernEventBus.isInstance(bus);
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
     * @throws IllegalStateException if the class cannot be loaded here, which covers both "not on this
     *                               server" and "on it but failing to link" - the lookup reports either
     *                               one as absent - and a lookup that throws instead of answering.
     */
    static Class<?> requireClass(String className) {
        Class<?> resolved = byName(className, () -> FCReflectionUtil.getClasses().getClass(className));
        if (resolved == null) {
            throw new IllegalStateException(className + " could not be loaded on this server - it is either"
                    + " absent or present and unlinkable. Nothing on this route works without it; ask"
                    + " ForgeListener.isAvailable() before getting this far.");
        }
        return resolved;
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
