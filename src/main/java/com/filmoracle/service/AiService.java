package com.filmoracle.service;

import com.filmoracle.model.AnalysisResult;
import com.filmoracle.model.Comment;
import com.filmoracle.model.Movie;
import com.filmoracle.util.HttpUtil;
import com.filmoracle.util.JsonUtil;
import java.util.*;

/**
 * AI 分析服务——调用 Moonshot/Kimi API 进行评论分析
 * 当用户提供 API Key 时使用，否则回退到 AnalysisService 规则引擎
 */
public class AiService {

    private static final String MOONSHOT_URL = "https://api.moonshot.cn/v1/chat/completions";

    /**
     * 默认 AI 提示词（详尽版）
     */
    public static final String DEFAULT_PROMPT = """
        你是一位专业的电影评论分析师。请分析以下电影评论数据，输出纯JSON格式的分析结果（不要markdown，不要```json标记）。

        分析要求：
        1. 优点提炼：从评论中找出观众反复提及的亮点。不要泛泛说"演技好、画面美"，要具体到哪个场景、哪个细节被称赞，引用评论者的原话或观点。
        2. 缺点与争议：诚实指出评论中暴露的问题。如果评论间存在分歧，要呈现这种张力。
        3. 横向比较：将本片与同类型、同题材作品比较，落到具体手法上。
        4. 总评：约400字综合评析，包含优缺点、横向比较、结论。
        5. 关键词必须来自评论中的经典总结词语或高频短语，避免"剧情、演技、画面"等通用词。
        6. 十维雷达图必须拉开差距，批评维度低到4-6分，突出维度可到8.5-9.6分。
        7. scatter中每条评论需要判断x(-1到1, 情绪倾向)、y(0到1, 情绪强度)、sentiment、emotionLabel(具体情绪如压抑/热血/感动/遗憾/治愈/孤独/过誉)、quadrant、weight(1-10)、quote(评论摘要前30字)。
        8. emotionMap需包含四象限统计(含count和percent)、情绪重心centroid(x,y,label)、summary(dominantEmotion, distributionSummary, interpretation)。

        输出JSON格式（纯JSON，不要markdown标记）：
        {
          "keywords": [["关键词", 次数], ...],
          "ratingDistribution": [["5星", 百分比], ["4星", 百分比], ["3星", 百分比], ["2星", 百分比], ["1星", 百分比]],
          "comparison": [["本片评分", 值], ["同类型热度", 值], ["评价人数", 值], ["正向情绪", 值]],
          "radar": [["剧本", 分数], ["导演", 分数], ["表演", 分数], ["摄影", 分数], ["剪辑", 分数], ["声音", 分数], ["美术", 分数], ["特效", 分数], ["主题", 分数], ["完成度", 分数]],
          "scatter": [{"x": 坐标, "y": 坐标, "sentiment": "正面/负面/中性", "emotionLabel": "情绪标签", "quadrant": "象限", "weight": 权重, "quote": "评论摘要", "index": 序号}, ...],
          "emotionMap": {
            "quadrants": [{"name": "压抑/惊悚", "position": "leftTop", "count": 数量, "percent": 百分比, "description": "描述"}, ...],
            "centroid": {"x": 值, "y": 值, "label": "重心说明"},
            "summary": {"dominantEmotion": "主导情绪", "distributionSummary": "分布总结", "interpretation": "解读"}
          },
          "sentimentDistribution": {"positive": 百分比, "negative": 百分比, "neutral": 百分比},
          "summary": {"positiveRate": 值, "negativeRate": 值, "neutralRate": 值, "keywordsSummary": "关键词摘要", "mainControversy": "争议点", "totalComments": 数量},
          "review": "400字综合评析"
        }
        """;

    /**
     * 调用 AI API 分析评论
     * @param comments 筛选后的有价值评论
     * @param movie 电影信息
     * @param apiKey Moonshot API Key
     * @param model 模型名称（如 moonshot-v1-8k）
     * @param customPrompt 自定义提示词（为空则用默认）
     * @return AnalysisResult 分析结果，失败返回 null
     */
    @SuppressWarnings("unchecked")
    public static AnalysisResult analyzeWithAi(List<Comment> comments, Movie movie, String apiKey, String model, String customPrompt) {
        try {
            String prompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : DEFAULT_PROMPT;

            // 构建评论文本
            StringBuilder commentText = new StringBuilder();
            commentText.append("电影：").append(movie.getTitle()).append("（").append(movie.getYear()).append("）\n");
            commentText.append("类型：").append(movie.getGenre()).append("\n");
            commentText.append("评分：").append(movie.getRating()).append("\n");
            commentText.append("导演：").append(movie.getDirector()).append("\n\n");
            commentText.append("评论数据（共").append(comments.size()).append("条）：\n\n");

            for (int i = 0; i < comments.size(); i++) {
                Comment c = comments.get(i);
                commentText.append(String.format("[%d] %d星 | %s\n%s\n\n",
                        i, c.getRatingValue(), c.getUser(), c.getText()));
            }

            // 构建请求
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model != null && !model.isBlank() ? model : "moonshot-v1-8k");
            request.put("temperature", 0.3);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", prompt));
            messages.add(Map.of("role", "user", "content", commentText.toString()));
            request.put("messages", messages);

            String jsonBody = JsonUtil.toJson(request);

            // 调用 API
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + apiKey);

            System.out.println("[AI] Calling Moonshot API: model=" + model + ", comments=" + comments.size());
            String response = HttpUtil.post(MOONSHOT_URL, jsonBody, headers, 60);
            System.out.println("[AI] Response received, parsing...");

            // 解析响应
            Map<String, Object> respMap = JsonUtil.parseToMap(response);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("AI response has no choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = JsonUtil.getStr(message, "content");

            if (content == null || content.isBlank()) {
                throw new RuntimeException("AI response content is empty");
            }

            // 提取JSON（AI可能在前后加了非JSON文字）
            String json = extractJson(content);
            if (json == null) {
                throw new RuntimeException("AI response is not valid JSON: " + content.substring(0, Math.min(100, content.length())));
            }

            Map<String, Object> aiResult = JsonUtil.parseToMap(json);

            // 映射到 AnalysisResult
            AnalysisResult result = new AnalysisResult();
            result.setEngine("ai");

            // keywords
            result.setKeywords(extractObjectArrayList(aiResult, "keywords"));

            // ratingDistribution
            result.setRatingDistribution(extractObjectArrayList(aiResult, "ratingDistribution"));

            // comparison
            result.setComparison(extractObjectArrayList(aiResult, "comparison"));

            // radar
            result.setRadar(extractObjectArrayList(aiResult, "radar"));

            // scatter
            result.setScatter(extractScatterList(aiResult));

            // emotionMap (if AI returns it)
            Map<String, Object> emotionMap = (Map<String, Object>) aiResult.get("emotionMap");
            if (emotionMap != null) {
                result.setEmotionMap(emotionMap);
            }

            // sentimentDistribution
            Map<String, Object> sd = (Map<String, Object>) aiResult.get("sentimentDistribution");
            if (sd != null) {
                Map<String, Integer> sentimentDist = new LinkedHashMap<>();
                sentimentDist.put("positive", JsonUtil.getInt(sd, "positive", 0));
                sentimentDist.put("negative", JsonUtil.getInt(sd, "negative", 0));
                sentimentDist.put("neutral", JsonUtil.getInt(sd, "neutral", 0));
                result.setSentimentDistribution(sentimentDist);
            }

            // summary
            Map<String, Object> summary = (Map<String, Object>) aiResult.get("summary");
            if (summary != null) {
                result.setSummary(summary);
            }

            // review
            result.setReview(JsonUtil.getStr(aiResult, "review"));

            // analyzedComments - 用规则引擎补充每条评论的分析
            result.setAnalyzedComments(buildAnalyzedComments(comments));

            System.out.println("[AI] Analysis complete: review=" + (result.getReview() != null ? result.getReview().length() : 0) + " chars");

            return result;

        } catch (Exception e) {
            System.err.println("[AI ERROR] " + e.getMessage());
            return null;
        }
    }

    /**
     * 从AI回复中提取JSON字符串
     */
    private static String extractJson(String content) {
        // 去除可能的markdown标记
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            // 去掉 ```json 或 ``` 开头
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) cleaned = cleaned.substring(firstNewline + 1);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();
        }

        // 找到第一个 { 和最后一个 }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return null;
    }

    /**
     * 从Map中提取 List<Object[]>
     */
    @SuppressWarnings("unchecked")
    private static List<Object[]> extractObjectArrayList(Map<String, Object> map, String key) {
        Object obj = map.get(key);
        if (!(obj instanceof List)) return null;
        List<?> list = (List<?>) obj;
        List<Object[]> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof List) {
                List<?> pair = (List<?>) item;
                if (pair.size() >= 2) {
                    result.add(new Object[]{pair.get(0), pair.get(1)});
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 从Map中提取散点数据
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractScatterList(Map<String, Object> map) {
        Object obj = map.get("scatter");
        if (!(obj instanceof List)) return null;
        List<?> list = (List<?>) obj;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * 用规则引擎为每条评论生成分析数据（补充AI可能遗漏的逐条分析）
     */
    private static List<Map<String, Object>> buildAnalyzedComments(List<Comment> comments) {
        List<Map<String, Object>> analyzed = new ArrayList<>();
        for (int i = 0; i < comments.size(); i++) {
            Comment c = comments.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("sentiment", c.getSentiment() != null ? c.getSentiment() : "中性");
            item.put("aspect", c.getAspect() != null ? c.getAspect() : "完成度与影响力");
            item.put("quadrant", c.getQuadrant() != null ? c.getQuadrant() : "孤独/克制");
            double[] xy = AnalysisService.calculateXYForComment(c);
            item.put("x", Math.round(xy[0] * 100) / 100.0);
            item.put("y", Math.round(xy[1] * 100) / 100.0);
            item.put("keywords", new ArrayList<String>());
            item.put("confidence", 0.7);
            analyzed.add(item);
        }
        return analyzed;
    }
}
