package com.usermgmt.rest;

import com.usermgmt.model.ApiResponse;
import com.usermgmt.model.LoginRequest;
import com.usermgmt.model.User;
import com.usermgmt.service.UserService;
import com.usermgmt.service.impl.UserServiceImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Web Service for Session-based Authentication.
 * Handles Login, Logout, and Session Status verification.
 */
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    private final UserService userService;

    public AuthResource() {
        this.userService = new UserServiceImpl();
    }

    public AuthResource(UserService userService) {
        this.userService = userService;
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest loginRequest, @Context HttpServletRequest request) {
        if (loginRequest == null || loginRequest.getUsername() == null || loginRequest.getPassword() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error("Username and password are required."))
                    .build();
        }

        String clientIp = request.getRemoteAddr();

        try {
            User user = userService.authenticate(loginRequest.getUsername(), loginRequest.getPassword(), clientIp);

            if (user == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(ApiResponse.error("Invalid username or password."))
                        .build();
            }

            // Establish secure HTTP Session
            HttpSession session = request.getSession(true);

            // Session fixation protection (change session ID upon authentication)
            try {
                request.changeSessionId();
            } catch (Throwable ignored) {
                // Fallback for older servlet containers
            }

            session.setAttribute("LOGGED_IN_USER", user);
            session.setAttribute("USER_ID", user.getId());
            session.setAttribute("USERNAME", user.getUsername());
            session.setAttribute("USER_ROLE", user.getRole());
            session.setAttribute("LOGIN_TIME", System.currentTimeMillis());

            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("id", user.getId());
            sessionData.put("username", user.getUsername());
            sessionData.put("fullName", user.getFullName());
            sessionData.put("email", user.getEmail());
            sessionData.put("role", user.getRole());
            sessionData.put("status", user.getStatus());
            sessionData.put("department", user.getDepartment());
            sessionData.put("sessionId", session.getId());

            return Response.ok(ApiResponse.ok("Login successful! Welcome back, " + user.getFullName() + ".", sessionData)).build();

        } catch (IllegalStateException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Authentication server error: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/logout")
    public Response logout(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute("LOGGED_IN_USER");
            session.removeAttribute("USER_ID");
            session.removeAttribute("USERNAME");
            session.removeAttribute("USER_ROLE");
            session.invalidate();
        }
        return Response.ok(ApiResponse.ok("Logged out successfully.")).build();
    }

    @GET
    @Path("/check")
    public Response checkSession(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("LOGGED_IN_USER") == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Session expired or unauthenticated."))
                    .build();
        }

        User user = (User) session.getAttribute("LOGGED_IN_USER");
        // Fresh reload from DB to ensure status/role changes take effect
        User fresh = userService.getUserById(user.getId());
        if (fresh == null || !"ACTIVE".equalsIgnoreCase(fresh.getStatus())) {
            session.invalidate();
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("User account is no longer active."))
                    .build();
        }

        session.setAttribute("LOGGED_IN_USER", fresh);

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("id", fresh.getId());
        sessionData.put("username", fresh.getUsername());
        sessionData.put("fullName", fresh.getFullName());
        sessionData.put("email", fresh.getEmail());
        sessionData.put("role", fresh.getRole());
        sessionData.put("status", fresh.getStatus());
        sessionData.put("department", fresh.getDepartment());
        sessionData.put("sessionId", session.getId());

        return Response.ok(ApiResponse.ok("Session active", sessionData)).build();
    }
}
