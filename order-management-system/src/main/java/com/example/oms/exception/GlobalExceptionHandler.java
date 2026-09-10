package com.example.oms.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Centralized exception handling entry point. Every top-level caller
 * (Main / a future REST controller layer) should route caught exceptions
 * through {@link #handle(Throwable)} so that diagnostic logging, error-code
 * normalization, and correlation IDs stay consistent in one place instead
 * of being duplicated at every call site.
 */
public final class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private GlobalExceptionHandler() {
    }

    /**
     * Logs the exception with a correlation ID and returns a client-safe
     * error code + message pair. Internal details (stack traces, SQL
     * fragments) are written to the diagnostic log only, never surfaced
     * to the caller.
     */
    public static HandledError handle(Throwable ex) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            if (ex instanceof ValidationException ve) {
                log.warn("Validation failure [{}]: {}", correlationId, ve.getMessage());
                return new HandledError(correlationId, ve.getErrorCode(), ve.getMessage());
            } else if (ex instanceof InsufficientInventoryException iie) {
                log.warn("Inventory failure [{}]: {}", correlationId, iie.getMessage());
                return new HandledError(correlationId, iie.getErrorCode(), iie.getMessage());
            } else if (ex instanceof DataAccessException dae) {
                log.error("Data access failure [{}]: {}", correlationId, dae.getMessage(), dae.getCause());
                return new HandledError(correlationId, dae.getErrorCode(),
                        "A database error occurred. Reference: " + correlationId);
            } else if (ex instanceof OmsException oe) {
                log.error("Application failure [{}]: {}", correlationId, oe.getMessage(), oe.getCause());
                return new HandledError(correlationId, oe.getErrorCode(), oe.getMessage());
            } else {
                log.error("Unhandled failure [{}]: {}", correlationId, ex.getMessage(), ex);
                return new HandledError(correlationId, "INTERNAL_ERROR",
                        "An unexpected error occurred. Reference: " + correlationId);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    /** Client-safe representation of a handled failure. */
    public record HandledError(String correlationId, String errorCode, String message) {
    }
}
