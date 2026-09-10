package com.example.oms.exception;

/** Thrown when caller-supplied input fails sanitization or business validation. */
public class ValidationException extends OmsException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
