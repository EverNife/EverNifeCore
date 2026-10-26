package br.com.finalcraft.evernifecore.minecraft.waypoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Calls into a plugin this jar does not compile against (hologram and web-map plugins): by class name, method name and
 * argument count. Every failure surfaces as an {@link IllegalStateException} naming the call, so a plugin version that
 * moved a method is reported, not swallowed.
 */
final class Reflect {

    private Reflect() {
    }

    static Class<?> type(String name) {
        try {
            return Class.forName(name, true, Reflect.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("The class " + name + " is not on the server: the plugin that ships it is missing or too old.", missing);
        }
    }

    /** A static method of {@code type}. */
    static Object callStatic(Class<?> type, String method, Object... args) {
        return invoke(type, null, method, args);
    }

    /** An instance method of {@code target}. */
    static Object call(Object target, String method, Object... args) {
        return invoke(target.getClass(), target, method, args);
    }

    static Object construct(Class<?> type, Object... args) {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (accepts(constructor.getParameterTypes(), args)) {
                try {
                    return constructor.newInstance(args);
                } catch (InvocationTargetException thrown) {
                    throw rethrow(type.getName() + ".<init>", thrown.getCause());
                } catch (ReflectiveOperationException failure) {
                    throw rethrow(type.getName() + ".<init>", failure);
                }
            }
        }
        throw new IllegalStateException("No constructor of " + type.getName() + " takes " + args.length + " such arguments: the plugin's API changed.");
    }

    private static Object invoke(Class<?> type, Object target, String name, Object... args) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && (target == null) == Modifier.isStatic(method.getModifiers())
                    && accepts(method.getParameterTypes(), args)) {
                try {
                    //a public method inherited from a package-private class is not reachable without this
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (InvocationTargetException thrown) {
                    throw rethrow(type.getName() + "." + name, thrown.getCause());
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    throw rethrow(type.getName() + "." + name, failure);
                }
            }
        }
        throw new IllegalStateException("No method " + type.getName() + "." + name + " takes " + args.length + " such arguments: the plugin's API changed.");
    }

    private static boolean accepts(Class<?>[] params, Object[] args) {
        if (params.length != args.length) {
            return false;
        }
        for (int i = 0; i < params.length; i++) {
            if (args[i] != null && !box(params[i]).isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == double.class) return Double.class;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == float.class) return Float.class;
        if (type == long.class) return Long.class;
        return type;
    }

    private static IllegalStateException rethrow(String call, Throwable cause) {
        return new IllegalStateException(call + " failed: " + cause, cause);
    }
}
