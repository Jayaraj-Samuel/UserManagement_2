package com.usermgmt.dao;

import com.usermgmt.util.PasswordUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.*;
import java.util.Properties;

/**
 * High-performance Database Connection Factory using HikariCP.
 * Features automatic database table initialization, seed data population,
 * and seamless fallback to H2 in MySQL mode if local MySQL is offline.
 */
public class DBConnectionFactory {

    private static final Logger logger = LoggerFactory.getLogger(DBConnectionFactory.class);
    private static HikariDataSource dataSource;
    private static boolean usingFallback = false;
    private static String databaseEngine = "MySQL";

    static {
        initDataSource();
    }

    private static synchronized void initDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        Properties props = new Properties();
        try (InputStream is = DBConnectionFactory.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            logger.warn("Could not load db.properties, using built-in defaults: {}", e.getMessage());
        }

        String driver = props.getProperty("db.driver", "com.mysql.cj.jdbc.Driver");
        String url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/user_mgmt_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&createDatabaseIfNotExist=true");
        String user = props.getProperty("db.user", "root");
        String password = props.getProperty("db.password", "root");
        boolean fallbackEnabled = Boolean.parseBoolean(props.getProperty("db.fallback.enabled", "true"));

        try {
            logger.info("Attempting to connect to MySQL database at: {}", url);
            HikariConfig config = new HikariConfig();
            config.setDriverClassName(driver);
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);
            config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.maximumPoolSize", "10")));
            config.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.minimumIdle", "2")));
            config.setConnectionTimeout(Long.parseLong(props.getProperty("db.pool.connectionTimeout", "3000")));
            config.setIdleTimeout(Long.parseLong(props.getProperty("db.pool.idleTimeout", "30000")));
            config.setPoolName("MySQL-HikariCP-Pool");

            dataSource = new HikariDataSource(config);

            // Test connection
            try (Connection conn = dataSource.getConnection()) {
                logger.info("Successfully connected to MySQL database: {}", conn.getMetaData().getDatabaseProductName());
                databaseEngine = "MySQL (" + conn.getMetaData().getDatabaseProductVersion() + ")";
                usingFallback = false;
                initializeDatabaseSchema(conn);
            }
        } catch (Exception ex) {
            logger.warn("Could not connect to MySQL server: {}", ex.getMessage());
            if (fallbackEnabled) {
                logger.info("Engaging self-healing fallback: Starting Embedded H2 database in MySQL compatibility mode...");
                initFallbackDataSource();
            } else {
                throw new RuntimeException("Database connection failed and fallback is disabled.", ex);
            }
        }
    }

    private static void initFallbackDataSource() {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            HikariConfig h2Config = new HikariConfig();
            h2Config.setDriverClassName("org.h2.Driver");
            h2Config.setJdbcUrl("jdbc:h2:mem:user_mgmt_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            h2Config.setUsername("sa");
            h2Config.setPassword("");
            h2Config.setMaximumPoolSize(10);
            h2Config.setMinimumIdle(2);
            h2Config.setPoolName("H2-Fallback-Pool");

            dataSource = new HikariDataSource(h2Config);
            usingFallback = true;
            databaseEngine = "Embedded H2 (MySQL Mode)";

            try (Connection conn = dataSource.getConnection()) {
                logger.info("Embedded H2 fallback database initialized successfully.");
                initializeDatabaseSchema(conn);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize embedded fallback database: {}", e.getMessage(), e);
            throw new RuntimeException("Fatal: Database initialization failed completely.", e);
        }
    }

    private static void initializeDatabaseSchema(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Create Users table
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) NOT NULL UNIQUE, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(100) NOT NULL UNIQUE, " +
                    "password_hash VARCHAR(255) NOT NULL, " +
                    "salt VARCHAR(64) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL DEFAULT 'USER', " +
                    "status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', " +
                    "phone VARCHAR(20), " +
                    "department VARCHAR(50), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.execute(createUsersTable);

            // Create Activity Logs table
            String createLogsTable = "CREATE TABLE IF NOT EXISTS activity_logs (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_id INT NULL, " +
                    "action VARCHAR(50) NOT NULL, " +
                    "details TEXT, " +
                    "ip_address VARCHAR(45), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";
            stmt.execute(createLogsTable);

            // Check if users table is empty; if so, seed demo users
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    logger.info("Seeding initial administrator and demo users...");
                    seedInitialUsers(conn);
                }
            }
        } catch (SQLException e) {
            logger.error("Error initializing schema: {}", e.getMessage(), e);
        }
    }

    private static void seedInitialUsers(Connection conn) {
        String insertSql = "INSERT INTO users (username, full_name, email, password_hash, salt, role, status, phone, department) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            // 1. Admin user: admin / Admin@123
            String salt1 = PasswordUtil.generateSalt();
            String hash1 = PasswordUtil.hashPassword("Admin@123", salt1);
            ps.setString(1, "admin");
            ps.setString(2, "System Administrator");
            ps.setString(3, "admin@usermgmt.com");
            ps.setString(4, hash1);
            ps.setString(5, salt1);
            ps.setString(6, "ADMIN");
            ps.setString(7, "ACTIVE");
            ps.setString(8, "+1-555-0199");
            ps.setString(9, "Executive & IT Security");
            ps.addBatch();

            // 2. Manager user: jane_manager / Manager@123
            String salt2 = PasswordUtil.generateSalt();
            String hash2 = PasswordUtil.hashPassword("Manager@123", salt2);
            ps.setString(1, "jane_manager");
            ps.setString(2, "Jane Smith (Manager)");
            ps.setString(3, "jane.smith@usermgmt.com");
            ps.setString(4, hash2);
            ps.setString(5, salt2);
            ps.setString(6, "MANAGER");
            ps.setString(7, "ACTIVE");
            ps.setString(8, "+1-555-0142");
            ps.setString(9, "Engineering Operations");
            ps.addBatch();

            // 3. Regular User: john_doe / User@123
            String salt3 = PasswordUtil.generateSalt();
            String hash3 = PasswordUtil.hashPassword("User@123", salt3);
            ps.setString(1, "john_doe");
            ps.setString(2, "John Doe");
            ps.setString(3, "john.doe@usermgmt.com");
            ps.setString(4, hash3);
            ps.setString(5, salt3);
            ps.setString(6, "USER");
            ps.setString(7, "ACTIVE");
            ps.setString(8, "+1-555-0187");
            ps.setString(9, "Product Development");
            ps.addBatch();

            // 4. Inactive User: robert_inactive / User@123
            String salt4 = PasswordUtil.generateSalt();
            String hash4 = PasswordUtil.hashPassword("User@123", salt4);
            ps.setString(1, "robert_inactive");
            ps.setString(2, "Robert Wilson");
            ps.setString(3, "robert.w@usermgmt.com");
            ps.setString(4, hash4);
            ps.setString(5, salt4);
            ps.setString(6, "USER");
            ps.setString(7, "INACTIVE");
            ps.setString(8, "+1-555-0131");
            ps.setString(9, "Quality Assurance");
            ps.addBatch();

            // 5. User: emily_rose / User@123
            String salt5 = PasswordUtil.generateSalt();
            String hash5 = PasswordUtil.hashPassword("User@123", salt5);
            ps.setString(1, "emily_rose");
            ps.setString(2, "Emily Rose");
            ps.setString(3, "emily.rose@usermgmt.com");
            ps.setString(4, hash5);
            ps.setString(5, salt5);
            ps.setString(6, "USER");
            ps.setString(7, "ACTIVE");
            ps.setString(8, "+1-555-0165");
            ps.setString(9, "UI/UX Design");
            ps.addBatch();

            ps.executeBatch();
            logger.info("Successfully seeded 5 initial users.");
        } catch (SQLException e) {
            logger.error("Error seeding users: {}", e.getMessage(), e);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initDataSource();
        }
        return dataSource.getConnection();
    }

    public static boolean isUsingFallback() {
        return usingFallback;
    }

    public static String getDatabaseEngine() {
        return databaseEngine;
    }

    public static void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP DataSource shut down.");
        }
    }
}
