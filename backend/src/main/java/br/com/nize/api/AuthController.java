package br.com.nize.api;

import br.com.nize.domain.AppUser;
import br.com.nize.security.TokenService;
import br.com.nize.service.AuthService;
import br.com.nize.service.PasswordResetService;
import br.com.nize.service.RegistrationService;
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

    public AuthController(RegistrationService registration, AuthService auth,
                          PasswordResetService passwordReset) {
        this.registration = registration;
        this.auth = auth;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = registration.register(request.name(), request.email(), request.password());
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        TokenService.IssuedToken token = auth.login(request.email(), request.password());
        return new LoginResponse(token.value(), token.expiresInSeconds());
    }

    /** Always 202, whether or not the email exists. */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordReset.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.resetPassword(request.token(), request.newPassword());
    }
}
