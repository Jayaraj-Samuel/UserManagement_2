/**
 * User Management Dashboard Application
 * jQuery, Ajax, REST API Integrations, and Grid Manipulation
 */

let currentUser = null;
let currentPage = 1;
let currentPageSize = 10;
let searchTimer = null;

$(document).ready(function () {
    // 1. Verify Active Session & User Profile
    checkCurrentSession();

    // 2. Event Listeners for Filters & Search
    $("#searchInput").on("input", function () {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(function () {
            currentPage = 1;
            loadUsersGrid();
        }, 350);
    });

    $("#roleFilter, #statusFilter, #pageSizeSelect").on("change", function () {
        currentPage = 1;
        currentPageSize = parseInt($("#pageSizeSelect").val()) || 10;
        loadUsersGrid();
    });

    $("#btnRefresh").on("click", function () {
        $(this).find("i").addClass("spin");
        loadUsersGrid();
        loadDashboardStats();
        setTimeout(() => $(this).find("i").removeClass("spin"), 800);
    });

    // 3. User Modal Forms
    $("#btnAddUser").on("click", openAddUserModal);
    $("#saveAddUserBtn").on("click", submitAddUser);
    $("#saveEditUserBtn").on("click", submitEditUser);
    $("#confirmDeleteBtn").on("click", submitDeleteUser);

    // 4. Logout Action
    $("#btnLogout").on("click", function (e) {
        e.preventDefault();
        performLogout();
    });
});

/**
 * Validates and retrieves current session details from server
 */
function checkCurrentSession() {
    $.ajax({
        url: "api/auth/check",
        type: "GET",
        dataType: "json",
        success: function (res) {
            if (res && res.success && res.data) {
                currentUser = res.data;
                renderCurrentUserProfile(currentUser);
                loadDashboardStats();
                loadUsersGrid();
            } else {
                redirectToLogin();
            }
        },
        error: function () {
            redirectToLogin();
        }
    });
}

function redirectToLogin() {
    window.location.href = "login.jsp?sessionExpired=true";
}

function renderCurrentUserProfile(user) {
    $("#currentUserName").text(user.fullName || user.username);
    $("#currentUserRole").text(user.role);
    $("#currentUserAvatar").text((user.fullName || user.username).substring(0, 2).toUpperCase());

    // Role-based UI customizations
    if (user.role === "USER") {
        $("#btnAddUser").hide(); // Regular users cannot add users
    } else {
        $("#btnAddUser").show();
    }
}

/**
 * Loads KPI dashboard statistics
 */
function loadDashboardStats() {
    $.ajax({
        url: "api/stats",
        type: "GET",
        dataType: "json",
        success: function (res) {
            if (res && res.success && res.data) {
                const s = res.data;
                $("#statTotalUsers").text(s.totalUsers || 0);
                $("#statActiveUsers").text(s.activeUsers || 0);
                $("#statInactiveUsers").text(s.inactiveUsers || 0);
                $("#statAdminUsers").text(s.adminUsers || 0);

                if (s.databaseEngine) {
                    $("#dbEngineBadge").html('<i class="bi bi-database-check me-1"></i>' + s.databaseEngine);
                    if (s.isFallback) {
                        $("#dbEngineBadge").removeClass("bg-success").addClass("bg-warning text-dark")
                            .attr("title", "MySQL was not active, so embedded H2 MySQL-mode engaged automatically.");
                    }
                }
            }
        }
    });
}

/**
 * Loads the users table grid with pagination and filter criteria
 */
function loadUsersGrid() {
    const search = $("#searchInput").val().trim();
    const role = $("#roleFilter").val();
    const status = $("#statusFilter").val();
    const tbody = $("#usersTableBody");

    tbody.html('<tr><td colspan="7" class="text-center py-4 text-muted"><div class="spinner-border spinner-border-sm me-2 text-primary"></div>Loading users data...</td></tr>');

    $.ajax({
        url: "api/users",
        type: "GET",
        data: {
            search: search,
            role: role,
            status: status,
            page: currentPage,
            pageSize: currentPageSize
        },
        dataType: "json",
        success: function (res) {
            if (res && res.success && res.data) {
                renderUsersTable(res.data);
            } else {
                tbody.html('<tr><td colspan="7" class="text-center py-4 text-danger">Failed to load users data.</td></tr>');
            }
        },
        error: function (xhr) {
            if (xhr.status === 401) {
                redirectToLogin();
            } else {
                tbody.html('<tr><td colspan="7" class="text-center py-4 text-danger">Error connecting to server.</td></tr>');
            }
        }
    });
}

function renderUsersTable(data) {
    const tbody = $("#usersTableBody");
    tbody.empty();

    const users = data.users || [];
    const totalCount = data.totalCount || 0;
    const totalPages = data.totalPages || 1;
    currentPage = data.currentPage || 1;

    $("#totalUsersCountDisplay").text(totalCount + " total users found");

    if (users.length === 0) {
        tbody.html('<tr><td colspan="7" class="text-center py-5 text-muted"><i class="bi bi-people fs-2 d-block mb-2 text-secondary"></i>No users found matching current filters.</td></tr>');
        renderPagination(1, 1);
        return;
    }

    users.forEach(function (u, index) {
        const rowNum = (currentPage - 1) * currentPageSize + index + 1;
        const initials = (u.fullName || u.username).substring(0, 2).toUpperCase();

        // Role badge
        let roleBadge = '<span class="badge badge-role-user">User</span>';
        if (u.role === "ADMIN") {
            roleBadge = '<span class="badge badge-role-admin"><i class="bi bi-shield-lock-fill me-1"></i>Admin</span>';
        } else if (u.role === "MANAGER") {
            roleBadge = '<span class="badge badge-role-manager"><i class="bi bi-award-fill me-1"></i>Manager</span>';
        }

        // Status badge
        let statusBadge = '<span class="badge badge-status-active">Active</span>';
        if (u.status !== "ACTIVE") {
            statusBadge = '<span class="badge badge-status-inactive">' + (u.status || 'Inactive') + '</span>';
        }

        // Action buttons based on permissions
        const canEdit = (currentUser.role === "ADMIN" || currentUser.role === "MANAGER" || currentUser.id === u.id);
        const canDelete = (currentUser.role === "ADMIN" && currentUser.id !== u.id);

        let actionsHtml = `
            <div class="d-flex justify-content-end gap-1">
                <button type="button" class="btn btn-sm btn-outline-secondary btn-action" title="View Details" onclick="viewUser(${u.id})">
                    <i class="bi bi-eye"></i>
                </button>
        `;

        if (canEdit) {
            actionsHtml += `
                <button type="button" class="btn btn-sm btn-outline-primary btn-action" title="Edit User" onclick="editUser(${u.id})">
                    <i class="bi bi-pencil-square"></i>
                </button>
            `;
        }

        if (canDelete) {
            actionsHtml += `
                <button type="button" class="btn btn-sm btn-outline-danger btn-action" title="Delete User" onclick="confirmDeleteUser(${u.id}, '${escapeHtml(u.username)}')">
                    <i class="bi bi-trash"></i>
                </button>
            `;
        }

        actionsHtml += `</div>`;

        const row = `
            <tr>
                <td class="fw-semibold text-secondary text-nowrap">
                    <span class="badge bg-secondary-subtle text-secondary fw-bold me-1">#${rowNum}</span>
                    <small class="text-muted" title="Database User ID">(ID ${u.id})</small>
                </td>
                <td>
                    <div class="user-cell">
                        <div class="user-avatar-sm">${initials}</div>
                        <div>
                            <div class="fw-bold">${escapeHtml(u.fullName)}</div>
                            <div class="small text-muted">@${escapeHtml(u.username)}</div>
                        </div>
                    </div>
                </td>
                <td>${escapeHtml(u.email)}</td>
                <td><span class="text-secondary">${escapeHtml(u.department || '—')}</span></td>
                <td>${roleBadge}</td>
                <td>${statusBadge}</td>
                <td>${actionsHtml}</td>
            </tr>
        `;
        tbody.append(row);
    });

    renderPagination(currentPage, totalPages);
}

function renderPagination(current, total) {
    const container = $("#paginationContainer");
    container.empty();

    if (total <= 1) return;

    // Previous Button
    const prevDisabled = current === 1 ? "disabled" : "";
    container.append(`<li class="page-item ${prevDisabled}"><a class="page-link" href="#" onclick="changePage(${current - 1}); return false;"><i class="bi bi-chevron-left"></i></a></li>`);

    // Page Numbers
    for (let i = 1; i <= total; i++) {
        if (i === 1 || i === total || (i >= current - 1 && i <= current + 1)) {
            const activeClass = i === current ? "active" : "";
            container.append(`<li class="page-item ${activeClass}"><a class="page-link" href="#" onclick="changePage(${i}); return false;">${i}</a></li>`);
        } else if (i === current - 2 || i === current + 2) {
            container.append(`<li class="page-item disabled"><span class="page-link">...</span></li>`);
        }
    }

    // Next Button
    const nextDisabled = current === total ? "disabled" : "";
    container.append(`<li class="page-item ${nextDisabled}"><a class="page-link" href="#" onclick="changePage(${current + 1}); return false;"><i class="bi bi-chevron-right"></i></a></li>`);
}

function changePage(page) {
    currentPage = page;
    loadUsersGrid();
}

/**
 * Opens Add User Modal
 */
function openAddUserModal() {
    $("#addUserForm")[0].reset();
    $("#addUserAlert").addClass("d-none");
    const modal = new bootstrap.Modal(document.getElementById("addUserModal"));
    modal.show();
}

/**
 * Submits Add User form via Ajax
 */
function submitAddUser() {
    const form = $("#addUserForm");
    const username = $("#addUsername").val().trim();
    const fullName = $("#addFullName").val().trim();
    const email = $("#addEmail").val().trim();
    const password = $("#addPassword").val();
    const role = $("#addRole").val();
    const status = $("#addStatus").val();
    const phone = $("#addPhone").val().trim();
    const department = $("#addDepartment").val().trim();

    if (!username || !fullName || !email || !password) {
        showModalAlert("#addUserAlert", "Please fill in all required fields marked with *.", "danger");
        return;
    }

    const btn = $("#saveAddUserBtn");
    const originalText = btn.html();
    btn.prop("disabled", true).html('<span class="spinner-border spinner-border-sm me-2"></span>Saving...');

    const payload = {
        username: username,
        fullName: fullName,
        email: email,
        password: password,
        role: role,
        status: status,
        phone: phone,
        department: department
    };

    $.ajax({
        url: "api/users",
        type: "POST",
        contentType: "application/json",
        dataType: "json",
        data: JSON.stringify(payload),
        success: function (res) {
            btn.prop("disabled", false).html(originalText);
            if (res && res.success) {
                bootstrap.Modal.getInstance(document.getElementById("addUserModal")).hide();
                showToast("User @" + username + " added successfully!", "success");
                loadUsersGrid();
                loadDashboardStats();
            } else {
                showModalAlert("#addUserAlert", res.message || "Failed to create user.", "danger");
            }
        },
        error: function (xhr) {
            btn.prop("disabled", false).html(originalText);
            let msg = "Error creating user.";
            if (xhr.responseJSON && xhr.responseJSON.message) {
                msg = xhr.responseJSON.message;
            }
            showModalAlert("#addUserAlert", msg, "danger");
        }
    });
}

/**
 * Fetches user info and opens Edit Modal
 */
function editUser(id) {
    $("#editUserAlert").addClass("d-none");
    $("#editPassword").val("");

    $.ajax({
        url: "api/users/" + id,
        type: "GET",
        dataType: "json",
        success: function (res) {
            if (res && res.success && res.data) {
                const u = res.data;
                $("#editUserId").val(u.id);
                $("#editUsername").val(u.username);
                $("#editFullName").val(u.fullName);
                $("#editEmail").val(u.email);
                $("#editRole").val(u.role);
                $("#editStatus").val(u.status);
                $("#editPhone").val(u.phone || "");
                $("#editDepartment").val(u.department || "");

                // Prevent editing role/status if regular user
                if (currentUser.role === "USER") {
                    $("#editRole, #editStatus").prop("disabled", true);
                } else {
                    $("#editRole, #editStatus").prop("disabled", false);
                }

                const modal = new bootstrap.Modal(document.getElementById("editUserModal"));
                modal.show();
            } else {
                showToast("Could not retrieve user details.", "error");
            }
        },
        error: function () {
            showToast("Server error fetching user details.", "error");
        }
    });
}

/**
 * Submits Edit User form via Ajax
 */
function submitEditUser() {
    const id = $("#editUserId").val();
    const fullName = $("#editFullName").val().trim();
    const email = $("#editEmail").val().trim();
    const role = $("#editRole").val();
    const status = $("#editStatus").val();
    const phone = $("#editPhone").val().trim();
    const department = $("#editDepartment").val().trim();
    const password = $("#editPassword").val().trim();

    if (!fullName || !email) {
        showModalAlert("#editUserAlert", "Full name and email cannot be empty.", "danger");
        return;
    }

    const btn = $("#saveEditUserBtn");
    const originalText = btn.html();
    btn.prop("disabled", true).html('<span class="spinner-border spinner-border-sm me-2"></span>Updating...');

    const payload = {
        fullName: fullName,
        email: email,
        role: role,
        status: status,
        phone: phone,
        department: department
    };

    if (password && password.length > 0) {
        payload.password = password;
    }

    $.ajax({
        url: "api/users/" + id,
        type: "PUT",
        contentType: "application/json",
        dataType: "json",
        data: JSON.stringify(payload),
        success: function (res) {
            btn.prop("disabled", false).html(originalText);
            if (res && res.success) {
                bootstrap.Modal.getInstance(document.getElementById("editUserModal")).hide();
                showToast("User details updated successfully!", "success");
                loadUsersGrid();
                loadDashboardStats();
                // If current user updated their own profile, refresh header badge
                if (currentUser.id == id) {
                    checkCurrentSession();
                }
            } else {
                showModalAlert("#editUserAlert", res.message || "Failed to update user.", "danger");
            }
        },
        error: function (xhr) {
            btn.prop("disabled", false).html(originalText);
            let msg = "Error updating user.";
            if (xhr.responseJSON && xhr.responseJSON.message) {
                msg = xhr.responseJSON.message;
            }
            showModalAlert("#editUserAlert", msg, "danger");
        }
    });
}

/**
 * Opens Delete Confirmation modal
 */
function confirmDeleteUser(id, username) {
    $("#deleteUserId").val(id);
    $("#deleteTargetName").text(username);
    $("#deleteAlert").addClass("d-none");
    const modal = new bootstrap.Modal(document.getElementById("deleteUserModal"));
    modal.show();
}

/**
 * Submits Delete User via Ajax
 */
function submitDeleteUser() {
    const id = $("#deleteUserId").val();
    const btn = $("#confirmDeleteBtn");
    const originalText = btn.html();
    btn.prop("disabled", true).html('<span class="spinner-border spinner-border-sm me-2"></span>Deleting...');

    $.ajax({
        url: "api/users/" + id,
        type: "DELETE",
        dataType: "json",
        success: function (res) {
            btn.prop("disabled", false).html(originalText);
            if (res && res.success) {
                bootstrap.Modal.getInstance(document.getElementById("deleteUserModal")).hide();
                showToast("User successfully removed from system.", "success");
                loadUsersGrid();
                loadDashboardStats();
            } else {
                showModalAlert("#deleteAlert", res.message || "Failed to delete user.", "danger");
            }
        },
        error: function (xhr) {
            btn.prop("disabled", false).html(originalText);
            let msg = "Error deleting user.";
            if (xhr.responseJSON && xhr.responseJSON.message) {
                msg = xhr.responseJSON.message;
            }
            showModalAlert("#deleteAlert", msg, "danger");
        }
    });
}

/**
 * View User Profile Modal
 */
function viewUser(id) {
    $.ajax({
        url: "api/users/" + id,
        type: "GET",
        dataType: "json",
        success: function (res) {
            if (res && res.success && res.data) {
                const u = res.data;
                $("#viewAvatar").text((u.fullName || u.username).substring(0, 2).toUpperCase());
                $("#viewFullName").text(u.fullName);
                $("#viewUsername").text("@" + u.username);
                $("#viewEmail").text(u.email);
                $("#viewPhone").text(u.phone || "Not specified");
                $("#viewDepartment").text(u.department || "General");
                $("#viewRole").text(u.role);
                $("#viewStatus").text(u.status);
                $("#viewCreatedAt").text(u.createdAt ? new Date(u.createdAt).toLocaleString() : "—");
                $("#viewUpdatedAt").text(u.updatedAt ? new Date(u.updatedAt).toLocaleString() : "—");

                const modal = new bootstrap.Modal(document.getElementById("viewUserModal"));
                modal.show();
            }
        }
    });
}

/**
 * Logout
 */
function performLogout() {
    $.ajax({
        url: "api/auth/logout",
        type: "POST",
        dataType: "json",
        complete: function () {
            window.location.href = "login.jsp";
        }
    });
}

/**
 * Toast and Alert Utilities
 */
function showToast(message, type) {
    let icon = "bi-check-circle-fill text-success";
    let borderClass = "toast-success";
    if (type === "error") {
        icon = "bi-exclamation-triangle-fill text-danger";
        borderClass = "toast-error";
    }

    const toastHtml = $(`
        <div class="custom-toast ${borderClass}">
            <i class="bi ${icon} fs-5"></i>
            <div class="flex-grow-1 text-dark">${escapeHtml(message)}</div>
            <button type="button" class="btn-close btn-sm" aria-label="Close"></button>
        </div>
    `);

    $("#toastContainer").append(toastHtml);

    toastHtml.find(".btn-close").on("click", function () {
        toastHtml.fadeOut(300, function () { $(this).remove(); });
    });

    setTimeout(function () {
        toastHtml.fadeOut(400, function () { $(this).remove(); });
    }, 4000);
}

function showModalAlert(selector, message, alertClass) {
    $(selector).removeClass("d-none alert-danger alert-success alert-warning")
        .addClass("alert-" + alertClass)
        .html('<i class="bi bi-exclamation-octagon-fill me-2"></i>' + escapeHtml(message));
}

function escapeHtml(text) {
    if (!text) return "";
    return text.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
