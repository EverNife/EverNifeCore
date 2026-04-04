package br.com.finalcraft.evernifecore.dependencies;

import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.classloader.URLClassLoaderHelper;
import net.byteflux.libby.logging.adapters.JDKLogAdapter;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class DependencyManager extends LibraryManager {

    private final URLClassLoaderHelper classLoader;

    public DependencyManager(String pluginName, File pluginRootFolder, String libsFolderName) {
        this(pluginName, pluginRootFolder, libsFolderName, DependencyManager.class.getClassLoader());
    }

    public DependencyManager(String pluginName, File pluginRootFolder, String libsFolderName, ClassLoader classLoader) {
        super(
                new JDKLogAdapter(Logger.getLogger("DependencyManager_" + pluginName)),
                pluginRootFolder.toPath(),
                libsFolderName
        );
        this.classLoader = new URLClassLoaderHelper((URLClassLoader) classLoader, this);
    }

    /**
     * Adds a file to the Bukkit plugin's classpath.
     *
     * @param file the file to add
     */
    @Override
    protected void addToClasspath(Path file) {
        this.classLoader.addToClasspath(file);
    }

    /**
     * Loads a set os libraries jar into the plugin's classpath. If the
     * library jar doesn't exist locally, it will be downloaded.
     * <p>
     * If the provided library has any relocations, they will be applied to
     * create a relocated jar and the relocated jar will be loaded instead.
     *
     * @param libraries the set of libraries to load
     * @see #downloadLibrary(Library)
     */
    public void loadLibrary(Collection<Library> libraries) {

        if (libraries.size() == 0){
            return;
        }

        CountDownLatch latch = new CountDownLatch(libraries.size());

        final ExecutorService executor = Executors.newFixedThreadPool(3);

        for (Library library : libraries) {
            executor.execute(() -> {
                try {
                    this.loadLibrary(library);
                } catch (Throwable e) {
                    logger.error("Unable to load dependency " + library.toString() + ".", e);
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

        executor.shutdown();
    }

}
