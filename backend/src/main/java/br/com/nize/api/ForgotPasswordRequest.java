package br.com.nize.api;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(@NotBlank String email) {

    public ForgotPasswordRequest {
        email = email == null ? null : email.trim();
    }
}
