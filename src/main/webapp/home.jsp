<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.usermgmt.model.User"%>
<%
    // Server-side session verification guard
    User sessionUser = (User) session.getAttribute("LOGGED_IN_USER");
    if (sessionUser == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp?sessionExpired=true");
        return;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="User Management Dashboard with CRUD, Grid, and Session Security">
    <title>Dashboard &mdash; User Management System</title>

    <!-- Bootstrap 5.3 CSS -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <!-- Bootstrap Icons -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" rel="stylesheet">
    <!-- Custom Modern Styling -->
    <link href="assets/css/custom.css" rel="stylesheet">
</head>
<body>

    <!-- Sticky Navigation Bar -->
    <nav class="navbar navbar-expand-lg navbar-custom">
        <div class="container-fluid px-lg-4">
            <!-- Brand -->
            <a class="navbar-brand" href="home.jsp">
                <span class="logo-badge"><i class="bi bi-people-fill"></i></span>
                <div>
                    <div class="lh-1 brand-font">UserSphere</div>
                    <span class="small text-muted fw-normal" style="font-size: 0.72rem;">Enterprise Directory</span>
                </div>
            </a>

            <!-- Right Controls: DB status, user profile & logout -->
            <div class="d-flex align-items-center gap-3 ms-auto">
                <!-- Database Engine Indicator -->
                <span id="dbEngineBadge" class="badge bg-success-subtle text-success border border-success-subtle d-none d-md-inline-flex align-items-center py-2 px-3 rounded-pill small">
                    <i class="bi bi-database-check me-1"></i>MySQL Connected
                </span>

                <!-- Current User Profile Pill -->
                <div class="user-profile-badge">
                    <div class="avatar-circle" id="currentUserAvatar">
                        <%= sessionUser.getFullName() != null && sessionUser.getFullName().length() >= 2 ? sessionUser.getFullName().substring(0, 2).toUpperCase() : "US" %>
                    </div>
                    <div class="d-none d-sm-block text-start lh-sm pe-1">
                        <div class="fw-bold small" id="currentUserName"><%= sessionUser.getFullName() %></div>
                        <span class="badge bg-primary-subtle text-primary" style="font-size: 0.65rem;" id="currentUserRole"><%= sessionUser.getRole() %></span>
                    </div>
                </div>

                <!-- Logout Button -->
                <button type="button" id="btnLogout" class="btn btn-outline-danger btn-sm rounded-pill px-3 py-1 d-flex align-items-center gap-1 shadow-sm" title="End Current Session">
                    <i class="bi bi-box-arrow-right"></i>
                    <span class="d-none d-sm-inline">Sign Out</span>
                </button>
            </div>
        </div>
    </nav>

    <!-- Main Container -->
    <main class="container-fluid px-lg-5 py-4">

        <!-- Top Welcome & KPIs Section -->
        <div class="row g-3 mb-4">
            <!-- Total Users -->
            <div class="col-12 col-sm-6 col-xl-3">
                <div class="stat-card primary">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <span class="text-secondary small fw-semibold text-uppercase">Total Users</span>
                            <div class="stat-value text-dark" id="statTotalUsers">0</div>
                        </div>
                        <div class="stat-icon"><i class="bi bi-people"></i></div>
                    </div>
                    <div class="mt-2 small text-muted"><i class="bi bi-info-circle me-1"></i>Registered directory accounts</div>
                </div>
            </div>

            <!-- Active Users -->
            <div class="col-12 col-sm-6 col-xl-3">
                <div class="stat-card success">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <span class="text-secondary small fw-semibold text-uppercase">Active Accounts</span>
                            <div class="stat-value text-dark" id="statActiveUsers">0</div>
                        </div>
                        <div class="stat-icon"><i class="bi bi-check2-circle"></i></div>
                    </div>
                    <div class="mt-2 small text-muted"><i class="bi bi-shield-check me-1 text-success"></i>Operational & authorized</div>
                </div>
            </div>

            <!-- Inactive Users -->
            <div class="col-12 col-sm-6 col-xl-3">
                <div class="stat-card warning">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <span class="text-secondary small fw-semibold text-uppercase">Inactive / Suspended</span>
                            <div class="stat-value text-dark" id="statInactiveUsers">0</div>
                        </div>
                        <div class="stat-icon"><i class="bi bi-pause-circle"></i></div>
                    </div>
                    <div class="mt-2 small text-muted"><i class="bi bi-lock me-1 text-warning"></i>Login access restricted</div>
                </div>
            </div>

            <!-- Administrators -->
            <div class="col-12 col-sm-6 col-xl-3">
                <div class="stat-card info">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <span class="text-secondary small fw-semibold text-uppercase">Admins & Managers</span>
                            <div class="stat-value text-dark" id="statAdminUsers">0</div>
                        </div>
                        <div class="stat-icon"><i class="bi bi-shield-shaded"></i></div>
                    </div>
                    <div class="mt-2 small text-muted"><i class="bi bi-key me-1 text-primary"></i>Elevated governance roles</div>
                </div>
            </div>
        </div>

        <!-- Main Card: User Management Grid -->
        <div class="main-card">
            <!-- Header Controls -->
            <div class="main-card-header">
                <div>
                    <h5 class="fw-bold mb-0 brand-font text-dark">Directory Users</h5>
                    <small class="text-muted" id="totalUsersCountDisplay">Loading user records...</small>
                </div>

                <div class="d-flex flex-wrap align-items-center gap-2">
                    <!-- Search Input -->
                    <div class="input-group input-group-sm" style="max-width: 260px;">
                        <span class="input-group-text bg-white border-end-0 text-muted"><i class="bi bi-search"></i></span>
                        <input type="text" class="form-control border-start-0 ps-0" id="searchInput" placeholder="Search by name, email, user...">
                    </div>

                    <!-- Role Filter -->
                    <select class="form-select form-select-sm" id="roleFilter" style="width: auto;">
                        <option value="ALL">All Roles</option>
                        <option value="ADMIN">Admin</option>
                        <option value="MANAGER">Manager</option>
                        <option value="USER">User</option>
                    </select>

                    <!-- Status Filter -->
                    <select class="form-select form-select-sm" id="statusFilter" style="width: auto;">
                        <option value="ALL">All Statuses</option>
                        <option value="ACTIVE">Active</option>
                        <option value="INACTIVE">Inactive</option>
                        <option value="SUSPENDED">Suspended</option>
                    </select>

                    <!-- Refresh Button -->
                    <button type="button" class="btn btn-outline-secondary btn-sm rounded-3" id="btnRefresh" title="Refresh Table">
                        <i class="bi bi-arrow-clockwise"></i>
                    </button>

                    <!-- Add User Button -->
                    <button type="button" class="btn btn-primary btn-sm rounded-3 d-flex align-items-center gap-1 shadow-sm" id="btnAddUser">
                        <i class="bi bi-person-plus-fill"></i>
                        <span>Add User</span>
                    </button>
                </div>
            </div>

            <!-- Table Grid -->
            <div class="table-responsive">
                <table class="table table-custom table-hover align-middle">
                    <thead>
                        <tr>
                            <th scope="col" style="width: 105px;"># / ID</th>
                            <th scope="col">User</th>
                            <th scope="col">Email Address</th>
                            <th scope="col">Department</th>
                            <th scope="col">Role</th>
                            <th scope="col">Status</th>
                            <th scope="col" class="text-end" style="width: 140px;">Actions</th>
                        </tr>
                    </thead>
                    <tbody id="usersTableBody">
                        <!-- Populated dynamically via Ajax -->
                    </tbody>
                </table>
            </div>

            <!-- Card Footer: Pagination & Page Size -->
            <div class="p-3 border-top bg-light d-flex flex-wrap justify-content-between align-items-center gap-3">
                <div class="d-flex align-items-center gap-2">
                    <span class="small text-muted">Rows per page:</span>
                    <select class="form-select form-select-sm" id="pageSizeSelect" style="width: 70px;">
                        <option value="5">5</option>
                        <option value="10" selected>10</option>
                        <option value="25">25</option>
                        <option value="50">50</option>
                    </select>
                </div>

                <!-- Pagination Nav -->
                <nav aria-label="Users pagination">
                    <ul class="pagination pagination-sm mb-0" id="paginationContainer">
                        <!-- Dynamic page links -->
                    </ul>
                </nav>
            </div>
        </div>

    </main>

    <!-- ====================================================================
         MODALS
         ==================================================================== -->

    <!-- 1. Add User Modal -->
    <div class="modal fade" id="addUserModal" tabindex="-1" aria-labelledby="addUserModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title fw-bold" id="addUserModalLabel"><i class="bi bi-person-plus me-2 text-primary"></i>Add New User</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body">
                    <div id="addUserAlert" class="alert d-none py-2 px-3 small"></div>
                    <form id="addUserForm">
                        <div class="row g-3">
                            <div class="col-md-6">
                                <label for="addUsername" class="form-label small fw-semibold">Username *</label>
                                <input type="text" class="form-control" id="addUsername" required placeholder="e.g. alex_stone">
                            </div>
                            <div class="col-md-6">
                                <label for="addFullName" class="form-label small fw-semibold">Full Name *</label>
                                <input type="text" class="form-control" id="addFullName" required placeholder="e.g. Alex Stone">
                            </div>
                            <div class="col-12">
                                <label for="addEmail" class="form-label small fw-semibold">Email Address *</label>
                                <input type="email" class="form-control" id="addEmail" required placeholder="alex@company.com">
                            </div>
                            <div class="col-12">
                                <label for="addPassword" class="form-label small fw-semibold">Temporary Password *</label>
                                <input type="password" class="form-control" id="addPassword" required placeholder="Minimum 6 characters">
                            </div>
                            <div class="col-md-6">
                                <label for="addRole" class="form-label small fw-semibold">Role *</label>
                                <select class="form-select" id="addRole">
                                    <option value="USER" selected>User</option>
                                    <option value="MANAGER">Manager</option>
                                    <option value="ADMIN">Administrator</option>
                                </select>
                            </div>
                            <div class="col-md-6">
                                <label for="addStatus" class="form-label small fw-semibold">Status *</label>
                                <select class="form-select" id="addStatus">
                                    <option value="ACTIVE" selected>Active</option>
                                    <option value="INACTIVE">Inactive</option>
                                </select>
                            </div>
                            <div class="col-md-6">
                                <label for="addPhone" class="form-label small fw-semibold">Phone</label>
                                <input type="text" class="form-control" id="addPhone" placeholder="+1-555-0100">
                            </div>
                            <div class="col-md-6">
                                <label for="addDepartment" class="form-label small fw-semibold">Department</label>
                                <input type="text" class="form-control" id="addDepartment" placeholder="Engineering">
                            </div>
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-light" data-bs-dismiss="modal">Cancel</button>
                    <button type="button" class="btn btn-primary" id="saveAddUserBtn">
                        <i class="bi bi-check2-circle me-1"></i>Create User
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- 2. Edit User Modal -->
    <div class="modal fade" id="editUserModal" tabindex="-1" aria-labelledby="editUserModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title fw-bold" id="editUserModalLabel"><i class="bi bi-pencil-square me-2 text-primary"></i>Update User Record</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body">
                    <div id="editUserAlert" class="alert d-none py-2 px-3 small"></div>
                    <form id="editUserForm">
                        <input type="hidden" id="editUserId">
                        <div class="row g-3">
                            <div class="col-md-6">
                                <label for="editUsername" class="form-label small fw-semibold">Username</label>
                                <input type="text" class="form-control bg-light" id="editUsername" readonly disabled>
                            </div>
                            <div class="col-md-6">
                                <label for="editFullName" class="form-label small fw-semibold">Full Name *</label>
                                <input type="text" class="form-control" id="editFullName" required>
                            </div>
                            <div class="col-12">
                                <label for="editEmail" class="form-label small fw-semibold">Email Address *</label>
                                <input type="email" class="form-control" id="editEmail" required>
                            </div>
                            <div class="col-md-6">
                                <label for="editRole" class="form-label small fw-semibold">Role</label>
                                <select class="form-select" id="editRole">
                                    <option value="USER">User</option>
                                    <option value="MANAGER">Manager</option>
                                    <option value="ADMIN">Administrator</option>
                                </select>
                            </div>
                            <div class="col-md-6">
                                <label for="editStatus" class="form-label small fw-semibold">Status</label>
                                <select class="form-select" id="editStatus">
                                    <option value="ACTIVE">Active</option>
                                    <option value="INACTIVE">Inactive</option>
                                    <option value="SUSPENDED">Suspended</option>
                                </select>
                            </div>
                            <div class="col-md-6">
                                <label for="editPhone" class="form-label small fw-semibold">Phone</label>
                                <input type="text" class="form-control" id="editPhone">
                            </div>
                            <div class="col-md-6">
                                <label for="editDepartment" class="form-label small fw-semibold">Department</label>
                                <input type="text" class="form-control" id="editDepartment">
                            </div>
                            <div class="col-12">
                                <label for="editPassword" class="form-label small fw-semibold">Reset Password (Optional)</label>
                                <input type="password" class="form-control" id="editPassword" placeholder="Leave blank to keep current password">
                            </div>
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-light" data-bs-dismiss="modal">Cancel</button>
                    <button type="button" class="btn btn-primary" id="saveEditUserBtn">
                        <i class="bi bi-save me-1"></i>Save Changes
                    </button>
                </div>
            </div>
        </div>
    </div>

    <!-- 3. View User Profile Modal -->
    <div class="modal fade" id="viewUserModal" tabindex="-1" aria-labelledby="viewUserModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <h5 class="modal-title fw-bold" id="viewUserModalLabel"><i class="bi bi-person-badge me-2 text-primary"></i>User Profile</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body text-center pt-4">
                    <div class="avatar-circle mx-auto mb-3" id="viewAvatar" style="width: 64px; height: 64px; font-size: 1.5rem;">US</div>
                    <h4 class="fw-bold mb-1" id="viewFullName">User Full Name</h4>
                    <p class="text-muted small mb-3" id="viewUsername">@username</p>

                    <div class="text-start bg-light p-3 rounded-3 small">
                        <div class="row g-2">
                            <div class="col-4 text-secondary">Email:</div>
                            <div class="col-8 fw-semibold" id="viewEmail">—</div>

                            <div class="col-4 text-secondary">Department:</div>
                            <div class="col-8 fw-semibold" id="viewDepartment">—</div>

                            <div class="col-4 text-secondary">Phone:</div>
                            <div class="col-8 fw-semibold" id="viewPhone">—</div>

                            <div class="col-4 text-secondary">Role:</div>
                            <div class="col-8 fw-semibold" id="viewRole">—</div>

                            <div class="col-4 text-secondary">Status:</div>
                            <div class="col-8 fw-semibold" id="viewStatus">—</div>

                            <div class="col-4 text-secondary">Joined:</div>
                            <div class="col-8 fw-semibold" id="viewCreatedAt">—</div>

                            <div class="col-4 text-secondary">Updated:</div>
                            <div class="col-8 fw-semibold" id="viewUpdatedAt">—</div>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary btn-sm" data-bs-dismiss="modal">Close</button>
                </div>
            </div>
        </div>
    </div>

    <!-- 4. Delete Confirmation Modal -->
    <div class="modal fade" id="deleteUserModal" tabindex="-1" aria-labelledby="deleteUserModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered modal-sm">
            <div class="modal-content">
                <div class="modal-header border-0 pb-0">
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <div class="modal-body text-center pt-0">
                    <div class="text-danger mb-3">
                        <i class="bi bi-exclamation-triangle-fill" style="font-size: 3rem;"></i>
                    </div>
                    <h5 class="fw-bold mb-2">Delete User?</h5>
                    <p class="text-muted small mb-3">Are you sure you want to permanently delete user <strong id="deleteTargetName">@user</strong>? This action cannot be undone.</p>
                    <div id="deleteAlert" class="alert d-none py-2 px-3 small text-start"></div>
                    <input type="hidden" id="deleteUserId">
                </div>
                <div class="modal-footer border-0 justify-content-center pt-0 pb-3">
                    <button type="button" class="btn btn-light btn-sm" data-bs-dismiss="modal">Cancel</button>
                    <button type="button" class="btn btn-danger btn-sm px-3" id="confirmDeleteBtn">Delete Permanently</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Toast Notification Container -->
    <div class="toast-container" id="toastContainer"></div>

    <!-- jQuery 3.7.1 -->
    <script src="https://code.jquery.com/jquery-3.7.1.min.js"></script>
    <!-- Bootstrap 5.3 JS Bundle -->
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
    <!-- Application Logic -->
    <script src="assets/js/app.js"></script>
</body>
</html>
