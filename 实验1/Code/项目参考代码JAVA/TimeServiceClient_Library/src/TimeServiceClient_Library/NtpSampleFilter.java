package TimeServiceClient_Library;

import java.util.*;

/** One instance per server; timeMs must come from a monotonic clock. */
public final class NtpSampleFilter {
    // Use a regular class so the reference Java 8 project can compile this module.
    private static final class Sample {
        private final long offset, delay, timeMs;
        Sample(long offset, long delay, long timeMs) {
            this.offset = offset; this.delay = delay; this.timeMs = timeMs;
        }
        long offset() { return offset; }
        long delay() { return delay; }
        long timeMs() { return timeMs; }
    }
    private final Deque<Sample> window = new ArrayDeque<>();
    private static final int WINDOW = 8;
    private static final long MAX_AGE_MS = 60_000;

    public OptionalLong add(long offsetMs, long delayMs, long timeMs) {
        while (!window.isEmpty() && timeMs - window.peekFirst().timeMs() > MAX_AGE_MS)
            window.removeFirst();
        if (delayMs < 0) return OptionalLong.empty();
        window.addLast(new Sample(offsetMs, delayMs, timeMs));
        while (window.size() > WINDOW) window.removeFirst();
        if (window.size() < 3) return OptionalLong.empty();
        List<Sample> ordered = new ArrayList<>(window);
        ordered.sort(Comparator.comparingLong(Sample::delay)
            .thenComparing(Comparator.comparingLong(Sample::timeMs).reversed()));
        long[] best = { ordered.get(0).offset(), ordered.get(1).offset(),
                        ordered.get(2).offset() };
        Arrays.sort(best);
        return OptionalLong.of(best[1]);
    }

    public void reset() { window.clear(); }
}
