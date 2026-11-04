package br.com.nize.api;

import br.com.nize.domain.AppUser;
import br.com.nize.security.AttemptLimiter;
import br.com.nize.security.RateLimitProperties;
import br.com.nize.security.TokenService;
import br.com.nize.service.AuthService;
import br.com.nize.service.InvalidCredentialsException;
import br.com.nize.service.InvalidResetTokenException;
import br.com.nize.service.PasswordResetService;
import br.com.nize.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registration;
    private final AuthService auth;
    private final PasswordResetService passwordReset;
    private final AttemptLimiter limiter;
    private final RateLimitProperties limits;

    public AuthController(RegistrationService registration, AuthService auth,
                          PasswordResetService passwordReset, AttemptLimiter limiter, RateLimitProperties limits) {
        this.registration = registration;
        this.auth = auth;
        this.passwordReset = passwordReset;
        this.limiter = limiter;
        this.limits = limits;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        String ipKey = "register:ip:" + http.getRemoteAddr();
        limiter.checkAllowed(ipKey, limits.registerIp());
        limiter.recordAttempt(ipKey, limits.registerIp());
        AppUser user = registration.register(request.name(), request.email(), request.password());
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Counted for unknown e-mails too, so the limit reveals nothing about which accounts exist.
        String emailKey = "login:email:" + RegistrationService.normalizeEmail(request.email());
        String ipKey = "login:ip:" + http.getRemoteAddr();
        limiter.checkAllowed(emailKey, limits.loginEmail());
        limiter.checkAllowed(ipKey, limits.loginIp());
        try {
            TokenService.IssuedToken token = auth.login(request.email(), request.password());
            limiter.reset(emailKey);
            return new LoginResponse(token.value(), token.expiresInSeconds());
        } catch (InvalidCredentialsException e) {
            limiter.recordAttempt(emailKey, limits.loginEmail());
            limiter.recordAttempt(ipKey, limits.loginIp());
            throw e;
        }
    }

    /** Always 202, whether or not the email exists. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        String emailKey = "forgot:email:" + RegistrationService.normalizeEmail(request.email());
        String ipKey = "forgot:ip:" + http.getRemoteAddr();
        limiter.checkAllowed(emailKey, limits.forgotEmail());
        limiter.checkAllowed(ipKey, limits.forgotIp());
        limiter.recordAttempt(emailKey, limits.forgotEmail());
        limiter.recordAttempt(ipKey, limits.forgotIp());
        passwordReset.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        String ipKey = "reset:ip:" + http.getRemoteAddr();
        limiter.checkAllowed(ipKey, limits.resetIp());
        try {
            passwordReset.resetPassword(request.token(), request.newPassword());
        } catch (InvalidResetTokenException e) {
            limiter.recordAttempt(ipKey, limits.resetIp());
            throw e;
        }
    }
}
