package br.com.finalcraft.evernifecore.minecraft.gui.testkit;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds an interface double by naming only the methods a test actually exercises.
 *
 * <p>Bukkit's interfaces are enormous - {@code Player} alone answers hundreds of questions - and a
 * gui reads five or six of them. Everything unnamed answers the neutral value for its return type
 * instead of throwing, because a proxy that throws on the first unlisted call turns every unrelated
 * platform call inside the code under test into a test failure with no bearing on what is being
 * proven.</p>
 */
public final class Doubles {

    private Doubles() {

    }

    public static <T> Builder<T> of(Class<T> type) {
        return new Builder<>(type);
    }

    public static final class Builder<T> {

        private final Class<T> type;
        private final Map<String, Function<Object[], Object>> answers = new HashMap<>();

        private Builder(Class<T> type) {
            this.type = type;
        }

        /** Answers {@code methodName} with {@code answer}, whatever its argument list. */
        public Builder<T> on(String methodName, Function<Object[], Object> answer) {
            answers.put(methodName, answer);
            return this;
        }

        public Builder<T> returning(String methodName, Object value) {
            return on(methodName, args -> value);
        }

        @SuppressWarnings("unchecked")
        public T build() {
            Map<String, Function<Object[], Object>> table = new HashMap<>(answers);
            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    Function<Object[], Object> answer = table.get(method.getName());
                    if (answer != null) {
                        return answer.apply(args == null ? new Object[0] : args);
                    }
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return type.getSimpleName() + "Double@" + Integer.toHexString(System.identityHashCode(proxy));
                        default:
                            return neutral(method.getReturnType());
                    }
                }
            };
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }

    }

    /** What an unlisted method answers: {@code null} for a reference, zero/false for a primitive. */
    private static Object neutral(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return Boolean.FALSE;
        }
        if (returnType == char.class) {
            return (char) 0;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }

}
