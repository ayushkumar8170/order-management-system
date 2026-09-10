-- ============================================================================
-- Relational Order Management & Transaction Processing System
-- Schema definition (MySQL 8.0+)
-- InnoDB is required on every table for FK integrity and row-level locking.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS oms_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE oms_db;

-- ----------------------------------------------------------------------------
-- products: source of truth for real-time inventory
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    sku                 VARCHAR(64)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    unit_price          DECIMAL(10,2)   NOT NULL,
    quantity_on_hand    INT             NOT NULL DEFAULT 0,
    reorder_threshold   INT             NOT NULL DEFAULT 10,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT chk_products_price_nonneg CHECK (unit_price >= 0),
    CONSTRAINT chk_products_qty_nonneg CHECK (quantity_on_hand >= 0)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- customers
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name   VARCHAR(255)    NOT NULL,
    email       VARCHAR(255)    NOT NULL,
    phone       VARCHAR(32),
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_customers_email UNIQUE (email)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- orders
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    customer_id     BIGINT UNSIGNED NOT NULL,
    status          ENUM('PENDING','CONFIRMED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'PENDING',
    total_amount    DECIMAL(12,2)   NOT NULL DEFAULT 0.00,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES customers(id) ON DELETE RESTRICT,
    INDEX idx_orders_customer_id (customer_id),
    INDEX idx_orders_status (status)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- order_items: line items, price snapshotted at time of purchase
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_items (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT UNSIGNED NOT NULL,
    product_id  BIGINT UNSIGNED NOT NULL,
    quantity    INT             NOT NULL,
    unit_price  DECIMAL(10,2)   NOT NULL,
    line_total  DECIMAL(12,2)   NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_items_qty_pos CHECK (quantity > 0),
    INDEX idx_order_items_order_id (order_id),
    INDEX idx_order_items_product_id (product_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- transactions: financial ledger tied 1:many to an order
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id            BIGINT UNSIGNED NOT NULL,
    transaction_type    ENUM('DEBIT','CREDIT','REFUND') NOT NULL,
    amount              DECIMAL(12,2)   NOT NULL,
    status              ENUM('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    reference_code      VARCHAR(64)     NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_order FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT uq_transactions_reference UNIQUE (reference_code),
    INDEX idx_transactions_order_id (order_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- inventory_audit_log: append-only trail of every stock adjustment
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory_audit_log (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT UNSIGNED NOT NULL,
    change_qty  INT             NOT NULL,
    reason      VARCHAR(255)    NOT NULL,
    order_id    BIGINT UNSIGNED,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_product FOREIGN KEY (product_id)
        REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_audit_product_id (product_id)
) ENGINE=InnoDB;
