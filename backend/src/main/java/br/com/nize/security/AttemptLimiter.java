package br.com.nize.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fixed-window attempt counter (REQ-047, D-16). Good for the single free-tier instance;
 * a restart clears the counters.
 */
@Component
public class AttemptLimiter {

    public record Limit(int max, Duration window) {
    }

    private record Window(Instant start, int count) {
    }

    /** Above this many keys, expired windows are purged so memory stays bounded. */
    private static final int PURGE_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AttemptLimiter() {
        this(Clock.systemUTC());
    }

    AttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Throws {@link TooManyAttemptsException} while the key has used up its window. */
    public void checkAllowed(String key, Limit limit) {
        Instant now = clock.instant();
        Window w = windows.get(key);
        if (w == null || expired(w, limit, now) || w.count() < limit.max()) {
            return;
        }
        long retryAfter = Duration.between(now, w.start().plus(limit.window())).toSeconds();
        throw new TooManyAttemptsException(Math.max(1, retryAfter));
    }

    public void recordAttempt(String key, Limit limit) {
        Instant now = clock.instant();
        windows.compute(key, (k, w) -> w == null || expired(w, limit, now)
                ? new Window(now, 1)
                : new Window(w.start(), w.count() + 1));
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet().removeIf(e -> expired(e.getValue(), limit, now));
        }
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private static boolean expired(Window w, Limit limit, Instant now) {
        return !now.isBefore(w.start().plus(limit.window()));
    }
}
