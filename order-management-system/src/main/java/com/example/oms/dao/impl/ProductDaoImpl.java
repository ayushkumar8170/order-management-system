package com.example.oms.dao.impl;

import com.example.oms.dao.ProductDao;
import com.example.oms.exception.DataAccessException;
import com.example.oms.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDaoImpl implements ProductDao {

    private static final Logger log = LoggerFactory.getLogger(ProductDaoImpl.class);
    private final DataSource dataSource;

    public ProductDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Product> findById(long id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById failed for product {}", id, e);
            throw new DataAccessException("Failed to load product " + id, e);
        }
    }

    @Override
    public Optional<Product> findByIdForUpdate(Connection conn, long id) {
        // SELECT ... FOR UPDATE takes an exclusive row lock for the life of
        // the caller's transaction, so two concurrent orders for the same
        // SKU serialize on this row instead of both reading stale stock.
        String sql = "SELECT * FROM products WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findByIdForUpdate failed for product {}", id, e);
            throw new DataAccessException("Failed to lock product " + id, e);
        }
    }

    @Override
    public List<Product> findAll() {
        String sql = "SELECT * FROM products ORDER BY id";
        List<Product> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(map(rs));
            }
            return results;
        } catch (SQLException e) {
            log.error("findAll products failed", e);
            throw new DataAccessException("Failed to list products", e);
        }
    }

    @Override
    public Product save(Product product) {
        String sql = """
            INSERT INTO products (sku, name, description, unit_price, quantity_on_hand, reorder_threshold)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, product.getSku());
            ps.setString(2, product.getName());
            ps.setString(3, product.getDescription());
            ps.setBigDecimal(4, product.getUnitPrice());
            ps.setInt(5, product.getQuantityOnHand());
            ps.setInt(6, product.getReorderThreshold());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    product.setId(keys.getLong(1));
                }
            }
            return product;
        } catch (SQLException e) {
            log.error("save product failed for sku {}", product.getSku(), e);
            throw new DataAccessException("Failed to save product " + product.getSku(), e);
        }
    }

    @Override
    public void adjustQuantity(Connection conn, long productId, int delta) {
        // The CHECK constraint on quantity_on_hand (>= 0) is the last line
        // of defense; the service layer must still validate availability
        // before calling this, since a CHECK violation here means the
        // in-flight transaction aborts.
        String sql = "UPDATE products SET quantity_on_hand = quantity_on_hand + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setLong(2, productId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new DataAccessException("No product row updated for id " + productId, null);
            }
        } catch (SQLException e) {
            log.error("adjustQuantity failed for product {} delta {}", productId, delta, e);
            throw new DataAccessException("Failed to adjust quantity for product " + productId, e);
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getLong("id"));
        p.setSku(rs.getString("sku"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setUnitPrice(rs.getBigDecimal("unit_price"));
        p.setQuantityOnHand(rs.getInt("quantity_on_hand"));
        p.setReorderThreshold(rs.getInt("reorder_threshold"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) p.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) p.setUpdatedAt(updated.toLocalDateTime());
        return p;
    }
}
