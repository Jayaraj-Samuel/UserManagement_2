package com.usermgmt.dao.impl;

import com.usermgmt.dao.DBConnectionFactory;
import com.usermgmt.dao.UserDAO;
import com.usermgmt.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * JDBC Implementation of UserDAO.
 * Ensures parameterized statements to prevent SQL Injection
 * and uses try-with-resources for deterministic resource cleanup.
 */
public class UserDAOImpl implements UserDAO {

    private static final Logger logger = LoggerFactory.getLogger(UserDAOImpl.class);

    @Override
    public User findById(int id) {
        String sql = "SELECT id, username, full_name, email, password_hash, salt, role, status, phone, department, created_at, updated_at " +
                "FROM users WHERE id = ?";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by id: {}", id, e);
        }
        return null;
    }

    @Override
    public User findByUsername(String username) {
        String sql = "SELECT id, username, full_name, email, password_hash, salt, role, status, phone, department, created_at, updated_at " +
                "FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username: {}", username, e);
        }
        return null;
    }

    @Override
    public User findByEmail(String email) {
        String sql = "SELECT id, username, full_name, email, password_hash, salt, role, status, phone, department, created_at, updated_at " +
                "FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by email: {}", email, e);
        }
        return null;
    }

    @Override
    public List<User> findAll(String search, String role, String status, int offset, int limit) {
        List<User> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, username, full_name, email, password_hash, salt, role, status, phone, department, created_at, updated_at FROM users WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildFilterConditions(search, role, status, sql, params);

        sql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");
        params.add(limit > 0 ? limit : 20);
        params.add(offset >= 0 ? offset : 0);

        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching users list with filters", e);
        }
        return list;
    }

    @Override
    public int count(String search, String role, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        buildFilterConditions(search, role, status, sql, params);

        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting users", e);
        }
        return 0;
    }

    private void buildFilterConditions(String search, String role, String status, StringBuilder sql, List<Object> params) {
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(username) LIKE ? OR LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(department) LIKE ?)");
            String queryParam = "%" + search.trim().toLowerCase() + "%";
            params.add(queryParam);
            params.add(queryParam);
            params.add(queryParam);
            params.add(queryParam);
        }
        if (role != null && !role.trim().isEmpty() && !"ALL".equalsIgnoreCase(role)) {
            sql.append(" AND role = ?");
            params.add(role.trim().toUpperCase());
        }
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND status = ?");
            params.add(status.trim().toUpperCase());
        }
    }

    @Override
    public boolean create(User user) {
        String sql = "INSERT INTO users (username, full_name, email, password_hash, salt, role, status, phone, department, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash());
            ps.setString(5, user.getSalt());
            ps.setString(6, user.getRole() != null ? user.getRole() : "USER");
            ps.setString(7, user.getStatus() != null ? user.getStatus() : "ACTIVE");
            ps.setString(8, user.getPhone());
            ps.setString(9, user.getDepartment());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        user.setId(rs.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error creating user: {}", user.getUsername(), e);
        }
        return false;
    }

    @Override
    public boolean update(User user) {
        String sql = "UPDATE users SET full_name = ?, email = ?, role = ?, status = ?, phone = ?, department = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getRole());
            ps.setString(4, user.getStatus());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getDepartment());
            ps.setInt(7, user.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating user id: {}", user.getId(), e);
        }
        return false;
    }

    @Override
    public boolean updatePassword(int userId, String newHash, String newSalt) {
        String sql = "UPDATE users SET password_hash = ?, salt = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, newSalt);
            ps.setInt(3, userId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating password for user id: {}", userId, e);
        }
        return false;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error deleting user id: {}", id, e);
        }
        return false;
    }

    @Override
    public boolean isUsernameTaken(String username, Integer excludeId) {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)" +
                (excludeId != null ? " AND id != ?" : "");
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            if (excludeId != null) {
                ps.setInt(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking username uniqueness: {}", username, e);
        }
        return false;
    }

    @Override
    public boolean isEmailTaken(String email, Integer excludeId) {
        String sql = "SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)" +
                (excludeId != null ? " AND id != ?" : "");
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            if (excludeId != null) {
                ps.setInt(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking email uniqueness: {}", email, e);
        }
        return false;
    }

    @Override
    public void logActivity(Integer userId, String action, String details, String ipAddress) {
        String sql = "INSERT INTO activity_logs (user_id, action, details, ip_address, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, action);
            ps.setString(3, details);
            ps.setString(4, ipAddress);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Could not write audit log: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        String sql = "SELECT " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) AS active, " +
                "SUM(CASE WHEN status != 'ACTIVE' THEN 1 ELSE 0 END) AS inactive, " +
                "SUM(CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END) AS admins, " +
                "SUM(CASE WHEN role = 'MANAGER' THEN 1 ELSE 0 END) AS managers, " +
                "SUM(CASE WHEN role = 'USER' THEN 1 ELSE 0 END) AS standard_users " +
                "FROM users";

        try (Connection conn = DBConnectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                stats.put("totalUsers", rs.getInt("total"));
                stats.put("activeUsers", rs.getInt("active"));
                stats.put("inactiveUsers", rs.getInt("inactive"));
                stats.put("adminUsers", rs.getInt("admins"));
                stats.put("managerUsers", rs.getInt("managers"));
                stats.put("standardUsers", rs.getInt("standard_users"));
            }
        } catch (SQLException e) {
            logger.error("Error gathering dashboard stats", e);
            stats.put("totalUsers", 0);
            stats.put("activeUsers", 0);
            stats.put("inactiveUsers", 0);
            stats.put("adminUsers", 0);
            stats.put("managerUsers", 0);
            stats.put("standardUsers", 0);
        }
        stats.put("databaseEngine", DBConnectionFactory.getDatabaseEngine());
        stats.put("isFallback", DBConnectionFactory.isUsingFallback());
        return stats;
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setRole(rs.getString("role"));
        u.setStatus(rs.getString("status"));
        u.setPhone(rs.getString("phone"));
        u.setDepartment(rs.getString("department"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        u.setUpdatedAt(rs.getTimestamp("updated_at"));
        return u;
    }
}
