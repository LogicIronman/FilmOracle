package com.filmoracle.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具类（基于 Jackson）
 */
public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static ObjectMapper getMapper() {
        return mapper;
    }

    /** 将对象序列化为 JSON 字符串 */
    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /** 将 JSON 字符串解析为 Map */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> parseToMap(String json) {
        try {
            return mapper.readValue(json, java.util.Map.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败: " + e.getMessage(), e);
        }
    }

    /** 将 JSON 字符串解析为 List<Map> */
    @SuppressWarnings("unchecked")
    public static java.util.List<java.util.Map<String, Object>> parseToList(String json) {
        try {
            return mapper.readValue(json, java.util.List.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败: " + e.getMessage(), e);
        }
    }

    /** 安全地从 Map 中获取字符串值 */
    public static String getStr(java.util.Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) return val.toString();
        }
        return "";
    }

    /** 安全地从 Map 中获取整数值（单键） */
    public static int getInt(java.util.Map<String, Object> map, String key, int def) {
        if (map == null) return def;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (Exception e) { return def; }
        }
        return def;
    }

    /** 安全地从 Map 中获取整数值（双键回退） */
    public static int getInt(java.util.Map<String, Object> map, String key1, String key2, int def) {
        int val = getInt(map, key1, Integer.MIN_VALUE);
        if (val != Integer.MIN_VALUE) return val;
        return getInt(map, key2, def);
    }

    /** 安全地从 Map 中获取浮点值 */
    public static double getDouble(java.util.Map<String, Object> map, String key, double def) {
        if (map == null) return def;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (Exception e) { return def; }
        }
        return def;
    }
}
