package br.com.nize.api;

import jakarta.validation.constraints.NotNull;

public record PaidRequest(@NotNull Boolean paid) {
}
