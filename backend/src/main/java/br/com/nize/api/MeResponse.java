package br.com.nize.api;

public record MeResponse(Long id, String name, String email, boolean onboardingSeen) {
}
