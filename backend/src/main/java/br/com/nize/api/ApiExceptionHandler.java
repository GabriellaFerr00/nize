package br.com.nize.api;

import br.com.nize.service.CardNameTakenException;
import br.com.nize.service.CategoryInUseException;
import br.com.nize.service.CategoryNameTakenException;
import br.com.nize.service.EmailAlreadyRegisteredException;
import br.com.nize.service.InvalidCredentialsException;
import br.com.nize.service.InvalidYearException;
import br.com.nize.service.InvalidResetTokenException;
import br.com.nize.service.PersonNameTakenException;
import br.com.nize.service.ResourceNotFoundException;
import br.com.nize.service.UnknownYearException;
import br.com.nize.service.YearAlreadyExistsException;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", fields));
    }
}
