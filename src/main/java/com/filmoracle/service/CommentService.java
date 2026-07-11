package com.filmoracle.service;

import com.filmoracle.model.Comment;
import com.filmoracle.model.Movie;
import com.filmoracle.util.DatabaseUtil;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 评论管理服务：负责单条/批量评论写入，以及按电影、关键词、情感查询。
 */
public final class CommentService {
    private CommentService() {}

    public static Map<String, Object> saveSingle(Movie movie, Comment comment, String source) {
        return saveBatch(movie, List.of(comment), source);
    }

    /**
     * 以事务批量保存评论。相同电影、相同内容的评论会更新而不是重复插入。
     */
    public static Map<String, Object> saveBatch(Movie movie, List<Comment> comments, String source) {
        if (movie == null || isBlank(movie.getTitle())) {
            return Map.of("ok", false, "error", "电影名称不能为空");
        }
        if (comments == null || comments.isEmpty()) {
            return Map.of("ok", false, "error", "没有可保存的评论");
        }

        int inserted = 0;
        int updated = 0;
        try (Connection connection = DatabaseUtil.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long movieId = upsertMovie(connection, movie);
                for (Comment comment : comments) {
                    if (comment == null || isBlank(comment.getText())) continue;
                    if (upsertComment(connection, movieId, comment, source)) inserted++;
                    else updated++;
                }
                connection.commit();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("movieId", movieId);
                result.put("movieTitle", movie.getTitle());
                result.put("inserted", inserted);
                result.put("updated", updated);
                result.put("saved", inserted + updated);
                return result;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            System.err.println("[COMMENT] Save error: " + e.getMessage());
            return Map.of("ok", false, "error", "评论保存失败: " + e.getMessage());
        }
    }

    /**
     * 按电影名称、关键词、情感类型读取已持久化评论。
     */
    public static List<Map<String, Object>> query(String movieTitle, String keyword, String sentiment, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, m.douban_id, m.title, c.content, c.rating, c.star, c.user_name, " +
                "c.created_at, c.vote_count, c.source, c.sentiment, c.aspect, c.quadrant " +
                "FROM comment c JOIN movie_cache m ON c.movie_id = m.id WHERE 1=1");
        List<String> params = new ArrayList<>();
        if (!isBlank(movieTitle)) {
            sql.append(" AND m.title LIKE ?");
            params.add("%" + movieTitle.trim() + "%");
        }
        if (!isBlank(keyword)) {
            sql.append(" AND (c.content LIKE ? OR c.aspect LIKE ? OR c.user_name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
            params.add(value);
        }
        if (!isBlank(sentiment) && List.of("正面", "负面", "中性").contains(sentiment.trim())) {
            sql.append(" AND c.sentiment = ?");
            params.add(sentiment.trim());
        }
        sql.append(" ORDER BY c.id DESC LIMIT ?");

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (String param : params) statement.setString(index++, param);
            statement.setInt(index, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", String.valueOf(rs.getLong("id")));
                    item.put("movieId", rs.getString("douban_id"));
                    item.put("movieTitle", rs.getString("title"));
                    item.put("text", rs.getString("content"));
                    item.put("ratingValue", rs.getInt("rating"));
                    item.put("star", rs.getString("star"));
                    item.put("user", rs.getString("user_name"));
                    item.put("createdAt", rs.getString("created_at"));
                    item.put("voteCount", rs.getInt("vote_count"));
                    item.put("source", rs.getString("source"));
                    item.put("sentiment", rs.getString("sentiment"));
                    item.put("aspect", rs.getString("aspect"));
                    item.put("quadrant", rs.getString("quadrant"));
                    results.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("[COMMENT] Query error: " + e.getMessage());
        }
        return results;
    }

    private static long upsertMovie(Connection connection, Movie movie) throws SQLException {
        String externalId = movieKey(movie);
        String sql = "INSERT INTO movie_cache (douban_id, title, year, genre, rating, votes, director, cast, region, language, pubdate, duration, summary, poster_url) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE title = VALUES(title), year = VALUES(year), genre = VALUES(genre), " +
                "rating = VALUES(rating), votes = VALUES(votes), director = VALUES(director), cast = VALUES(cast), " +
                "region = VALUES(region), language = VALUES(language), pubdate = VALUES(pubdate), duration = VALUES(duration), " +
                "summary = VALUES(summary), poster_url = VALUES(poster_url), cached_at = CURRENT_TIMESTAMP";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, externalId);
            statement.setString(2, valueOr(movie.getTitle(), "未命名电影"));
            statement.setString(3, valueOr(movie.getYear(), ""));
            statement.setString(4, valueOr(movie.getGenre(), ""));
            setDecimal(statement, 5, movie.getRating());
            statement.setInt(6, parseInt(movie.getVotes()));
            statement.setString(7, valueOr(movie.getDirector(), ""));
            statement.setString(8, valueOr(movie.getCast(), ""));
            statement.setString(9, valueOr(movie.getRegion(), ""));
            statement.setString(10, valueOr(movie.getLanguage(), ""));
            statement.setString(11, valueOr(movie.getDate(), ""));
            statement.setString(12, valueOr(movie.getDuration(), ""));
            statement.setString(13, valueOr(movie.getSummary(), ""));
            statement.setString(14, valueOr(movie.getPosterUrl(), ""));
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM movie_cache WHERE douban_id = ?")) {
            statement.setString(1, externalId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("无法读取已保存电影的主键");
    }

    private static boolean upsertComment(Connection connection, long movieId, Comment comment, String source) throws SQLException {
        String dedupHash = sha256(movieId + "|" + valueOr(comment.getId(), "") + "|" + comment.getText().trim());
        Long existingId = null;
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM comment WHERE movie_id = ? AND dedup_hash = ? LIMIT 1")) {
            statement.setLong(1, movieId);
            statement.setString(2, dedupHash);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) existingId = rs.getLong(1);
            }
        }

        if (existingId == null) {
            String sql = "INSERT INTO comment (movie_id, douban_comment_id, content, rating, star, user_name, created_at, vote_count, source, dedup_hash, sentiment, aspect, quadrant) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindComment(statement, movieId, comment, source, dedupHash);
                statement.executeUpdate();
            }
            return true;
        }

        String sql = "UPDATE comment SET douban_comment_id = ?, content = ?, rating = ?, star = ?, user_name = ?, created_at = ?, " +
                "vote_count = ?, source = ?, sentiment = ?, aspect = ?, quadrant = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, valueOr(comment.getId(), ""));
            statement.setString(2, comment.getText().trim());
            statement.setInt(3, Math.max(0, comment.getRatingValue()));
            statement.setString(4, valueOr(comment.getStar(), starOf(comment)));
            statement.setString(5, valueOr(comment.getUser(), "匿名用户"));
            statement.setString(6, valueOr(comment.getCreatedAt(), ""));
            statement.setInt(7, Math.max(0, comment.getVoteCount()));
            statement.setString(8, valueOr(source, "manual"));
            statement.setString(9, valueOr(comment.getSentiment(), "中性"));
            statement.setString(10, valueOr(comment.getAspect(), "完成度与影响力"));
            statement.setString(11, valueOr(comment.getQuadrant(), "孤独/克制"));
            statement.setLong(12, existingId);
            statement.executeUpdate();
        }
        return false;
    }

    private static void bindComment(PreparedStatement statement, long movieId, Comment comment, String source, String dedupHash) throws SQLException {
        statement.setLong(1, movieId);
        statement.setString(2, valueOr(comment.getId(), ""));
        statement.setString(3, comment.getText().trim());
        statement.setInt(4, Math.max(0, comment.getRatingValue()));
        statement.setString(5, valueOr(comment.getStar(), starOf(comment)));
        statement.setString(6, valueOr(comment.getUser(), "匿名用户"));
        statement.setString(7, valueOr(comment.getCreatedAt(), ""));
        statement.setInt(8, Math.max(0, comment.getVoteCount()));
        statement.setString(9, valueOr(source, "manual"));
        statement.setString(10, dedupHash);
        statement.setString(11, valueOr(comment.getSentiment(), "中性"));
        statement.setString(12, valueOr(comment.getAspect(), "完成度与影响力"));
        statement.setString(13, valueOr(comment.getQuadrant(), "孤独/克制"));
    }

    private static String movieKey(Movie movie) {
        String id = valueOr(movie.getId(), "").trim();
        if (!id.isEmpty() && !id.startsWith("import-")) return id;
        return "import-" + sha256(valueOr(movie.getTitle(), "") + "|" + valueOr(movie.getYear(), "")).substring(0, 13);
    }

    private static void setDecimal(PreparedStatement statement, int index, String value) throws SQLException {
        if (isBlank(value)) {
            statement.setNull(index, Types.DECIMAL);
            return;
        }
        try {
            statement.setBigDecimal(index, new BigDecimal(value.trim()));
        } catch (NumberFormatException e) {
            statement.setNull(index, Types.DECIMAL);
        }
    }

    private static int parseInt(String value) {
        if (isBlank(value)) return 0;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try {
            long parsed = Long.parseLong(digits);
            return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String starOf(Comment comment) {
        return comment.getRatingValue() > 0 ? comment.getRatingValue() + "星" : "未评分";
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format(Locale.ROOT, "%02x", b));
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法生成评论指纹", e);
        }
    }

    private static String valueOr(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
