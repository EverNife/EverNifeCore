package br.com.finalcraft.evernifecore.dependencies.util;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.dependencies.DependencyManager;
import br.com.finalcraft.evernifecore.util.FCScheduller;
import com.google.common.collect.ImmutableSet;
import net.byteflux.libby.Library;
import net.byteflux.libby.classloader.IsolatedClassLoader;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class IsolatedCLUtil {

    private static final Map<ImmutableSet<Library>, IsolatedClassLoader> loaders = new HashMap<>();

    public static IsolatedClassLoader obtainClassLoaderWith(Collection<Library> libraries) {
        return obtainClassLoaderWith(libraries, EverNifeCore.instance.getDependencyManager());
    }

    public static IsolatedClassLoader obtainClassLoaderWith(Collection<Library> libraries, DependencyManager dependencyManager) {
        ImmutableSet<Library> set = ImmutableSet.copyOf(libraries);

        IsolatedClassLoader existingClassLoader = loaders.get(set);
        if (existingClassLoader != null) {
            return existingClassLoader;
        }

        IsolatedClassLoader classLoader = new IsolatedClassLoader();

        CountDownLatch latch = new CountDownLatch(set.size());

        for (Library library : set) {
            FCScheduller.runAssync(() -> {
                try {
                    Path path = dependencyManager.downloadLibrary(library);
                    classLoader.addPath(path);
                } catch (Throwable e) {
                    EverNifeCore.warning("Unable to load dependency " + library.toString() + ".");
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        loaders.put(set, classLoader);
        return classLoader;
    }

}
