package br.com.nize.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePersonRequest(@NotBlank @Size(max = 80) String name) {

    public CreatePersonRequest {
        name = name == null ? null : name.trim();
    }
}
