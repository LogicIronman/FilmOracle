package com.filmoracle.web;

import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.*;
import com.filmoracle.service.*;
import com.filmoracle.util.JsonUtil;
import com.filmoracle.util.HttpUtil;
import com.filmoracle.util.FallbackData;
import com.filmoracle.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.*;

/**
 * API 路由 Servlet——处理所有 /api/* 请求
 * 第二阶段：豆瓣数据接口（搜索/详情/评论）
 * 第三阶段：分析接口（情感分析/图表数据）
 * 第四阶段：AI API 集成 + 海报代理
 */
@MultipartConfig(
        location = "/app/uploads",
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 10 * 1024 * 1024,
        maxRequestSize = 12 * 1024 * 1024
)
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

        // 评论管理 GET：按电影、关键词、情感类型查询已持久化评论
        if (full.equals("/api/comments")) {
            List<Map<String, Object>> comments = CommentService.query(
                    req.getParameter("movie"),
                    req.getParameter("keyword"),
                    req.getParameter("sentiment"),
                    parseInt(req.getParameter("limit"), 100)
            );
            sendJson(resp, 200, Map.of("ok", true, "comments", comments, "count", comments.size()));
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

        // POST /api/comments：单条录入或 JSON 批量导入评论
        if (full.equals("/api/comments")) {
            Map<String, Object> user = AuthService.checkSession(req);
            if (user == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            try {
                Map<String, Object> body = readJsonBody(req);
                Movie movie = movieFromMap((Map<String, Object>) body.get("movie"));
                List<Comment> comments = commentsFromBody(body);
                Map<String, Object> result = CommentService.saveBatch(movie, comments, "manual");
                sendJson(resp, Boolean.TRUE.equals(result.get("ok")) ? 200 : 400, result);
            } catch (Exception e) {
                sendJson(resp, 500, Map.of("ok", false, "error", "评论保存失败: " + e.getMessage()));
            }
            return;
        }

        // POST /api/comments/import：上传 CSV/TXT，文件保存在 Docker 导入卷后批量写入数据库
        if (full.equals("/api/comments/import")) {
            Map<String, Object> user = AuthService.checkSession(req);
            if (user == null) {
                sendJson(resp, 401, Map.of("ok", false, "error", "请先登录"));
                return;
            }
            try {
                Part filePart = req.getPart("file");
                if (filePart == null || filePart.getSize() == 0) {
                    sendJson(resp, 400, Map.of("ok", false, "error", "请选择 CSV 或 TXT 评论文件"));
                    return;
                }
                String originalName = safeFileName(filePart.getSubmittedFileName());
                if (!(originalName.endsWith(".csv") || originalName.endsWith(".txt"))) {
                    sendJson(resp, 400, Map.of("ok", false, "error", "仅支持 CSV 或 TXT 评论文件"));
                    return;
                }
                Path storedFile = storeImportFile(filePart, originalName);
                String content = Files.readString(storedFile, StandardCharsets.UTF_8);
                List<Comment> comments = parseImportedComments(content, originalName);
                Movie movie = new Movie();
                movie.setId(req.getParameter("movieId"));
                movie.setTitle(req.getParameter("movieTitle"));
                movie.setYear(valueOr(req.getParameter("year"), ""));
                movie.setGenre(valueOr(req.getParameter("genre"), ""));
                Map<String, Object> result = CommentService.saveBatch(movie, comments, "import");
                Map<String, Object> response = new LinkedHashMap<>(result);
                response.put("file", storedFile.getFileName().toString());
                response.put("parsed", comments.size());
                sendJson(resp, Boolean.TRUE.equals(result.get("ok")) ? 200 : 400, response);
            } catch (Exception e) {
                sendJson(resp, 500, Map.of("ok", false, "error", "评论文件导入失败: " + e.getMessage()));
            }
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
                String aiModel = modelObj != null ? modelObj.toString() : "deepseek-chat";
                if ("null".equals(aiModel)) aiModel = "deepseek-chat";
                Object promptObj = dbSettings.get("aiPrompt");
                String aiPrompt = promptObj != null ? promptObj.toString() : "";
                if ("null".equals(aiPrompt) || aiPrompt.isEmpty()) aiPrompt = AiService.DEFAULT_PROMPT;
                Object apiUrlObj = dbSettings.get("aiApiUrl");
                String apiUrl = apiUrlObj != null ? apiUrlObj.toString() : "";
                if ("null".equals(apiUrl)) apiUrl = "";

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

                // AI 与规则引擎都使用本次请求携带的完整评论列表：不再按数量截断或筛选。
                // 这样前端单次抓取多少条，AI 就收到多少条。
                List<Comment> analysisComments = new ArrayList<>(allComments);

                AnalysisResult analysis;
                String engine;

                // 如果有 API Key，尝试用 AI 分析
                if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("sk-***")) {
                    System.out.println("[ANALYZE] Using AI: model=" + aiModel + ", comments=" + analysisComments.size());
                    analysis = AiService.analyzeWithAi(analysisComments, movie, apiKey, aiModel, aiPrompt, apiUrl);
                    if (analysis != null) {
                        engine = "ai:" + aiModel;
                        System.out.println("[ANALYZE] AI analysis succeeded");
                    } else {
                        System.out.println("[ANALYZE] AI analysis failed, falling back to rule-based");
                        analysis = AnalysisService.analyze(analysisComments, movie);
                        engine = "rule-based (AI failed)";
                    }
                } else {
                    System.out.println("[ANALYZE] No API key, using rule-based engine");
                    analysis = AnalysisService.analyze(analysisComments, movie);
                    engine = "rule-based";
                }

                // 复用现有分析链路，同时把页面已获取的评论批量写入评论管理库。
                applyAnalyzedLabels(allComments, analysis.getAnalyzedComments());
                Map<String, Object> persistence = CommentService.saveBatch(movie, allComments, "analysis");
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("source", "analysis");
                meta.put("engine", engine);
                meta.put("totalComments", allComments.size());
                meta.put("analyzedComments", analysisComments.size());
                meta.put("persistence", persistence);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("ok", true);
                result.put("analysis", analysis);
                result.put("comments", allComments);
                result.put("meta", meta);

                sendJson(resp, 200, result);
                System.out.println("[ANALYZE] Done: " + allComments.size() + " total, " + analysisComments.size() + " analyzed, engine=" + engine);
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

    private Map<String, Object> readJsonBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        if (body.isEmpty()) return new LinkedHashMap<>();
        return JsonUtil.getMapper().readValue(body.toString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Movie movieFromMap(Map<String, Object> data) {
        Movie movie = new Movie();
        if (data == null) return movie;
        movie.setId(JsonUtil.getStr(data, "id", "movieId"));
        movie.setTitle(JsonUtil.getStr(data, "title", "name", "movieTitle"));
        movie.setYear(JsonUtil.getStr(data, "year"));
        movie.setGenre(JsonUtil.getStr(data, "genre"));
        movie.setRating(JsonUtil.getStr(data, "rating"));
        movie.setVotes(JsonUtil.getStr(data, "votes", "vote_count"));
        movie.setDirector(JsonUtil.getStr(data, "director"));
        movie.setCast(JsonUtil.getStr(data, "cast"));
        movie.setRegion(JsonUtil.getStr(data, "region"));
        movie.setLanguage(JsonUtil.getStr(data, "language"));
        movie.setDate(JsonUtil.getStr(data, "date", "pubdate"));
        movie.setDuration(JsonUtil.getStr(data, "duration"));
        movie.setSummary(JsonUtil.getStr(data, "summary"));
        movie.setPosterUrl(JsonUtil.getStr(data, "posterUrl", "poster_url"));
        return movie;
    }

    @SuppressWarnings("unchecked")
    private List<Comment> commentsFromBody(Map<String, Object> body) {
        List<Comment> comments = new ArrayList<>();
        Object oneComment = body.get("comment");
        if (oneComment instanceof Map) comments.add(commentFromMap((Map<String, Object>) oneComment));
        Object listComment = body.get("comments");
        if (listComment instanceof List) {
            for (Object item : (List<?>) listComment) {
                if (item instanceof Map) comments.add(commentFromMap((Map<String, Object>) item));
            }
        }
        return comments;
    }

    private Comment commentFromMap(Map<String, Object> data) {
        Comment comment = new Comment();
        comment.setId(JsonUtil.getStr(data, "id", "commentId"));
        comment.setText(JsonUtil.getStr(data, "text", "content", "comment"));
        comment.setRatingValue(JsonUtil.getInt(data, "ratingValue", "rating", 0));
        comment.setStar(JsonUtil.getStr(data, "star"));
        comment.setUser(JsonUtil.getStr(data, "user", "name", "userName"));
        comment.setCreatedAt(JsonUtil.getStr(data, "createdAt", "created_at"));
        comment.setVoteCount(JsonUtil.getInt(data, "voteCount", "useful_count", 0));
        comment.setSentiment(JsonUtil.getStr(data, "sentiment"));
        comment.setAspect(JsonUtil.getStr(data, "aspect"));
        comment.setQuadrant(JsonUtil.getStr(data, "quadrant"));
        return comment;
    }

    private void applyAnalyzedLabels(List<Comment> comments, List<Map<String, Object>> analyzedComments) {
        if (analyzedComments == null || analyzedComments.isEmpty()) return;
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> analyzed : analyzedComments) {
            String id = JsonUtil.getStr(analyzed, "id");
            if (!id.isBlank()) byId.put(id, analyzed);
        }
        for (Comment comment : comments) {
            Map<String, Object> analyzed = byId.get(comment.getId());
            if (analyzed == null) continue;
            comment.setSentiment(JsonUtil.getStr(analyzed, "sentiment"));
            comment.setAspect(JsonUtil.getStr(analyzed, "aspect"));
            comment.setQuadrant(JsonUtil.getStr(analyzed, "quadrant"));
        }
    }

    private Path storeImportFile(Part filePart, String originalName) throws IOException {
        Path uploadDir = Path.of(valueOr(System.getenv("UPLOAD_DIR"), "/app/uploads"));
        Files.createDirectories(uploadDir);
        String storedName = System.currentTimeMillis() + "-" + UUID.randomUUID() + "-" + originalName;
        Path target = uploadDir.resolve(storedName);
        try (InputStream input = filePart.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "comments.txt";
        String name = Path.of(fileName).getFileName().toString().replaceAll("[\\r\\n\\\\/:*?\"<>|]", "_");
        return name.isBlank() ? "comments.txt" : name;
    }

    private List<Comment> parseImportedComments(String content, String fileName) {
        List<Comment> comments = new ArrayList<>();
        String[] lines = content.split("\\R");
        boolean csv = fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
        int start = 0;
        int textColumn = 0;
        int ratingColumn = -1;
        int userColumn = -1;
        if (csv && lines.length > 0) {
            List<String> header = parseCsvRow(lines[0]);
            boolean hasHeader = false;
            for (int i = 0; i < header.size(); i++) {
                String value = header.get(i).trim().toLowerCase(Locale.ROOT);
                if (value.contains("text") || value.contains("comment") || value.contains("评论")) {
                    textColumn = i;
                    hasHeader = true;
                }
                if (value.contains("rating") || value.contains("star") || value.contains("星级")) ratingColumn = i;
                if (value.contains("user") || value.contains("name") || value.contains("用户")) userColumn = i;
            }
            if (hasHeader) start = 1;
        }
        for (int lineIndex = start; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (line.isEmpty()) continue;
            String text = line;
            String user = "导入用户";
            int rating = 3;
            if (csv) {
                List<String> cells = parseCsvRow(line);
                if (textColumn >= cells.size()) continue;
                text = cells.get(textColumn).trim();
                if (ratingColumn >= 0 && ratingColumn < cells.size()) rating = parseImportRating(cells.get(ratingColumn), rating);
                if (userColumn >= 0 && userColumn < cells.size() && !cells.get(userColumn).isBlank()) user = cells.get(userColumn).trim();
            } else {
                Matcher star = Pattern.compile("^([1-5])\\s*星\\s*(.*)$").matcher(text);
                if (star.matches()) {
                    rating = Integer.parseInt(star.group(1));
                    text = star.group(2).trim();
                }
            }
            if (text.length() < 2) continue;
            Comment comment = new Comment();
            comment.setId("import-" + (lineIndex + 1));
            comment.setText(text);
            comment.setRatingValue(rating);
            comment.setStar(rating + "星");
            comment.setUser(user);
            comments.add(comment);
        }
        return comments;
    }

    private List<String> parseCsvRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else quoted = !quoted;
            } else if (current == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else cell.append(current);
        }
        cells.add(cell.toString());
        return cells;
    }

    private int parseImportRating(String value, int fallback) {
        Matcher matcher = Pattern.compile("[1-5]").matcher(value == null ? "" : value);
        return matcher.find() ? Integer.parseInt(matcher.group()) : fallback;
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
