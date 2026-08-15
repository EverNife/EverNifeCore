package net.minecraftforge.common;

/**
 * A test-only target for a lookup that goes by name.
 *
 * <p>The Forge adapters read {@code MinecraftForge.EVENT_BUS} through reflection, so proving that the
 * read works needs a class under that exact name on the test classpath - there is no other way to be
 * found by {@code Class.forName}. This is not a compile-time stand-in and nothing compiles against
 * it: it lives in the test source set, no production class names it, and it ships in no artifact.</p>
 *
 * <p>The field is an {@link Object} on purpose. A real hybrid declares it as its era's bus type
 * ({@code cpw.mods.fml.common.eventhandler.EventBus} on 1.7.10, {@code IEventBus} from 1.16.5 on),
 * and a test that could only hold one of those would be testing the very coupling the adapters
 * dropped.</p>
 */
public class MinecraftForge {

    public static Object EVENT_BUS;

}
