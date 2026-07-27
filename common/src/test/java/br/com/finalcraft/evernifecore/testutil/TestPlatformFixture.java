package br.com.finalcraft.evernifecore.testutil;

import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;

import java.util.ArrayList;
import java.util.List;

/**
 * The last caller-facing remnant of the old fixture, kept for the three classes that assert on
 * {@link #shutdownRequests()}: {@code PlayerControllerStorageBootTest}, {@code StorageBootGuardTest}
 * and {@code RefReloadSurvivalTest}. Their assertions name this class, and rewriting an assertion
 * to migrate it is exactly the move this suite forbids.
 *
 * <p>It owns no platform of its own anymore - it installs the same {@link Platforms#lenient()}
 * double everything else uses and reads the shutdown reasons back off it.</p>
 */
public final class TestPlatformFixture {

    private static ECoreTestWorld world;

    private TestPlatformFixture() {
    }

    /** Installs the lenient platform once; later calls do nothing. */
    public static synchronized void ensureInstalled() {
        if (world == null) {
            world = Platforms.lenient().install();
        }
    }

    /**
     * Installs a fresh lenient platform even when another one is already registered - a test that
     * observes {@link #shutdownRequests()} has to be reading its own double, not an inherited one.
     */
    public static synchronized void forceInstallNoop() {
        if (world != null) {
            world.close();
        }
        world = Platforms.lenient().install();
    }

    /** Every reason passed to {@code IPlatform.shutdown} since the last {@link #clearShutdownRequests()}. */
    public static synchronized List<String> shutdownRequests() {
        return world == null ? new ArrayList<String>() : new ArrayList<String>(world.platform().getShutdownReasons());
    }

    public static synchronized void clearShutdownRequests() {
        if (world != null) {
            world.platform().reset();
        }
    }
}
