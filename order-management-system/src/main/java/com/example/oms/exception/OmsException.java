package com.example.oms.exception;

/**
 * Root of the application's exception hierarchy. Carries a stable error
 * code so callers (CLI, future REST layer, logs) can branch on failure
 * type without parsing message text.
 */
public class OmsException extends RuntimeException {

    private final String errorCode;

    public OmsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public OmsException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
