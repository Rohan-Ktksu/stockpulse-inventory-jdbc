-- StockPulse Inventory Management System - MySQL Schema
CREATE DATABASE IF NOT EXISTS stockpulse_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE stockpulse_db;

-- Users table for Registration and Login
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'STAFF',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Products table for full CRUD operations
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(60) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    unit_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    supplier VARCHAR(100) NOT NULL,
    description VARCHAR(255) DEFAULT '',
    created_by INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_products_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

-- Optional sample products (inserted only if table is empty)
INSERT IGNORE INTO products (sku, name, category, quantity, unit_price, supplier, description) VALUES
('ELEC-101', 'Wireless Ergonomic Mouse', 'Electronics', 45, 29.99, 'LogiTech Supply Co.', '2.4GHz silent optical mouse with USB-C receiver'),
('ELEC-102', 'Mechanical Keyboard 75%', 'Electronics', 8, 89.50, 'KeyCraft Distributors', 'Hot-swappable tactile switches with aluminum frame'),
('OFF-201', 'A4 Copy Paper (500 Sheets)', 'Office Supplies', 120, 6.25, 'PaperMill Direct', '80gsm bright white multipurpose printing paper'),
('FURN-301', 'Adjustable Mesh Office Chair', 'Furniture', 4, 215.00, 'ErgoSeat Industries', 'Lumbar support with breathable mesh backrest'),
('NET-401', 'USB-C Gigabit Ethernet Adapter', 'Networking', 0, 19.95, 'NetLink Hardware', 'Plug-and-play RJ45 aluminum adapter');
