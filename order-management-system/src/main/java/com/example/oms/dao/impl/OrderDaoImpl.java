package com.example.oms.dao.impl;

import com.example.oms.dao.OrderDao;
import com.example.oms.exception.DataAccessException;
import com.example.oms.model.Order;
import com.example.oms.model.OrderItem;
import com.example.oms.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

public class OrderDaoImpl implements OrderDao {

    private static final Logger log = LoggerFactory.getLogger(OrderDaoImpl.class);
    private final DataSource dataSource;

    public OrderDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Order insertOrder(Connection conn, Order order) {
        String sql = "INSERT INTO orders (customer_id, status, total_amount) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, order.getCustomerId());
            ps.setString(2, order.getStatus().name());
            ps.setBigDecimal(3, order.getTotalAmount());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    order.setId(keys.getLong(1));
                }
            }
            return order;
        } catch (SQLException e) {
            log.error("insertOrder failed for customer {}", order.getCustomerId(), e);
            throw new DataAccessException("Failed to insert order", e);
        }
    }

    @Override
    public OrderItem insertOrderItem(Connection conn, long orderId, OrderItem item) {
        String sql = """
            INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, orderId);
            ps.setLong(2, item.getProductId());
            ps.setInt(3, item.getQuantity());
            ps.setBigDecimal(4, item.getUnitPrice());
            ps.setBigDecimal(5, item.getLineTotal());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setId(keys.getLong(1));
                }
            }
            item.setOrderId(orderId);
            return item;
        } catch (SQLException e) {
            log.error("insertOrderItem failed for order {} product {}", orderId, item.getProductId(), e);
            throw new DataAccessException("Failed to insert order item", e);
        }
    }

    @Override
    public void updateStatus(Connection conn, long orderId, OrderStatus status) {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateStatus failed for order {}", orderId, e);
            throw new DataAccessException("Failed to update order status", e);
        }
    }

    @Override
    public Optional<Order> findById(long id) {
        String orderSql = "SELECT * FROM orders WHERE id = ?";
        String itemsSql = "SELECT * FROM order_items WHERE order_id = ?";
        try (Connection conn = dataSource.getConnection()) {
            Order order;
            try (PreparedStatement ps = conn.prepareStatement(orderSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    order = mapOrder(rs);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        order.getItems().add(mapItem(rs));
                    }
                }
            }
            return Optional.of(order);
        } catch (SQLException e) {
            log.error("findById failed for order {}", id, e);
            throw new DataAccessException("Failed to load order " + id, e);
        }
    }

    private Order mapOrder(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getLong("id"));
        o.setCustomerId(rs.getLong("customer_id"));
        o.setStatus(OrderStatus.valueOf(rs.getString("status")));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) o.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) o.setUpdatedAt(updated.toLocalDateTime());
        return o;
    }

    private OrderItem mapItem(ResultSet rs) throws SQLException {
        OrderItem item = new OrderItem();
        item.setId(rs.getLong("id"));
        item.setOrderId(rs.getLong("order_id"));
        item.setProductId(rs.getLong("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setUnitPrice(rs.getBigDecimal("unit_price"));
        item.setLineTotal(rs.getBigDecimal("line_total"));
        return item;
    }
}
