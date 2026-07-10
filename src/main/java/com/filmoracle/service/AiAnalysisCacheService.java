package com.filmoracle.service;

import com.filmoracle.model.AnalysisResult;
import com.filmoracle.model.Comment;
import com.filmoracle.model.Movie;
import com.filmoracle.util.DatabaseUtil;
import com.filmoracle.util.JsonUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** AI 分析缓存：将成功的远程 AI 分析结果持久化到 MySQL。 */
public final class AiAnalysisCacheService {
    private AiAnalysisCacheService() {}

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS ai_analysis_cache (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                movie_key VARCHAR(255) NOT NULL,
                comment_fingerprint CHAR(64) NOT NULL,
                ai_model VARCHAR(100) NOT NULL,
                prompt_fingerprint CHAR(64) NOT NULL,
                analysis_json LONGTEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_ai_analysis_cache (movie_key, comment_fingerprint, ai_model, prompt_fingerprint)
            )
            """;

    public record CachedAnalysis(AnalysisResult analysis, String cachedAt) {}

    public static Optional<CachedAnalysis> find(Movie movie, List<Comment> comments, String model, String prompt) {
        try (Connection connection = DatabaseUtil.getConnection()) {
            ensureTable(connection);
            String sql = "SELECT analysis_json, updated_at FROM ai_analysis_cache " +
                    "WHERE movie_key = ? AND comment_fingerprint = ? AND ai_model = ? AND prompt_fingerprint = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, movieKey(movie));
                statement.setString(2, commentFingerprint(comments));
                statement.setString(3, normalizedModel(model));
                statement.setString(4, fingerprint(normalizedPrompt(prompt)));
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) return Optional.empty();
                    return Optional.of(new CachedAnalysis(fromJson(result.getString("analysis_json")), result.getString("updated_at")));
                }
            }
        } catch (Exception e) {
            System.err.println("[AI CACHE] Read failed, continuing without cache: " + e.getMessage());
            return Optional.empty();
        }
    }

    public static boolean save(Movie movie, List<Comment> comments, String model, String prompt, AnalysisResult analysis) {
        try (Connection connection = DatabaseUtil.getConnection()) {
            ensureTable(connection);
            String sql = "INSERT INTO ai_analysis_cache (movie_key, comment_fingerprint, ai_model, prompt_fingerprint, analysis_json) " +
                    "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE analysis_json = VALUES(analysis_json), updated_at = CURRENT_TIMESTAMP";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, movieKey(movie));
                statement.setString(2, commentFingerprint(comments));
                statement.setString(3, normalizedModel(model));
                statement.setString(4, fingerprint(normalizedPrompt(prompt)));
                statement.setString(5, toJson(analysis));
                statement.executeUpdate();
            }
            System.out.println("[AI CACHE] Saved: movie=" + movieKey(movie) + ", comments=" + comments.size());
            return true;
        } catch (Exception e) {
            System.err.println("[AI CACHE] Save failed, analysis result remains usable: " + e.getMessage());
            return false;
        }
    }

    public static String cacheKey(Movie movie, List<Comment> comments, String model, String prompt) {
        return fingerprint(movieKey(movie) + "\n" + commentFingerprint(comments) + "\n" + normalizedModel(model) + "\n" + fingerprint(normalizedPrompt(prompt)));
    }

    public static String commentFingerprint(List<Comment> comments) {
        StringBuilder input = new StringBuilder();
        if (comments != null) {
            for (Comment comment : comments) {
                appendField(input, comment.getId());
                appendField(input, comment.getText());
                appendField(input, String.valueOf(comment.getRatingValue()));
                appendField(input, comment.getUser());
                appendField(input, String.valueOf(comment.getVoteCount()));
            }
        }
        return fingerprint(input.toString());
    }

    public static String toJson(AnalysisResult analysis) {
        return JsonUtil.toJson(analysis);
    }

    public static AnalysisResult fromJson(String json) {
        try {
            return JsonUtil.getMapper().readValue(json, AnalysisResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("AI 缓存结果无法恢复", e);
        }
    }

    private static void ensureTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(CREATE_TABLE)) {
            statement.execute();
        }
    }

    private static String movieKey(Movie movie) {
        if (movie != null && movie.getId() != null && !movie.getId().isBlank()) return "id:" + movie.getId().trim();
        String title = movie != null && movie.getTitle() != null ? movie.getTitle().trim() : "";
        String year = movie != null && movie.getYear() != null ? movie.getYear().trim() : "";
        return "title:" + title + "|" + year;
    }

    private static String normalizedModel(String model) {
        return model == null || model.isBlank() ? "deepseek-chat" : model.trim();
    }

    private static String normalizedPrompt(String prompt) {
        return prompt == null ? "" : prompt;
    }

    private static void appendField(StringBuilder target, String value) {
        String safe = value == null ? "" : value;
        target.append(safe.length()).append(':').append(safe).append('|');
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成缓存指纹", e);
        }
    }
}
