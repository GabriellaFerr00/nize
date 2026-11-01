package br.com.nize.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {

    public ResetPasswordRequest {
        token = token == null ? null : token.trim();
    }
}
