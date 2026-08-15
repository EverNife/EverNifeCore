package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Compile-time stand-in for Forge's entry point. Only {@link #EVENT_BUS} is ever read, and pulling
 * the whole Forge distribution in through ForgeGradle just to reach one field is not worth the build
 * it would force this module onto.
 */
public class MinecraftForge {

    public static IEventBus EVENT_BUS;

}
