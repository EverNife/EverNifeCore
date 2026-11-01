package br.com.finalcraft.evernifecore.playerdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Defines one test class on its own and, once {@link #close() closed}, refuses every class lookup with the
 * exception Bukkit's plugin class loader throws after the plugin's jar was closed.
 */
final class ClosingClassLoader extends ClassLoader {

    private final String definedHere;
    private volatile boolean closed;

    ClosingClassLoader(Class<?> definedHere) {
        super(definedHere.getClassLoader());
        this.definedHere = definedHere.getName();
    }

    /** The copy of the class this loader defines - a different {@code Class} from the one it was read from. */
    @SuppressWarnings("unchecked")
    <T> Class<? extends T> definedClass() throws ClassNotFoundException {
        return (Class<? extends T>) loadClass(definedHere);
    }

    void close() {
        closed = true;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (closed) throw new IllegalStateException("zip file closed");
        if (!name.equals(definedHere)) return super.loadClass(name, resolve);
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) return loaded;
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int read; (read = in.read(buffer)) > 0; ) bytes.write(buffer, 0, read);
                return defineClass(name, bytes.toByteArray(), 0, bytes.size());
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
