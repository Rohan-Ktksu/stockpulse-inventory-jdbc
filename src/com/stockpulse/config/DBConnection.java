package com.stockpulse.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Manages MySQL JDBC connections using DriverManager, Connection, Statement, and ResultSet.
 */
public class DBConnection {

    private static final String PROPERTIES_PATH = "config/db.properties";
    private static final Properties PROPS = new Properties();

    static {
        loadProperties();
        loadDriver();
    }

    private static void loadProperties() {
        try (InputStream input = new FileInputStream(PROPERTIES_PATH)) {
            PROPS.load(input);
        } catch (IOException e) {
            System.err.println("[DBConnection] Could not read " + PROPERTIES_PATH + ", using defaults: " + e.getMessage());
        }
    }

    private static void loadDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("============================================================");
            System.err.println("ERROR: MySQL JDBC Driver (com.mysql.cj.jdbc.Driver) not found!");
            System.err.println("Download mysql-connector-j-*.jar and place it inside the lib/ folder.");
            System.err.println("============================================================");
        }
    }

    public static String getUrl() {
        String env = System.getenv("STOCKPULSE_DB_URL");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String host = System.getenv("DB_HOST");
        String dbName = System.getenv("DB_NAME");
        if (host != null && !host.trim().isEmpty()) {
            String port = System.getenv("DB_PORT");
            String resolvedPort = (port != null && !port.trim().isEmpty()) ? port.trim() : "3306";
            String resolvedDb = (dbName != null && !dbName.trim().isEmpty()) ? dbName.trim() : getDbName();
            String sslMode = System.getenv("DB_SSL_MODE");
            String sslParam = (sslMode != null && !sslMode.trim().isEmpty()) ? "sslMode=" + sslMode.trim() : "useSSL=false&allowPublicKeyRetrieval=true";
            return "jdbc:mysql://" + host.trim() + ":" + resolvedPort + "/" + resolvedDb + "?" + sslParam + "&serverTimezone=UTC";
        }
        return PROPS.getProperty(
            "db.url",
            "jdbc:mysql://localhost:3306/stockpulse_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        );
    }

    public static String getBaseUrl() {
        String env = System.getenv("STOCKPULSE_DB_BASE_URL");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        return PROPS.getProperty(
            "db.baseUrl",
            "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        );
    }

    public static String getDbName() {
        String env = System.getenv("DB_NAME");
        String raw = (env != null && !env.trim().isEmpty()) ? env.trim() : PROPS.getProperty("db.name", "stockpulse_db");
        return raw.replaceAll("[^a-zA-Z0-9_]", "");
    }

    public static String getUser() {
        String env = System.getenv("STOCKPULSE_DB_USER");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        String dbUser = System.getenv("DB_USER");
        if (dbUser != null && !dbUser.trim().isEmpty()) {
            return dbUser.trim();
        }
        return PROPS.getProperty("db.user", "root");
    }

    public static String getPassword() {
        String env = System.getenv("STOCKPULSE_DB_PASSWORD");
        if (env != null) {
            return env;
        }
        String dbPass = System.getenv("DB_PASSWORD");
        if (dbPass != null) {
            return dbPass;
        }
        return PROPS.getProperty("db.password", "root");
    }

    public static int getServerPort() {
        String renderPort = System.getenv("PORT");
        if (renderPort != null && !renderPort.trim().isEmpty()) {
            try {
                return Integer.parseInt(renderPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String portStr = PROPS.getProperty("server.port", "8080");
        try {
            return Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    /**
     * Opens and returns a JDBC Connection to the configured MySQL database.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getUrl(), getUser(), getPassword());
    }

    /**
     * Creates the database, tables, and initial seed rows if they do not exist yet.
     * Demonstrates JDBC DriverManager, Connection, Statement, and ResultSet.
     */
    public static void initializeDatabase() throws SQLException {
        String dbName = getDbName();

        // Step 1: When connecting locally (no DB_HOST/STOCKPULSE_DB_URL override), ensure database exists
        String customUrl = System.getenv("STOCKPULSE_DB_URL");
        String customHost = System.getenv("DB_HOST");
        if ((customUrl == null || customUrl.trim().isEmpty()) && (customHost == null || customHost.trim().isEmpty())) {
            try (Connection baseConn = DriverManager.getConnection(getBaseUrl(), getUser(), getPassword());
                 Statement stmt = baseConn.createStatement()) {
                stmt.executeUpdate(
                    "CREATE DATABASE IF NOT EXISTS `" + dbName + "` " +
                    "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                );
            } catch (SQLException ignored) {
                // Managed MySQL instances may disallow CREATE DATABASE and pre-create the schema
            }
        }

        // Step 2: Connect to the target database and create tables if needed
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "full_name VARCHAR(100) NOT NULL, " +
                "username VARCHAR(50) NOT NULL UNIQUE, " +
                "email VARCHAR(120) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(64) NOT NULL, " +
                "role VARCHAR(30) NOT NULL DEFAULT 'STAFF', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS products (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "sku VARCHAR(40) NOT NULL UNIQUE, " +
                "name VARCHAR(120) NOT NULL, " +
                "category VARCHAR(60) NOT NULL, " +
                "quantity INT NOT NULL DEFAULT 0, " +
                "unit_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00, " +
                "supplier VARCHAR(100) NOT NULL, " +
                "description VARCHAR(255) DEFAULT '', " +
                "created_by INT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "CONSTRAINT fk_products_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL" +
                ")"
            );

            // Seed sample products only when the products table is completely empty
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM products")) {
                if (rs.next() && rs.getInt("total") == 0) {
                    stmt.executeUpdate(
                        "INSERT INTO products (sku, name, category, quantity, unit_price, supplier, description) VALUES " +
                        "('ELEC-101', 'Wireless Ergonomic Mouse', 'Electronics', 45, 29.99, 'LogiTech Supply Co.', '2.4GHz silent optical mouse with USB-C receiver'), " +
                        "('ELEC-102', 'Mechanical Keyboard 75%', 'Electronics', 8, 89.50, 'KeyCraft Distributors', 'Hot-swappable tactile switches with aluminum frame'), " +
                        "('OFF-201', 'A4 Copy Paper (500 Sheets)', 'Office Supplies', 120, 6.25, 'PaperMill Direct', '80gsm bright white multipurpose printing paper'), " +
                        "('FURN-301', 'Adjustable Mesh Office Chair', 'Furniture', 4, 215.00, 'ErgoSeat Industries', 'Lumbar support with breathable mesh backrest'), " +
                        "('NET-401', 'USB-C Gigabit Ethernet Adapter', 'Networking', 0, 19.95, 'NetLink Hardware', 'Plug-and-play RJ45 aluminum adapter')"
                    );
                }
            }
        }
    }

    /**
     * Checks live database connectivity with a simple Statement query.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next() && rs.getInt(1) == 1;
        } catch (SQLException e) {
            return false;
        }
    }
}
