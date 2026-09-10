package com.example.oms.service;

import com.example.oms.dao.CustomerDao;
import com.example.oms.dao.OrderDao;
import com.example.oms.dao.ProductDao;
import com.example.oms.dao.TransactionDao;
import com.example.oms.exception.InsufficientInventoryException;
import com.example.oms.exception.OrderProcessingException;
import com.example.oms.exception.ValidationException;
import com.example.oms.model.*;
import com.example.oms.service.dto.OrderLineRequest;
import com.example.oms.service.dto.OrderResult;
import com.example.oms.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates order placement across products, orders, order_items, and
 * transactions as a single ACID unit.
 *
 * <h2>Concurrency / consistency contract</h2>
 * All writes for one order happen on one JDBC {@link Connection} with
 * auto-commit disabled and isolation raised to REPEATABLE_READ. Inventory
 * rows are locked with {@code SELECT ... FOR UPDATE} (see
 * {@link ProductDao#findByIdForUpdate}) before being decremented, so two
 * concurrent orders against the same SKU serialize instead of racing on a
 * stale read. Any failure — validation, insufficient stock, or a SQL
 * error — rolls back every write made so far for that order; nothing is
 * left half-applied.
 */
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final DataSource dataSource;
    private final ProductDao productDao;
    private final CustomerDao customerDao;
    private final OrderDao orderDao;
    private final TransactionDao transactionDao;

    public OrderService(DataSource dataSource, ProductDao productDao, CustomerDao customerDao,
                         OrderDao orderDao, TransactionDao transactionDao) {
        this.dataSource = dataSource;
        this.productDao = productDao;
        this.customerDao = customerDao;
        this.orderDao = orderDao;
        this.transactionDao = transactionDao;
    }

    /**
     * Places an order for a customer against one or more product lines.
     *
     * Contract:
     *  - preconditions: customerId refers to an existing customer; lines is
     *    non-empty; every line has quantity > 0 and a productId that exists
     *    with enough quantity_on_hand.
     *  - postconditions on success: one new orders row (status CONFIRMED),
     *    one order_items row per line, inventory decremented by exactly the
     *    ordered quantities, and one SUCCESS transactions row whose amount
     *    equals the order total.
     *  - postconditions on failure: no rows from this call are visible to
     *    any other transaction — the whole operation rolls back atomically.
     */
    public OrderResult placeOrder(long customerId, List<OrderLineRequest> lines) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        try {
            validateRequest(customerId, lines);

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                try {
                    OrderResult result = doPlaceOrder(conn, customerId, lines);
                    conn.commit();
                    log.info("Order {} confirmed for customer {} (total={})",
                            result.order().getId(), customerId, result.order().getTotalAmount());
                    return result;
                } catch (RuntimeException e) {
                    safeRollback(conn);
                    throw e;
                }
            } catch (SQLException e) {
                throw new OrderProcessingException("Database connection failure while placing order", e);
            }
        } finally {
            MDC.remove("correlationId");
        }
    }

    private OrderResult doPlaceOrder(Connection conn, long customerId, List<OrderLineRequest> lines) {
        customerDao.findById(customerId)
                .orElseThrow(() -> new ValidationException("No such customer: " + customerId));

        Order order = new Order(customerId);
        order = orderDao.insertOrder(conn, order);

        for (OrderLineRequest line : lines) {
            Product product = productDao.findByIdForUpdate(conn, line.productId())
                    .orElseThrow(() -> new ValidationException("No such product: " + line.productId()));

            if (product.getQuantityOnHand() < line.quantity()) {
                throw new InsufficientInventoryException(
                        line.productId(), line.quantity(), product.getQuantityOnHand());
            }

            productDao.adjustQuantity(conn, line.productId(), -line.quantity());

            OrderItem item = new OrderItem(line.productId(), line.quantity(), product.getUnitPrice());
            orderDao.insertOrderItem(conn, order.getId(), item);
            order.addItem(item);
        }

        orderDao.updateStatus(conn, order.getId(), OrderStatus.CONFIRMED);
        order.setStatus(OrderStatus.CONFIRMED);

        Transaction transaction = new Transaction(
                order.getId(),
                TransactionType.DEBIT,
                order.getTotalAmount(),
                TransactionStatus.SUCCESS,
                "TXN-" + UUID.randomUUID()
        );
        transaction = transactionDao.insert(conn, transaction);

        return new OrderResult(order, transaction);
    }

    private void validateRequest(long customerId, List<OrderLineRequest> lines) {
        Validator.requirePositive((int) customerId, "customerId");
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("An order must contain at least one line item");
        }
        for (OrderLineRequest line : lines) {
            Validator.requireNonNull(line, "orderLine");
            Validator.requirePositive(line.quantity(), "quantity");
            Validator.requirePositive((int) line.productId(), "productId");
        }
    }

    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException rollbackEx) {
            // Rollback failure is logged but never masks the original
            // exception — the caller must still see why the order failed.
            log.error("Rollback failed after order placement error", rollbackEx);
        }
    }

    /** Convenience read-only accessor used by the CLI demo and tests. */
    public BigDecimal quoteTotal(List<OrderLineRequest> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLineRequest line : lines) {
            Product product = productDao.findById(line.productId())
                    .orElseThrow(() -> new ValidationException("No such product: " + line.productId()));
            total = total.add(product.getUnitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return total;
    }
}
