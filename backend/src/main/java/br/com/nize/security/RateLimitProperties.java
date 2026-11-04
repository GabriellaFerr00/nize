package br.com.nize.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Attempt limits for /api/auth/** (REQ-047). Defaults live in application.yml. */
@ConfigurationProperties(prefix = "nize.rate-limit")
public record RateLimitProperties(int loginPerEmail, int loginPerIp, Duration loginWindow,
                                  int registerPerIp, int forgotPerEmail, int forgotPerIp, int resetPerIp,
                                  Duration hourWindow) {

    public AttemptLimiter.Limit loginEmail() {
        return new AttemptLimiter.Limit(loginPerEmail, loginWindow);
    }

    public AttemptLimiter.Limit loginIp() {
        return new AttemptLimiter.Limit(loginPerIp, loginWindow);
    }

    public AttemptLimiter.Limit registerIp() {
        return new AttemptLimiter.Limit(registerPerIp, hourWindow);
    }

    public AttemptLimiter.Limit forgotEmail() {
        return new AttemptLimiter.Limit(forgotPerEmail, hourWindow);
    }

    public AttemptLimiter.Limit forgotIp() {
        return new AttemptLimiter.Limit(forgotPerIp, hourWindow);
    }

    public AttemptLimiter.Limit resetIp() {
        return new AttemptLimiter.Limit(resetPerIp, hourWindow);
    }
}
