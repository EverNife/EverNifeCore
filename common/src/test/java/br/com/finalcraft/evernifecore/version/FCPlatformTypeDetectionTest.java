package br.com.finalcraft.evernifecore.version;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the classpath probe that tells Bukkit from Hytale apart. A real Bukkit server was shipping
 * as Hytale because the probe asked for {@code org/bukkit/Bukkit} without the {@code .class}
 * suffix - a path no jar has an entry for - so the fallback branch always won.
 */
class FCPlatformTypeDetectionTest {

    @Test
    void detectsMinecraftWhenTheLoaderServesTheBukkitClassEntry() {
        ClassLoader serverLike = loaderServing(FCPlatformType.BUKKIT_MARKER);

        assertEquals(FCPlatformType.MINECRAFT, FCPlatformType.detectPlatform(serverLike));
    }

    @Test
    void detectsHytaleWhenTheLoaderHasNoBukkitAtAll() {
        ClassLoader bukkitLess = loaderServing();

        assertEquals(FCPlatformType.HYTALE, FCPlatformType.detectPlatform(bukkitLess));
    }

    /**
     * A suffix-less probe is exactly what the bug did. The marker has to name a real jar entry,
     * so a loader that only serves the package directory must NOT read as Bukkit.
     */
    @Test
    void theMarkerNamesAClassEntryAndNotThePackageDirectory() {
        ClassLoader packageDirOnly = loaderServing("org/bukkit/", "org/bukkit/Bukkit");

        assertEquals(FCPlatformType.HYTALE, FCPlatformType.detectPlatform(packageDirOnly));
        assertEquals("org/bukkit/Bukkit.class", FCPlatformType.BUKKIT_MARKER);
    }

    /** A loader that resolves exactly the given resource names and nothing else. */
    private static ClassLoader loaderServing(String... resourceNames) {
        Set<String> served = new HashSet<>();
        Collections.addAll(served, resourceNames);

        return new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                if (!served.contains(name)) {
                    return null;
                }
                try {
                    //only the presence of a URL matters to the probe; nothing ever opens it
                    return new URL("file:/test-classpath/" + name);
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}
