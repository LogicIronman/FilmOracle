package com.filmoracle.service;

import com.filmoracle.util.DatabaseUtil;
import java.sql.*;
import java.util.*;

/**
 * 系统设置服务——读写app_setting表
 * 单行设计（id=1），全局设置
 */
public class SettingService {

    /**
     * 读取设置
     */
    public static Map<String, Object> getSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM app_setting WHERE id = 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                settings.put("aiModel", rs.getString("ai_model"));
                settings.put("apiKey", rs.getString("api_key"));
                settings.put("aiPrompt", rs.getString("ai_prompt"));
                settings.put("crawlerApiUrl", rs.getString("crawler_api_url"));
                settings.put("commentCount", rs.getInt("comment_count"));
                settings.put("requestTimeout", rs.getInt("request_timeout"));
                settings.put("fallbackEnabled", rs.getBoolean("fallback_enabled"));
            } else {
                // 如果表为空，返回默认值
                settings.put("aiModel", "moonshot-v1-8k");
                settings.put("apiKey", "");
                settings.put("aiPrompt", "");
                settings.put("crawlerApiUrl", "https://m.douban.com/rexxar/api/v2");
                settings.put("commentCount", 100);
                settings.put("requestTimeout", 9);
                settings.put("fallbackEnabled", true);
            }
        } catch (SQLException e) {
            System.err.println("[SETTINGS] Load error: " + e.getMessage());
            settings.put("aiModel", "moonshot-v1-8k");
            settings.put("apiKey", "");
            settings.put("aiPrompt", "");
            settings.put("crawlerApiUrl", "https://m.douban.com/rexxar/api/v2");
            settings.put("commentCount", 100);
            settings.put("requestTimeout", 9);
            settings.put("fallbackEnabled", true);
        }
        return settings;
    }

    /**
     * 更新设置
     */
    public static Map<String, Object> updateSettings(Map<String, Object> settings) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE app_setting SET ai_model = ?, api_key = ?, ai_prompt = ?, " +
                     "comment_count = ?, request_timeout = ?, fallback_enabled = ? WHERE id = 1")) {
            ps.setString(1, String.valueOf(settings.getOrDefault("aiModel", "moonshot-v1-8k")));
            ps.setString(2, String.valueOf(settings.getOrDefault("apiKey", "")));
            ps.setString(3, String.valueOf(settings.getOrDefault("aiPrompt", "")));
            ps.setInt(4, toInt(settings.get("commentCount"), 100));
            ps.setInt(5, toInt(settings.get("requestTimeout"), 9));
            ps.setBoolean(6, toBool(settings.get("fallbackEnabled"), true));
            int rows = ps.executeUpdate();
            if (rows == 0) {
                // 不存在则插入
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO app_setting (id, ai_model, api_key, ai_prompt, comment_count, request_timeout, fallback_enabled) " +
                        "VALUES (1, ?, ?, ?, ?, ?, ?)")) {
                    ins.setString(1, String.valueOf(settings.getOrDefault("aiModel", "moonshot-v1-8k")));
                    ins.setString(2, String.valueOf(settings.getOrDefault("apiKey", "")));
                    ins.setString(3, String.valueOf(settings.getOrDefault("aiPrompt", "")));
                    ins.setInt(4, toInt(settings.get("commentCount"), 100));
                    ins.setInt(5, toInt(settings.get("requestTimeout"), 9));
                    ins.setBoolean(6, toBool(settings.get("fallbackEnabled"), true));
                    ins.executeUpdate();
                }
            }
            result.put("ok", true);
            System.out.println("[SETTINGS] Updated successfully");
        } catch (SQLException e) {
            System.err.println("[SETTINGS] Update error: " + e.getMessage());
            result.put("ok", false);
            result.put("error", "更新失败: " + e.getMessage());
        }
        return result;
    }

    private static int toInt(Object value, int def) {
        if (value == null) return def;
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return def; }
    }

    private static boolean toBool(Object value, boolean def) {
        if (value == null) return def;
        String s = String.valueOf(value);
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }
}
