package com.example.oms.dao;

import com.example.oms.model.Product;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for products/inventory.
 *
 * The connection-accepting overloads (findByIdForUpdate, adjustQuantity)
 * exist so the service layer can compose multiple DAO calls inside a
 * single caller-managed transaction — see OrderService.placeOrder.
 */
public interface ProductDao {

    Optional<Product> findById(long id);

    /** Locks the row (SELECT ... FOR UPDATE) within the caller's transaction. */
    Optional<Product> findByIdForUpdate(Connection conn, long id);

    List<Product> findAll();

    Product save(Product product);

    /** Applies a signed delta to quantity_on_hand within the caller's transaction. */
    void adjustQuantity(Connection conn, long productId, int delta);
}
