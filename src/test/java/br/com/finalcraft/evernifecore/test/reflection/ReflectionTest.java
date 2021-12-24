package br.com.finalcraft.evernifecore.test.reflection;

import br.com.finalcraft.evernifecore.util.ReflectionUtil;
import br.com.finalcraft.evernifecore.util.reflection.ConstructorInvoker;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReflectionTest {

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

        constructorInvoker = ReflectionUtil.getConstructor(TestClass.class, int.class, Boolean.class, Float.class, double.class);
        test = constructorInvoker.invoke(1,true, 1F, 1D);

        System.out.println("Test1: ");
        System.out.println(test);

        constructorInvoker = ReflectionUtil.getConstructor(TestClass.class, Integer.class, Boolean.class, Float.class, Double.class);
        test = constructorInvoker.invoke(2,false, 2F, 2D);

        System.out.println("Test2: ");
        System.out.println(test);
    }


}
