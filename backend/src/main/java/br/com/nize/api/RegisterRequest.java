package br.com.nize.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** BCrypt only uses the first 72 bytes, hence the password upper bound. */
public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    /** Trims name and email before validation, so " ana@x.com " is a valid email. */
    public RegisterRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim();
    }
}
