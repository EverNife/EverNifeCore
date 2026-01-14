package br.com.finalcraft.evernifecore.dependencies;

import br.com.finalcraft.evernifecore.util.FCExecutorsUtil;
import net.byteflux.libby.Library;
import net.byteflux.libby.LibraryManager;
import net.byteflux.libby.classloader.URLClassLoaderHelper;
import net.byteflux.libby.logging.adapters.JDKLogAdapter;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

public class DependencyManager extends LibraryManager {

    private final URLClassLoaderHelper classLoader;

    public DependencyManager() {
        super(new JDKLogAdapter(Logger.getLogger("DefaultDependencyManager")), new File("plugins/EverNifeCore/").toPath());
        classLoader = new URLClassLoaderHelper((URLClassLoader) DependencyManager.class.getClassLoader(), this);
    }

    public DependencyManager(Plugin plugin) {
        super(new JDKLogAdapter((Objects.requireNonNull(plugin, "plugin")).getLogger()), plugin.getDataFolder().toPath());
        classLoader = new URLClassLoaderHelper((URLClassLoader) plugin.getClass().getClassLoader(), this);
    }

    public DependencyManager(Plugin plugin, Path dataDirectory, String folderName) {
        super(new JDKLogAdapter((Objects.requireNonNull(plugin, "plugin")).getLogger()), dataDirectory, folderName);
        classLoader = new URLClassLoaderHelper((URLClassLoader) plugin.getClass().getClassLoader(), this);
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

        final ExecutorService executor = FCExecutorsUtil.createVirtualExecutorIfPossible(
                "dependencymanager"
        );

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
