package br.com.nize.api;

import br.com.nize.domain.CardBrand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCardRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull @Min(1) @Max(31) Integer dueDay,
        @Pattern(regexp = CardBrand.PATTERN) String brand) {

    public CreateCardRequest {
        name = name == null ? null : name.trim();
    }
}
