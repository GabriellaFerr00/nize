package br.com.nize.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixed-window attempt counter behind REQ-047 (D-16). */
class AttemptLimiterTest {

    private static final AttemptLimiter.Limit THREE_PER_15_MIN = new AttemptLimiter.Limit(3, Duration.ofMinutes(15));

    private Instant now = Instant.parse("2026-11-01T10:00:00Z");
    private final AttemptLimiter limiter = new AttemptLimiter(new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    });

    @Test
    void blocksOnceTheLimitIsReachedAndTellsWhenToRetry() {
        for (int i = 0; i < 3; i++) {
            limiter.checkAllowed("k", THREE_PER_15_MIN);
            limiter.recordAttempt("k", THREE_PER_15_MIN);
        }
        now = now.plus(Duration.ofMinutes(5));

        assertThatThrownBy(() -> limiter.checkAllowed("k", THREE_PER_15_MIN))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(600));
    }

    @Test
    void windowExpiryAllowsAgain() {
        for (int i = 0; i < 3; i++) {
            limiter.recordAttempt("k", THREE_PER_15_MIN);
        }
        now = now.plus(Duration.ofMinutes(15));

        assertThatCode(() -> limiter.checkAllowed("k", THREE_PER_15_MIN)).doesNotThrowAnyException();
    }

    @Test
    void resetClearsTheKeyAndKeysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.recordAttempt("a", THREE_PER_15_MIN);
            limiter.recordAttempt("b", THREE_PER_15_MIN);
        }
        limiter.reset("a");

        assertThatCode(() -> limiter.checkAllowed("a", THREE_PER_15_MIN)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.checkAllowed("b", THREE_PER_15_MIN))
                .isInstanceOf(TooManyAttemptsException.class);
    }
}
