# Architecture & Technical Implementation Guide

> **Technical Interview Dossier for the User Management Java Web Application**  
> *Authoritative architectural justification, database query breakdown, security mechanics, and interview Q&A guide.*

---

## 📑 Contents
1. [Architectural Rationale: What We Built and Why](#1-architectural-rationale-what-we-built-and-why)
2. [Session-Based Security Mechanics](#2-session-based-security-mechanics)
3. [Database Queries & Persistence Layer Deep Dive](#3-database-queries--persistence-layer-deep-dive)
4. [Jersey JAX-RS REST Web Services](#4-jersey-jax-rs-rest-web-services)
5. [Frontend UI, jQuery, and AJAX Dataflow](#5-frontend-ui-jquery-and-ajax-dataflow)
6. [Top Interview Questions & Model Answers](#6-top-interview-questions--model-answers)

---

## 1. Architectural Rationale: What We Built and Why

### The Challenge
Build a robust, enterprise-grade Java web application featuring session-based authentication, user CRUD operations in a responsive grid, RESTful web services via Jersey, MySQL database persistence, and an intuitive UI utilizing JSP, jQuery, and Bootstrap.

### Design Patterns Applied
1. **Layered Architecture (Separation of Concerns)**:
   - **View Layer**: JSP files (`login.jsp`, `home.jsp`) provide initial semantic layout and server-side session guards.
   - **Client Controller (AJAX/jQuery)**: Modular JS files (`auth.js`, `app.js`) manage asynchronous events, UI transitions, debounced search, and DOM rendering without full page reloads.
   - **Filter Layer**: Intercepts HTTP requests to enforce session validity, UTF-8 encoding, and cache-busting headers.
   - **API Controller Layer (Jersey JAX-RS)**: Declarative REST controllers (`AuthResource`, `UserResource`, `StatsResource`) parsing JSON via Jackson.
   - **Business Service Layer**: Enforces business validation, role permissions, password cryptography, and audit logs.
   - **Data Access Object (DAO) Layer**: Encapsulates all raw JDBC operations using `PreparedStatement`.
   - **Connection Pool**: HikariCP managing reusable JDBC connections.

### Why this stack over alternatives?
- **Why Jersey (JAX-RS) over Raw Servlets?**  
  Raw servlets require manual `doGet`/`doPost` boilerplate, manual URL parameter parsing, and manual JSON serialization. Jersey provides declarative HTTP verb annotations (`@GET`, `@POST`, `@PUT`, `@DELETE`), typed path parameters (`@PathParam`), automatic Jackson serialization, and standard HTTP response status builders (`Response.status().entity().build()`).
- **Why JSP + jQuery/AJAX over Single-Page App (SPA)?**  
  Meets the exact enterprise test requirements while eliminating heavy Node/Webpack build chains. JSP handles the initial server-side session validation, while jQuery and AJAX provide a snappy SPA-like user experience.

---

## 2. Session-Based Security Mechanics

### 1. The HTTP Session Lifecycle
```
[Browser Client]                                  [Server / Tomcat]
       │                                                  │
       │─── POST /api/auth/login (username, password) ───▶│
       │                                                  │ Verify Credentials (PBKDF2)
       │                                                  │ Create HttpSession
       │                                                  │ request.changeSessionId()
       │                                                  │ session.setAttribute("LOGGED_IN_USER", u)
       │◀── Set-Cookie: JSESSIONID=xyz... (HttpOnly) ─────│
       │                                                  │
       │─── GET /home.jsp [Cookie: JSESSIONID=xyz...] ───▶│
       │                                                  │ AuthenticationFilter verifies session
       │◀── HTTP 200 OK (Render Dashboard) ───────────────│
       │                                                  │
       │─── POST /api/auth/logout ───────────────────────▶│
       │                                                  │ session.invalidate()
       │◀── Set-Cookie: JSESSIONID=; Max-Age=0 ───────────│
```

### 2. Authentication Filter (`AuthenticationFilter.java`)
```java
// Extracts requested resource path
String path = uri.substring(contextPath.length());

// Whitelist unauthenticated endpoints
boolean isPublicResource = path.equals("/") ||
        path.equals("/login.jsp") ||
        path.equals("/index.jsp") ||
        path.startsWith("/assets/") ||
        path.equals("/api/auth/login");

// Security Headers: Prevent browser caching of protected pages
response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
response.setHeader("Pragma", "no-cache");
response.setDateHeader("Expires", 0);

// Verification
HttpSession session = request.getSession(false);
boolean isLoggedIn = (session != null && session.getAttribute("LOGGED_IN_USER") != null);

if (isLoggedIn) {
    chain.doFilter(request, response);
} else {
    if (path.startsWith("/api/")) {
        // Return 401 JSON for AJAX callers
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"success\":false,\"message\":\"Session expired.\"}");
    } else {
        // Redirect web browser to login
        response.sendRedirect(contextPath + "/login.jsp?sessionExpired=true");
    }
}
```

### 3. Mitigating Top Security Vulnerabilities
- **Session Fixation**: Upon successful authentication in `AuthResource`, `request.changeSessionId()` is called. This issues a new `JSESSIONID` cookie value while preserving session data, neutralizing attackers who try to pre-set a session ID.
- **Credential Storage**: Passwords are never stored in plaintext or weak hashes (MD5/SHA1). We use **PBKDF2WithHmacSHA256** with 65,536 iterations and 16-byte cryptographically secure random salts.
- **Timing Attack Resistance**: Password hash verification uses `MessageDigest.isEqual()`, which executes in constant time regardless of where mismatches occur.
- **Back-Button Leakage**: Browser `Cache-Control: no-cache, no-store` prevents users from viewing sensitive directory data by hitting the browser's back button after logging out.

---

## 3. Database Queries & Persistence Layer Deep Dive

### 1. Relational Schema (`schema.sql`)
```sql
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    phone VARCHAR(20),
    department VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_role (role),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```
*Why indexes on `username`, `email`, `role`, and `status`?*  
Lookup queries (`findByUsername`, `findByEmail`) and filtering in the user grid execute with `O(log N)` index seeks rather than full table scans `O(N)`.

---

### 2. Detailed Query Analysis

#### A. Dynamic Search, Filtering & Server-Side Pagination
**Problem**: The grid needs to support search across multiple columns (username, full name, email, department), filter by role, filter by status, and paginate efficiently.

**Solution (`UserDAOImpl.java`)**:
```sql
SELECT id, username, full_name, email, password_hash, salt, role, status, phone, department, created_at, updated_at 
FROM users 
WHERE 1=1 
  AND (LOWER(username) LIKE ? OR LOWER(full_name) LIKE ? OR LOWER(email) LIKE ? OR LOWER(department) LIKE ?)
  AND role = ? 
  AND status = ? 
ORDER BY id DESC 
LIMIT ? OFFSET ?
```
**Why parameterized?**  
Every filter parameter is bound via `ps.setObject(i + 1, params.get(i))`. This completely eliminates SQL Injection vulnerabilities because the SQL structure is pre-compiled by the database engine before user input is supplied.

#### B. Dashboard Statistics Aggregation
**Problem**: Computing total users, active accounts, inactive accounts, and admins in a single round-trip without executing 4 separate queries.

**Solution**:
```sql
SELECT 
    COUNT(*) AS total, 
    SUM(CASE WHEN status = 'ACTIVE' THEN 1 ELSE 0 END) AS active, 
    SUM(CASE WHEN status != 'ACTIVE' THEN 1 ELSE 0 END) AS inactive, 
    SUM(CASE WHEN role = 'ADMIN' THEN 1 ELSE 0 END) AS admins, 
    SUM(CASE WHEN role = 'MANAGER' THEN 1 ELSE 0 END) AS managers, 
    SUM(CASE WHEN role = 'USER' THEN 1 ELSE 0 END) AS standard_users 
FROM users;
```
**Why this query rocks in an interview**:  
Instead of sending 5 network requests (`SELECT COUNT(*) WHERE role='ADMIN'`, etc.), this conditional aggregation scans the table once in memory, maximizing database throughput.

#### C. User Insertion with Auto-Generated Keys
```sql
INSERT INTO users (username, full_name, email, password_hash, salt, role, status, phone, department, created_at, updated_at) 
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```
Using `PreparedStatement.RETURN_GENERATED_KEYS` enables retrieving the database-generated primary key (`id`) immediately without an extra `SELECT MAX(id)` race condition.

---

### 3. HikariCP Connection Pooling & Self-Healing Dual-Mode Fallback
In enterprise Java, creating a new `DriverManager.getConnection()` per HTTP request is a performance anti-pattern (TCP handshake + SSL negotiation + authentication overhead takes 50-100ms per request).

**HikariCP Configuration**:
- Maintains a pool of pre-warmed connections (`maximumPoolSize = 10`, `minimumIdle = 2`).
- Borrows connections in microseconds.
- Closes connections gracefully using standard Java `try-with-resources`.

**Self-Healing Dual Mode**:
- `DBConnectionFactory` attempts to connect to the configured MySQL instance on `localhost:3306`.
- If MySQL is running, it connects, executes `CREATE DATABASE IF NOT EXISTS`, creates tables, and seeds initial users.
- If MySQL is offline (e.g. during an interview live demo where MySQL wasn't started), it catches the exception and engages an in-memory **H2 database configured in MySQL compatibility mode** (`MODE=MySQL;DATABASE_TO_LOWER=TRUE`).
- The application never crashes with a `Communications link failure`, ensuring 100% testability and reliability!

---

## 4. Jersey JAX-RS REST Web Services

### Clean Uniform Response Format: `ApiResponse<T>`
```json
{
  "success": true,
  "message": "User created successfully!",
  "data": {
    "id": 6,
    "username": "alex_stone",
    "fullName": "Alex Stone",
    "email": "alex@company.com",
    "role": "USER",
    "status": "ACTIVE",
    "department": "Engineering"
  },
  "timestamp": 1790907410000
}
```

### Endpoint Architecture
1. **`POST /api/auth/login`**: Authenticates credentials, binds user session, returns HTTP 200 on success or 401 on bad password.
2. **`POST /api/auth/logout`**: Invalidates `HttpSession`.
3. **`GET /api/auth/check`**: Validates session freshness and returns logged-in user profile.
4. **`GET /api/users`**: Accepts `search`, `role`, `status`, `page`, `pageSize` query params.
5. **`GET /api/users/{id}`**: Returns single user record.
6. **`POST /api/users`**: Creates user (checks permissions: ADMIN/MANAGER only).
7. **`PUT /api/users/{id}`**: Updates user details and optional password.
8. **`DELETE /api/users/{id}`**: Deletes user (ADMIN only, blocks self-deletion and last-admin deletion).
9. **`GET /api/stats`**: Returns high-level metrics for dashboard cards.

---

## 5. Frontend UI, jQuery, and AJAX Dataflow

### Debounced Live Search
Typing in the search input field resets a 350ms timer (`clearTimeout(searchTimer)`). Only when the user stops typing for 350ms does the AJAX request dispatch:
```javascript
$("#searchInput").on("input", function () {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(function () {
        currentPage = 1;
        loadUsersGrid();
    }, 350);
});
```
*Why?* Prevents firing 10 database queries when the user rapidly types a 10-character name.

### Error Handling & Session Interception
If an AJAX call returns HTTP 401 (e.g. session expired in another tab), the client intercepts it and redirects cleanly to the login screen:
```javascript
error: function (xhr) {
    if (xhr.status === 401) {
        window.location.href = "login.jsp?sessionExpired=true";
    } else {
        showToast("Server request failed", "error");
    }
}
```

---

## 6. Top Interview Questions & Model Answers

### Q1: How does session management work in your application?
> **Answer**: "We utilize Java EE `HttpSession` backed by standard `JSESSIONID` cookies. When a user submits credentials to `POST /api/auth/login`, our `UserService` authenticates the credentials against the salted PBKDF2 hash. If valid, we create a session via `request.getSession(true)` and invoke `request.changeSessionId()` to defend against Session Fixation attacks. We store the user principal in the session. An `AuthenticationFilter` intercepts incoming requests, verifying session validity and blocking unauthenticated calls with an HTTP 401 JSON response for APIs or a redirect to `login.jsp` for page views."

### Q2: How did you ensure SQL Injection cannot occur?
> **Answer**: "Every single SQL query in our DAO layer uses parameterized `PreparedStatement` with placeholder tokens (`?`). Values are bound strictly via typed setters (`ps.setString`, `ps.setInt`, `ps.setObject`). The database engine compiles the SQL query plan before parameter substitution, treating all user input strictly as literal data rather than executable SQL syntax."

### Q3: Why did you use PBKDF2 instead of MD5 or SHA-256 for passwords?
> **Answer**: "Plain MD5 and SHA-256 are fast cryptographic hash algorithms designed for message integrity, making them vulnerable to brute-force and rainbow table attacks using modern GPUs. PBKDF2 (Password-Based Key Derivation Function 2) introduces a configurable work factor (we configured 65,536 iterations of HMAC-SHA256) combined with a cryptographically secure 16-byte random salt. This makes brute-force attacks computationally prohibitive."

### Q4: How is connection pooling configured, and why is it important?
> **Answer**: "We integrated HikariCP, known as the fastest and most lightweight JDBC connection pool in the Java ecosystem. Establishing a new database connection for every HTTP request incurs substantial TCP handshake and authentication overhead. HikariCP maintains a pool of pre-warmed connections (`maximumPoolSize=10`, `minimumIdle=2`), allowing threads to borrow and return connections in microseconds. We encapsulate resource management using Java `try-with-resources` to guarantee deterministic closure of connections and statements."

### Q5: How did you prevent accidental lockouts (e.g. deleting the last admin)?
> **Answer**: "Our `UserServiceImpl` enforces critical business constraints:
> 1. It prohibits self-deletion (`targetUserId == currentUserId`).
> 2. When attempting to delete or demote an Administrator, it queries active admin count and blocks the operation if only one Administrator remains in the system."
