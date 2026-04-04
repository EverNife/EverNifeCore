package br.com.finalcraft.evernifecore.util;

import br.com.finalcraft.evernifecore.util.commons.MinMax;
import br.com.finalcraft.evernifecore.version.FCJavaVersion;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FCExecutorsUtil {

    public static MinMax<Integer> getMinMaxThreadCountBoundedToSystemCoreCount(int max) {
        return getMinMaxThreadCountBoundedToSystemCoreCount(1, max);
    }

    public static MinMax<Integer> getMinMaxThreadCountBoundedToSystemCoreCount(int min, int max) {
        int coreCount = Runtime.getRuntime().availableProcessors();
        min = Math.max(min, 1); // Ensure min is at least 1
        max = Math.max(max, min); // Ensure max is at least min
        max = Math.min(max, coreCount); // Ensure max does not exceed core count
        min = Math.min(min, max); // Ensure min does not exceed max
        return MinMax.of(min, max);
    }

    public static ExecutorService createVirtualExecutorIfPossible(String identifierOfNotPossible) {
        if (FCJavaVersion.isHigherEquals(FCJavaVersion.JAVA_21)) {
            // Virtual threads for actual execution, on Java 21+ we can use Virtual Threads
            return (ExecutorService) FCReflectionUtil.getMethod(Executors.class, "newVirtualThreadPerTaskExecutor")
                    .invoke(null);
        } else {
            // Fallback to a fixed thread pool on lower Java versions, this is not ideal but better than nothing
            Integer maxThreads = FCExecutorsUtil.getMinMaxThreadCountBoundedToSystemCoreCount(Runtime.getRuntime().availableProcessors())
                    .getMax();

            ThreadFactory threadFactory = Executors.defaultThreadFactory();
            return new ThreadPoolExecutor(
                    1,
                    maxThreads,
                    3, TimeUnit.SECONDS, // Keep alive time for idle threads above core pool size
                    new LinkedBlockingQueue<>(),
                    new ThreadFactory() {
                        private final AtomicInteger counter = new AtomicInteger(0);

                        @Override
                        public Thread newThread(Runnable r) {
                            Thread thread = threadFactory.newThread(r);
                            thread.setName(identifierOfNotPossible + "-evernifecore-executor-" + counter.getAndIncrement());
                            thread.setDaemon(true);
                            return thread;
                        }
                    }
            );
        }
    }


}
