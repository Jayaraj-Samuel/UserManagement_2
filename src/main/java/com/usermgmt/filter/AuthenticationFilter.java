package com.usermgmt.filter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Session-based Authentication Filter.
 * Guards protected pages and APIs against unauthenticated access.
 * Automatically invalidates back-button history via Cache-Control headers.
 */
public class AuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.substring(contextPath.length());

        // Whitelist public endpoints
        boolean isPublicResource = path.equals("/") ||
                path.equals("/login.jsp") ||
                path.equals("/index.jsp") ||
                path.startsWith("/assets/") ||
                path.equals("/api/auth/login") ||
                path.equals("/favicon.ico");

        // Prevent caching of sensitive pages
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        if (isPublicResource) {
            // If already logged in and visiting login.jsp or /, redirect to home.jsp
            if (path.equals("/login.jsp") || path.equals("/")) {
                HttpSession session = request.getSession(false);
                if (session != null && session.getAttribute("LOGGED_IN_USER") != null) {
                    response.sendRedirect(contextPath + "/home.jsp");
                    return;
                }
            }
            chain.doFilter(request, response);
            return;
        }

        // Check active session
        HttpSession session = request.getSession(false);
        boolean isLoggedIn = (session != null && session.getAttribute("LOGGED_IN_USER") != null);

        if (isLoggedIn) {
            chain.doFilter(request, response);
        } else {
            // If API request, respond with 401 JSON
            if (path.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"message\":\"Session expired or unauthorized. Please log in.\"}");
            } else {
                // If Web Page, redirect to login.jsp
                response.sendRedirect(contextPath + "/login.jsp?sessionExpired=true");
            }
        }
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
