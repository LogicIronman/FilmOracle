package com.filmoracle.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 工具类（基于 Java 21 HttpClient）
 * 用于请求豆瓣 API，携带正确的 User-Agent 和 Referer
 */
public class HttpUtil {
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // 移动端请求头
    private static final Map<String, String> headersMobile = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
            "Referer", "https://m.douban.com/movie/",
            "Accept", "application/json,text/html;q=0.9,*/*;q=0.8",
            "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6"
    );

    // PC端请求头
    private static final Map<String, String> headersPC = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
            "Accept", "application/json,text/html;q=0.9,*/*;q=0.8",
            "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6"
    );

    // Frodo(微信小程序)请求头
    private static final Map<String, String> headersFrodo = Map.of(
            "User-Agent", "MicroMessenger/",
            "Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/91/page-frame.html",
            "Accept", "application/json"
    );

    // 图片代理请求头（用于绕过豆瓣防盗链）
    private static final Map<String, String> headersImage = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
            "Referer", "https://movie.douban.com/",
            "Accept", "image/webp,image/apng,image/*,*/*;q=0.8"
    );

    // 搜索请求头（带bid cookie，绕过豆瓣搜索需要登录的限制）
    private static final String BID = generateBid();
    private static final Map<String, String> headersSearch = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
            "Referer", "https://m.douban.com/search/",
            "Accept", "application/json,text/html;q=0.9,*/*;q=0.8",
            "Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6",
            "Cookie", "bid=" + BID
    );

    private static String generateBid() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    /**
     * GET 请求（带重试和超时）
     * @param url 请求 URL
     * @param headerType 请求头类型: "mobile" / "pc" / "frodo" / "image"
     * @param maxRetries 最大重试次数
     * @param timeoutSeconds 超时秒数
     * @return 响应体文本
     */
    public static String get(String url, String headerType, int maxRetries, int timeoutSeconds) throws Exception {
        Map<String, String> headers = switch (headerType) {
            case "pc" -> headersPC;
            case "frodo" -> headersFrodo;
            case "image" -> headersImage;
            case "search" -> headersSearch;
            default -> headersMobile;
        };

        Exception lastError = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .GET();
                headers.forEach(builder::header);
                HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    return resp.body();
                }
                throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body().substring(0, Math.min(200, resp.body().length())));
            } catch (Exception e) {
                lastError = e;
                if (attempt < maxRetries) {
                    long delay = (long) (400 * Math.pow(1.8, attempt) + Math.random() * 200);
                    Thread.sleep(delay);
                    System.out.println("  [retry " + (attempt + 1) + "/" + maxRetries + "] " + url.substring(0, Math.min(80, url.length())));
                }
            }
        }
        throw lastError;
    }

    /** 默认移动端请求 */
    public static String getMobile(String url) throws Exception {
        return get(url, "mobile", 2, 10);
    }

    /** PC端请求 */
    public static String getPC(String url) throws Exception {
        return get(url, "pc", 2, 10);
    }

    /** 获取 JSON 并解析为 Map */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> getJsonAsMap(String url, String headerType) throws Exception {
        String body = get(url, headerType, 2, 10);
        return JsonUtil.getMapper().readValue(body, java.util.Map.class);
    }

    /**
     * 获取图片字节数据（用于海报代理，绕过防盗链）
     */
    public static byte[] getBytes(String url, int timeoutSeconds) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();
        headersImage.forEach(builder::header);
        HttpResponse<byte[]> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() == 200) {
            return resp.body();
        }
        throw new RuntimeException("HTTP " + resp.statusCode() + " for image: " + url);
    }

    /**
     * POST 请求（发送 JSON，用于调用 AI API）
     * @param url 请求 URL
     * @param jsonBody JSON 请求体
     * @param extraHeaders 额外请求头（如 Authorization）
     * @param timeoutSeconds 超时秒数
     * @return 响应体文本
     */
    public static String post(String url, String jsonBody, Map<String, String> extraHeaders, int timeoutSeconds) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, java.nio.charset.StandardCharsets.UTF_8));
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            return resp.body();
        }
        throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
    }
}
