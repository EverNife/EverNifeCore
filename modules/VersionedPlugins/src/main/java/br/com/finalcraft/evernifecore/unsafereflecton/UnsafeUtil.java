package br.com.finalcraft.evernifecore.unsafereflecton;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeUtil {

    static Field theUnsafe;
    static Unsafe UNSAFE;
    static {
        try {
            theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null); // Cache this
        }catch (Throwable e){

        }
    }

    // As noted here https://github.com/GTNewHorizons/lwjgl3ify?tab=readme-ov-file#javabasejavalangreflect-classes-are-protected-from-reflective-access
    // this method 'MIGHT' work until java 2025
    public static void setField(Field data, Object object, Object value) {
        if (object == null) {
            long offset = UNSAFE.staticFieldOffset(data);
            Object base = UNSAFE.staticFieldBase(data);
            UNSAFE.putObject(base, offset, value);
        } else {
            long offset = UNSAFE.objectFieldOffset(data);
            UNSAFE.putObject(object, offset, value);
        }
    }
}
