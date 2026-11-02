package br.com.nize.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable fields: rename, describe, activate/deactivate (REQ-011). */
public record UpdateCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 255) String description,
        @NotNull Boolean active) {

    public UpdateCategoryRequest {
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
