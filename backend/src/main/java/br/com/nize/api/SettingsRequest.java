package br.com.nize.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** No user id here on purpose: the owner is always the token's user. */
public record SettingsRequest(
        @NotNull @Min(2000) @Max(2100) Integer currentYear,
        @NotNull Boolean autoInstallments) {
}
