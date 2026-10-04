package com.usermgmt.service.impl;

import com.usermgmt.dao.UserDAO;
import com.usermgmt.dao.impl.UserDAOImpl;
import com.usermgmt.model.User;
import com.usermgmt.service.UserService;
import com.usermgmt.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service Implementation enforcing business rules, input validation,
 * cryptographic password handling, and audit trail logging.
 */
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    private final UserDAO userDAO;

    public UserServiceImpl() {
        this.userDAO = new UserDAOImpl();
    }

    public UserServiceImpl(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public User authenticate(String username, String password, String ipAddress) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Username and password are required.");
        }

        User user = userDAO.findByUsername(username.trim());
        if (user == null) {
            userDAO.logActivity(null, "LOGIN_FAILED", "Failed login attempt for username: " + username, ipAddress);
            logger.warn("Authentication failed: User '{}' not found", username);
            return null;
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            userDAO.logActivity(user.getId(), "LOGIN_BLOCKED", "Attempted login to inactive/suspended account", ipAddress);
            throw new IllegalStateException("Account is inactive or suspended. Please contact the administrator.");
        }

        boolean valid = PasswordUtil.verifyPassword(password, user.getPasswordHash(), user.getSalt());
        if (!valid) {
            userDAO.logActivity(user.getId(), "LOGIN_FAILED", "Invalid credentials entered", ipAddress);
            logger.warn("Authentication failed: Invalid password for user '{}'", username);
            return null;
        }

        userDAO.logActivity(user.getId(), "LOGIN_SUCCESS", "User logged in successfully", ipAddress);
        logger.info("User '{}' authenticated successfully", username);
        return user;
    }

    @Override
    public User getUserById(int id) {
        return userDAO.findById(id);
    }

    @Override
    public List<User> listUsers(String search, String role, String status, int page, int pageSize) {
        return listUsers(search, role, status, "id", "ASC", page, pageSize);
    }

    @Override
    public List<User> listUsers(String search, String role, String status, String sortBy, String sortDir, int page, int pageSize) {
        int limit = pageSize > 0 ? pageSize : 10;
        int offset = (page > 0 ? page - 1 : 0) * limit;
        return userDAO.findAll(search, role, status, sortBy, sortDir, offset, limit);
    }

    @Override
    public int getTotalUserCount(String search, String role, String status) {
        return userDAO.count(search, role, status);
    }

    @Override
    public User createUser(User user, String clientIp) {
        validateUser(user, true);

        if (userDAO.isUsernameTaken(user.getUsername(), null)) {
            throw new IllegalArgumentException("Username '" + user.getUsername() + "' is already registered.");
        }
        if (userDAO.isEmailTaken(user.getEmail(), null)) {
            throw new IllegalArgumentException("Email address '" + user.getEmail() + "' is already registered.");
        }

        // Generate salt and hash password
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(user.getPassword(), salt);
        user.setSalt(salt);
        user.setPasswordHash(hash);

        // Sanitize defaults
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole("USER");
        }
        if (user.getStatus() == null || user.getStatus().trim().isEmpty()) {
            user.setStatus("ACTIVE");
        }

        boolean created = userDAO.create(user);
        if (!created) {
            throw new RuntimeException("Database error: Could not insert new user record.");
        }

        userDAO.logActivity(user.getId(), "USER_CREATED", "User created: " + user.getUsername() + " (" + user.getRole() + ")", clientIp);
        logger.info("New user created: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Override
    public User updateUser(User user, String clientIp) {
        if (user.getId() == null || user.getId() <= 0) {
            throw new IllegalArgumentException("Valid user ID is required for update.");
        }

        User existing = userDAO.findById(user.getId());
        if (existing == null) {
            throw new IllegalArgumentException("User with ID " + user.getId() + " does not exist.");
        }

        validateUser(user, false);

        if (userDAO.isEmailTaken(user.getEmail(), user.getId())) {
            throw new IllegalArgumentException("Email address '" + user.getEmail() + "' is already in use by another account.");
        }

        // Guard: Prevent demoting the last active ADMIN
        if ("ADMIN".equalsIgnoreCase(existing.getRole()) && !"ADMIN".equalsIgnoreCase(user.getRole())) {
            Map<String, Object> stats = userDAO.getDashboardStats();
            int adminCount = (Integer) stats.getOrDefault("adminUsers", 0);
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot change role. The system requires at least one active Administrator.");
            }
        }

        // Apply fields to existing
        existing.setFullName(user.getFullName().trim());
        existing.setEmail(user.getEmail().trim().toLowerCase());
        existing.setRole(user.getRole().trim().toUpperCase());
        existing.setStatus(user.getStatus().trim().toUpperCase());
        existing.setPhone(user.getPhone() != null ? user.getPhone().trim() : null);
        existing.setDepartment(user.getDepartment() != null ? user.getDepartment().trim() : null);

        // If an optional new password was provided
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            if (user.getPassword().trim().length() < 6) {
                throw new IllegalArgumentException("Password must be at least 6 characters long.");
            }
            String newSalt = PasswordUtil.generateSalt();
            String newHash = PasswordUtil.hashPassword(user.getPassword().trim(), newSalt);
            userDAO.updatePassword(existing.getId(), newHash, newSalt);
        }

        boolean updated = userDAO.update(existing);
        if (!updated) {
            throw new RuntimeException("Database error: Could not update user record.");
        }

        userDAO.logActivity(existing.getId(), "USER_UPDATED", "Updated user details for: " + existing.getUsername(), clientIp);
        logger.info("User updated: id={}, username={}", existing.getId(), existing.getUsername());
        return existing;
    }

    @Override
    public boolean changePassword(int userId, String oldPassword, String newPassword, String clientIp) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        if (!PasswordUtil.verifyPassword(oldPassword, user.getPasswordHash(), user.getSalt())) {
            throw new IllegalArgumentException("Current password does not match.");
        }
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long.");
        }

        String newSalt = PasswordUtil.generateSalt();
        String newHash = PasswordUtil.hashPassword(newPassword.trim(), newSalt);
        boolean success = userDAO.updatePassword(userId, newHash, newSalt);
        if (success) {
            userDAO.logActivity(userId, "PASSWORD_CHANGED", "User password was changed", clientIp);
        }
        return success;
    }

    @Override
    public boolean deleteUser(int targetUserId, int currentUserId, String clientIp) {
        if (targetUserId == currentUserId) {
            throw new IllegalStateException("Self-deletion is prohibited. You cannot delete your own logged-in account.");
        }

        User target = userDAO.findById(targetUserId);
        if (target == null) {
            throw new IllegalArgumentException("User with ID " + targetUserId + " does not exist.");
        }

        if ("ADMIN".equalsIgnoreCase(target.getRole())) {
            Map<String, Object> stats = userDAO.getDashboardStats();
            int adminCount = (Integer) stats.getOrDefault("adminUsers", 0);
            if (adminCount <= 1) {
                throw new IllegalStateException("Cannot delete the only remaining Administrator account in the system.");
            }
        }

        boolean deleted = userDAO.delete(targetUserId);
        if (deleted) {
            userDAO.logActivity(currentUserId, "USER_DELETED", "Deleted user account: " + target.getUsername() + " (ID: " + targetUserId + ")", clientIp);
            logger.info("User deleted: id={}, username={}", targetUserId, target.getUsername());
        }
        return deleted;
    }

    @Override
    public Map<String, Object> getDashboardStatistics() {
        return userDAO.getDashboardStats();
    }

    private void validateUser(User user, boolean isNew) {
        if (user == null) {
            throw new IllegalArgumentException("User payload must not be null.");
        }
        if (isNew) {
            if (user.getUsername() == null || !USERNAME_PATTERN.matcher(user.getUsername().trim()).matches()) {
                throw new IllegalArgumentException("Username must be between 3 and 30 alphanumeric characters (letters, numbers, underscore, hyphen).");
            }
            if (user.getPassword() == null || user.getPassword().trim().length() < 6) {
                throw new IllegalArgumentException("Password is required and must be at least 6 characters long.");
            }
        }
        if (user.getFullName() == null || user.getFullName().trim().length() < 2) {
            throw new IllegalArgumentException("Full name is required (minimum 2 characters).");
        }
        if (user.getEmail() == null || !EMAIL_PATTERN.matcher(user.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("Please provide a valid email address.");
        }
        if (user.getRole() != null) {
            String role = user.getRole().trim().toUpperCase();
            if (!role.equals("ADMIN") && !role.equals("MANAGER") && !role.equals("USER")) {
                throw new IllegalArgumentException("Invalid role. Permitted values: ADMIN, MANAGER, USER.");
            }
        }
        if (user.getStatus() != null) {
            String status = user.getStatus().trim().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE") && !status.equals("SUSPENDED")) {
                throw new IllegalArgumentException("Invalid status. Permitted values: ACTIVE, INACTIVE, SUSPENDED.");
            }
        }
    }
}
