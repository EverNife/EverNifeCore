package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.reflection.ConstructorInvoker;
import br.com.finalcraft.evernifecore.util.reflection.FieldAccessor;
import br.com.finalcraft.evernifecore.util.reflection.MethodInvoker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A set of utilities to help with Reflection.
 * Based on {@link <a href="https://github.com/dmulloy2/ProtocolLib/blob/gradle/TinyProtocol/src/main/java/com/comphenix/tinyprotocol/Reflection.java">ProtocolLib Reflection</a>}.
 * Based on juanmuscaria version of ProtocolLib Reflection
 *
 * @author EverNife
 */
public class ReflectionUtil {

    /**
     * Retrieve a field accessor for a specific field type and name.
     *
     * @param method  - the method
     * @param argType - the class of the arg being looked at
     * @return The number of the arg in this method.
     */
    public static Integer getArgIndex(Method method, Class argType, boolean checkExtends) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (checkExtends){
                if (argType.isAssignableFrom(parameterTypes[i])) {
                    return i;
                }
            }else {
                if (argType == parameterTypes[i]) {
                    return i;
                }
            }
        }
        return null;
    }

    /**
     * Retrieve a field accessor for a specific field type and name.
     *
     * @param method  - the method
     * @param index   - the index of the parameter of the method
     * @return The class of the parameter at the index value.
     */
    public static Class getArgAtIndex(Method method, int index) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (index >= parameterTypes.length) return null;
        return parameterTypes[index];
    }

    /**
     * Retrieve a field accessor for a specific field type and name.
     *
     * @param target - the target type.
     * @param name   - the name of the field, or NULL to ignore.
     * @return The field accessor.
     */
    public static <T> FieldAccessor<T> getField(Class<?> target, String name) {
        return getField(target, name, null, 0);
    }

    /**
     * Retrieve a field accessor for a specific field type and name.
     *
     * @param target    - the target type.
     * @param name      - the name of the field, or NULL to ignore.
     * @param fieldType - a compatible field type.
     * @return The field accessor.
     */
    public static <T> FieldAccessor<T> getField(Class<?> target, String name, Class<T> fieldType) {
        return getField(target, name, fieldType, 0);
    }

    /**
     * Retrieve a field accessor for a specific field type and name.
     *
     * @param className - lookup name of the class, see {@link #getClass(String)}.
     * @param name      - the name of the field, or NULL to ignore.
     * @param fieldType - a compatible field type.
     * @return The field accessor.
     */
    public static <T> FieldAccessor<T> getField(String className, String name, Class<T> fieldType) {
        return getField(getClass(className), name, fieldType, 0);
    }

    /**
     * Retrieve a field accessor for a specific field type and name.
     *
     * @param target    - the target type.
     * @param fieldType - a compatible field type.
     * @param index     - the number of compatible fields to skip.
     * @return The field accessor.
     */
    public static <T> FieldAccessor<T> getField(Class<?> target, Class<T> fieldType, int index) {
        return getField(target, null, fieldType, index);
    }

    /**
     * Retrieve a field accessor for a specific field type and name.
     *
     * @param className - lookup name of the class, see {@link #getClass(String)}.
     * @param fieldType - a compatible field type.
     * @param index     - the number of compatible fields to skip.
     * @return The field accessor.
     */
    public static <T> FieldAccessor<T> getField(String className, Class<T> fieldType, int index) {
        return getField(getClass(className), fieldType, index);
    }

    // Common method
    private static <T> FieldAccessor<T> getField(Class<?> target, String name, Class<T> fieldType, int index) {
        for (final Field field : target.getDeclaredFields()) {
            if ((name == null || field.getName().equals(name)) && (fieldType == null || fieldType.isAssignableFrom(field.getType())) && index-- <= 0) {
                field.setAccessible(true);

                // A function for retrieving a specific field value
                return new FieldAccessor<T>() {

                    @Override
                    @SuppressWarnings("unchecked")
                    public T get(Object target) {
                        try {
                            return (T) field.get(target);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Cannot access reflection.", e);
                        }
                    }

                    @Override
                    public void set(Object target, Object value) {
                        try {
                            field.set(target, value);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Cannot access reflection.", e);
                        }
                    }

                    @Override
                    public boolean hasField(Object target) {
                        // target instanceof DeclaringClass
                        return field.getDeclaringClass().isAssignableFrom(target.getClass());
                    }

                    @Override
                    public Field getTheField() {
                        return field;
                    }
                };
            }
        }

        // Search in parent classes
        if (target.getSuperclass() != null)
            return getField(target.getSuperclass(), name, fieldType, index);

        throw new IllegalArgumentException("Cannot find field with type " + fieldType);
    }

    /**
     * Search for the first publicly and privately defined method of the given name and parameter count.
     *
     * @param className  - lookup name of the class, see {@link #getClass(String)}.
     * @return An object that invokes this specific method.
     * @throws IllegalStateException If we cannot find this method.
     */
    public static Stream<MethodInvoker> getMethods(String className, Function<Method, Boolean> filter) {
        return getMethods(getClass(className), filter);
    }

    /**
     * Search for the first publicly and privately defined method of the given name and parameter count.
     *
     * @param clazz      - a class to start with.
     * @return An object that invokes this specific method.
     * @throws IllegalStateException If we cannot find this method.
     */
    public static Stream<MethodInvoker> getMethods(Class<?> clazz, Function<Method, Boolean> filter) {
        return Arrays.stream(clazz.getMethods()).filter(method -> filter.apply(method)).map(method -> {
           return new MethodInvoker() {
                @Override
                public Object invoke(Object target, Object... arguments) {
                    try {
                        return method.invoke(target, arguments);
                    } catch (Exception e) {
                        throw new RuntimeException("Cannot invoke method " + method, e);
                    }
                }

                @Override
                public Method get() {
                    return method;
                }
            };
        });
    }


    /**
     * Search for the first publicly and privately defined method of the given name and parameter count.
     *
     * @param className  - lookup name of the class, see {@link #getClass(String)}.
     * @param methodName - the method name, or NULL to skip.
     * @param params     - the expected parameters.
     * @return An object that invokes this specific method.
     * @throws IllegalStateException If we cannot find this method.
     */
    public static MethodInvoker getMethod(String className, String methodName, Class<?>... params) {
        return getTypedMethod(getClass(className), methodName, null, params);
    }

    /**
     * Search for the first publicly and privately defined method of the given name and parameter count.
     *
     * @param clazz      - a class to start with.
     * @param methodName - the method name, or NULL to skip.
     * @param params     - the expected parameters.
     * @return An object that invokes this specific method.
     * @throws IllegalStateException If we cannot find this method.
     */
    public static MethodInvoker getMethod(Class<?> clazz, String methodName, Class<?>... params) {
        return getTypedMethod(clazz, methodName, null, params);
    }

    /**
     * Search for the first publicly and privately defined method of the given name and parameter count.
     *
     * @param clazz      - a class to start with.
     * @param methodName - the method name, or NULL to skip.
     * @param returnType - the expected return type, or NULL to ignore.
     * @param params     - the expected parameters.
     * @return An object that invokes this specific method.
     * @throws IllegalStateException If we cannot find this method.
     */
    public static MethodInvoker getTypedMethod(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... params) {
        //System.out.println(String.format("Looking for: %s(%s).", methodName, Arrays.asList(params)));
        for (final Method method : clazz.getDeclaredMethods()) {
            if ((methodName == null || method.getName().equals(methodName))
                    && (returnType == null || method.getReturnType().equals(returnType))
                    && (params.length == 0 || Arrays.equals(method.getParameterTypes(), params))) {
                method.setAccessible(true);

                return new MethodInvoker() {

                    @Override
                    public Object invoke(Object target, Object... arguments) {
                        Object[] correctArgs = new Object[arguments.length];
                        for (int i = 0; i < arguments.length; i++) {
                            //Fix this error https://stackoverflow.com/questions/48577435/why-do-i-get-java-lang-illegalargumentexception-wrong-number-of-arguments-while
                            correctArgs[i] = arguments[i] != null && arguments[i].getClass().isArray() ? new Object[]{arguments[i]} : arguments[i];
                        }
                        try {
                            return method.invoke(target, correctArgs);
                        } catch (Exception e) {
                            throw new RuntimeException("Cannot invoke method " + method, e);
                        }
                    }

                    @Override
                    public Method get() {
                        return method;
                    }

                };
            }
        }

        // Search in every superclass
        if (clazz.getSuperclass() != null)
            return getMethod(clazz.getSuperclass(), methodName, params);

        throw new IllegalStateException(String.format("Unable to find method %s(%s).", methodName, Arrays.asList(params)));
    }

    /**
     * Search for the first publically and privately defined constructor of the given name and parameter count.
     *
     * @param className - lookup name of the class, see {@link #getClass(String)}.
     * @param params    - the expected parameters.
     * @return An object that invokes this constructor.
     * @throws IllegalStateException If we cannot find this method.
     */
    public static ConstructorInvoker getConstructor(String className, Class<?>... params) {
        return getConstructor(getClass(className), params);
    }

    /**
     * Search for the first publically and privately defined constructor of the given name and parameter count.
     *
     * @param clazz  - a class to start with.
     * @param params - the expected parameters.
     * @return An object that invokes this constructor.
     * @throws IllegalStateException If we cannot find this method.
     */
    public static <T> ConstructorInvoker<T> getConstructor(Class<T> clazz, Class<?>... params) {
        for (final Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (Arrays.equals(constructor.getParameterTypes(), params)) {
                constructor.setAccessible(true);

                return new ConstructorInvoker() {

                    @Override
                    public Object invoke(Object... arguments) {
                        try {
                            return constructor.newInstance(arguments);
                        } catch (Exception e) {
                            throw new RuntimeException("Cannot invoke constructor " + constructor, e);
                        }
                    }

                };
            }
        }

        throw new IllegalStateException(String.format("Unable to find constructor for %s (%s).", clazz, Arrays.asList(params)));
    }

    /**
     * Retrieve a class from its full name, without knowing its type on compile time.
     * <p>
     * This is useful when looking up fields by a NMS or OBC type.
     * <p>
     *
     * @param lookupName - the class name with variables.
     * @return The class.
     * @see #getClass(String) for more information.
     */
    public static Class<Object> getUntypedClass(String lookupName) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        Class<Object> clazz = (Class) getClass(lookupName);
        return clazz;
    }

    /**
     * Retrieve a class from its full name.
     *
     * @param lookupName - the class name.
     * @return The looked up class.
     * @throws IllegalArgumentException If a variable or class could not be found.
     */
    public static Class<?> getClass(String lookupName) {
        try {
            return Class.forName(lookupName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot find " + lookupName, e);
        }
    }

    /**
     * Checks if a class exists.
     *
     * @param name the class name.
     * @return true if the class exists.
     */
    public static boolean doesClassExist(String name) {
        try {
            Class c = Class.forName(name);
            if (c != null)
                return true;
        } catch (NoClassDefFoundError | ClassNotFoundException ignored) {
        }
        return false;
    }

    /**
     * Get all fields of a class on declaration order
     *
     * @param clazz - a class to look into.
     * @return the list of Fields encapsulated on FieldAccessors
     */
    public static List<FieldAccessor> getDeclaredFields(Class clazz){
        List<FieldAccessor> fieldAccessorList = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            fieldAccessorList.add(new FieldAccessor() {
                @Override
                @SuppressWarnings("unchecked")
                public Object get(Object target) {
                    try {
                        return field.get(target);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Cannot access reflection.", e);
                    }
                }

                @Override
                public void set(Object target, Object value) {
                    try {
                        field.set(target, value);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Cannot access reflection.", e);
                    }
                }

                @Override
                public boolean hasField(Object target) {
                    // target instanceof DeclaringClass
                    return field.getDeclaringClass().isAssignableFrom(target.getClass());
                }

                @Override
                public Field getTheField() {
                    return field;
                }
            });
        }
        return fieldAccessorList;
    }
}
