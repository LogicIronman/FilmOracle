package com.filmoracle.service;

import com.filmoracle.util.DatabaseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/**
 * 认证服务——用户注册、登录、会话管理
 * 使用MySQL数据库持久化用户数据
 */
public class AuthService {

    /**
     * 密码SHA-256哈希
     */
    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }

    /**
     * 注册新用户
     */
    public static Map<String, Object> register(String username, String password, String role) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (username == null || username.trim().length() < 2) {
            result.put("ok", false);
            result.put("error", "用户名至少2个字符");
            return result;
        }
        if (password == null || password.length() < 4) {
            result.put("ok", false);
            result.put("error", "密码至少4个字符");
            return result;
        }

        String actualRole = "admin".equals(role) ? "admin" : "user";
        String hash = hashPassword(password);

        try (Connection conn = DatabaseUtil.getConnection()) {
            // 检查用户名是否已存在
            try (PreparedStatement ps = conn.prepareStatement("SELECT username FROM user_account WHERE username = ?")) {
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    result.put("ok", false);
                    result.put("error", "用户名已存在");
                    return result;
                }
            }
            // 插入新用户
            String userId = "user-" + System.currentTimeMillis();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO user_account (username, password_hash, role) VALUES (?, ?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, hash);
                ps.setString(3, actualRole);
                ps.executeUpdate();
            }
            result.put("ok", true);
            result.put("user", Map.of("id", userId, "username", username, "role", actualRole));
            System.out.println("[AUTH] Registered: " + username + " (" + actualRole + ")");
        } catch (SQLException e) {
            System.err.println("[AUTH] Register error: " + e.getMessage());
            result.put("ok", false);
            result.put("error", "注册失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 用户登录
     */
    public static Map<String, Object> login(String username, String password, HttpServletRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (username == null || password == null) {
            result.put("ok", false);
            result.put("error", "用户名和密码不能为空");
            return result;
        }

        String hash = hashPassword(password);

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username, role FROM user_account WHERE username = ? AND password_hash = ?")) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String userId = String.valueOf(rs.getLong("id"));
                String role = rs.getString("role");

                HttpSession session = req.getSession(true);
                session.setAttribute("user", Map.of(
                    "id", userId,
                    "username", username,
                    "role", role
                ));
                session.setMaxInactiveInterval(3600 * 24);

                result.put("ok", true);
                result.put("user", Map.of("id", userId, "username", username, "role", role));
                System.out.println("[AUTH] Login: " + username + " (" + role + ")");
            } else {
                result.put("ok", false);
                result.put("error", "用户名或密码错误");
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] Login error: " + e.getMessage());
            result.put("ok", false);
            result.put("error", "登录失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 退出登录
     */
    public static void logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * 检查会话状态
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> checkSession(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (Map<String, Object>) session.getAttribute("user");
        }
        return null;
    }

    /**
     * 是否已登录
     */
    public static boolean isLoggedIn(HttpServletRequest req) {
        return checkSession(req) != null;
    }

    /**
     * 是否是管理员
     */
    public static boolean isAdmin(HttpServletRequest req) {
        Map<String, Object> user = checkSession(req);
        return user != null && "admin".equals(user.get("role"));
    }
}
