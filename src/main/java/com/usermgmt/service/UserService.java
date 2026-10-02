package com.usermgmt.service;

import com.usermgmt.model.User;

import java.util.List;
import java.util.Map;

/**
 * Business Service interface for User operations and Authentication.
 */
public interface UserService {

    User authenticate(String username, String password, String ipAddress);

    User getUserById(int id);

    List<User> listUsers(String search, String role, String status, int page, int pageSize);

    int getTotalUserCount(String search, String role, String status);

    User createUser(User user, String clientIp);

    User updateUser(User user, String clientIp);

    boolean changePassword(int userId, String oldPassword, String newPassword, String clientIp);

    boolean deleteUser(int targetUserId, int currentUserId, String clientIp);

    Map<String, Object> getDashboardStatistics();
}
