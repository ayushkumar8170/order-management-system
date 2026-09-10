package com.example.oms.exception;

/** Thrown when an order line requests more stock than is currently available. */
public class InsufficientInventoryException extends OmsException {

    private final Long productId;
    private final int requested;
    private final int available;

    public InsufficientInventoryException(Long productId, int requested, int available) {
        super("INSUFFICIENT_INVENTORY",
                "Product %d: requested %d but only %d available".formatted(productId, requested, available));
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public Long getProductId() { return productId; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
