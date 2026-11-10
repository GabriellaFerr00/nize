package br.com.nize.api;

import static br.com.nize.domain.MoneyColumnConverter.MAX_CENTS;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable fields: rename, describe, activate/deactivate (REQ-011). */
public record UpdateCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @NotNull Boolean active,
        @Positive @Max(MAX_CENTS) Long monthlyLimitCents) {

    public UpdateCategoryRequest {
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
