package com.example.oms.dao;

import com.example.oms.model.Transaction;

import java.sql.Connection;

public interface TransactionDao {

    Transaction insert(Connection conn, Transaction transaction);
}
