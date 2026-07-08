package com.filmoracle.service;

import com.filmoracle.model.Movie;
import com.filmoracle.model.Comment;
import com.filmoracle.util.HttpUtil;
import com.filmoracle.util.JsonUtil;
import com.filmoracle.util.FallbackData;
import java.util.*;
import java.util.regex.*;

/**
 * 豆瓣 API 服务——负责调用豆瓣接口获取电影数据
 * 多策略容错：m.douban.com → frodo.douban.com → 兜底数据
 */
public class DoubanApiService {

    // 缓存 TTL
    private static final long TTL_HOT = 10 * 60 * 1000;
    private static final long TTL_SEARCH = 10 * 60 * 1000;
    private static final long TTL_DETAIL = 15 * 60 * 1000;
    private static final long TTL_INTERESTS = 3 * 60 * 1000;
    private static final long TTL_TAGS = 10 * 60 * 1000;
    private static final long TTL_TOP = 15 * 60 * 1000;

    private static final String FRODO_KEY = "0ac44ae016490db2204ce0a042db2916";

    // ─── 热门电影 ───
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getHotMovies(int start, int limit) {
        String cacheKey = "hot:" + start + ":" + limit;
        Map<String, Object> cached = (Map<String, Object>) CacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            String url = "https://m.douban.com/rexxar/api/v2/subject/recent_hot/movie?start=" + start
                    + "&limit=" + limit + "&category=" + java.net.URLEncoder.encode("豆瓣高分", java.nio.charset.StandardCharsets.UTF_8)
                    + "&type=" + java.net.URLEncoder.encode("全部", java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> data = HttpUtil.getJsonAsMap(url, "mobile");
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
            if (items == null || items.isEmpty()) throw new RuntimeException("empty items");

            List<Movie> movies = new ArrayList<>();
            for (Map<String, Object> item : items) {
                movies.add(normalizeMovie(item));
            }
            Map<String, Object> result = Map.of(
                    "ok", true, "movies", movies,
                    "meta", Map.of("source", "douban", "endpoint", "recent_hot", "count", movies.size())
            );
            CacheService.put(cacheKey, result, TTL_HOT, "hot");
            return result;
        } catch (Exception e) {
            return Map.of("ok", false, "movies", FallbackData.getFallbackMovies(),
                    "meta", Map.of("source", "fallback", "error", e.getMessage(), "count", FallbackData.getFallbackMovies().size()));
        }
    }

    // ─── 搜索电影（多策略）───
    @SuppressWarnings("unchecked")
    public static Map<String, Object> searchMovies(String query) {
        String q = (query == null || query.isBlank()) ? "寄生虫" : query.trim();
        String cacheKey = "search:" + q;
        Map<String, Object> cached = (Map<String, Object>) CacheService.get(cacheKey);
        if (cached != null) return cached;

        // 策略1: m.douban.com/rexxar/api/v2/search (移动端搜索API，反爬最松)
        try {
            String url = "https://m.douban.com/rexxar/api/v2/search?q="
                    + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8) + "&count=10&type=movie";
            Map<String, Object> data = HttpUtil.getJsonAsMap(url, "search");
            Map<String, Object> subjects = (Map<String, Object>) data.get("subjects");
            List<Map<String, Object>> items = subjects != null ? (List<Map<String, Object>>) subjects.get("items") : null;
            if (items != null && !items.isEmpty()) {
                List<Movie> movies = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    Map<String, Object> target = (Map<String, Object>) item.get("target");
                    if (target != null) {
                        Movie m = normalizeMovie(target);
                        if (!m.getId().isEmpty() && !m.getTitle().isEmpty()) movies.add(m);
                    }
                }
                if (!movies.isEmpty()) {
                    Map<String, Object> result = Map.of("ok", true, "movies", movies,
                            "meta", Map.of("source", "douban", "endpoint", "rexxar_search", "count", movies.size()));
                    CacheService.put(cacheKey, result, TTL_SEARCH, "search");
                    return result;
                }
            }
        } catch (Exception e1) {
            System.err.println("[SEARCH] Rexxar search failed: " + e1.getMessage());
        }

        // 策略2: subject_suggest 接口
        try {
            String url = "https://movie.douban.com/j/subject_suggest?q="
                    + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
            String body = HttpUtil.get(url, "pc", 1, 8);
            List<Map<String, Object>> suggestData = JsonUtil.parseToList(body);
            List<Movie> movies = new ArrayList<>();
            for (Map<String, Object> s : suggestData) {
                if (!"movie".equals(JsonUtil.getStr(s, "type"))) continue;
                Movie m = new Movie();
                m.setId(JsonUtil.getStr(s, "id"));
                m.setTitle(JsonUtil.getStr(s, "title"));
                m.setYear(JsonUtil.getStr(s, "year"));
                m.setPosterUrl(JsonUtil.getStr(s, "img"));
                m.setCast(JsonUtil.getStr(s, "sub_title"));
                m.setSource("douban-suggest");
                movies.add(m);
            }
            if (!movies.isEmpty()) {
                Map<String, Object> result = Map.of("ok", true, "movies", movies,
                        "meta", Map.of("source", "douban", "endpoint", "subject_suggest", "count", movies.size()));
                CacheService.put(cacheKey, result, TTL_SEARCH, "search");
                return result;
            }
        } catch (Exception e2) {
            System.err.println("[SEARCH] Suggest search failed: " + e2.getMessage());
        }

        // 兜底：仅返回标题匹配的fallback电影，不返回全部
        List<Movie> fallback = new ArrayList<>();
        for (Movie m : FallbackData.getFallbackMovies()) {
            if (m.getTitle().contains(q) || q.contains(m.getTitle())) fallback.add(m);
        }
        if (fallback.isEmpty()) {
            return Map.of("ok", false, "movies", new ArrayList<>(),
                    "meta", Map.of("source", "empty", "error", "未找到匹配的电影", "count", 0));
        }
        return Map.of("ok", false, "movies", fallback,
                "meta", Map.of("source", "fallback", "error", "豆瓣搜索不可用", "count", fallback.size()));
    }

    // ─── 电影详情 ───
    public static Map<String, Object> getMovieDetail(String id) {
        String cacheKey = "detail:" + id;
        Map<String, Object> cached = (Map<String, Object>) CacheService.get(cacheKey);
        if (cached != null) return cached;

        // 策略1: m.douban.com
        try {
            String url = "https://m.douban.com/rexxar/api/v2/movie/" + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> data = HttpUtil.getJsonAsMap(url, "mobile");
            Movie movie = normalizeMovie(data);
            Map<String, Object> result = Map.of("ok", true, "movie", movie,
                    "meta", Map.of("source", "douban", "endpoint", "movie_detail"));
            CacheService.put(cacheKey, result, TTL_DETAIL, "detail");
            return result;
        } catch (Exception e1) {
            // 策略2: frodo.douban.com
            try {
                String url = "https://frodo.douban.com/api/v2/movie/" + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8) + "?apiKey=" + FRODO_KEY;
                Map<String, Object> data = HttpUtil.getJsonAsMap(url, "frodo");
                Movie movie = normalizeMovie(data);
                Map<String, Object> result = Map.of("ok", true, "movie", movie,
                        "meta", Map.of("source", "douban-frodo", "endpoint", "movie_detail"));
                CacheService.put(cacheKey, result, TTL_DETAIL, "detail");
                return result;
            } catch (Exception e2) {
                // 兜底
                Movie fallback = FallbackData.findMovieById(id);
                return Map.of("ok", false, "movie", fallback,
                        "meta", Map.of("source", "fallback", "error", e1.getMessage() + "; " + e2.getMessage()));
            }
        }
    }

    // ─── 电影短评 ───
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMovieInterests(String id, int count) {
        String cacheKey = "interests:" + id + ":" + count;
        Map<String, Object> cached = (Map<String, Object>) CacheService.get(cacheKey);
        if (cached != null) return cached;

        List<Comment> allComments = new ArrayList<>();
        int start = 0;
        int pageSize = 50;
        int maxPages = Math.min(20, (count + pageSize - 1) / pageSize);
        String lastError = "";

        for (int page = 0; page < maxPages && allComments.size() < count; page++) {
            try {
                String url = "https://m.douban.com/rexxar/api/v2/movie/" + java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8)
                        + "/interests?start=" + start + "&count=" + pageSize + "&order_by=hot";
                Map<String, Object> data = HttpUtil.getJsonAsMap(url, "mobile");
                List<Map<String, Object>> interests = (List<Map<String, Object>>) data.get("interests");
                if (interests == null || interests.isEmpty()) break;

                for (int i = 0; i < interests.size(); i++) {
                    Comment c = normalizeInterest(interests.get(i), allComments.size());
                    if (!c.getText().isEmpty()) allComments.add(c);
                }
                start += interests.size();
                if (interests.size() < pageSize) break;
            } catch (Exception e) {
                lastError = e.getMessage();
                break;
            }
        }

        if (allComments.isEmpty()) {
            List<Comment> fallback = FallbackData.getFallbackComments();
            return Map.of("ok", false, "comments", fallback,
                    "meta", Map.of("source", "fallback", "error", lastError.isEmpty() ? "no comments" : lastError, "count", fallback.size()));
        }

        if (allComments.size() > count) allComments = allComments.subList(0, count);

        Map<String, Object> result = Map.of("ok", true, "comments", allComments,
                "meta", Map.of("source", "douban", "endpoint", "movie_interests", "count", allComments.size()));
        CacheService.put(cacheKey, result, TTL_INTERESTS, "interests");
        return result;
    }

    // ─── 按标签浏览 ───
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMoviesByTag(String tag, String sort, int limit, int start) {
        String cacheKey = "tags:" + tag + ":" + sort + ":" + start + ":" + limit;
        Map<String, Object> cached = (Map<String, Object>) CacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            String url = "https://movie.douban.com/j/search_subjects?type=movie&tag="
                    + java.net.URLEncoder.encode(tag, java.nio.charset.StandardCharsets.UTF_8)
                    + "&sort=" + sort + "&page_limit=" + limit + "&page_start=" + start;
            Map<String, Object> data = HttpUtil.getJsonAsMap(url, "pc");
            List<Map<String, Object>> subjects = (List<Map<String, Object>>) data.get("subjects");
            if (subjects == null) subjects = Collections.emptyList();

            List<Movie> movies = new ArrayList<>();
            for (Map<String, Object> s : subjects) {
                Movie m = new Movie();
                m.setId(JsonUtil.getStr(s, "id"));
                m.setTitle(JsonUtil.getStr(s, "title"));
                m.setRating(JsonUtil.getStr(s, "rate"));
                m.setGenre(tag);
                m.setPosterUrl(JsonUtil.getStr(s, "cover"));
                m.setSource("douban-tag");
                movies.add(m);
            }
            Map<String, Object> result = Map.of("ok", true, "movies", movies,
                    "meta", Map.of("source", "douban", "endpoint", "search_subjects", "tag", tag, "count", movies.size()));
            CacheService.put(cacheKey, result, TTL_TAGS, "tags");
            return result;
        } catch (Exception e) {
            return Map.of("ok", false, "movies", FallbackData.getFallbackMovies().subList(0, Math.min(limit, 8)),
                    "meta", Map.of("source", "fallback", "error", e.getMessage()));
        }
    }

    // ─── Top排行榜 ───
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getTopMovies(String typeId, int start, int limit) {
        String cacheKey = "top:" + typeId + ":" + start + ":" + limit;
        Map<String, Object> cached = (Map<String, Object>) CacheService.get(cacheKey);
        if (cached != null) return cached;

        try {
            String url = "https://movie.douban.com/j/chart/top_list?type=" + typeId
                    + "&interval_id=100:90&action=&start=" + start + "&limit=" + limit;
            String body = HttpUtil.get(url, "pc", 2, 10);
            List<Map<String, Object>> items = JsonUtil.parseToList(body);

            List<Movie> movies = new ArrayList<>();
            for (Map<String, Object> s : items) {
                Movie m = new Movie();
                m.setId(JsonUtil.getStr(s, "id"));
                m.setTitle(JsonUtil.getStr(s, "title"));
                m.setYear(JsonUtil.getStr(s, "release_date"));
                m.setRating(JsonUtil.getStr(s, "score"));
                m.setRegion(((List<String>) s.getOrDefault("regions", Collections.emptyList())).stream().reduce((a, b) -> a + " / " + b).orElse(""));
                m.setCast(((List<String>) s.getOrDefault("actors", Collections.emptyList())).stream().reduce((a, b) -> a + " / " + b).orElse(""));
                m.setPosterUrl(JsonUtil.getStr(s, "cover_url"));
                m.setSource("douban-top");
                movies.add(m);
            }
            Map<String, Object> result = Map.of("ok", true, "movies", movies,
                    "meta", Map.of("source", "douban", "endpoint", "top_list", "count", movies.size()));
            CacheService.put(cacheKey, result, TTL_TOP, "top");
            return result;
        } catch (Exception e) {
            return Map.of("ok", false, "movies", FallbackData.getFallbackMovies().subList(0, Math.min(limit, 8)),
                    "meta", Map.of("source", "fallback", "error", e.getMessage()));
        }
    }

    // ─── 数据规范化 ───
    @SuppressWarnings("unchecked")
    private static Movie normalizeMovie(Map<String, Object> item) {
        Movie m = new Movie();
        m.setId(JsonUtil.getStr(item, "id", "subject_id"));
        m.setTitle(JsonUtil.getStr(item, "title", "name"));
        if (m.getTitle().isEmpty()) m.setTitle("未知电影");

        // 评分
        Map<String, Object> rating = (Map<String, Object>) item.get("rating");
        double ratingVal = rating != null ? JsonUtil.getDouble(rating, "value", 0) : 0;
        m.setRating(ratingVal > 0 ? String.format("%.1f", ratingVal).replaceAll("\\.0$", "") : "");
        int voteCount = rating != null ? JsonUtil.getInt(rating, "count", 0) : JsonUtil.getInt(item, "vote_count", 0);
        m.setVotes(String.valueOf(voteCount));

        // 年份
        String subtitle = JsonUtil.getStr(item, "card_subtitle", "abstract");
        String year = JsonUtil.getStr(item, "year");
        if (year.isEmpty()) {
            Matcher ym = Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(subtitle);
            if (ym.find()) year = ym.group();
        }
        m.setYear(year);

        // 类型
        Object genres = item.get("genres");
        if (genres instanceof List) {
            m.setGenre(((List<?>) genres).stream().filter(Objects::nonNull).map(String::valueOf).reduce((a, b) -> a + " / " + b).orElse(""));
        } else if (!subtitle.isEmpty()) {
            m.setGenre(subtitle.replaceAll("\\d{4}\\s*/?\\s*", "").trim());
        } else {
            m.setGenre("电影");
        }

        // 导演
        List<Map<String, Object>> directors = (List<Map<String, Object>>) item.get("directors");
        if (directors != null && !directors.isEmpty()) {
            m.setDirector(directors.stream().map(d -> JsonUtil.getStr(d, "name")).filter(s -> !s.isEmpty()).reduce((a, b) -> a + " / " + b).orElse("未知导演"));
        } else {
            m.setDirector(JsonUtil.getStr(item, "director", "未知导演"));
        }

        // 主演
        List<Map<String, Object>> actors = (List<Map<String, Object>>) item.get("actors");
        List<Map<String, Object>> casts = (List<Map<String, Object>>) item.get("casts");
        List<Map<String, Object>> castList = actors != null ? actors : casts;
        if (castList != null && !castList.isEmpty()) {
            m.setCast(castList.stream().map(c -> JsonUtil.getStr(c, "name")).filter(s -> !s.isEmpty()).reduce((a, b) -> a + " / " + b).orElse("演员信息待同步"));
        } else {
            m.setCast(JsonUtil.getStr(item, "cast", "演员信息待同步"));
        }

        m.setRegion(JsonUtil.getStr(item, "countries", "region"));
        m.setLanguage(JsonUtil.getStr(item, "languages", "language"));

        // 上映日期
        Object pubdate = item.get("pubdate");
        if (pubdate instanceof List && !((List<?>) pubdate).isEmpty()) {
            m.setDate(String.valueOf(((List<?>) pubdate).get(0)));
        } else {
            m.setDate(JsonUtil.getStr(item, "date", "pubdates"));
        }

        // 片长
        Object durations = item.get("durations");
        if (durations instanceof List && !((List<?>) durations).isEmpty()) {
            m.setDuration(String.valueOf(((List<?>) durations).get(0)));
        } else {
            m.setDuration(JsonUtil.getStr(item, "duration"));
        }

        // 简介
        m.setSummary(JsonUtil.getStr(item, "intro", "summary", "desc"));
        if (m.getSummary().isEmpty()) {
            Map<String, Object> shortComment = (Map<String, Object>) item.get("short_comment");
            if (shortComment != null) m.setSummary(JsonUtil.getStr(shortComment, "content"));
        }
        if (m.getSummary().isEmpty()) m.setSummary("详情简介待同步。");

        // 海报
        Map<String, Object> pic = (Map<String, Object>) item.get("pic");
        if (pic != null) {
            m.setPosterUrl(JsonUtil.getStr(pic, "large", "normal"));
        }
        if (m.getPosterUrl() == null || m.getPosterUrl().isEmpty()) {
            m.setPosterUrl(JsonUtil.getStr(item, "cover_url", "cover", "img"));
        }
        m.setPoster(JsonUtil.getStr(item, "poster"));
        m.setSource("douban");
        return m;
    }

    private static Movie normalizeSearchItem(Map<String, Object> item) {
        Movie m = normalizeMovie(item);
        String abstract2 = JsonUtil.getStr(item, "abstract_2");
        if (!abstract2.isEmpty()) m.setDirector(abstract2);
        String abs = JsonUtil.getStr(item, "abstract");
        if (!abs.isEmpty()) {
            if (m.getCast().isEmpty() || m.getCast().equals("演员信息待同步")) m.setCast(abs);
            if (m.getSummary().isEmpty() || m.getSummary().equals("详情简介待同步。")) m.setSummary(abs);
        }
        m.setSource("douban-search");
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Comment normalizeInterest(Map<String, Object> item, int index) {
        Comment c = new Comment();
        c.setId(JsonUtil.getStr(item, "id"));
        if (c.getId().isEmpty()) c.setId("comment-" + index);

        String text = JsonUtil.getStr(item, "comment", "content", "text");
        c.setText(text.isEmpty() ? "这条评论没有公开正文。" : text);

        Map<String, Object> rating = (Map<String, Object>) item.get("rating");
        int value = rating != null ? JsonUtil.getInt(rating, "value", 0) : JsonUtil.getInt(item, "rating", 0);
        c.setRatingValue(value);
        c.setStar(value > 0 ? value + "星" : "未评分");

        Map<String, Object> user = (Map<String, Object>) item.get("user");
        if (user != null) {
            c.setUser(JsonUtil.getStr(user, "name"));
        } else {
            Map<String, Object> author = (Map<String, Object>) item.get("author");
            c.setUser(author != null ? JsonUtil.getStr(author, "name") : "豆瓣用户");
        }
        if (c.getUser().isEmpty()) c.setUser("豆瓣用户");

        c.setCreatedAt(JsonUtil.getStr(item, "create_time", "created_at"));
        c.setVoteCount(JsonUtil.getInt(item, "vote_count", "useful_count", 0));
        return c;
    }

    // ─── 搜索页 HTML 解析（花括号匹配提取 JSON）───
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractSearchData(String html) {
        String marker = "window.__DATA__";
        int startIdx = html.indexOf(marker);
        if (startIdx == -1) return Collections.emptyList();

        int eqIdx = html.indexOf("=", startIdx);
        if (eqIdx == -1) return Collections.emptyList();
        int braceIdx = html.indexOf("{", eqIdx);
        if (braceIdx == -1) return Collections.emptyList();

        // 花括号匹配
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = braceIdx; i < html.length(); i++) {
            char ch = html.charAt(i);
            if (escape) { escape = false; continue; }
            if (ch == '\\') { escape = true; continue; }
            if (ch == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    String jsonStr = html.substring(braceIdx, i + 1);
                    try {
                        Map<String, Object> data = JsonUtil.getMapper().readValue(jsonStr, Map.class);
                        Object items = data.get("items");
                        if (items instanceof List) return (List<Map<String, Object>>) items;
                    } catch (Exception e) {
                        return Collections.emptyList();
                    }
                }
            }
        }
        return Collections.emptyList();
    }
}
