package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.reflection.ConstructorInvoker;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.SimpleTimeZone;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FCReflectionUtilTest {

    private static class TestClass { //Class With Primitives Classes in Construtor
        int int_value;
        Boolean boolean_value;
        Float float_value;
        double double_value;

        public TestClass(int int_value, Boolean boolean_value, Float float_value, double double_value) {
            this.int_value = int_value;
            this.boolean_value = boolean_value;
            this.float_value = float_value;
            this.double_value = double_value;
        }

        @Override
        public String toString() {
            return "TestClass{" +
                    "int_value=" + int_value +
                    ", boolean_value=" + boolean_value +
                    ", float_value=" + float_value +
                    ", double_value=" + double_value +
                    '}';
        }
    }

    @Test
    @Order(0)
    public void testeConstrutor() {
        TestClass test;
        ConstructorInvoker<TestClass> constructorInvoker;

        constructorInvoker = FCReflectionUtil.getConstructor(TestClass.class, int.class, Boolean.class, Float.class, double.class);
        test = constructorInvoker.invoke(1,true, 1F, 1D);

        System.out.println("Test1: ");
        System.out.println(test);

        constructorInvoker = FCReflectionUtil.getConstructor(TestClass.class, Integer.class, Boolean.class, Float.class, Double.class);
        test = constructorInvoker.invoke(2,false, 2F, 2D);

        System.out.println("Test2: ");
        System.out.println(test);
    }

    public Tuple<SimpleTimeZone,SimpleTimeZone> integers;

    @Test
    public void testeGenericTypeInstanceofParameterizedType() throws NoSuchFieldException {
        if (true) {
            return; // Disabled test to avoid execution during normal test runs
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
