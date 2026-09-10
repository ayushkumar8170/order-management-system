package com.example.oms.service.dto;

/** A single requested line item: buy `quantity` units of `productId`. */
public record OrderLineRequest(long productId, int quantity) {
}
