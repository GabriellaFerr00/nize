package br.com.nize.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable fields: rename, activate/deactivate (REQ-014). */
public record UpdatePersonRequest(@NotBlank @Size(max = 80) String name, @NotNull Boolean active) {

    public UpdatePersonRequest {
        name = name == null ? null : name.trim();
    }
}
