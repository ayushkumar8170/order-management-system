-- Sample seed data for local development / demo runs
USE oms_db;

INSERT INTO products (sku, name, description, unit_price, quantity_on_hand, reorder_threshold) VALUES
  ('SKU-1001', 'Wireless Mouse',        'Ergonomic 2.4GHz wireless mouse',       19.99, 150, 20),
  ('SKU-1002', 'Mechanical Keyboard',   'Tactile-switch mechanical keyboard',    79.99,  60, 15),
  ('SKU-1003', '27in Monitor',          '27" 1440p IPS monitor',                249.99,  25,  5),
  ('SKU-1004', 'USB-C Hub',             '7-in-1 USB-C hub',                      34.50, 200, 30),
  ('SKU-1005', 'Laptop Stand',          'Aluminum adjustable laptop stand',      45.00,  80, 10)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO customers (full_name, email, phone) VALUES
  ('Asha Rao',      '[email protected]',    '+91-98200-11111'),
  ('Daniel Kim',    '[email protected]',   '+1-206-555-0110'),
  ('Marta Silva',   '[email protected]',    '+55-11-95555-2222')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);
