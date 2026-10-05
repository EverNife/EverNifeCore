package net.neoforged.neoforge.common;

/**
 * A test-only target for a lookup that goes by name: the home of NeoForge's main event bus, read
 * through reflection the same way {@code MinecraftForge.EVENT_BUS} is. Nothing compiles against it and
 * it ships in no artifact.
 */
public class NeoForge {

    public static Object EVENT_BUS;

}
