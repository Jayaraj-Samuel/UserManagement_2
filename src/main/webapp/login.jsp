<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Secure Session-based Login for Enterprise User Management System">
    <title>Sign In &mdash; User Management System</title>

    <!-- Bootstrap 5.3 CSS -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <!-- Bootstrap Icons -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" rel="stylesheet">
    <!-- Custom Modern Styling -->
    <link href="assets/css/custom.css" rel="stylesheet">
</head>
<body class="login-body">

    <div class="login-card">
        <!-- Header -->
        <div class="login-header">
            <div class="brand-icon">
                <i class="bi bi-shield-lock-fill"></i>
            </div>
            <h3 class="fw-bold mb-1 brand-font">Enterprise Portal</h3>
            <p class="mb-0 text-white-50 small">Session-Based Secure Authentication</p>
        </div>

        <!-- Body -->
        <div class="login-body-content">
            <!-- Alert message container -->
            <div id="loginAlert" class="alert d-none py-2 px-3 small" role="alert"></div>

            <form id="loginForm" novalidate>
                <!-- Username -->
                <div class="mb-3">
                    <label for="username" class="form-label small fw-semibold text-secondary">Username</label>
                    <div class="input-group">
                        <span class="input-group-text bg-light text-muted border-end-0"><i class="bi bi-person"></i></span>
                        <input type="text" class="form-control border-start-0 ps-0" id="username" name="username" placeholder="Enter your username" required autofocus autocomplete="username">
                    </div>
                </div>

                <!-- Password -->
                <div class="mb-3">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                        <label for="password" class="form-label small fw-semibold text-secondary mb-0">Password</label>
                    </div>
                    <div class="input-group">
                        <span class="input-group-text bg-light text-muted border-end-0"><i class="bi bi-key"></i></span>
                        <input type="password" class="form-control border-start-0 border-end-0 ps-0" id="password" name="password" placeholder="Enter your password" required autocomplete="current-password">
                        <button class="btn btn-outline-secondary border-start-0" type="button" id="togglePassword" title="Show or hide password">
                            <i class="bi bi-eye"></i>
                        </button>
                    </div>
                </div>

                <!-- Remember Me / Info -->
                <div class="d-flex justify-content-between align-items-center mb-4">
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" id="rememberMe" checked>
                        <label class="form-check-label small text-muted" for="rememberMe">Session Cookie (HttpOnly)</label>
                    </div>
                    <span class="badge bg-light text-secondary border">SHA-256 / PBKDF2</span>
                </div>

                <!-- Submit Button -->
                <div class="d-grid mb-3">
                    <button type="submit" id="btnLogin" class="btn btn-primary py-2 fs-6">
                        <i class="bi bi-box-arrow-in-right me-2"></i>Sign In
                    </button>
                </div>
            </form>

            <!-- Quick Demo Credentials for Reviewers / Interviewers -->
            <div class="mt-4 pt-3 border-top text-center">
                <span class="small text-muted d-block mb-2 fw-semibold">Quick-fill Demo Credentials:</span>
                <div class="d-flex justify-content-center gap-2 flex-wrap">
                    <span class="demo-pill" data-user="admin" data-pass="Admin@123" title="Full Administrator Privileges">
                        <i class="bi bi-shield-check me-1 text-danger"></i><strong>Admin</strong>
                    </span>
                    <span class="demo-pill" data-user="jane_manager" data-pass="Manager@123" title="Manager Privileges">
                        <i class="bi bi-award me-1 text-warning"></i><strong>Manager</strong>
                    </span>
                    <span class="demo-pill" data-user="john_doe" data-pass="User@123" title="Standard User Privileges">
                        <i class="bi bi-person me-1 text-primary"></i><strong>User</strong>
                    </span>
                </div>
            </div>

            <!-- Footer note -->
            <div class="text-center mt-3">
                <small class="text-muted" style="font-size: 0.75rem;">
                    <i class="bi bi-database me-1"></i>MySQL Backend &bull; Jersey REST &bull; JSP &bull; Ajax
                </small>
            </div>
        </div>
    </div>

    <!-- jQuery 3.7.1 -->
    <script src="https://code.jquery.com/jquery-3.7.1.min.js"></script>
    <!-- Bootstrap 5.3 JS Bundle -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
    <!-- Authentication Logic -->
    <script src="assets/js/auth.js"></script>
</body>
</html>
