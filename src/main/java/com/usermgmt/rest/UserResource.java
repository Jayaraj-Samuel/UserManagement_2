package com.usermgmt.rest;

import com.usermgmt.model.ApiResponse;
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
import java.util.List;
import java.util.Map;

/**
 * REST Web Service for User CRUD operations.
 * Requires active session authentication.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private final UserService userService;

    public UserResource() {
        this.userService = new UserServiceImpl();
    }

    public UserResource(UserService userService) {
        this.userService = userService;
    }

    @GET
    public Response getUsers(
            @QueryParam("search") String search,
            @QueryParam("role") String role,
            @QueryParam("status") String status,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize,
            @Context HttpServletRequest request) {

        int totalCount = userService.getTotalUserCount(search, role, status);
        List<User> users = userService.listUsers(search, role, status, page, pageSize);

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        if (totalPages == 0) totalPages = 1;

        Map<String, Object> result = new HashMap<>();
        result.put("users", users);
        result.put("totalCount", totalCount);
        result.put("totalPages", totalPages);
        result.put("currentPage", page);
        result.put("pageSize", pageSize);

        return Response.ok(ApiResponse.ok("Users retrieved successfully", result)).build();
    }

    @GET
    @Path("/{id}")
    public Response getUserById(@PathParam("id") int id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ApiResponse.error("User with ID " + id + " was not found."))
                    .build();
        }
        return Response.ok(ApiResponse.ok("User retrieved", user)).build();
    }

    @POST
    public Response createUser(User user, @Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String currentRole = session != null ? (String) session.getAttribute("USER_ROLE") : null;

        // Role check: Only ADMIN and MANAGER can create users
        if (!"ADMIN".equalsIgnoreCase(currentRole) && !"MANAGER".equalsIgnoreCase(currentRole)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied. Only Administrators and Managers can add new users."))
                    .build();
        }

        // Managers cannot create Admin users
        if ("MANAGER".equalsIgnoreCase(currentRole) && "ADMIN".equalsIgnoreCase(user.getRole())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Managers are not authorized to create Administrator accounts."))
                    .build();
        }

        try {
            String clientIp = request.getRemoteAddr();
            User created = userService.createUser(user, clientIp);
            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.ok("User created successfully!", created))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to create user: " + e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateUser(@PathParam("id") int id, User user, @Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String currentRole = session != null ? (String) session.getAttribute("USER_ROLE") : null;
        Integer currentUserId = session != null ? (Integer) session.getAttribute("USER_ID") : null;

        // Role check: Admin, Manager, or user editing themselves
        boolean isSelf = (currentUserId != null && currentUserId.equals(id));
        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentRole);
        boolean isManager = "MANAGER".equalsIgnoreCase(currentRole);

        if (!isAdmin && !isManager && !isSelf) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Access denied. You do not have permission to update this profile."))
                    .build();
        }

        user.setId(id);

        // Regular users editing themselves cannot elevate their role or change status
        if (isSelf && !isAdmin && !isManager) {
            User existing = userService.getUserById(id);
            if (existing != null) {
                user.setRole(existing.getRole());
                user.setStatus(existing.getStatus());
            }
        }

        // Managers cannot elevate to ADMIN or edit ADMINs
        if (isManager && !isAdmin) {
            User existing = userService.getUserById(id);
            if (existing != null && "ADMIN".equalsIgnoreCase(existing.getRole())) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(ApiResponse.error("Managers cannot modify Administrator accounts."))
                        .build();
            }
            if ("ADMIN".equalsIgnoreCase(user.getRole())) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(ApiResponse.error("Managers cannot grant Administrator role."))
                        .build();
            }
        }

        try {
            String clientIp = request.getRemoteAddr();
            User updated = userService.updateUser(user, clientIp);

            // If updating self, refresh session attribute
            if (isSelf && session != null) {
                session.setAttribute("LOGGED_IN_USER", updated);
                session.setAttribute("USER_ROLE", updated.getRole());
                session.setAttribute("USERNAME", updated.getUsername());
            }

            return Response.ok(ApiResponse.ok("User updated successfully!", updated)).build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to update user: " + e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteUser(@PathParam("id") int id, @Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String currentRole = session != null ? (String) session.getAttribute("USER_ROLE") : null;
        Integer currentUserId = session != null ? (Integer) session.getAttribute("USER_ID") : null;

        // Only ADMIN can delete users
        if (!"ADMIN".equalsIgnoreCase(currentRole)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(ApiResponse.error("Permission denied. Only Administrators can delete user accounts."))
                    .build();
        }

        if (currentUserId == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ApiResponse.error("Session invalid."))
                    .build();
        }

        try {
            String clientIp = request.getRemoteAddr();
            boolean success = userService.deleteUser(id, currentUserId, clientIp);
            if (success) {
                return Response.ok(ApiResponse.ok("User successfully deleted.")).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ApiResponse.error("User not found or could not be deleted."))
                        .build();
            }
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to delete user: " + e.getMessage()))
                    .build();
        }
    }
}
