package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.everylibs.commons.Tuple;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Pairs each parameter of a command method with the annotations declared on it.
 * <p>
 * If no parameter carries any annotation, the scan falls back to the same method declared on the
 * superclass - command methods sometimes inherit their {@code @Arg}-annotated signature from a base
 * class, and the reflected subclass method would otherwise expose bare (annotation-less) parameters.
 */
public class MethodArgScanner {

    public static List<Tuple<Class, Annotation[]>> getArgsAndAnnotationsDeeply(Method method) {
        List<Tuple<Class, Annotation[]>> argsAndAnnotations = new ArrayList<>();

        Class<?>[] parameterTypes = method.getParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();

        boolean foundAtLeastOneAnnotation = false;

        for (int i = 0; i < parameterTypes.length; i++) {
            Class theArg = parameterTypes[i];
            Annotation[] annotationsOnThisArg = annotations[i];
            if (annotationsOnThisArg.length > 0) {
                foundAtLeastOneAnnotation = true;
            }
            argsAndAnnotations.add(Tuple.of(theArg, annotationsOnThisArg));
        }

        if (!foundAtLeastOneAnnotation) { //Look for the father's method
            Class father = method.getDeclaringClass().getSuperclass();
            if (father == null) { //No father to look up
                return argsAndAnnotations;
            }
            try {
                method = father.getDeclaredMethod(method.getName(), method.getParameterTypes());
                return getArgsAndAnnotationsDeeply(method);
            } catch (NoSuchMethodException e) {
                //The method on this class is not present on its father
            }
        }

        return argsAndAnnotations;
    }
}
