package br.com.nize.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable fields: rename, due day, activate/deactivate (REQ-013). */
public record UpdateCardRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Min(1) @Max(31) Integer dueDay,
        @NotNull Boolean active) {

    public UpdateCardRequest {
        name = name == null ? null : name.trim();
    }
}
