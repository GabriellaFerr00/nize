package br.com.nize.api;

import br.com.nize.service.EmailAlreadyRegisteredException;
import br.com.nize.service.InvalidCredentialsException;
import br.com.nize.service.InvalidResetTokenException;
import br.com.nize.service.UnknownYearException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable, English error codes; the front translates them (D-11). */
@RestControllerAdvice
class ApiExceptionHandler {

    record ErrorResponse(String code, Map<String, String> fields) {
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ErrorResponse> emailAlreadyRegistered() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("EMAIL_ALREADY_REGISTERED", Map.of()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_CREDENTIALS", Map.of()));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    ResponseEntity<ErrorResponse> invalidResetToken() {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_RESET_TOKEN", Map.of()));
    }

    @ExceptionHandler(UnknownYearException.class)
    ResponseEntity<ErrorResponse> unknownYear() {
        return ResponseEntity.badRequest().body(new ErrorResponse("UNKNOWN_YEAR", Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", fields));
    }
}
