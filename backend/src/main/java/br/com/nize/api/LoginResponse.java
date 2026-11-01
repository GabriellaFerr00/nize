package br.com.nize.api;

public record LoginResponse(String token, long expiresInSeconds) {
}
