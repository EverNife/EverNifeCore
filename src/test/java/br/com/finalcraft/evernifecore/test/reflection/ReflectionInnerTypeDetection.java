package br.com.finalcraft.evernifecore.test.reflection;

import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.SimpleTimeZone;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReflectionInnerTypeDetection {

    public Tuple<SimpleTimeZone,SimpleTimeZone> integers;

    @Test
    public void teste() throws NoSuchFieldException {
        if (true) {
            return;
        }

        Field field = this.getClass().getDeclaredField("integers");
        Type genericType = field.getGenericType();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] fieldArgTypes = pt.getActualTypeArguments();
            for (Type type : fieldArgTypes) {
                System.out.println("Field type: " + type);
            }
        } else {
            System.out.println("Field is not parameterized");
        }
    }

}
