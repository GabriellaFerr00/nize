package br.com.nize.api;

import static br.com.nize.domain.MoneyColumnConverter.MAX_CENTS;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @Positive @Max(MAX_CENTS) Long monthlyLimitCents) {

    public CreateCategoryRequest {
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
