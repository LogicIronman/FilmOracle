package com.filmoracle.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存缓存服务（带 TTL）
 */
public class CacheService {
    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(Object value, long expiresAt, String endpoint) {}

    public static Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt) {
            if (entry != null) cache.remove(key);
            return null;
        }
        return entry.value;
    }

    public static void put(String key, Object value, long ttlMs, String endpoint) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttlMs, endpoint));
    }

    public static int size() {
        return cache.size();
    }

    public static java.util.Set<String> keys() {
        return cache.keySet();
    }

    public static void clear() {
        cache.clear();
    }
}
