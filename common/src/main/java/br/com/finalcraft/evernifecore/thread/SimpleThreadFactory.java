package br.com.finalcraft.evernifecore.thread;

import jakarta.annotation.Nonnull;
import lombok.Data;
import jakarta.annotation.Nonnull;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class SimpleThreadFactory implements ThreadFactory {

    private final @Nonnull String identifier;
    private final ThreadFactory threadFactory;

    private final AtomicInteger counter = new AtomicInteger(0);

    public SimpleThreadFactory(@Nonnull String identifier) {
        this.identifier = identifier;
        this.threadFactory = Executors.defaultThreadFactory();
    }

    public SimpleThreadFactory(@Nonnull String identifier, ThreadFactory threadFactory) {
        this.identifier = identifier;
        this.threadFactory = threadFactory;
    }

    @Override
    public Thread newThread(@Nonnull Runnable r) {
        Thread thread = threadFactory.newThread(r);
        if (identifier != null) {
            thread.setName(String.format(Locale.ROOT, identifier, counter.getAndIncrement()));
        }
        thread.setDaemon(true);
        return thread;
    }

}
