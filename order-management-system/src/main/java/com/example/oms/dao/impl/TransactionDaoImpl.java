package com.example.oms.dao.impl;

import com.example.oms.dao.TransactionDao;
import com.example.oms.exception.DataAccessException;
import com.example.oms.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TransactionDaoImpl implements TransactionDao {

    private static final Logger log = LoggerFactory.getLogger(TransactionDaoImpl.class);

    @Override
    public Transaction insert(Connection conn, Transaction transaction) {
        String sql = """
            INSERT INTO transactions (order_id, transaction_type, amount, status, reference_code)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, transaction.getOrderId());
            ps.setString(2, transaction.getTransactionType().name());
            ps.setBigDecimal(3, transaction.getAmount());
            ps.setString(4, transaction.getStatus().name());
            ps.setString(5, transaction.getReferenceCode());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    transaction.setId(keys.getLong(1));
                }
            }
            return transaction;
        } catch (SQLException e) {
            log.error("insert transaction failed for order {}", transaction.getOrderId(), e);
            throw new DataAccessException("Failed to record transaction for order " + transaction.getOrderId(), e);
        }
    }
}
