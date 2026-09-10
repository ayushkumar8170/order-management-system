package com.example.oms.service;

import com.example.oms.dao.CustomerDao;
import com.example.oms.dao.OrderDao;
import com.example.oms.dao.ProductDao;
import com.example.oms.dao.TransactionDao;
import com.example.oms.exception.InsufficientInventoryException;
import com.example.oms.exception.ValidationException;
import com.example.oms.model.*;
import com.example.oms.service.dto.OrderLineRequest;
import com.example.oms.service.dto.OrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Functional contract tests: verify OrderService honors its documented
 * pre/postconditions (see OrderService.placeOrder javadoc) rather than
 * testing implementation details. DAOs are mocked so these run without a
 * live database; integration coverage against real MySQL is exercised via
 * `docker-compose up` + Main as described in the README.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock DataSource dataSource;
    @Mock Connection connection;
    @Mock ProductDao productDao;
    @Mock CustomerDao customerDao;
    @Mock OrderDao orderDao;
    @Mock TransactionDao transactionDao;

    OrderService orderService;

    @BeforeEach
    void setUp() throws Exception {
        orderService = new OrderService(dataSource, productDao, customerDao, orderDao, transactionDao);
    }

    private void givenOpenConnection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void placeOrder_happyPath_decrementsInventoryAndReturnsSuccessfulTransaction() throws Exception {
        givenOpenConnection();

        Customer customer = new Customer(1L, "Asha Rao", "[email protected]", null);
        when(customerDao.findById(1L)).thenReturn(Optional.of(customer));

        Product product = new Product(10L, "SKU-1", "Widget", "desc", new BigDecimal("9.99"), 50, 5);
        when(productDao.findByIdForUpdate(eq(connection), eq(10L))).thenReturn(Optional.of(product));

        Order insertedOrder = new Order(1L);
        insertedOrder.setId(500L);
        when(orderDao.insertOrder(eq(connection), any(Order.class))).thenReturn(insertedOrder);

        when(orderDao.insertOrderItem(eq(connection), eq(500L), any(OrderItem.class)))
                .thenAnswer(inv -> inv.getArgument(2));

        when(transactionDao.insert(eq(connection), any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction t = inv.getArgument(1);
                    t.setId(900L);
                    return t;
                });

        OrderResult result = orderService.placeOrder(1L, List.of(new OrderLineRequest(10L, 3)));

        // Postcondition: inventory decremented by exactly the ordered quantity.
        verify(productDao).adjustQuantity(connection, 10L, -3);
        // Postcondition: order confirmed and total = unitPrice * quantity.
        assertEquals(OrderStatus.CONFIRMED, result.order().getStatus());
        assertEquals(new BigDecimal("29.97"), result.order().getTotalAmount());
        // Postcondition: a SUCCESS transaction was recorded for the same amount.
        assertEquals(TransactionStatus.SUCCESS, result.transaction().getStatus());
        assertEquals(result.order().getTotalAmount(), result.transaction().getAmount());
        // Contract: the transaction must be committed, never rolled back, on success.
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void placeOrder_insufficientInventory_rollsBackAndThrowsWithoutRecordingTransaction() throws Exception {
        givenOpenConnection();

        when(customerDao.findById(1L)).thenReturn(Optional.of(new Customer(1L, "A", "[email protected]", null)));

        Product product = new Product(10L, "SKU-1", "Widget", "desc", new BigDecimal("9.99"), 2, 5);
        when(productDao.findByIdForUpdate(eq(connection), eq(10L))).thenReturn(Optional.of(product));

        Order insertedOrder = new Order(1L);
        insertedOrder.setId(501L);
        when(orderDao.insertOrder(eq(connection), any(Order.class))).thenReturn(insertedOrder);

        assertThrows(InsufficientInventoryException.class, () ->
                orderService.placeOrder(1L, List.of(new OrderLineRequest(10L, 5))));

        // Contract: on failure, inventory is never adjusted and no ledger entry is written.
        verify(productDao, never()).adjustQuantity(any(), anyLong(), anyInt());
        verify(transactionDao, never()).insert(any(), any());
        // Contract: the whole unit of work rolls back atomically.
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void placeOrder_unknownCustomer_isRejectedBeforeAnyWrite() {
        // No DataSource interaction should be needed: validation of the
        // basic request shape happens before a connection is even opened
        // for line-level checks that require the DB.
        when(customerDao.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () ->
                orderService.placeOrder(999L, List.of(new OrderLineRequest(10L, 1))));
    }

    @Test
    void placeOrder_emptyLineList_rejectedByInputContract() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                orderService.placeOrder(1L, List.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("line item"));
    }

    @Test
    void placeOrder_nonPositiveQuantity_rejectedByInputContract() {
        assertThrows(ValidationException.class, () ->
                orderService.placeOrder(1L, List.of(new OrderLineRequest(10L, 0))));
    }

    @Test
    void quoteTotal_sumsAcrossMultipleLines() {
        Product widget = new Product(1L, "SKU-1", "Widget", "", new BigDecimal("10.00"), 100, 5);
        Product gadget = new Product(2L, "SKU-2", "Gadget", "", new BigDecimal("2.50"), 100, 5);
        when(productDao.findById(1L)).thenReturn(Optional.of(widget));
        when(productDao.findById(2L)).thenReturn(Optional.of(gadget));

        BigDecimal total = orderService.quoteTotal(List.of(
                new OrderLineRequest(1L, 2),   // 20.00
                new OrderLineRequest(2L, 4)    // 10.00
        ));

        assertEquals(new BigDecimal("30.00"), total);
    }
}
