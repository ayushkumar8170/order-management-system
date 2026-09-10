package com.example.oms.dao;

import com.example.oms.model.Order;
import com.example.oms.model.OrderItem;

import java.sql.Connection;
import java.util.Optional;

/**
 * All write methods take the caller's Connection so order-header,
 * order-item, and inventory writes commit atomically as one unit.
 */
public interface OrderDao {

    Order insertOrder(Connection conn, Order order);

    OrderItem insertOrderItem(Connection conn, long orderId, OrderItem item);

    void updateStatus(Connection conn, long orderId, com.example.oms.model.OrderStatus status);

    Optional<Order> findById(long id);
}
