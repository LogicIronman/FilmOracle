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

    private static final String DEFAULT_API_URL = "https://api.deepseek.com/v1/chat/completions";

    /**
     * 默认 AI 提示词（详尽版）
     */
    public static final String DEFAULT_PROMPT = """
        你是一位专业的电影评论分析师。请分析以下电影评论数据，输出纯 JSON 格式的分析结果（不要 markdown，不要 ```json 标记）。

        分析要求：

        1. 优点提炼：
        从评论中找出观众反复提及的亮点，具体到场景、段落、人物关系、视听细节、叙事设计或主题表达，引用评论者的原话或观点。不要泛泛说"演技好、画面美、剧情好"。

        2. 缺点与争议：
        诚实指出评论中暴露的问题，例如节奏拖沓、人物动机不足、反转刻意、逻辑不通、表达用力、角色单薄、情绪操控明显等。如果评论间存在分歧，要呈现这种张力，不要回避。

        3. 横向比较：
        将本片与同类型、同题材、同导演或评论中提到的参照作品进行比较。比较必须落到具体手法上，例如叙事策略、视听表达、主题处理、人物塑造、类型完成度，而不是空泛地说"更深刻"或"更好"。

        4. AI 影评生成：
        请根据全部评论生成一段结构清晰、有判断力的 AI 影评。影评必须先概括观众对这部电影的整体评价，包括好评率、差评率、中性评价比例，以及评论整体是偏向一致好评、两极分化，还是褒贬不一。

        影评需要明确说明：
        - 观众总体怎么看这部电影。
        - 好评率是多少，必须引用 sentimentDistribution 或 summary 中的 positiveRate。
        - 出彩的地方在哪里：从评论中提炼观众反复提到的亮点。
        - 不出彩的地方在哪里：指出评论中暴露的问题。
        - 如果评论存在分歧，要说明分歧点。
        - 最后做整体评价：说明这部电影适合什么样的观众，主要价值在哪里，局限在哪里。

        影评语气要求：像专业影评人，每个判断都要能从评论中找到依据，优缺点比例要符合评论真实分布，字数约 400 字。

        5. 关键词：
        关键词必须来自评论中的经典总结词语或高频短语，避免"剧情、演技、画面"等通用词。

        6. 十维雷达图：
        十维雷达图必须拉开差距，批评维度低到 4-6 分，突出维度可到 8.5-9.6 分。十维包括：剧本、导演、表演、摄影、剪辑、声音、美术、特效、主题、完成度。

        7. scatter（方面级情感分析）：
        对评论做方面级情感分析，每条评论可拆成多条记录（每条对应一个方面）。
        - aspect：枚举值 剧情|演技|视听|节奏|主题|配乐|美术|结尾
        - polarity：-5.0到5.0，负值=差评，正值=好评
        - intensity：0.0到5.0，0=平淡，5=强烈情绪
        - votes：点赞数（没有填0）
        - text：评论原文摘录，最多60字
        一条评论涉及多个方面就拆分。至少输出8条。反讽按真实情感打分。

        8. emotionMap：基于scatter统计四象限。
        - quadrants：每个含name/position/count/percent/description
        - centroid：x(polarity均值)/y(intensity均值)/label
        - summary：dominantAspect/distributionSummary/interpretation
        四象限：rightTop=狂热好评, leftTop=强烈差评, leftBottom=差强人意, rightBottom=比较推荐

        9. 情感分布：positive、negative、neutral 百分比总和必须为 100。
        10. 星级分布：5星到1星占比总和必须为 100。
        11. 输出要求：只输出一个合法 JSON 对象，不要输出解释、markdown、代码块标记。所有百分比使用整数，分数保留 1 位小数，坐标保留 2 位小数。

        输出 JSON 格式：
        {
          "keywords": [["关键词", 次数]],
          "ratingDistribution": [["5星", 百分比], ["4星", 百分比], ["3星", 百分比], ["2星", 百分比], ["1星", 百分比]],
          "comparison": [["本片评分", 值], ["同类型热度", 值], ["评价人数", 值], ["正向情绪", 值]],
          "radar": [["剧本", 分数], ["导演", 分数], ["表演", 分数], ["摄影", 分数], ["剪辑", 分数], ["声音", 分数], ["美术", 分数], ["特效", 分数], ["主题", 分数], ["完成度", 分数]],
          "scatter": [{"aspect": "方面", "polarity": -5.0到5.0, "intensity": 0.0到5.0, "votes": 点赞数, "text": "评论摘录"}],
          "emotionMap": {
            "quadrants": [
              {"name": "狂热好评", "position": "rightTop", "count": 数量, "percent": 百分比, "description": "高强度正面"},
              {"name": "强烈差评", "position": "leftTop", "count": 数量, "percent": 百分比, "description": "高强度负面"},
              {"name": "差强人意", "position": "leftBottom", "count": 数量, "percent": 百分比, "description": "低强度负面"},
              {"name": "比较推荐", "position": "rightBottom", "count": 数量, "percent": 百分比, "description": "低强度正面"}
            ],
            "centroid": {"x": 值, "y": 值, "label": "重心说明"},
            "summary": {"dominantAspect": "主导方面", "distributionSummary": "分布总结", "interpretation": "解读"}
          },
          "sentimentDistribution": {"positive": 百分比, "negative": 百分比, "neutral": 百分比},
          "summary": {"positiveRate": 值, "negativeRate": 值, "neutralRate": 值, "keywordsSummary": "关键词摘要", "mainControversy": "争议点", "totalComments": 数量},
          "review": {
            "overallReception": "观众整体评价概括，包含好评率、差评率、中性比例",
            "highlightPoints": ["出彩点1", "出彩点2"],
            "weaknesses": ["不足点1", "不足点2"],
            "finalJudgement": "整体评价：适合什么观众、价值、局限",
            "fullText": "约400字完整AI影评"
          }
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
    public static AnalysisResult analyzeWithAi(List<Comment> comments, Movie movie, String apiKey, String model, String customPrompt, String apiUrl) {
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

            System.out.println("[AI] Calling AI API: url=" + (apiUrl != null && !apiUrl.isBlank() ? apiUrl : DEFAULT_API_URL) + ", model=" + model + ", comments=" + comments.size());
            String response = HttpUtil.post(apiUrl != null && !apiUrl.isBlank() ? apiUrl : DEFAULT_API_URL, jsonBody, headers, 60);
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

            // review - 可能是字符串（旧格式）或对象（新格式）
            Object reviewObj = aiResult.get("review");
            if (reviewObj instanceof Map) {
                // 新格式：对象，转为JSON字符串存储
                result.setReview(JsonUtil.toJson(reviewObj));
            } else if (reviewObj instanceof String) {
                result.setReview((String) reviewObj);
            } else {
                result.setReview(JsonUtil.getStr(aiResult, "review"));
            }

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
