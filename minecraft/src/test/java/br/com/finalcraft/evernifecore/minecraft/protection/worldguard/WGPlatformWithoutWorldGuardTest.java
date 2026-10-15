package br.com.finalcraft.evernifecore.minecraft.protection.worldguard;

import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A server without WorldGuard: both classes load, the platform says it is absent, and getInstance() names the check. */
class WGPlatformWithoutWorldGuardTest {

    @TempDirNobodyCleans
    Path tempDir;

    @Test
    void loadingWGPlatformAndWGFlagsWithoutWorldGuardDoesNotThrow() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            ClassLoader loader = getClass().getClassLoader();
            assertDoesNotThrow(() -> Class.forName(WGPlatform.class.getName(), true, loader));
            assertDoesNotThrow(() -> Class.forName(WGFlags.class.getName(), true, loader));

            assertFalse(WGPlatform.isPresent());
            assertNull(WGFlags.BUILD, "no WorldGuard, no flag to mirror");
        }
    }

    @Test
    void getInstanceWithoutWorldGuardTeachesIsPresent() {
        try (BukkitEventWorld world = BukkitEventWorld.install(tempDir)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class, WGPlatform::getInstance);
            assertTrue(refused.getMessage().contains("isPresent()"), "the message names the check to make first: " + refused.getMessage());
        }
    }
}
