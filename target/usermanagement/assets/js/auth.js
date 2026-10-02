/**
 * Authentication and Session Management Logic using jQuery and Ajax
 */
$(document).ready(function () {
    const contextPath = window.location.pathname.substring(0, window.location.pathname.indexOf("/", 2)) || "";

    // Toggle password visibility
    $("#togglePassword").on("click", function () {
        const passwordField = $("#password");
        const type = passwordField.attr("type") === "password" ? "text" : "password";
        passwordField.attr("type", type);
        $(this).find("i").toggleClass("bi-eye bi-eye-slash");
    });

    // Quick fill demo credentials
    $(".demo-pill").on("click", function () {
        const u = $(this).data("user");
        const p = $(this).data("pass");
        $("#username").val(u);
        $("#password").val(p);
        $("#loginAlert").addClass("d-none");
    });

    // Check if sessionExpired parameter is present in URL
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get("sessionExpired") === "true") {
        showAlert("Your session has expired or you need to log in first.", "warning");
    }

    // Handle Login Form Submit via Ajax
    $("#loginForm").on("submit", function (e) {
        e.preventDefault();

        const username = $("#username").val().trim();
        const password = $("#password").val();

        if (!username || !password) {
            showAlert("Please enter both username and password.", "danger");
            return;
        }

        const submitBtn = $("#btnLogin");
        const originalText = submitBtn.html();
        submitBtn.prop("disabled", true).html('<span class="spinner-border spinner-border-sm me-2"></span>Authenticating...');
        $("#loginAlert").addClass("d-none");

        $.ajax({
            url: "api/auth/login",
            type: "POST",
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify({
                username: username,
                password: password
            }),
            success: function (res) {
                if (res && res.success) {
                    showAlert("Authentication successful! Redirecting to dashboard...", "success");
                    setTimeout(function () {
                        window.location.href = "home.jsp";
                    }, 600);
                } else {
                    showAlert(res.message || "Invalid username or password.", "danger");
                    submitBtn.prop("disabled", false).html(originalText);
                }
            },
            error: function (xhr) {
                submitBtn.prop("disabled", false).html(originalText);
                let msg = "Authentication failed. Please check your credentials.";
                if (xhr.responseJSON && xhr.responseJSON.message) {
                    msg = xhr.responseJSON.message;
                }
                showAlert(msg, "danger");
            }
        });
    });

    function showAlert(message, type) {
        const alertBox = $("#loginAlert");
        alertBox.removeClass("d-none alert-danger alert-success alert-warning")
            .addClass("alert-" + type)
            .html('<i class="bi bi-info-circle-fill me-2"></i>' + message);
    }
});
