package com.example.oms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {
    private Long id;
    private Long orderId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private TransactionStatus status;
    private String referenceCode;
    private LocalDateTime createdAt;

    public Transaction() {
    }

    public Transaction(Long orderId, TransactionType transactionType, BigDecimal amount,
                        TransactionStatus status, String referenceCode) {
        this.orderId = orderId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.status = status;
        this.referenceCode = referenceCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
