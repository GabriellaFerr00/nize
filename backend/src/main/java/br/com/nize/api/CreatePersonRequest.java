package br.com.nize.api;

import br.com.nize.domain.TagColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePersonRequest(@NotBlank @Size(max = 80) String name, @Pattern(regexp = TagColor.PATTERN) String color) {

    public CreatePersonRequest {
        name = name == null ? null : name.trim();
    }
}
