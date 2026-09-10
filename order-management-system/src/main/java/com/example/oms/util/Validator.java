package com.example.oms.util;

import com.example.oms.exception.ValidationException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Input-contract enforcement shared by the service layer. Every public
 * OrderService method validates its arguments through here before any
 * database work begins, so invalid state can never reach a SQL statement.
 */
public final class Validator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private Validator() {
    }

    public static void requireNonBlank(String value, String fieldName) {
        if (value == null || InputSanitizer.clean(value).isEmpty()) {
            throw new ValidationException(fieldName + " must not be blank");
        }
    }

    public static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(fieldName + " must be greater than zero (got " + value + ")");
        }
    }

    public static void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new ValidationException(fieldName + " must be greater than zero");
        }
    }

    public static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName + " must not be null");
        }
    }

    public static void requireValidEmail(String email) {
        requireNonBlank(email, "email");
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("email is not a valid address: " + email);
        }
    }
}
