package com.filmoracle.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.filmoracle.service.*;
import com.filmoracle.util.JsonUtil;
import com.filmoracle.util.HttpUtil;
import com.filmoracle.util.FallbackData;
import com.filmoracle.model.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * API 路由 Servlet——处理所有 /api/* 请求
 * 第二阶段：豆瓣数据接口（搜索/详情/评论）
 * 第三阶段：分析接口（情感分析/图表数据）
 * 第四阶段：AI API 集成 + 海报代理
 */
public class ApiServlet extends HttpServlet {
    private static final Pattern MOVIE_DETAIL = Pattern.compile("^/api/movie/([^/]+)$");
    private static final Pattern MOVIE_INTERESTS = Pattern.compile("^/api/movie/([^/]+)/interests$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long startTime = System.currentTimeMillis();
        String full = req.getServletPath() + (req.getPathInfo() != null ? req.getPathInfo() : "");

        // 海报代理——绕过豆瓣防盗链
        if (full.equals("/api/poster")) {
            handlePosterProxy(req, resp);
            return;
        }

        // 认证检查
        boolean isPublicGet = full.equals("/api/health") || full.equals("/api/hot") || full.startsWith("/api/auth/");
        if (!isPublicGet) {
            Map<String, Object> authUser = AuthService.checkSession(req);
            if (authUser == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            if ("admin".equals(authUser.get("role")) && (full.equals("/api/search") || full.startsWith("/api/movie/") || full.equals("/api/tags") || full.equals("/api/top"))) {
                sendJson(resp, 403, Map.of("ok", false, "error", "管理员无法访问搜索功能"));
                return;
            }
        }

        // 认证端点
        if (full.equals("/api/auth/check")) {
            Map<String, Object> user = AuthService.checkSession(req);
            if (user != null) {
                sendJson(resp, 200, Map.of("ok", true, "user", user));
            } else {
                sendJson(resp, 200, Map.of("ok", false));
            }
            return;
        }

        // 浏览历史 GET
        if (full.equals("/api/history")) {
            Map<String, Object> user = AuthService.checkSession(req);
            if (user == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            List<Map<String, Object>> history = HistoryService.loadHistory(String.valueOf(user.get("username")));
            sendJson(resp, 200, Map.of("ok", true, "history", history));
            return;
        }

        // 系统设置 GET
        if (full.equals("/api/settings")) {
            Map<String, Object> user = AuthService.checkSession(req);
            if (user == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            boolean isAdmin = "admin".equals(user.get("role"));
            Map<String, Object> settings = SettingService.getSettings();
            if (!isAdmin) {
                String apiKey = String.valueOf(settings.getOrDefault("apiKey", ""));
                if (apiKey.length() > 6) {
                    settings.put("apiKey", apiKey.substring(0, 6) + "***");
                }
            }
            sendJson(resp, 200, Map.of("ok", true, "settings", settings));
            return;
        }

        try {
            Object result = null;

            if (full.equals("/api/health")) {
                Map<String, Object> health = new LinkedHashMap<>();
                health.put("ok", true);
                health.put("service", "FilmOracle Java Backend");
                health.put("version", "3.0");
                health.put("cacheSize", CacheService.size());
                health.put("cacheKeys", CacheService.keys());
                result = health;
            }
            else if (full.equals("/api/hot")) {
                int start = parseInt(req.getParameter("start"), 0);
                int limit = parseInt(req.getParameter("limit"), 20);
                result = DoubanApiService.getHotMovies(start, limit);
                logRequest("HOT", startTime, result);
            }
            else if (full.equals("/api/search")) {
                result = DoubanApiService.searchMovies(req.getParameter("q"));
                logRequest("SEARCH", startTime, result);
            }
            else if (full.equals("/api/tags")) {
                String tag = req.getParameter("tag") != null ? req.getParameter("tag") : "热门";
                String sort = req.getParameter("sort") != null ? req.getParameter("sort") : "recommend";
                int limit = parseInt(req.getParameter("limit"), 20);
                int start = parseInt(req.getParameter("start"), 0);
                result = DoubanApiService.getMoviesByTag(tag, sort, limit, start);
                logRequest("TAGS", startTime, result);
            }
            else if (full.equals("/api/top")) {
                String type = req.getParameter("type") != null ? req.getParameter("type") : "24";
                int start = parseInt(req.getParameter("start"), 0);
                int limit = parseInt(req.getParameter("limit"), 20);
                result = DoubanApiService.getTopMovies(type, start, limit);
                logRequest("TOP", startTime, result);
            }
            else {
                Matcher detailMatch = MOVIE_DETAIL.matcher(full);
                Matcher interestMatch = MOVIE_INTERESTS.matcher(full);
                if (detailMatch.matches()) {
                    result = DoubanApiService.getMovieDetail(detailMatch.group(1));
                    logRequest("DETAIL", startTime, result);
                } else if (interestMatch.matches()) {
                    int count = parseInt(req.getParameter("count"), 100);
                    result = DoubanApiService.getMovieInterests(interestMatch.group(1), count);
                    logRequest("INTERESTS", startTime, result);
                } else {
                    sendJson(resp, 404, Map.of("ok", false, "error", "unknown: " + full));
                    return;
                }
            }
            sendJson(resp, 200, result);
        } catch (Exception e) {
            System.err.println("[API ERROR] " + full + ": " + e.getMessage());
            sendJson(resp, 500, Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String full = req.getServletPath() + (req.getPathInfo() != null ? req.getPathInfo() : "");

        // 认证端点
        if (full.equals("/api/auth/login")) {
            try {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                Map<String, Object> reqBody = JsonUtil.getMapper().readValue(body.toString(), Map.class);
                String username = JsonUtil.getStr(reqBody, "username");
                String password = JsonUtil.getStr(reqBody, "password");
                Map<String, Object> result = AuthService.login(username, password, req);
                sendJson(resp, 200, result);
            } catch (Exception e) {
                sendJson(resp, 500, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }

        if (full.equals("/api/auth/register")) {
            try {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                Map<String, Object> reqBody = JsonUtil.getMapper().readValue(body.toString(), Map.class);
                String username = JsonUtil.getStr(reqBody, "username");
                String password = JsonUtil.getStr(reqBody, "password");
                String role = JsonUtil.getStr(reqBody, "role");
                Map<String, Object> result = AuthService.register(username, password, role);
                sendJson(resp, 200, result);
            } catch (Exception e) {
                sendJson(resp, 500, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }

        if (full.equals("/api/auth/logout")) {
            AuthService.logout(req);
            sendJson(resp, 200, Map.of("ok", true));
            return;
        }

        // POST /api/history — 保存浏览历史
        if (full.equals("/api/history")) {
            Map<String, Object> histUser = AuthService.checkSession(req);
            if (histUser == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            try {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                Map<String, Object> movieData = JsonUtil.getMapper().readValue(body.toString(), Map.class);
                Map<String, Object> result = HistoryService.saveHistory(String.valueOf(histUser.get("username")), movieData);
                sendJson(resp, 200, result);
            } catch (Exception e) {
                sendJson(resp, 500, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }

        // POST /api/settings — 更新系统设置（仅管理员）
        if (full.equals("/api/settings")) {
            Map<String, Object> settingsUser = AuthService.checkSession(req);
            if (settingsUser == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            if (!"admin".equals(settingsUser.get("role"))) {
                sendJson(resp, 403, Map.of("ok", false, "error", "仅管理员可修改设置"));
                return;
            }
            try {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> reqBody = JsonUtil.getMapper().readValue(body.toString(), Map.class);
                Map<String, Object> result = SettingService.updateSettings(reqBody);
                sendJson(resp, 200, result);
            } catch (Exception e) {
                sendJson(resp, 500, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }

        // POST /api/analyze — 分析评论（支持 AI 和规则引擎）
        if (full.equals("/api/analyze")) {
            // 认证检查
            Map<String, Object> analyzeUser = AuthService.checkSession(req);
            if (analyzeUser == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            if ("admin".equals(analyzeUser.get("role"))) {
                sendJson(resp, 403, Map.of("ok", false, "error", "管理员无法使用分析功能"));
                return;
            }
            try {
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = req.getReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                Map<String, Object> requestBody = JsonUtil.getMapper().readValue(body.toString(), Map.class);

                List<Map<String, Object>> commentMaps = (List<Map<String, Object>>) requestBody.get("comments");
                Map<String, Object> movieMap = (Map<String, Object>) requestBody.get("movie");

                // AI 参数：从数据库读取（不依赖前端传值，避免掩码Key问题）
                Map<String, Object> dbSettings = SettingService.getSettings();
                Object apiKeyObj = dbSettings.get("apiKey");
                String apiKey = apiKeyObj != null ? apiKeyObj.toString() : "";
                if ("null".equals(apiKey)) apiKey = "";
                Object modelObj = dbSettings.get("aiModel");
                String aiModel = modelObj != null ? modelObj.toString() : "moonshot-v1-8k";
                if ("null".equals(aiModel)) aiModel = "moonshot-v1-8k";
                Object promptObj = dbSettings.get("aiPrompt");
                String aiPrompt = promptObj != null ? promptObj.toString() : "";
                if ("null".equals(aiPrompt) || aiPrompt.isEmpty()) aiPrompt = AiService.DEFAULT_PROMPT;

                // 解析评论
                List<Comment> allComments = new ArrayList<>();
                if (commentMaps != null) {
                    for (int i = 0; i < commentMaps.size(); i++) {
                        Map<String, Object> cm = commentMaps.get(i);
                        Comment c = new Comment();
                        c.setId(JsonUtil.getStr(cm, "id"));
                        if (c.getId().isEmpty()) c.setId("comment-" + i);
                        c.setText(JsonUtil.getStr(cm, "text", "content", "comment"));
                        c.setRatingValue(JsonUtil.getInt(cm, "ratingValue", "rating", 3));
                        c.setStar(c.getRatingValue() > 0 ? c.getRatingValue() + "星" : "未评分");
                        c.setUser(JsonUtil.getStr(cm, "user", "name"));
                        if (c.getUser().isEmpty()) c.setUser("豆瓣用户");
                        c.setVoteCount(JsonUtil.getInt(cm, "voteCount", "useful_count", 0));
                        allComments.add(c);
                    }
                }
                if (allComments.isEmpty()) allComments = FallbackData.getFallbackComments();

                // 解析电影
                Movie movie = new Movie();
                if (movieMap != null) {
                    movie.setId(JsonUtil.getStr(movieMap, "id"));
                    movie.setTitle(JsonUtil.getStr(movieMap, "title", "name"));
                    movie.setRating(JsonUtil.getStr(movieMap, "rating"));
                    movie.setVotes(JsonUtil.getStr(movieMap, "votes", "vote_count"));
                    movie.setGenre(JsonUtil.getStr(movieMap, "genre"));
                    movie.setYear(JsonUtil.getStr(movieMap, "year"));
                    movie.setDirector(JsonUtil.getStr(movieMap, "director"));
                }
                if (movie.getTitle().isEmpty()) movie = FallbackData.getFallbackMovies().get(0);

                // 使用本次爬取到的全部有效评论参与分析。展示版需要让 AI 看到完整口碑分布，
                // 只过滤空文本，不再固定截断 50 条。
                List<Comment> valuableComments = AnalysisService.filterValuableComments(allComments, allComments.size());

                AnalysisResult analysis;
                String engine;

                // 如果有 API Key，尝试用 AI 分析
                if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-***")) {
                    System.out.println("[ANALYZE] Using AI: model=" + aiModel + ", comments=" + valuableComments.size());
                    analysis = AiService.analyzeWithAi(valuableComments, movie, apiKey, aiModel, aiPrompt);
                    if (analysis != null) {
                        engine = "ai:" + aiModel;
                        System.out.println("[ANALYZE] AI analysis succeeded");
                    } else {
                        System.out.println("[ANALYZE] AI analysis failed, falling back to rule-based");
                        analysis = AnalysisService.analyze(valuableComments, movie);
                        engine = "rule-based (AI failed)";
                    }
                } else {
                    System.out.println("[ANALYZE] No API key, using rule-based engine");
                    analysis = AnalysisService.analyze(valuableComments, movie);
                    engine = "rule-based";
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("analysis", analysis);
                result.put("comments", allComments);
                result.put("meta", Map.of(
                    "source", "analysis",
                    "engine", engine,
                    "totalComments", allComments.size(),
                    "analyzedComments", valuableComments.size()
                ));

                sendJson(resp, 200, result);
                System.out.println("[ANALYZE] Done: " + allComments.size() + " total, " + valuableComments.size() + " analyzed, engine=" + engine);
            } catch (Exception e) {
                System.err.println("[ANALYZE ERROR] " + e.getMessage());
                e.printStackTrace();
                sendJson(resp, 500, Map.of("ok", false, "error", e.getMessage()));
            }
            return;
        }

        // 其他 POST 请求走 GET
        doGet(req, resp);
    }

    /**
     * 海报代理——服务端请求豆瓣图片，绕过浏览器防盗链
     */
    private void handlePosterProxy(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String posterUrl = req.getParameter("url");
        if (posterUrl == null || posterUrl.isEmpty()) {
            resp.setStatus(404);
            return;
        }
        try {
            byte[] imageBytes = HttpUtil.getBytes(posterUrl, 10);
            // 根据内容判断 content-type
            String contentType = "image/jpeg";
            if (posterUrl.toLowerCase().contains(".png")) contentType = "image/png";
            else if (posterUrl.toLowerCase().contains(".webp")) contentType = "image/webp";
            resp.setContentType(contentType);
            resp.setHeader("Cache-Control", "public, max-age=86400"); // 缓存1天
            resp.getOutputStream().write(imageBytes);
            resp.getOutputStream().flush();
        } catch (Exception e) {
            resp.setStatus(404);
            System.err.println("[POSTER PROXY] Failed: " + posterUrl + " - " + e.getMessage());
        }
    }

    private void sendJson(HttpServletResponse resp, int status, Object data) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=utf-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(JsonUtil.toJson(data));
    }

    private int parseInt(String value, int def) {
        if (value == null || value.isBlank()) return def;
        try { return Integer.parseInt(value); } catch (Exception e) { return def; }
    }

    private void logRequest(String label, long startTime, Object result) {
        long ms = System.currentTimeMillis() - startTime;
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            Map<?, ?> meta = (Map<?, ?>) map.get("meta");
            String source = meta != null ? String.valueOf(meta.get("source")) : "unknown";
            int count = 0;
            if (map.containsKey("movies")) count = ((List<?>) map.get("movies")).size();
            else if (map.containsKey("comments")) count = ((List<?>) map.get("comments")).size();
            System.out.println("[" + label + "] " + source + " " + count + " items (" + ms + "ms)");
        }
    }
}
