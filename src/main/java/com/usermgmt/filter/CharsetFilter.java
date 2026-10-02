package com.usermgmt.filter;

import javax.servlet.*;
import java.io.IOException;

/**
 * Filter ensuring all requests and responses use UTF-8 character encoding.
 */
public class CharsetFilter implements Filter {

    private static final String UTF_8 = "UTF-8";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding(UTF_8);
        response.setCharacterEncoding(UTF_8);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
