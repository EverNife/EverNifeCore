package br.com.finalcraft.evernifecore.listeners.forge.imp;

import br.com.finalcraft.everylibs.reflection.FCReflectionUtil;
import br.com.finalcraft.everylibs.reflection.FieldAccessor;
import br.com.finalcraft.everylibs.reflection.MethodInvoker;

/**
 * By-name access to the Forge side of a hybrid server, for adapters that must never name a Forge type.
 *
 * <p>Every lookup here starts from a string, so this module compiles against no Forge artifact and its
 * bytecode carries no Forge descriptor. That is what lets one adapter serve eras that disagree on
 * types: {@code MinecraftForge.EVENT_BUS} is a {@code cpw.mods.fml.common.eventhandler.EventBus} on
 * 1.7.10 and a {@code net.minecraftforge.eventbus.api.IEventBus} from 1.16.5 on, and a by-name read
 * hands back either one as an opaque handle for the bus to receive later.</p>
 *
 * <p>A miss is an {@link IllegalStateException} thrown at call time - never at class-initialization
 * time - so a server that lacks the type loses this route and nothing else.</p>
 */
final class ForgeReflection {

    private static final String MINECRAFT_FORGE = "net.minecraftforge.common.MinecraftForge";
    private static final String EVENT_BUS = "EVENT_BUS";
    private static final String MODERN_EVENT_BUS = "net.minecraftforge.eventbus.api.IEventBus";

    private ForgeReflection() {

    }

    /**
     * @return Forge's main event bus, as whatever type this era declares it to be.
     * @throws IllegalStateException if this runtime does not have it.
     */
    static Object defaultEventBus() {
        Class<?> minecraftForge = requireClass(MINECRAFT_FORGE);
        FieldAccessor<Object> eventBus = FCReflectionUtil.getFields().getField(minecraftForge, EVENT_BUS);
        if (eventBus == null) {
            throw new IllegalStateException(MINECRAFT_FORGE + " is on this server but declares no field named '"
                    + EVENT_BUS + "'. Hand the bus you already hold to "
                    + "registerListener(plugin, listener, eventBus) instead of asking for the default one.");
        }
        return eventBus.get();
    }

    /**
     * @return whether {@code bus} is one of the buses Forge hands out from 1.16.5 on, and {@code false}
     * on a runtime that has no such interface at all.
     */
    static boolean isModernEventBus(Object bus) {
        Class<?> modernEventBus = FCReflectionUtil.getClasses().getClass(MODERN_EVENT_BUS);
        return modernEventBus != null && modernEventBus.isInstance(bus);
    }

    /**
     * @param parameterCount how many arguments the caller is going to pass - checked here because a
     *                       by-name lookup would otherwise bind to an overload taking something else
     *                       and only fail deep inside the invocation.
     * @throws IllegalStateException if the class, the method, or that arity is not on this runtime.
     */
    static MethodInvoker<Object> method(String className, String methodName, int parameterCount) {
        Class<?> owner = requireClass(className);
        MethodInvoker<Object> invoker = FCReflectionUtil.getMethods().getMethod(owner, methodName);
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
     *                               server" and "on it but failing to link".
     */
    static Class<?> requireClass(String className) {
        Class<?> resolved = FCReflectionUtil.getClasses().getClass(className);
        if (resolved == null) {
            throw new IllegalStateException(className + " could not be loaded on this server - it is either"
                    + " absent or present and unlinkable. Nothing on this route works without it; ask"
                    + " ForgeListener.isAvailable() before getting this far.");
        }
        return resolved;
    }

}
