# User Management System — Interview Context

This document is a practical guide to the project’s purpose, structure, runtime flow, and the effects of changing its files. It is based on the current source code, so it also calls out places where the implementation differs from a general architectural description.

## 1. Project at a glance

This is a Java web application for managing user accounts. It provides a browser-based login and dashboard, REST APIs for authentication and user operations, and database persistence. A signed-in user’s role determines which management actions the API allows.

The application uses:

- Java 17 and Maven
- JSP for web pages
- jQuery and AJAX for browser interactions
- Jersey (JAX-RS) for REST endpoints
- Jackson for JSON conversion
- JDBC and `PreparedStatement` for SQL
- HikariCP for connection pooling
- MySQL as the configured database, with an optional in-memory H2 fallback
- Embedded Tomcat for local one-command startup; the project also packages as a WAR

### Architecture in one picture

```text
Browser
  ├── JSP pages + CSS
  └── jQuery/AJAX ── HTTP/JSON ──┐
                                 ▼
                          Servlet filters
                                 ▼
                       Jersey REST resources
                                 ▼
                          UserService
                      (business rules)
                                 ▼
                            UserDAO
                         (JDBC / SQL)
                                 ▼
                  HikariCP DataSource / database
                       MySQL or H2 fallback
```

The main separation of responsibilities is:

- **Web/UI:** what the user sees and browser-side behavior.
- **REST resources:** HTTP endpoints, request/response handling, and endpoint-level access checks.
- **Service:** business rules, validation, password handling, and safeguards.
- **DAO:** SQL queries and persistence.
- **Model:** Java objects used to carry user, request, and response data.

## 2. Repository layout

```text
UserManagementSystem2/
├── pom.xml
├── README.md
├── IMPLEMENTATION.md
├── Dockerfile
├── schema.sql
└── src/
    ├── main/
    │   ├── java/com/usermgmt/
    │   │   ├── EmbeddedServer.java
    │   │   ├── config/
    │   │   ├── dao/
    │   │   ├── filter/
    │   │   ├── model/
    │   │   ├── rest/
    │   │   ├── service/
    │   │   └── util/
    │   ├── resources/
    │   │   ├── db.properties
    │   │   └── schema.sql
    │   └── webapp/
    │       ├── WEB-INF/web.xml
    │       ├── assets/
    │       ├── home.jsp
    │       ├── index.jsp
    │       └── login.jsp
    └── test/java/com/usermgmt/AppTestRunner.java
```

Maven’s `target/` directory and the local `tomcat.8080/` directory are generated/runtime artifacts, not the source code to edit for ordinary feature work.

## 3. Application startup and configuration

### `pom.xml`

The Maven project file defines the project coordinates, Java source/target level (17), libraries, and build plugins. Notable dependencies include Jersey 2.40, embedded Tomcat 9.0.89, MySQL Connector/J, HikariCP, H2, Jackson, Servlet/JSP APIs, and SLF4J.

The project uses the `war` packaging type. The Exec Maven Plugin is configured to launch `com.usermgmt.EmbeddedServer`.

**Changing this file affects:** Java compatibility, dependency versions, build/package behavior, or the command used to launch the app.

### `EmbeddedServer.java`

Starts embedded Tomcat, selects a port from the `server.port` system property or `PORT` environment variable (default `8080`), serves `src/main/webapp`, and adds compiled classes from `target/classes`.

**Changing this file affects:** local embedded-server startup, port selection, or the webapp/class loading setup.

### `WEB-INF/web.xml`

This is the Servlet application configuration. It:

- Sets the HTTP session inactivity timeout to **30 minutes**.
- Registers `CharsetFilter` and `AuthenticationFilter`, each mapped to `/*`.
- Registers Jersey’s servlet and maps it to `/api/*`.
- Tells Jersey to scan `com.usermgmt.rest` and use Jackson’s provider.
- Defines the welcome page order (`index.jsp`, then `login.jsp`).

The `/*` pattern means all request paths **inside this web application** are presented to the filters. The filter code decides which paths are public and how to handle protected paths.

**Changing this file affects:** servlet/filter mappings, session timeout, REST URL prefix, or webapp defaults.

### Jersey configuration detail

`config/JerseyApplication.java` extends Jersey’s `ResourceConfig`, scans `com.usermgmt.rest`, and registers `JacksonFeature`. However, the current `web.xml` configures those same settings directly, and there is no visible reference wiring `JerseyApplication` into startup. For the current deployment, treat `web.xml` as the active Jersey configuration. If the project is changed to use `JerseyApplication`, avoid maintaining two competing configuration locations.

### Database settings

`src/main/resources/db.properties` contains the default JDBC driver, URL, username/password properties, pool settings, and fallback switch. Do not put real credentials in a document uploaded to an external site or commit them to source control. Use local configuration or environment variables for private values.

`DBConnectionFactory` reads the properties and can also use environment variables:

- `MYSQL_URL`
- `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`
- `USER_DB_URL`, `USER_DB_USER`, `USER_DB_PASSWORD`

It creates a HikariCP pool, checks the connection, and initializes tables and demo users. If MySQL connection fails and fallback is enabled, it starts H2 in MySQL compatibility mode. That fallback database is **in memory**, so its data is for development/demo use and is not durable across application restarts.

The project contains both a root `schema.sql` (manual MySQL setup, includes database creation and indexes/foreign key) and `src/main/resources/schema.sql` (schema resource). The current `DBConnectionFactory` creates its tables programmatically; it does not appear to execute either SQL file during initialization.

**Changing database configuration affects:** which database is used, credentials, pool behavior, or whether fallback is allowed. **Changing a schema affects:** table/column/index/constraint structure and usually requires corresponding DAO/model changes.

### Dockerfile

The Dockerfile uses a multi-stage build: Maven/Java 17 builds the WAR, then a Tomcat 9 image runs it as the root application. It exposes port 8080.

The source uses `javax.servlet` APIs and the Tomcat 9 / Jersey 2 generation. Do not assume it can be deployed unchanged to a Jakarta-based Tomcat 10+ runtime; that normally requires a Jakarta migration or a compatible deployment setup.

## 4. Source packages and files

### `filter/`

- **`CharsetFilter.java`** sets UTF-8 request and response character encoding before passing the request onward. Without it, text outside basic ASCII could be decoded or displayed incorrectly if no other layer sets the encoding.
- **`AuthenticationFilter.java`** runs for mapped requests. It identifies a small public-path allowlist (root, login/index pages, assets, login API, favicon), adds no-cache headers, then checks whether protected requests have an existing session containing `LOGGED_IN_USER`. Unauthenticated API calls receive 401 JSON; unauthenticated page requests are redirected to the login page. An authenticated visitor requesting `/` or `/login.jsp` is redirected to `/home.jsp`.

`getSession(false)` checks for an existing session without creating one. The filter checks for the session attribute; it does not reload the user from the database on each request.

**Changing these files affects:** request encoding, which paths are public, and the broad authentication gate/redirect behavior. Keep endpoint-level role checks in place as well; a logged-in session is not the same as permission to perform every operation.

### `rest/`

REST resource classes translate HTTP requests into service calls and build HTTP responses. Jersey finds these classes by their JAX-RS annotations.

- **`AuthResource.java`** handles:
  - `POST /api/auth/login`: validates request presence, delegates credential checking to the service, creates a session on success, stores user/session attributes, and returns JSON.
  - `POST /api/auth/logout`: invalidates the existing session.
  - `GET /api/auth/check`: verifies a session, reloads the user, and ends the session if the account is missing or inactive.
- **`UserResource.java`** handles user listing, detail lookup, creation, updates, and deletion. It translates service results/errors into HTTP responses and applies role-based rules: admins/managers can create users; managers cannot create or modify admins; regular users can update their own profile but cannot change their role/status; only admins can delete. The service additionally prevents self-deletion and deleting/demoting the final administrator according to its admin count check.
- **`StatsResource.java`** exposes `GET /api/stats` for dashboard counts and database-engine information.

Common annotations:

- `@Path` sets a resource or method URL.
- `@GET`, `@POST`, `@PUT`, and `@DELETE` select the HTTP method.
- `@PathParam` reads a value from the URL.
- `@QueryParam` reads a query-string value.
- `@Consumes` and `@Produces` declare JSON request/response media types.
- `@Context` injects Servlet request context, including session and client request information.

### `service/`

- **`UserService.java`** is the service contract: operations such as authentication, user lookup/listing, create/update/delete, password change, and dashboard stats.
- **`service/impl/UserServiceImpl.java`** implements business behavior. It validates user fields, checks username/email uniqueness, authenticates only active users, hashes passwords, applies default role/status, logs activities, paginates, and protects administrator/self-deletion invariants.

The service constructor can create the production DAO or accept a `UserDAO`, which helps tests supply an alternate implementation.

**Changing the interface affects:** all implementations and callers. **Changing the implementation affects:** business rules, validation, and outcomes regardless of which screen/API called the service.

### `dao/`

- **`UserDAO.java`** defines persistence operations without exposing SQL to the service layer.
- **`dao/impl/UserDAOImpl.java`** implements the contract using JDBC. It contains SQL for user lookup, search/filter/pagination, counts, insert/update/delete, password updates, uniqueness checks, audit logging, and dashboard aggregation. It binds data with `PreparedStatement` parameters and generally uses try-with-resources to close JDBC resources.
- **`DBConnectionFactory.java`** owns the HikariCP datasource lifecycle, database selection/fallback, schema initialization, and demo seeding.

**Changing DAO methods/SQL affects:** what is persisted or retrieved and how database queries behave. If a new user field is added, check the model, validation, API payload, SQL, schema, and UI together.

### `model/`

- **`User.java`** represents a user record and its fields (identity, contact details, role/status, timestamps, password input/hash/salt). `password` is write-only for JSON; `passwordHash` and `salt` are ignored during serialization to avoid exposing them in API responses.
- **`LoginRequest.java`** is the JSON input shape for username/password login.
- **`ApiResponse.java`** is a generic, consistent response wrapper with success flag, message, data, and timestamp.

**Changing a model affects:** Java data shape and potentially the JSON API contract. For a persisted field, update the schema, DAO mappings/SQL, validation, and UI/API payloads as appropriate.

### `util/`

- **`PasswordUtil.java`** generates a random 16-byte salt, hashes using PBKDF2 with HMAC-SHA256 (65,536 iterations and 256-bit output), and verifies with `MessageDigest.isEqual`.

It stores only the derived hash and salt, not the original password. Changing password algorithms or parameters needs a migration/compatibility plan for existing password hashes.

### Web UI: `src/main/webapp/`

- **`login.jsp`** is the login form and loads jQuery, Bootstrap, and `assets/js/auth.js`.
- **`home.jsp`** is the dashboard, with user/stat areas, modals, and `assets/js/app.js`. It has a server-side session guard as well as the filter/API checks.
- **`index.jsp`** redirects to the dashboard if a session is present, otherwise to the login page.
- **`assets/js/auth.js`** uses jQuery event handling and `$.ajax()` to submit JSON to the login API, show errors/success, and redirect after successful login. It also handles password visibility and demo credential autofill.
- **`assets/js/app.js`** checks the session, fetches statistics/users, performs CRUD operations, filters and paginates, manages dashboard UI, and logs out through AJAX calls. This updates portions of the page without a full page reload.
- **`assets/css/custom.css`** contains application-specific styling.

The UI may hide or disable actions according to role, but front-end behavior is not a security boundary. The server-side REST resources must enforce permissions because users can call APIs directly.

### Tests

`src/test/java/com/usermgmt/AppTestRunner.java` is a standalone test runner rather than a conventional JUnit test class. It exercises password hashing/verification, database startup/schema initialization, seeded admin lookup, search/count, authentication, CRUD and self-delete prevention, and dashboard statistics.

## 5. Key request flows

### Login

```text
login.jsp form
  → auth.js intercepts submit and sends JSON via AJAX
  → POST /api/auth/login
  → AuthenticationFilter permits the public login path
  → AuthResource calls UserService.authenticate(...)
  → UserServiceImpl loads user through UserDAO and verifies PBKDF2 hash
  → AuthResource stores LOGGED_IN_USER / USER_ID / USERNAME / USER_ROLE in HttpSession
  → JSON response returns; browser navigates to home.jsp
```

`request.changeSessionId()` is called after successful authentication as session-fixation protection.

### Protected dashboard/API request

```text
browser sends request with JSESSIONID cookie
  → filters process it
  → AuthenticationFilter looks for existing session + LOGGED_IN_USER
  → if absent: API gets 401 JSON, page request gets login redirect
  → if present: request reaches JSP or Jersey resource
  → resource applies action-specific role rules and calls service
  → service calls DAO; response returns as JSON
```

### Session lifetime

`<session-timeout>30</session-timeout>` in `web.xml` means 30 minutes of inactivity. Logout invalidates a session immediately. It is not a fixed 30-minute limit measured from login.

## 6. API reference

The Jersey servlet is mounted under `/api`. All resources use JSON.

| Method | Endpoint | Purpose / access notes |
|---|---|---|
| `POST` | `/api/auth/login` | Public login; successful login starts a session |
| `POST` | `/api/auth/logout` | Invalidates an authenticated session |
| `GET` | `/api/auth/check` | Checks session and confirms account is still active |
| `GET` | `/api/users?search=&role=&status=&page=&pageSize=` | Filtered/paginated user list; requires session |
| `GET` | `/api/users/{id}` | Fetch a user by ID; requires session |
| `POST` | `/api/users` | Create user; admin/manager, with manager restrictions |
| `PUT` | `/api/users/{id}` | Update user; admin/manager/self rules |
| `DELETE` | `/api/users/{id}` | Admin only; self/last-admin safeguards |
| `GET` | `/api/stats` | Dashboard statistics; requires session |

Responses are commonly wrapped in `ApiResponse<T>`; typical fields are `success`, `message`, `data`, and `timestamp`. HTTP status codes distinguish outcomes such as 201 created, 400 invalid input, 401 unauthenticated, 403 forbidden, 404 not found, and 409 conflict.

## 7. Database and data model

The main tables are:

- `users`: profile and account fields, role/status, password hash and salt, timestamps.
- `activity_logs`: user ID (nullable), action, details, IP address, and timestamp.

Roles are `ADMIN`, `MANAGER`, and `USER`; account statuses include `ACTIVE`, `INACTIVE`, and `SUSPENDED`.

MySQL schema setup in root `schema.sql` defines unique username/email constraints, indexes, and a foreign key from activity logs to users. The factory also creates the tables programmatically for startup and seeds demo accounts when the `users` table is empty.

Search matches username, full name, email, or department; role and status filters are optional; results are ordered by ID and paginated with limit/offset.

## 8. Security and design points to explain

- **Session authentication:** the server stores identity/role data in `HttpSession`; the browser sends the session cookie on later requests.
- **Authentication vs authorization:** the filter checks whether there is a logged-in session. The REST resources check whether that logged-in role is allowed to perform a particular operation.
- **Password storage:** PBKDF2-HMAC-SHA256 with a unique cryptographically random salt; raw passwords are not stored.
- **SQL injection mitigation:** values are bound as `PreparedStatement` parameters rather than inserted into SQL text.
- **Sensitive JSON fields:** `User` annotations prevent password hash and salt output and make the password input write-only.
- **Audit trail:** service operations record relevant activity and client IP through the DAO.
- **In-memory fallback limitation:** H2 is convenient for a demo if MySQL is unavailable, but its in-memory data is not persistent.

## 9. What to change for common requirements

| Requirement | Likely files/layers | Why |
|---|---|---|
| Add a user profile field | `User`, request/UI, service validation, `UserDAO`/`UserDAOImpl`, both schema definitions | The field must travel from form/API through Java into the database and back |
| Change a validation rule | `UserServiceImpl` (and possibly UI hints) | Validation is business behavior and should be enforced on the server |
| Add an API endpoint | REST resource; service/DAO if it needs business or database work | Resource is the HTTP boundary; lower layers own logic/data |
| Change role permissions | `UserResource` and, if shared business policy, service layer | UI-only permission changes can be bypassed |
| Change session timeout | `WEB-INF/web.xml` | Servlet container session configuration |
| Change allowed unauthenticated paths | `AuthenticationFilter` | Its public-resource allowlist drives filter behavior |
| Change database connection/pool | `db.properties` / environment and `DBConnectionFactory` | These select the datasource and pool behavior |
| Change search or ordering | DAO SQL plus service/resource parameters and UI controls | The query executes in the DAO; the other layers pass filters |
| Change dashboard appearance | `home.jsp`, `app.js`, and/or `custom.css` | These render and update the browser interface |
| Change login form behavior | `login.jsp` and `auth.js`; server rules remain in service/resource | Browser interactions are separate from credential verification |

## 10. Running and testing

From the project root:

```bash
mvn compile exec:java
```

This launches embedded Tomcat on port 8080 by default. The login page is normally at `http://localhost:8080/login.jsp`.

To build a WAR:

```bash
mvn clean package
```

The build output is `target/usermanagement.war`.

The standalone test runner is documented with this Maven command:

```bash
mvn compile org.codehaus.mojo:exec-maven-plugin:3.2.0:java -Dexec.mainClass=com.usermgmt.AppTestRunner -Dexec.classpathScope=test
```

Tests that connect to the database may initialize schema and demo data. Use a development database when running them.

## 11. Interview-ready explanation

### Short project summary

“This is a Java user-management web application built with JSP and jQuery on the front end and Jersey REST APIs on the server. It follows a layered design: REST resources handle HTTP, the service layer enforces business rules, and DAO classes isolate JDBC/database operations. It uses session-based authentication, role checks, PBKDF2 password hashing, and MySQL through a HikariCP connection pool, with an in-memory H2 fallback for demos.”

### Why separate service and DAO?

“The service layer keeps validation and business rules out of HTTP and SQL code. The DAO encapsulates persistence. That makes responsibilities clearer and makes it possible to test or replace parts of the application independently.”

### What happens when someone logs in?

“The browser sends JSON to the login REST endpoint. The resource delegates credential verification to the service. The service retrieves the user and verifies the password hash, then the resource creates an HTTP session and stores the authenticated user details. The browser’s session cookie identifies that session on subsequent requests.”

### How is a protected request handled?

“The authentication filter runs before the matched page/API handler. It allows the configured public paths; otherwise it looks for an existing session with `LOGGED_IN_USER`. It returns 401 JSON for unauthenticated API calls or redirects page requests to login. A resource still applies role-specific authorization after the filter.”

### What would you change for a new feature?

“I would identify the full data/request path first. For a new persisted field, I would update the model and input form/API, validate it in the service, persist/map it in the DAO, update the schema, and then test create/read/update behavior. I would keep business rules in the service and access decisions on the server rather than relying only on UI controls.”

## 12. Implementation notes to be candid about

These are useful to know rather than repeat inaccurate assumptions:

1. `JerseyApplication` exists, but the current Jersey servlet setup is configured directly in `web.xml`; the Java configuration class appears unwired.
2. There are two schema files. The factory initializes tables in Java rather than loading these files automatically.
3. H2 fallback is an in-memory database; it does not provide persistent storage.
4. `AppTestRunner` is a standalone test harness with assertions, not a typical JUnit test suite.
5. The login page’s “Session Cookie” checkbox is presentational in the current code; the shown login script does not read it to implement a remember-me option.
6. The web source uses the `javax.*` Servlet/JAX-RS namespace and Tomcat 9-era dependencies. A Jakarta-only container is not a drop-in target without migration.
7. The UI’s role-based hiding/disabling improves usability, but server-side checks in the API are what enforce authorization.

For database credentials, demo passwords, or any other private values, consult local project configuration rather than copying secrets into an interview-context upload.
