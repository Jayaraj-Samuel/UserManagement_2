<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Check for existing authenticated session
    if (session != null && session.getAttribute("LOGGED_IN_USER") != null) {
        response.sendRedirect(request.getContextPath() + "/home.jsp");
    } else {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
    }
%>
