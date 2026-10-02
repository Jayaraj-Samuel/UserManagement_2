# Enterprise User Management Web Application

> **A production-grade Java EE Web Application featuring Session-Based Security, Jersey JAX-RS Web Services, MySQL Database with HikariCP Connection Pooling, and a Modern UI built with JSP, jQuery, Bootstrap 5, and AJAX.**

---

## 📋 Table of Contents
1. [Project Overview](#-project-overview)
2. [Technology Stack](#-technology-stack)
3. [Architecture & Design](#-architecture--design)
4. [Prerequisites](#-prerequisites)
5. [Quick Start (One-Command Run)](#-quick-start-one-command-run)
6. [Database Setup & Configuration](#-database-setup--configuration)
7. [Default Demo Credentials](#-default-demo-credentials)
8. [REST API Documentation](#-rest-api-documentation)
9. [Security Implementation](#-security-implementation)
10. [Automated Test Suite](#-automated-test-suite)
11. [Standalone Tomcat Deployment (.WAR)](#-standalone-tomcat-deployment-war)

---

## 🌟 Project Overview

This project is a comprehensive **Enterprise User Management System** designed for high security, scalability, and code clarity. It demonstrates full-stack Java competency adhering to modern enterprise design patterns (Layered MVC/DAO Architecture, Dependency Separation, Defensive Programming, and Cryptographic Security).

### Core Features
- **Session-Based Authentication & Authorization**: State-managed HTTP sessions with `AuthenticationFilter`, session fixation prevention, and browser cache busting.
- **Role-Based Access Control (RBAC)**: Fine-grained permissions for `ADMIN`, `MANAGER`, and `USER` roles.
- **Dynamic User Directory (Grid)**: Real-time debounced search, role filtering, status filtering, and server-side pagination.
- **Full CRUD Operations**:
  - **Create**: Add new users with email/username uniqueness checks and password hashing.
  - **Read**: View paginated lists or inspect detailed user profile modals.
  - **Update**: Edit user details, assign roles/departments, and optional password resets.
  - **Delete**: Safely delete user records with confirmation safeguards, self-deletion prevention, and last-admin deletion locks.
- **Audit & Activity Logging**: Tracks logins, creations, updates, and deletions with client IP addresses.
- **Self-Healing Database Architecture**: Connects to MySQL with HikariCP connection pooling; if local MySQL is temporarily offline during demonstrations, it gracefully engages an in-memory MySQL-mode fallback so the application runs 100% bug-free immediately.

---

## 💻 Technology Stack

| Layer | Technology | Details |
|---|---|---|
| **Frontend View** | **JSP (JavaServer Pages) & JSTL** | Server-side rendered views (`login.jsp`, `home.jsp`, `index.jsp`) |
| **Frontend Logic** | **jQuery 3.7.1 & AJAX** | Async REST communication, DOM manipulation, promises, event debouncing |
| **Styling & UI** | **Bootstrap 5.3 & Bootstrap Icons** | Responsive layout, modern cards, modals, badges, toast notifications |
| **Design System** | **Custom CSS (Vanilla)** | Glassmorphism, Google Fonts (`Plus Jakarta Sans` & `Outfit`), micro-animations |
| **Web Services** | **Jersey 2.40 (JAX-RS)** | Clean RESTful endpoints (`@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`) |
| **Serialization** | **Jackson 2.16** | JSON serialization with `@JsonProperty` and `@JsonIgnore` security annotations |
| **Database** | **MySQL 8.x / 9.x** | Relational schema with foreign keys, indexes, and parameterized queries |
| **Connection Pool** | **HikariCP 5.1.0** | Ultra-fast JDBC connection pooling |
| **Security** | **PBKDF2WithHmacSHA256** | Salted cryptographic password hashing with constant-time verification |
| **Embedded Server** | **Apache Tomcat 9.0.89 Embed** | Runs out of the box with zero external application server installation needed |
| **Build & Packaging** | **Apache Maven** | Standard Maven WAR project structure compatible with Java 17, 21, and 22 |

---

## 🏛 Architecture & Design

The application follows the clean **Layered Architecture pattern**:

```
Client Browser (JSP + jQuery + Ajax + Bootstrap)
                     │  HTTP / JSON
                     ▼
             Servlet Filters
   (CharsetFilter & AuthenticationFilter)
                     │
         Jersey Servlet Container (/api/*)
                     │
              REST Resources
    (AuthResource, UserResource, StatsResource)
                     │
              Service Layer
   (UserServiceImpl - Business Rules & Validation)
                     │
                DAO Layer
  (UserDAOImpl - Parameterized PreparedStatements)
                     │
            DB Connection Factory
         (HikariCP DataSource Pool)
          ├── Primary: MySQL Database
          └── Fallback: In-Memory Engine
```

---

## ⚙ Prerequisites

- **Java Development Kit (JDK)**: JDK 17, JDK 21, or JDK 22 installed (`java -version`).
- **Apache Maven**: Version 3.8+ installed (`mvn -v`).
- **MySQL Server** (Optional for immediate testing, recommended for production): Port 3306.

---

## 🚀 Quick Start (One-Command Run)

### 1. Clone or Open Workspace
Open a terminal in the project directory (`c:\JavaProjects\Dhanush`).

### 2. Launch the Application
Run the following single command:
```bash
mvn compile exec:java
```

Tomcat will start automatically on port **8080**:
- **Application URL**: [http://localhost:8080/](http://localhost:8080/)
- **Login Page**: [http://localhost:8080/login.jsp](http://localhost:8080/login.jsp)
- **User Dashboard**: [http://localhost:8080/home.jsp](http://localhost:8080/home.jsp)
- **REST Endpoints**: [http://localhost:8080/api/users](http://localhost:8080/api/users)

---

## 🗄 Database Setup & Configuration

### Configuration File: `src/main/resources/db.properties`
```properties
db.driver=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://localhost:3306/user_mgmt_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&createDatabaseIfNotExist=true
db.user=root
db.password=root

db.pool.maximumPoolSize=10
db.pool.minimumIdle=2
db.fallback.enabled=true
```

### Manual MySQL Schema Setup (Optional)
If you wish to pre-create the database via MySQL CLI or MySQL Workbench, execute [`schema.sql`](file:///c:/JavaProjects/Dhanush/schema.sql):
```bash
mysql -u root -p < schema.sql
```

> **Smart Auto-Init Note**: If you run the app without executing `schema.sql`, `DBConnectionFactory` automatically detects missing tables and generates the schema and seed accounts programmatically!

---

## 🔑 Default Demo Credentials

Pre-configured accounts for testing different role permissions:

| Username | Password | Role | Permissions |
|---|---|---|---|
| **`admin`** | `Admin@123` | **ADMIN** | Full privileges (Create, View, Edit, Delete all users, Change roles) |
| **`jane_manager`** | `Manager@123` | **MANAGER** | Management privileges (Create, View, Edit regular users) |
| **`john_doe`** | `User@123` | **USER** | Standard privileges (View directory grid, View details) |
| **`robert_inactive`** | `User@123` | **USER (Inactive)** | Demonstrates account suspension blocks |

*(On the login screen, simply click any of the **Quick-fill Demo Credentials** badges to populate credentials in 1 click!)*

---

## 📡 REST API Documentation

All REST APIs return consistent JSON payloads wrapped in `ApiResponse<T>`:
```json
{
  "success": true,
  "message": "Operation successful",
  "data": { ... },
  "timestamp": 1790907410000
}
```

### Authentication Endpoints (`/api/auth`)
| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `POST` | `/api/auth/login` | Authenticates credentials and starts session | No |
| `POST` | `/api/auth/logout` | Invalidates session and clears cookies | Yes |
| `GET` | `/api/auth/check` | Returns current user profile and session validity | Yes |

### User Management Endpoints (`/api/users`)
| Method | Endpoint | Query Parameters | Description |
|---|---|---|---|
| `GET` | `/api/users` | `search`, `role`, `status`, `page`, `pageSize` | Paginated and filtered users list |
| `GET` | `/api/users/{id}` | - | Fetch single user by ID |
| `POST` | `/api/users` | - | Create new user (Admin/Manager only) |
| `PUT` | `/api/users/{id}` | - | Update user record (Admin/Manager/Self) |
| `DELETE` | `/api/users/{id}` | - | Delete user (Admin only, self-delete blocked) |

### Dashboard KPIs (`/api/stats`)
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/stats` | Aggregated metrics (total, active, inactive, admins, active DB engine) |

---

## 🔒 Security Implementation

1. **Session-Based Security**:
   - `AuthenticationFilter` intercepts all incoming requests.
   - Unauthenticated page requests are redirected to `/login.jsp?sessionExpired=true`.
   - Unauthenticated AJAX requests receive HTTP `401 Unauthorized` JSON.
   - `request.changeSessionId()` prevents Session Fixation attacks.
2. **Password Cryptography**:
   - `PBKDF2WithHmacSHA256` with 65,536 iterations and 16-byte cryptographically secure random salts.
   - Constant-time verification (`MessageDigest.isEqual`) to thwart timing attacks.
   - Sensitive hash and salt columns are annotated with `@JsonIgnore` to prevent leakage in API responses.
3. **SQL Injection Prevention**:
   - 100% of SQL operations utilize parameterized `PreparedStatement`. No string concatenation is used in queries.
4. **Browser Cache Hardening**:
   - `Cache-Control: no-cache, no-store, must-revalidate` headers are injected so that clicking the browser "Back" button after logout never exposes protected user pages.
5. **Business Safeguards**:
   - System prevents self-deletion of the active admin.
   - System prevents deleting or demoting the last remaining active Administrator.

---

## 🧪 Automated Test Suite

An automated end-to-end verification suite is included:
```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.2.0:java -Dexec.mainClass=com.usermgmt.AppTestRunner -Dexec.classpathScope=test
```

### Verified Scenarios:
- **Test 1**: PBKDF2 salt generation, hashing, and positive/negative password validation.
- **Test 2**: Database connection pooling and table auto-initialization.
- **Test 3**: DAO query contracts and seed administrator presence.
- **Test 4**: Dynamic filtering, multi-condition WHERE clauses, and pagination count queries.
- **Test 5**: User authentication workflow with invalid credential rejection.
- **Test 6**: Full CRUD lifecycle (Create -> Retrieve -> Update -> Self-Delete Block Check -> Delete).
- **Test 7**: SQL aggregation queries for KPI statistics.

---

## 📦 Standalone Tomcat Deployment (.WAR)

To package a standard Java Web Archive deployable to any standalone Tomcat 9/10, GlassFish, or WildFly server:
```bash
mvn clean package -DskipTests
```
The deployable archive will be created at:
```
target/usermanagement.war
```
Simply copy `target/usermanagement.war` into your Tomcat `webapps/` directory and start Tomcat.
