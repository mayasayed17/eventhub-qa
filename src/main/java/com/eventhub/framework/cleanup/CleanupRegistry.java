package com.eventhub.framework.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-test list of things to undo. API clients register what they create; the JUnit extension runs
 * everything after the test, pass or fail, newest first. Tests don't write cleanup code.
 */
public final class CleanupRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(CleanupRegistry.class);

    private record Entry(String key, Runnable action) {
    }

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void register(String key, Runnable action) {
        if (entries.stream().noneMatch(e -> e.key().equals(key))) {
            entries.push(new Entry(key, action));
        }
    }

    /** Call when the test itself removed the resource (e.g. a cancel test), so cleanup doesn't retry it. */
    public void unregister(String key) {
        entries.removeIf(e -> e.key().equals(key));
    }

    public boolean isTracked(String key) {
        return entries.stream().anyMatch(e -> e.key().equals(key));
    }

    /** Runs every action; one failing cleanup does not stop the others. Returns the number that failed. */
    public int runAll() {
        int failures = 0;
        Entry entry;
        while ((entry = entries.poll()) != null) {
            try {
                entry.action().run();
                LOG.debug("Cleaned up {}", entry.key());
            } catch (RuntimeException e) {
                failures++;
                LOG.warn("Cleanup of {} failed: {}", entry.key(), e.getMessage());
            }
        }
        return failures;
    }
}
