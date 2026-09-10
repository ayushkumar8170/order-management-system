package com.example.oms.dao.impl;

import com.example.oms.dao.CustomerDao;
import com.example.oms.exception.DataAccessException;
import com.example.oms.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDaoImpl implements CustomerDao {

    private static final Logger log = LoggerFactory.getLogger(CustomerDaoImpl.class);
    private final DataSource dataSource;

    public CustomerDaoImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Customer> findById(long id) {
        String sql = "SELECT * FROM customers WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById failed for customer {}", id, e);
            throw new DataAccessException("Failed to load customer " + id, e);
        }
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        String sql = "SELECT * FROM customers WHERE email = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findByEmail failed for {}", email, e);
            throw new DataAccessException("Failed to load customer by email", e);
        }
    }

    @Override
    public List<Customer> findAll() {
        String sql = "SELECT * FROM customers ORDER BY id";
        List<Customer> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(map(rs));
            }
            return results;
        } catch (SQLException e) {
            log.error("findAll customers failed", e);
            throw new DataAccessException("Failed to list customers", e);
        }
    }

    @Override
    public Customer save(Customer customer) {
        String sql = "INSERT INTO customers (full_name, email, phone) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, customer.getFullName());
            ps.setString(2, customer.getEmail());
            ps.setString(3, customer.getPhone());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    customer.setId(keys.getLong(1));
                }
            }
            return customer;
        } catch (SQLIntegrityConstraintViolationException e) {
            log.warn("Duplicate customer email attempted: {}", customer.getEmail());
            throw new DataAccessException("Email already registered: " + customer.getEmail(), e);
        } catch (SQLException e) {
            log.error("save customer failed for {}", customer.getEmail(), e);
            throw new DataAccessException("Failed to save customer " + customer.getEmail(), e);
        }
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getLong("id"));
        c.setFullName(rs.getString("full_name"));
        c.setEmail(rs.getString("email"));
        c.setPhone(rs.getString("phone"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) c.setCreatedAt(created.toLocalDateTime());
        return c;
    }
}
