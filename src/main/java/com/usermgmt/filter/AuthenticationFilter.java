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

    // session filtering

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.substring(contextPath.length());

        // Whitelist public endpoints (we can specify which endpoints need not to go
        // through the filter chain)
        boolean isPublicResource = path.equals("/") ||
                path.equals("/login.jsp") ||
                path.equals("/index.jsp") ||
                path.startsWith("/assets/") ||
                path.equals("/api/auth/login") ||
                path.equals("/favicon.ico");

        // Prevent caching of sensitive pages

        // without this the browser stores the page in cache so if he logout and click
        // back button it will show previous cached page
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        // older HTTP header mainly used for compatibility with older browsers/proxies.
        response.setHeader("Pragma", "no-cache");
        // sets the Expires header to a date representing January 1, 1970
        // his response is already expired, so don't use it as a fresh cached response.
        response.setDateHeader("Expires", 0);

        if (isPublicResource) {
            // If already logged in and visiting login.jsp or /, redirect to home.jsp
            if (path.equals("/login.jsp") || path.equals("/")) {
                HttpSession session = request.getSession(false);
                // reditects already logged in visitor away from login page
                if (session != null && session.getAttribute("LOGGED_IN_USER") != null) {
                    response.sendRedirect(contextPath + "/home.jsp");
                    return;
                }
            } // if already logged in go to home(main area)if not continue
              // let other public requests continue
              // This user is allowed to access login.jsp. Continue the request.

            chain.doFilter(request, response);
            return;
        }

        // Check active session
        HttpSession session = request.getSession(false);
        boolean isLoggedIn = (session != null && session.getAttribute("LOGGED_IN_USER") != null);

        if (isLoggedIn) {
            // if logged in allow the request
            chain.doFilter(request, response);
        } else {
            // If API request, respond with 401 JSON
            if (path.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter()
                        .write("{\"success\":false,\"message\":\"Session expired or unauthorized. Please log in.\"}");
            } else {
                // If Web Page, redirect to login.jsp
                response.sendRedirect(contextPath + "/login.jsp?sessionExpired=true");
            }
        }
    }

    @Override
    public void destroy() {
        // If I created something that needs to be closed or cleaned up when the Filter
        // stops, I can do it here
        // ex: connection.close();
        // and cleanup the resources created by filter after job

    }
}
