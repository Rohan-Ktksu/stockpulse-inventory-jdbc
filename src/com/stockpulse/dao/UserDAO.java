package com.stockpulse.dao;

import com.stockpulse.config.DBConnection;
import com.stockpulse.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Data Access Object for User registration and login using JDBC PreparedStatement and ResultSet.
 */
public class UserDAO {

    /**
     * Checks if a username or email is already registered.
     */
    public boolean existsByUsernameOrEmail(String username, String email) throws SQLException {
        String sql = "SELECT id FROM users WHERE username = ? OR email = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Inserts a new user into MySQL and populates the generated ID.
     */
    public User registerUser(User user) throws SQLException {
        String sql = "INSERT INTO users (full_name, username, email, password_hash, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getRole() != null ? user.getRole() : "STAFF");

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
            return findById(user.getId());
        }
    }

    /**
     * Authenticates a user by username or email and SHA-256 password hash.
     */
    public User authenticate(String identifier, String passwordHash) throws SQLException {
        String sql = "SELECT id, full_name, username, email, password_hash, role, created_at " +
                     "FROM users WHERE (username = ? OR email = ?) AND password_hash = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, passwordHash);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Finds a user by primary key ID.
     */
    public User findById(int id) throws SQLException {
        String sql = "SELECT id, full_name, username, email, password_hash, role, created_at FROM users WHERE id = ?";
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

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setFullName(rs.getString("full_name"));
        u.setUsername(rs.getString("username"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }
}
