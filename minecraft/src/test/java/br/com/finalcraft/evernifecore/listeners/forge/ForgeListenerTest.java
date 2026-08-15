package br.com.finalcraft.evernifecore.listeners.forge;

import br.com.finalcraft.evernifecore.listeners.forge.imp.ArclightForgeListener;
import br.com.finalcraft.evernifecore.listeners.forge.imp.CrucibleForgeListener;
import br.com.finalcraft.evernifecore.listeners.forge.imp.ModernMohistForgeListener;
import br.com.finalcraft.evernifecore.listeners.forge.imp.MohistForgeListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a server with no Forge on it gets, which is every server this suite runs on: no adapter, no
 * class-initialization failure anywhere, and a refusal that names what is missing whenever something
 * asks for the route anyway.
 *
 * <p>An adapter that resolved its Forge members while its class initialized would turn the very first
 * of these calls into an {@code ExceptionInInitializerError} and every call after it into a
 * {@code NoClassDefFoundError} - neither of which any caller can be expected to handle. Each
 * assertion below names an exception type on purpose: an {@link Error} fails it.</p>
 */
class ForgeListenerTest {

    @Test
    void withNoHybridBehindItThereIsNoAdapterAndAskingIsFree() {
        assertFalse(ForgeListener.isAvailable(),
                "no hybrid platform is on this classpath, so the detection finds nothing");
    }

    @Test
    void askingForARouteThatDoesNotExistIsAnIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> ForgeListener.registerListener(null, null),
                "the caller gets told there is no Forge side, not a linkage failure");
        assertThrows(IllegalStateException.class, () -> ForgeListener.registerListener(null, null, new Object()),
                "and the same for the overload that carries its own buses");
    }

    @Test
    void anAdapterForAnAbsentPlatformStillLoadsAndOnlyRefusesWhenItIsUsed() {
        IForgeListener arclight = assertDoesNotThrow(ArclightForgeListener::new,
                "building the adapter must not reach for a Forge type");
        IForgeListener crucible = assertDoesNotThrow(CrucibleForgeListener::new, "idem");
        IForgeListener modernMohist = assertDoesNotThrow(ModernMohistForgeListener::new, "idem");

        assertMissingTypeIsNamed(arclight, "io.izzel.arclight.api.Arclight");
        assertMissingTypeIsNamed(crucible, "io.github.crucible.api.CrucibleEventBus");
        assertMissingTypeIsNamed(modernMohist, "com.mohistmc.forge.MohistEventBus");
    }

    @Test
    void theMohistAdapterRefusesToBeBuiltAtAllAndSaysWhy() {
        //It is the one adapter whose constructor does the work: it wires a Bukkit bridge, so there is
        //nothing left to defer to a later call.
        IllegalStateException refusal = assertThrows(IllegalStateException.class, MohistForgeListener::new);

        assertTrue(refusal.getMessage().contains("SubscribeEvent"),
                "and it names the annotation it could not find: " + refusal.getMessage());
    }

    private static void assertMissingTypeIsNamed(IForgeListener adapter, String expectedType) {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> adapter.registerListener(null, null));

        assertTrue(refusal.getMessage().contains(expectedType),
                "the refusal has to name the type this server does not have: " + refusal.getMessage());
    }

}
