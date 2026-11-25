package com.stockpulse.dao;

import com.stockpulse.config.DBConnection;
import com.stockpulse.model.Product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for full Product CRUD and Dashboard Analytics via MySQL JDBC.
 */
public class ProductDAO {

    /**
     * INSERT: Adds a new product to MySQL and returns the saved record with its generated ID.
     */
    public Product insertProduct(Product product) throws SQLException {
        String sql = "INSERT INTO products (sku, name, category, quantity, unit_price, supplier, description, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, product.getSku());
            ps.setString(2, product.getName());
            ps.setString(3, product.getCategory());
            ps.setInt(4, product.getQuantity());
            ps.setBigDecimal(5, product.getUnitPrice());
            ps.setString(6, product.getSupplier());
            ps.setString(7, product.getDescription() != null ? product.getDescription() : "");
            if (product.getCreatedBy() != null) {
                ps.setInt(8, product.getCreatedBy());
            } else {
                ps.setNull(8, Types.INTEGER);
            }

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting product failed, no rows affected.");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return getProductById(keys.getInt(1));
                }
            }
        }
        return null;
    }

    /**
     * SELECT: Retrieves all products matching optional search query and category filter.
     */
    public List<Product> getAllProducts(String searchQuery, String category) throws SQLException {
        List<Product> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT id, sku, name, category, quantity, unit_price, supplier, description, created_by, created_at, updated_at " +
            "FROM products WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String like = "%" + searchQuery.trim() + "%";
            sql.append(" AND (sku LIKE ? OR name LIKE ? OR supplier LIKE ? OR description LIKE ?)");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category.trim())) {
            sql.append(" AND category = ?");
            params.add(category.trim());
        }

        sql.append(" ORDER BY updated_at DESC, id DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * SELECT by ID: Fetches a single product record.
     */
    public Product getProductById(int id) throws SQLException {
        String sql = "SELECT id, sku, name, category, quantity, unit_price, supplier, description, created_by, created_at, updated_at " +
                     "FROM products WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * UPDATE: Modifies an existing product in MySQL.
     */
    public Product updateProduct(Product product) throws SQLException {
        String sql = "UPDATE products SET sku = ?, name = ?, category = ?, quantity = ?, " +
                     "unit_price = ?, supplier = ?, description = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getSku());
            ps.setString(2, product.getName());
            ps.setString(3, product.getCategory());
            ps.setInt(4, product.getQuantity());
            ps.setBigDecimal(5, product.getUnitPrice());
            ps.setString(6, product.getSupplier());
            ps.setString(7, product.getDescription() != null ? product.getDescription() : "");
            ps.setInt(8, product.getId());

            int updated = ps.executeUpdate();
            if (updated > 0) {
                return getProductById(product.getId());
            }
        }
        return null;
    }

    /**
     * DELETE: Removes a product record from MySQL by ID.
     */
    public boolean deleteProduct(int id) throws SQLException {
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Dashboard statistics using JDBC Statement and ResultSet.
     */
    public Map<String, Object> getDashboardStats() throws SQLException {
        Map<String, Object> stats = new LinkedHashMap<>();

        String summarySql =
            "SELECT " +
            "  COUNT(*) AS total_products, " +
            "  COALESCE(SUM(quantity), 0) AS total_units, " +
            "  COALESCE(SUM(quantity * unit_price), 0.00) AS total_value, " +
            "  COALESCE(SUM(CASE WHEN quantity > 0 AND quantity <= 10 THEN 1 ELSE 0 END), 0) AS low_stock_count, " +
            "  COALESCE(SUM(CASE WHEN quantity <= 0 THEN 1 ELSE 0 END), 0) AS out_of_stock_count " +
            "FROM products";

        String categorySql =
            "SELECT category, COUNT(*) AS item_count, COALESCE(SUM(quantity), 0) AS unit_count, " +
            "COALESCE(SUM(quantity * unit_price), 0.00) AS category_value " +
            "FROM products GROUP BY category ORDER BY item_count DESC, category ASC";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(summarySql)) {
                if (rs.next()) {
                    stats.put("totalProducts", rs.getInt("total_products"));
                    stats.put("totalUnits", rs.getInt("total_units"));
                    BigDecimal totalVal = rs.getBigDecimal("total_value");
                    stats.put("totalValue", totalVal != null ? totalVal.toPlainString() : "0.00");
                    stats.put("lowStockCount", rs.getInt("low_stock_count"));
                    stats.put("outOfStockCount", rs.getInt("out_of_stock_count"));
                }
            }

            List<Map<String, Object>> categories = new ArrayList<>();
            try (ResultSet rsCat = stmt.executeQuery(categorySql)) {
                while (rsCat.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", rsCat.getString("category"));
                    row.put("itemCount", rsCat.getInt("item_count"));
                    row.put("unitCount", rsCat.getInt("unit_count"));
                    BigDecimal catVal = rsCat.getBigDecimal("category_value");
                    row.put("categoryValue", catVal != null ? catVal.toPlainString() : "0.00");
                    categories.add(row);
                }
            }
            stats.put("categories", categories);
        }

        return stats;
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setSku(rs.getString("sku"));
        p.setName(rs.getString("name"));
        p.setCategory(rs.getString("category"));
        p.setQuantity(rs.getInt("quantity"));
        p.setUnitPrice(rs.getBigDecimal("unit_price"));
        p.setSupplier(rs.getString("supplier"));
        p.setDescription(rs.getString("description"));
        int createdByVal = rs.getInt("created_by");
        p.setCreatedBy(rs.wasNull() ? null : createdByVal);
        p.setCreatedAt(rs.getString("created_at"));
        p.setUpdatedAt(rs.getString("updated_at"));
        return p;
    }
}
