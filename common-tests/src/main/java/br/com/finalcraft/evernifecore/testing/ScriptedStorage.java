package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.everydatabase.EntityDescriptor;
import br.com.finalcraft.everydatabase.HealthStatus;
import br.com.finalcraft.everydatabase.Repository;
import br.com.finalcraft.everydatabase.Storage;
import br.com.finalcraft.everydatabase.SyncParticipation;
import br.com.finalcraft.everydatabase.log.StorageLogConfig;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Storage} that answers exactly like the one it wraps, while counting what its repositories were
 * asked to do and letting a test script a write failure.
 *
 * <p>Two things no real backend gives a test. <b>Counting</b> is how "the chunk was saved, never deleted and
 * re-created" or "the peek read no backend" become assertions instead of hopes - the facade's return value
 * looks the same either way. <b>A scripted failure</b> is the only way to reach a write-back store's retry
 * path: an outage cannot be arranged on an in-memory backend, and without one the "left dirty, persisted by
 * the next flush" contract is untested.
 *
 * <pre>{@code
 * ScriptedStorage backend = ScriptedStorage.wrapping(Storages.createInMemory());
 * backend.init().join();
 * ...
 * backend.failNextSaveAll(new IllegalStateException("backend down"));
 * assertTrue(store.flush().join().hasFailures());
 * assertEquals(0, backend.callsTo("delete"));
 * }</pre>
 *
 * <p>The repositories it hands out are dynamic proxies, so a method added to {@link Repository} keeps
 * working (and counting) without touching this class.
 */
public final class ScriptedStorage implements Storage {

    private final Storage delegate;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<String, AtomicInteger>();
    private final AtomicReference<Throwable> nextSaveAllFailure = new AtomicReference<Throwable>();

    private ScriptedStorage(Storage delegate) {
        this.delegate = delegate;
    }

    /** Wraps a real backend - {@code init()} still has to be called (on this, or on the delegate). */
    public static ScriptedStorage wrapping(Storage delegate) {
        return new ScriptedStorage(delegate);
    }

    /**
     * Makes the next batched write fail with {@code cause} and every one after it succeed - a backend that
     * blinked. Nothing else is affected: the failure is produced here, so the delegate never sees the call
     * and its data stays exactly as it was.
     */
    public ScriptedStorage failNextSaveAll(Throwable cause) {
        nextSaveAllFailure.set(cause);
        return this;
    }

    /** How many times the repositories of this storage were asked for {@code method} (e.g. {@code "find"}). */
    public int callsTo(String method) {
        AtomicInteger counter = calls.get(method);
        return counter != null ? counter.get() : 0;
    }

    /** Forgets every count, so an assertion can talk about what happened after this point only. */
    public ScriptedStorage resetCalls() {
        calls.clear();
        return this;
    }

    // -----------------------------------------------------------------------------------------------------
    //  Storage
    // -----------------------------------------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> init() {
        return delegate.init();
    }

    @Override
    public CompletableFuture<Void> close() {
        return delegate.close();
    }

    @Override
    public CompletableFuture<HealthStatus> health() {
        return delegate.health();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Repository<K, V> repository(EntityDescriptor<K, V> descriptor) {
        Repository<K, V> real = delegate.repository(descriptor);
        return (Repository<K, V>) Proxy.newProxyInstance(Repository.class.getClassLoader(),
                new Class<?>[]{Repository.class}, new CountingCalls(real));
    }

    @Override
    public StorageLogConfig getStorageLogConfig() {
        return delegate.getStorageLogConfig();
    }

    @Override
    public Storage setStorageLogConfig(StorageLogConfig config) {
        delegate.setStorageLogConfig(config);
        return this;
    }

    //the four capability answers are the backend's, not this wrapper's: reporting the defaults would make
    //a test run against different write semantics than the backend it named
    @Override
    public boolean enforcesOptimisticLock() {
        return delegate.enforcesOptimisticLock();
    }

    @Override
    public String backendIdentity() {
        return delegate.backendIdentity();
    }

    @Override
    public SyncParticipation syncParticipation() {
        return delegate.syncParticipation();
    }

    @Override
    public boolean isMachineLocalIdentity() {
        return delegate.isMachineLocalIdentity();
    }

    /** Counts every repository call and answers the scripted failure before the delegate is reached. */
    private final class CountingCalls implements InvocationHandler {

        private final Repository<?, ?> real;

        CountingCalls(Repository<?, ?> real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            AtomicInteger counter = calls.get(name);
            if (counter == null) {
                counter = new AtomicInteger();
                AtomicInteger raced = calls.putIfAbsent(name, counter);
                counter = raced != null ? raced : counter;
            }
            counter.incrementAndGet();

            if ("saveAll".equals(name)) {
                Throwable scripted = nextSaveAllFailure.getAndSet(null);
                if (scripted != null) {
                    CompletableFuture<Object> failed = new CompletableFuture<Object>();
                    failed.completeExceptionally(scripted);
                    return failed;
                }
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException thrownByTheBackend) {
                throw thrownByTheBackend.getCause();
            }
        }
    }
}
