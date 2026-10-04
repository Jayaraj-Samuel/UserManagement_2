package com.usermgmt;

import com.usermgmt.dao.DBConnectionFactory;
import com.usermgmt.dao.UserDAO;
import com.usermgmt.dao.impl.UserDAOImpl;
import com.usermgmt.model.User;
import com.usermgmt.service.UserService;
import com.usermgmt.service.impl.UserServiceImpl;
import com.usermgmt.util.PasswordUtil;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Self-contained Automated Test Suite to verify all components,
 * database connectivity, SQL queries, business logic, and security rules.
 */
public class AppTestRunner {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println(">>> RUNNING AUTOMATED TEST SUITE FOR USER MANAGEMENT SYSTEM <<<");
        System.out.println("==================================================================");

        int passed = 0;
        int failed = 0;

        // Test 1: Password Utility Hashing and Verification
        try {
            System.out.print("[TEST 1] Password Utility (PBKDF2-HMAC-SHA256)... ");
            String rawPassword = "TestPassword@999";
            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(rawPassword, salt);

            if (!PasswordUtil.verifyPassword(rawPassword, hash, salt)) {
                throw new AssertionError("Password verification failed for valid password");
            }
            if (PasswordUtil.verifyPassword("WrongPassword", hash, salt)) {
                throw new AssertionError("Password verification passed for invalid password");
            }
            System.out.println("PASSED");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        // Test 2: Database Connection & Schema Auto-Initialization
        try {
            System.out.print("[TEST 2] Database Connection Factory & Auto-Init... ");
            try (Connection conn = DBConnectionFactory.getConnection()) {
                if (conn == null || conn.isClosed()) {
                    throw new AssertionError("Database connection is null or closed");
                }
            }
            System.out.println("PASSED (" + DBConnectionFactory.getDatabaseEngine() + ")");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        // Test 3: DAO Query and Seed Data Verification
        UserDAO userDAO = new UserDAOImpl();
        try {
            System.out.print("[TEST 3] UserDAO - Query Seed Users (Admin check)... ");
            User admin = userDAO.findByUsername("admin");
            if (admin == null) {
                throw new AssertionError("Seed admin user 'admin' not found");
            }
            if (!"ADMIN".equals(admin.getRole())) {
                throw new AssertionError("Admin role expected ADMIN, found " + admin.getRole());
            }
            if (!PasswordUtil.verifyPassword("Admin@123", admin.getPasswordHash(), admin.getSalt())) {
                throw new AssertionError("Admin password verification failed");
            }
            System.out.println("PASSED (Admin ID: " + admin.getId() + ")");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        // Test 4: Dynamic Search & Pagination Queries
        try {
            System.out.print("[TEST 4] Dynamic Filtering & Search Queries... ");
            List<User> list = userDAO.findAll("admin", "ADMIN", "ACTIVE", 0, 10);
            if (list.isEmpty()) {
                throw new AssertionError("Search for 'admin' with filter ADMIN returned 0 results");
            }
            int count = userDAO.count(null, "ALL", "ALL");
            if (count < 1) {
                throw new AssertionError("Total user count should be >= 1, found: " + count);
            }
            System.out.println("PASSED (Found " + count + " users)");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        // Test 5: Service Layer Authentication
        UserService userService = new UserServiceImpl(userDAO);
        try {
            System.out.print("[TEST 5] UserService - Authentication Workflow... ");
            User authUser = userService.authenticate("admin", "Admin@123", "127.0.0.1");
            if (authUser == null) {
                throw new AssertionError("Authentication failed for valid credentials");
            }

            User failedAuth = userService.authenticate("admin", "WrongPass!", "127.0.0.1");
            if (failedAuth != null) {
                throw new AssertionError("Authentication succeeded for invalid credentials");
            }
            System.out.println("PASSED");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        // Test 6: Full CRUD Workflow (Create -> Read -> Update -> Delete)
        try {
            System.out.print("[TEST 6] UserService - Full CRUD Lifecycle... ");
            String uniqueUser = "testuser_" + System.currentTimeMillis();
            User newUser = new User();
            newUser.setUsername(uniqueUser);
            newUser.setFullName("Test User Automation");
            newUser.setEmail(uniqueUser + "@example.com");
            newUser.setPassword("SecurePass@123");
            newUser.setRole("USER");
            newUser.setStatus("ACTIVE");
            newUser.setDepartment("Quality Engineering");

            User created = userService.createUser(newUser, "127.0.0.1");
            if (created.getId() == null || created.getId() <= 0) {
                throw new AssertionError("User creation failed, ID is null");
            }

            // Update user
            created.setFullName("Test User Updated");
            User updated = userService.updateUser(created, "127.0.0.1");
            if (!"Test User Updated".equals(updated.getFullName())) {
                throw new AssertionError("User update failed to persist full name");
            }

            // Test self-delete prevention rule
            boolean selfDeleteCaught = false;
            try {
                userService.deleteUser(created.getId(), created.getId(), "127.0.0.1");
            } catch (IllegalStateException e) {
                selfDeleteCaught = true;
            }
            if (!selfDeleteCaught) {
                throw new AssertionError("Security rule violation: self-deletion was not prevented!");
            }

            // Admin deletes user
            User admin = userDAO.findByUsername("admin");
            boolean deleted = userService.deleteUser(created.getId(), admin.getId(), "127.0.0.1");
            if (!deleted) {
                throw new AssertionError("Failed to delete user ID " + created.getId());
            }

            System.out.println("PASSED (Created, Updated, Self-Delete Blocked, Deleted successfully)");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        // Test 7: Dashboard Statistics aggregation query
        try {
            System.out.print("[TEST 7] Dashboard Statistics SQL Aggregations... ");
            Map<String, Object> stats = userService.getDashboardStatistics();
            int total = (Integer) stats.get("totalUsers");
            int active = (Integer) stats.get("activeUsers");
            int admins = (Integer) stats.get("adminUsers");
            if (total <= 0 || active <= 0 || admins <= 0) {
                throw new AssertionError("Invalid stats values: total=" + total + ", active=" + active + ", admins=" + admins);
            }
            System.out.println("PASSED (Total: " + total + ", Active: " + active + ", Admins: " + admins + ")");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        // Test 8: User Sorting Functionality (Ascending/Descending across columns)
        try {
            System.out.print("[TEST 8] User Sorting by ID DESC and Full Name ASC... ");
            List<User> sortedByIdDesc = userDAO.findAll(null, "ALL", "ALL", "id", "DESC", 0, 10);
            if (sortedByIdDesc.size() >= 2) {
                if (sortedByIdDesc.get(0).getId() < sortedByIdDesc.get(1).getId()) {
                    throw new AssertionError("Expected ID DESC order, but first ID (" + sortedByIdDesc.get(0).getId() + ") < second ID (" + sortedByIdDesc.get(1).getId() + ")");
                }
            }

            List<User> sortedByNameAsc = userService.listUsers(null, "ALL", "ALL", "full_name", "ASC", 1, 10);
            if (sortedByNameAsc.size() >= 2) {
                String first = sortedByNameAsc.get(0).getFullName().toLowerCase();
                String second = sortedByNameAsc.get(1).getFullName().toLowerCase();
                if (first.compareTo(second) > 0) {
                    throw new AssertionError("Expected Name ASC order, but '" + first + "' > '" + second + "'");
                }
            }
            System.out.println("PASSED (Sorted ID DESC and Name ASC validated)");
            passed++;
        } catch (Throwable t) {
            System.out.println("FAILED: " + t.getMessage());
            failed++;
        }

        System.out.println("==================================================================");
        System.out.println("TEST SUMMARY: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
