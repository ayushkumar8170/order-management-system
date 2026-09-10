package com.example.oms.exception;

/** Thrown when an order cannot be completed for reasons outside validation/inventory (e.g. failed commit). */
public class OrderProcessingException extends OmsException {
    public OrderProcessingException(String message, Throwable cause) {
        super("ORDER_PROCESSING_ERROR", message, cause);
    }

    public OrderProcessingException(String message) {
        super("ORDER_PROCESSING_ERROR", message);
    }
}
