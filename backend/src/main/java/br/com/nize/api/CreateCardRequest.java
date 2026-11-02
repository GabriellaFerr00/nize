package br.com.nize.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCardRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Min(1) @Max(31) Integer dueDay) {

    public CreateCardRequest {
        name = name == null ? null : name.trim();
    }
}
