package br.com.nize.api;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;

/**
 * No user id here on purpose: the owner is always the token's user. {@code closingsEnabled} and {@code closingDays}
 * are optional (null keeps what is saved); the days go from 1 to 31, without repeats (REQ-048).
 */
public record SettingsRequest(
        @NotNull @Min(2000) @Max(2100) Integer currentYear,
        @NotNull Boolean autoInstallments,
        Boolean closingsEnabled,
        @ClosingDays List<Integer> closingDays) {

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = ClosingDaysValidator.class)
    @interface ClosingDays {
        String message() default "days from 1 to 31, without repeats";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    static class ClosingDaysValidator implements ConstraintValidator<ClosingDays, List<Integer>> {
        @Override
        public boolean isValid(List<Integer> days, ConstraintValidatorContext context) {
            if (days == null) {
                return true;
            }
            HashSet<Integer> seen = new HashSet<>();
            for (Integer day : days) {
                if (day == null || day < 1 || day > 31 || !seen.add(day)) {
                    return false;
                }
            }
            return true;
        }
    }
}
