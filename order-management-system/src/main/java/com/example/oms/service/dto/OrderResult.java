package com.example.oms.service.dto;

import com.example.oms.model.Order;
import com.example.oms.model.Transaction;

/** Outcome of a successful order placement: the persisted order plus its ledger entry. */
public record OrderResult(Order order, Transaction transaction) {
}
