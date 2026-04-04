package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.ContextualArg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FlagArg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgContextualData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class MethodData<T extends CMDData> {

    private final T data;
    private final Method method;
    private final Map<Integer, Tuple<ArgData, Class>> argDataMap = new LinkedHashMap<>();
    private final Map<Integer, Tuple<ArgData, Class>> flagArgDataMap = new LinkedHashMap<>();
    private final Map<Integer, Tuple<ArgContextualData, Class>> contextualArgDataMap = new LinkedHashMap<>();

    public MethodData(@Nonnull T data, @Nullable Method method) {
        this.data = data;
        this.method = method;

        if (method == null) return;
        //Add all @Arg methods
        int index = -1;
        for (Tuple<Class, Annotation[]> tuple : FCReflectionUtil.getArgsAndAnnotationsDeeply(method)) {
            index++;

            Arg arg = (Arg) Arrays.stream(tuple.getRight())
                    .filter(annotation -> annotation.annotationType() == Arg.class)
                    .findFirst()
                    .orElse(null);
            if (arg != null){
                argDataMap.put(index, Tuple.of(new ArgData(arg), tuple.getLeft()));
            }

            FlagArg flagArg = (FlagArg) Arrays.stream(tuple.getRight())
                    .filter(annotation -> annotation.annotationType() == FlagArg.class)
                    .findFirst()
                    .orElse(null);
            if (flagArg != null){
                flagArgDataMap.put(index, Tuple.of(new ArgData(flagArg), tuple.getLeft()));
            }

            ContextualArg contextualArg = (ContextualArg) Arrays.stream(tuple.getRight())
                    .filter(annotation -> annotation.annotationType() == ContextualArg.class)
                    .findFirst()
                    .orElse(null);
            if (contextualArg != null){
                contextualArgDataMap.put(index, Tuple.of(new ArgContextualData(contextualArg), tuple.getLeft()));
            }

            if (tuple.getRight().length == 0){ //When no annotation at all, assume it's a contextual arg
                contextualArgDataMap.put(index, Tuple.of(new ArgContextualData(), tuple.getLeft()));
            }

        }
    }

    public T getData() {
        return data;
    }

    public Method getMethod() {
        return method;
    }

    public Map<Integer, Tuple<ArgData, Class>> getArgDataMap() {
        return argDataMap;
    }

    public Map<Integer, Tuple<ArgData, Class>> getFlagArgDataMap() {
        return flagArgDataMap;
    }

    public Map<Integer, Tuple<ArgContextualData, Class>> getContextualArgDataMap() {
        return contextualArgDataMap;
    }
}
