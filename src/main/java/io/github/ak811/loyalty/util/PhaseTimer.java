package io.github.ak811.loyalty.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records wall-clock durations of named phases using {@link System#nanoTime()}.
 *
 * <pre>{@code
 * LoadedHolders holders = timer.time("Load cardholders", source::loadHolders);
 * }</pre>
 */
public final class PhaseTimer {

    /** A unit of work that returns a value and may throw a checked exception. */
    @FunctionalInterface
    public interface Task<T, E extends Exception> {
        T run() throws E;
    }

    /** A completed phase and its duration in nanoseconds. */
    public record Entry(String name, long nanos) {
        public double seconds() {
            return nanos / 1e9;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Runs {@code task}, records its duration under {@code name}, and returns its result. */
    public <T, E extends Exception> T time(String name, Task<T, E> task) throws E {
        long start = System.nanoTime();
        try {
            return task.run();
        } finally {
            entries.add(new Entry(name, System.nanoTime() - start));
        }
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public long totalNanos() {
        long total = 0;
        for (Entry entry : entries) {
            total += entry.nanos();
        }
        return total;
    }
}
