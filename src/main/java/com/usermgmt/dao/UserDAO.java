package com.usermgmt.dao;

import com.usermgmt.model.User;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object interface for User operations.
 * Defines standard CRUD and filtering contracts.
 */
public interface UserDAO {

    User findById(int id);

    User findByUsername(String username);

    User findByEmail(String email);

    List<User> findAll(String search, String role, String status, int offset, int limit);

    List<User> findAll(String search, String role, String status, String sortBy, String sortDir, int offset, int limit);

    int count(String search, String role, String status);

    boolean create(User user);

    boolean update(User user);

    boolean updatePassword(int userId, String newHash, String newSalt);

    boolean delete(int id);

    boolean isUsernameTaken(String username, Integer excludeId);

    boolean isEmailTaken(String email, Integer excludeId);

    void logActivity(Integer userId, String action, String details, String ipAddress);

    Map<String, Object> getDashboardStats();
}
