package com.filmoracle.service;

import com.filmoracle.util.DatabaseUtil;
import java.sql.*;
import java.util.*;

/**
 * 浏览历史服务——按用户存储电影浏览记录
 * 使用MySQL数据库持久化
 */
public class HistoryService {

    /**
     * 保存电影到用户浏览历史（UPSERT方式：存在则更新，不存在则插入）
     */
    public static Map<String, Object> saveHistory(String username, Map<String, Object> movieData) {
        if (username == null || username.isEmpty()) {
            return Map.of("ok", false, "error", "用户名为空");
        }

        String movieId = String.valueOf(movieData.getOrDefault("id", ""));
        if (movieId.isEmpty()) {
            return Map.of("ok", false, "error", "电影ID为空");
        }

        String sql = "INSERT INTO user_history (username, movie_id, title, year, rating, poster_url, poster_local, genre, director, source, saved_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                     "ON DUPLICATE KEY UPDATE title = VALUES(title), year = VALUES(year), rating = VALUES(rating), " +
                     "poster_url = VALUES(poster_url), poster_local = VALUES(poster_local), genre = VALUES(genre), " +
                     "director = VALUES(director), source = VALUES(source), saved_at = NOW()";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, movieId);
            ps.setString(3, String.valueOf(movieData.getOrDefault("title", "未知电影")));
            ps.setString(4, String.valueOf(movieData.getOrDefault("year", "")));
            ps.setString(5, String.valueOf(movieData.getOrDefault("rating", "")));
            ps.setString(6, String.valueOf(movieData.getOrDefault("posterUrl", "")));
            ps.setString(7, String.valueOf(movieData.getOrDefault("poster", "")));
            ps.setString(8, String.valueOf(movieData.getOrDefault("genre", "")));
            ps.setString(9, String.valueOf(movieData.getOrDefault("director", "")));
            ps.setString(10, String.valueOf(movieData.getOrDefault("source", "")));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[HISTORY] Save error: " + e.getMessage());
            return Map.of("ok", false, "error", "保存失败: " + e.getMessage());
        }

        // 返回当前总数
        int count = getHistoryCount(username);
        System.out.println("[HISTORY] Saved for " + username + ": " + movieData.get("title") + " (total: " + count + ")");
        return Map.of("ok", true, "count", count);
    }

    /**
     * 加载用户浏览历史（最新的50条）
     */
    public static List<Map<String, Object>> loadHistory(String username) {
        List<Map<String, Object>> history = new ArrayList<>();
        if (username == null || username.isEmpty()) return history;

        String sql = "SELECT movie_id, title, year, rating, poster_url, poster_local, genre, director, source, saved_at " +
                     "FROM user_history WHERE username = ? ORDER BY saved_at DESC LIMIT 50";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", rs.getString("movie_id"));
                entry.put("title", rs.getString("title"));
                entry.put("year", rs.getString("year"));
                entry.put("rating", rs.getString("rating"));
                entry.put("posterUrl", rs.getString("poster_url"));
                entry.put("poster", rs.getString("poster_local"));
                entry.put("genre", rs.getString("genre"));
                entry.put("director", rs.getString("director"));
                entry.put("source", rs.getString("source"));
                entry.put("savedAt", rs.getString("saved_at"));
                history.add(entry);
            }
        } catch (SQLException e) {
            System.err.println("[HISTORY] Load error: " + e.getMessage());
        }
        return history;
    }

    /**
     * 获取用户历史记录数量
     */
    private static int getHistoryCount(String username) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM user_history WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[HISTORY] Count error: " + e.getMessage());
        }
        return 0;
    }

    /**
     * 清空用户浏览历史
     */
    public static void clearHistory(String username) {
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM user_history WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
            System.out.println("[HISTORY] Cleared for " + username);
        } catch (SQLException e) {
            System.err.println("[HISTORY] Clear error: " + e.getMessage());
        }
    }
}
