package br.com.nize.api;

import br.com.nize.security.TooManyAttemptsException;
import br.com.nize.service.CardNameTakenException;
import br.com.nize.service.CategoryInUseException;
import br.com.nize.service.CategoryNameTakenException;
import br.com.nize.service.EmailAlreadyRegisteredException;
import br.com.nize.service.InvalidCredentialsException;
import br.com.nize.service.InvalidExpenseException;
import br.com.nize.service.InvalidYearException;
import br.com.nize.service.InvalidResetTokenException;
import br.com.nize.service.PersonNameTakenException;
import br.com.nize.service.ResourceNotFoundException;
import br.com.nize.service.UnknownYearException;
import br.com.nize.service.YearAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable, English error codes; the front translates them (D-11). */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    record ErrorResponse(String code, Map<String, String> fields) {
    }

    /** Conflict that also tells the user how many records block the action. */
    record InUseResponse(String code, Map<String, String> fields, long count) {
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

    @ExceptionHandler(TooManyAttemptsException.class)
    ResponseEntity<ErrorResponse> tooManyAttempts(TooManyAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(new ErrorResponse("TOO_MANY_ATTEMPTS", Map.of()));
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    ResponseEntity<ErrorResponse> invalidResetToken() {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_RESET_TOKEN", Map.of()));
    }

    @ExceptionHandler(UnknownYearException.class)
    ResponseEntity<ErrorResponse> unknownYear() {
        return ResponseEntity.badRequest().body(new ErrorResponse("UNKNOWN_YEAR", Map.of()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", Map.of()));
    }

    @ExceptionHandler(CategoryNameTakenException.class)
    ResponseEntity<ErrorResponse> categoryNameTaken() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("CATEGORY_NAME_TAKEN", Map.of()));
    }

    @ExceptionHandler(CardNameTakenException.class)
    ResponseEntity<ErrorResponse> cardNameTaken() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("CARD_NAME_TAKEN", Map.of()));
    }

    @ExceptionHandler(PersonNameTakenException.class)
    ResponseEntity<ErrorResponse> personNameTaken() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("PERSON_NAME_TAKEN", Map.of()));
    }

    @ExceptionHandler(CategoryInUseException.class)
    ResponseEntity<InUseResponse> categoryInUse(CategoryInUseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new InUseResponse("CATEGORY_IN_USE", Map.of(), e.getExpenseCount()));
    }

    @ExceptionHandler(YearAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> yearAlreadyExists() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("YEAR_ALREADY_EXISTS", Map.of()));
    }

    @ExceptionHandler(InvalidYearException.class)
    ResponseEntity<ErrorResponse> invalidYear() {
        return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_YEAR", Map.of()));
    }

    @ExceptionHandler(InvalidExpenseException.class)
    ResponseEntity<ErrorResponse> invalidExpense(InvalidExpenseException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getCode(), Map.of()));
    }

    /** Broken JSON, wrong types (e.g. 10.5 for cents) or unknown enum values. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable() {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", fields));
    }

    /** A path or query value of the wrong type, e.g. {@code ?active=x' OR '1'='1} or {@code /years/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> typeMismatch() {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", Map.of()));
    }

    /**
     * Anything else: framework errors keep their status (404, 405, 415...) and unexpected ones become
     * 500. The body never carries messages, class names or SQL; the cause only goes to the server log.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> anyOther(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse framework) {
            HttpStatusCode status = framework.getStatusCode();
            return ResponseEntity.status(status).body(new ErrorResponse(codeFor(status), Map.of()));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("INTERNAL_ERROR", Map.of()));
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_ERROR";
        };
    }
}
