package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.util.ReflectionUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodData<T extends CMDData> {

    private final T data;
    private final Method method;
    private final List<Tuple<ArgData, Class>> argDataList = new ArrayList<>();

    public MethodData(T data, Method method) {
        this.data = data;
        this.method = method;

        if (method == null) return;
        //Add all @Arg methods
        for (Tuple<Class, Annotation[]> tuple : ReflectionUtil.getArgsAndAnnotations(method)) {
            Arg arg = (Arg) Arrays.stream(tuple.getBeta()).filter(annotation -> annotation.annotationType() == Arg.class).findFirst().orElse(null);
            if (arg != null){
                argDataList.add(Tuple.of(new ArgData(arg), tuple.getAlfa()));
            }
        }
    }

    public T getData() {
        return data;
    }

    public Method getMethod() {
        return method;
    }

    public List<Tuple<ArgData, Class>> getArgDataList() {
        return argDataList;
    }
}
