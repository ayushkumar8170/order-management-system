package com.example.oms.util;

import com.example.oms.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorTest {

    @Test
    void requireNonBlank_rejectsNullEmptyAndWhitespace() {
        assertThrows(ValidationException.class, () -> Validator.requireNonBlank(null, "name"));
        assertThrows(ValidationException.class, () -> Validator.requireNonBlank("", "name"));
        assertThrows(ValidationException.class, () -> Validator.requireNonBlank("   ", "name"));
        assertDoesNotThrow(() -> Validator.requireNonBlank("Asha", "name"));
    }

    @Test
    void requirePositive_int_rejectsZeroAndNegative() {
        assertThrows(ValidationException.class, () -> Validator.requirePositive(0, "quantity"));
        assertThrows(ValidationException.class, () -> Validator.requirePositive(-5, "quantity"));
        assertDoesNotThrow(() -> Validator.requirePositive(1, "quantity"));
    }

    @Test
    void requirePositive_bigDecimal_rejectsZeroNegativeAndNull() {
        assertThrows(ValidationException.class, () -> Validator.requirePositive((BigDecimal) null, "price"));
        assertThrows(ValidationException.class, () -> Validator.requirePositive(BigDecimal.ZERO, "price"));
        assertThrows(ValidationException.class, () -> Validator.requirePositive(new BigDecimal("-1.00"), "price"));
        assertDoesNotThrow(() -> Validator.requirePositive(new BigDecimal("0.01"), "price"));
    }

    @Test
    void requireValidEmail_acceptsWellFormedAndRejectsMalformed() {
        assertDoesNotThrow(() -> Validator.requireValidEmail("[email protected]"));
        assertThrows(ValidationException.class, () -> Validator.requireValidEmail("not-an-email"));
        assertThrows(ValidationException.class, () -> Validator.requireValidEmail("missing@domain"));
        assertThrows(ValidationException.class, () -> Validator.requireValidEmail(""));
    }

    @Test
    void inputSanitizer_clean_stripsControlCharsAndTrims() {
        String dirty = "  Hello\u0007World\u007F  ";
        assertEquals("HelloWorld", InputSanitizer.clean(dirty));
    }

    @Test
    void inputSanitizer_normalizeWhitespace_collapsesInternalSpaces() {
        assertEquals("Hello World", InputSanitizer.normalizeWhitespace("  Hello    World  "));
    }
}
