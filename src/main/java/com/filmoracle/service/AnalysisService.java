package com.filmoracle.service;

import com.filmoracle.model.Comment;
import com.filmoracle.model.Movie;
import com.filmoracle.model.AnalysisResult;
import java.util.*;
import java.util.regex.*;

/**
 * 评论分析服务（本地规则引擎）
 * 替代 AI API，基于词典和规则生成情感分析、关键词、雷达图等图表数据
 */
public class AnalysisService {

    // 十维评价维度
    private static final String[] RADAR_LABELS = {
            "剧本", "导演", "表演", "摄影", "剪辑", "声音", "美术", "特效", "主题", "完成度"
    };

    private static final String[] RADAR_FULL = {
            "剧本与叙事", "导演", "表演", "摄影与视觉", "剪辑", "声音与配乐",
            "美术与制作设计", "特效", "主题与思想", "完成度与影响力"
    };

    // 评价维度关键词规则
    private static final String[][] ASPECT_RULES = {
            {"剧本与叙事", "剧情|故事|叙事|节奏|反转|逻辑|情节|结尾|开头|剧本|对白|铺垫|伏笔"},
            {"导演", "导演|调度|镜头语言|风格|场面调度|执导|作者性"},
            {"表演", "演技|表演|演员|角色|主角|配角|出演|饰演|眼神|微表情"},
            {"摄影与视觉", "画面|摄影|构图|光影|色彩|视觉|取景|镜头美学"},
            {"剪辑", "剪辑|转场|蒙太奇|节奏控制|平行剪辑"},
            {"声音与配乐", "音乐|配乐|声音|音效|OST|主题曲|BGM"},
            {"美术与制作设计", "美术|场景|服装|道具|布景|制作设计|年代感|质感"},
            {"特效", "特效|CG|视觉特效|特技|后期|电脑特效"},
            {"主题与思想", "主题|阶层|社会|隐喻|思想|深度|哲学|内核|寓言"},
            {"完成度与影响力", "完成度|影响力|创新|经典|标杆|里程碑|杰作|神作"}
    };

    // 正面情感词
    private static final String POSITIVE_WORDS =
            "精彩|绝了|太绝|惊艳|神作|杰作|经典|完美|震撼|感动|戳中|共鸣|喜欢|爱了|吹爆|好看|推荐|必看|封神" +
            "大师|高级|精妙|细腻|到位|精准|教科书|层次|深度|出色|优秀|惊艳|赞叹|叹服|回味|余味|舒服|满足" +
            "实力|天赋|天才|影帝|影后|精湛|炉火纯青|入木三分|淋漓尽致|酣畅淋漓|热血沸腾|泪目|破防|治愈|温暖";

    // 负面情感词
    private static final String NEGATIVE_WORDS =
            "烂|差|失望|无聊|拖沓|尴尬|刻意|做作|生硬|多余|多余|浪费|可惜|遗憾|平庸|俗套|老套|套路|肤浅" +
            "空洞|乏味|没意思|不值|烂片|踩雷|避雷|无语|崩溃|窒息|压抑|难受|别扭|出戏|违和|尴尬癌|败笔" +
            "减分|拉胯|不行|太差|浪费时间|昏昏欲睡|如坐针毡|惨不忍睹|一言难尽|过誉|名不副实";

    // 情绪象限关键词
    private static final Pattern QUADRANT_REPRESSIVE = Pattern.compile("压抑|惊悚|恐怖|窒息|黑暗|绝望|崩溃|沉重|窒息");
    private static final Pattern QUADRANT_BURNING = Pattern.compile("燃|热血|激动|震撼|爽|炸裂|高能|沸腾|刺激");
    private static final Pattern QUADRANT_LONELY = Pattern.compile("孤独|克制|冷静|疏离|沉默|内敛|沉郁|寂寥|疏离");
    private static final Pattern QUADRANT_HEALING = Pattern.compile("治愈|轻松|温暖|舒服|可爱|温馨|柔软|明朗|甜蜜");

    private static final Set<String> GENERIC_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "电影", "影片", "真的", "感觉", "觉得", "一个", "这个", "就是", "还是", "没有", "不是",
            "非常", "比较", "有点", "剧情", "演技", "画面", "导演", "演员", "故事", "角色", "配乐",
            "镜头", "表演", "主题", "节奏", "音乐", "时候", "因为", "所以", "但是", "可以", "观众",
            "这部", "一片", "看了", "看完", "一种", "一些", "这种", "那种",
            "什么", "怎么", "为什么", "怎么样",
            "两个", "三个", "他们", "我们", "你们", "自己", "别人", "大家", "所有",
            "一直", "已经", "或者", "而且", "然后", "后来", "最后", "开始", "出现",
            "这是", "那是", "还有", "如果", "虽然", "不过", "其实", "这样", "那样",
            "不得", "不到", "不会", "不能", "不要", "不用", "不行", "无关",
            "很多", "很少", "特别", "确实", "相对", "一般", "普通",
            "好看", "不好", "不错", "还行", "太差", "太好", "说是", "来说", "对于"
    ));

    /**
     * 筛选有价值的评论（用于AI分析）
     * 策略：1) 过滤空评论和过短评论 2) 按点赞数排序 3) 保证星级多样性 4) 取前maxCount条
     */
    public static List<Comment> filterValuableComments(List<Comment> comments, int maxCount) {
        if (comments == null || comments.isEmpty()) return new ArrayList<>();

        // 第一步：过滤掉空评论和过短评论（少于10个字）
        List<Comment> filtered = new ArrayList<>();
        for (Comment c : comments) {
            if (c.getText() != null && c.getText().length() >= 10) {
                filtered.add(c);
            }
        }
        if (filtered.isEmpty()) filtered = new ArrayList<>(comments);

        // 第二步：按点赞数降序排序
        filtered.sort((a, b) -> Integer.compare(b.getVoteCount(), a.getVoteCount()));

        // 第三步：保证星级多样性——每个星级至少取一些
        List<Comment> result = new ArrayList<>();
        Set<String> selected = new HashSet<>();

        // 先从每个星级取前几条
        for (int star = 5; star >= 1; star--) {
            final int starVal = star;
            List<Comment> starComments = filtered.stream()
                    .filter(c -> c.getRatingValue() == starVal)
                    .toList();
            int take = Math.min(starComments.size(), Math.max(2, maxCount / 10));
            for (int i = 0; i < take && result.size() < maxCount; i++) {
                Comment c = starComments.get(i);
                if (selected.add(c.getId())) {
                    result.add(c);
                }
            }
        }

        // 如果还不够，从剩余评论中按点赞数补充
        if (result.size() < maxCount) {
            for (Comment c : filtered) {
                if (result.size() >= maxCount) break;
                if (selected.add(c.getId())) {
                    result.add(c);
                }
            }
        }

        // 如果还是不够（评论总数少于maxCount），返回全部
        if (result.isEmpty()) result = filtered;

        System.out.println("[FILTER] " + comments.size() + " comments -> " + result.size() + " valuable");
        return result;
    }

    /**
     * 分析评论并生成完整分析结果
     */
    public static AnalysisResult analyze(List<Comment> comments, Movie movie) {
        AnalysisResult result = new AnalysisResult();

        // 1. 分析每条评论
        List<Map<String, Object>> analyzedComments = new ArrayList<>();
        for (int i = 0; i < comments.size(); i++) {
            Comment c = comments.get(i);
            Map<String, Object> analyzed = new LinkedHashMap<>();
            analyzed.put("id", c.getId());
            analyzed.put("sentiment", classifySentiment(c));
            analyzed.put("aspect", classifyAspect(c.getText()));
            analyzed.put("quadrant", classifyQuadrant(c.getText(), c.getRatingValue()));

            // 情绪坐标
            double[] xy = calculateXY(c);
            analyzed.put("x", xy[0]);
            analyzed.put("y", xy[1]);

            // 关键词
            analyzed.put("keywords", extractCommentKeywords(c.getText()));
            analyzed.put("confidence", calculateConfidence(c));

            // 同步到 Comment 对象
            c.setSentiment((String) analyzed.get("sentiment"));
            c.setAspect((String) analyzed.get("aspect"));
            c.setQuadrant((String) analyzed.get("quadrant"));

            analyzedComments.add(analyzed);
        }
        result.setAnalyzedComments(analyzedComments);

        // 2. 关键词云
        result.setKeywords(extractKeywords(comments));

        // 3. 星级分布
        result.setRatingDistribution(calculateRatingDistribution(comments));

        // 4. 同类型对比
        result.setComparison(calculateComparison(comments, movie));

        // 5. 情感散点
        result.setScatter(calculateScatter(comments));

        // 6. 十维雷达
        result.setRadar(calculateRadar(comments, movie));

        // 7. 情感分布
        result.setSentimentDistribution(calculateSentimentDistribution(comments));

        // 8. 摘要
        result.setSummary(calculateSummary(comments, movie));

        // 9. 引擎标识
        result.setEngine("rule-based");

        // 10. 本地评析（作为无AI时的兜底）
        result.setReview(generateLocalReview(comments, movie));

        return result;
    }

    // ─── 本地生成评析（无AI时使用）───
    private static String generateLocalReview(List<Comment> comments, Movie movie) {
        int total = comments.size() > 0 ? comments.size() : 1;
        long positive = comments.stream().filter(c -> "正面".equals(c.getSentiment())).count();
        long negative = comments.stream().filter(c -> "负面".equals(c.getSentiment())).count();
        long neutral = comments.stream().filter(c -> "中性".equals(c.getSentiment())).count();

        double posRate = positive * 100.0 / total;
        double negRate = negative * 100.0 / total;

        List<Object[]> keywords = extractKeywords(comments);
        String topKeywords = keywords.stream().limit(5).map(k -> (String) k[0]).reduce((a, b) -> a + "、" + b).orElse("剧情、演技");

        StringBuilder sb = new StringBuilder();
        sb.append("《").append(movie.getTitle()).append("》");
        if (!movie.getYear().isEmpty()) sb.append("（").append(movie.getYear()).append("）");
        sb.append("在").append(total).append("条观众评论中获得了");

        if (posRate > 60) {
            sb.append("普遍正面的评价，").append(Math.round(posRate)).append("%的观众表达了认可。");
        } else if (negRate > 40) {
            sb.append("褒贬不一的评价，正面占").append(Math.round(posRate)).append("%，负面占").append(Math.round(negRate)).append("%。");
        } else {
            sb.append("以正面为主的评价，").append(Math.round(posRate)).append("%的观众持正面态度，").append(Math.round(negRate)).append("%表达不满。");
        }

        sb.append("评论高频词集中在").append(topKeywords).append("等方面，反映出观众对影片的核心关注点。");

        // 找到主要争议
        String controversy = "";
        if (negative > 0) {
            List<Comment> negComments = comments.stream().filter(c -> "负面".equals(c.getSentiment())).toList();
            String negText = negComments.stream().map(Comment::getText).reduce("", (a, b) -> a + " " + b);
            for (String[] rule : ASPECT_RULES) {
                if (Pattern.compile(rule[1]).matcher(negText).find()) {
                    controversy = rule[0];
                    break;
                }
            }
        }

        if (!controversy.isEmpty()) {
            sb.append("部分观众认为影片在").append(controversy).append("方面存在不足，");
        }

        // 雷达图最高和最低维度
        List<Object[]> radar = calculateRadar(comments, movie);
        if (radar != null && !radar.isEmpty()) {
            Object[] max = radar.stream().max(Comparator.comparingDouble(a -> ((Number) a[1]).doubleValue())).orElse(null);
            Object[] min = radar.stream().min(Comparator.comparingDouble(a -> ((Number) a[1]).doubleValue())).orElse(null);
            if (max != null) sb.append("观众普遍认可影片的").append(max[0]).append("表现，");
            if (min != null) sb.append("而").append(min[0]).append("相对薄弱。");
        }

        sb.append("综合来看，《").append(movie.getTitle()).append("》");
        if (posRate > 70) {
            sb.append("是一部值得推荐的作品，其在技术完成度和艺术表达上均达到了较高水准。");
        } else if (negRate > 30) {
            sb.append("虽有亮点但争议明显，是否值得观看取决于个人对相应题材和风格的偏好。");
        } else {
            sb.append("整体质量过硬，多数观众认可其价值，适合对该类型感兴趣的观众观看。");
        }

        return sb.toString();
    }

    // ─── 情感分类 ───
    private static String classifySentiment(Comment c) {
        if (c.getRatingValue() >= 4) return "正面";
        if (c.getRatingValue() <= 2) return "负面";
        // 3星根据文本判断
        int pos = countMatches(c.getText(), POSITIVE_WORDS);
        int neg = countMatches(c.getText(), NEGATIVE_WORDS);
        if (pos > neg + 1) return "正面";
        if (neg > pos + 1) return "负面";
        return "中性";
    }

    // ─── 评价维度分类 ───
    private static String classifyAspect(String text) {
        for (String[] rule : ASPECT_RULES) {
            if (Pattern.compile(rule[1]).matcher(text).find()) return rule[0];
        }
        return "完成度与影响力";
    }

    // ─── 情绪象限分类 ───
    private static String classifyQuadrant(String text, int rating) {
        if (QUADRANT_REPRESSIVE.matcher(text).find()) return "压抑/惊悚";
        if (QUADRANT_BURNING.matcher(text).find()) return "热血/高燃";
        if (QUADRANT_LONELY.matcher(text).find()) return "孤独/克制";
        if (QUADRANT_HEALING.matcher(text).find()) return "治愈/轻松";
        if (rating >= 4) return "热血/高燃";
        if (rating <= 2) return "压抑/惊悚";
        return "孤独/克制";
    }

    // ─── 情绪坐标计算（供 AiService 调用）───
    public static double[] calculateXYForComment(Comment c) {
        return calculateXY(c);
    }

    // ─── 情绪坐标计算 ───
    private static double[] calculateXY(Comment c) {
        int rating = c.getRatingValue();
        String text = c.getText();
        int pos = countMatches(text, POSITIVE_WORDS);
        int neg = countMatches(text, NEGATIVE_WORDS);

        // x轴: -1 到 1, 越负越消极
        double x = (rating - 3) / 2.0;
        x += (pos - neg) * 0.18;
        x = Math.max(-1, Math.min(1, x));

        // y轴: 0 到 1, 情绪强度
        int intensityWords = pos + neg + countMatches(text, "震撼|封神|绝|崩溃|窒息|压抑|泪目|愤怒|治愈|破防|惊艳|过誉");
        double y = Math.min(1, 0.22 + intensityWords * 0.16 + Math.min(0.28, text.length() / 260.0));
        y = Math.max(0.05, y);

        return new double[]{x, y};
    }

    // ─── 置信度计算 ───
    private static double calculateConfidence(Comment c) {
        int textLen = c.getText().length();
        int rating = c.getRatingValue();
        double conf = 0.4;
        if (textLen > 30) conf += 0.2;
        if (textLen > 80) conf += 0.15;
        if (rating > 0) conf += 0.15;
        int pos = countMatches(c.getText(), POSITIVE_WORDS);
        int neg = countMatches(c.getText(), NEGATIVE_WORDS);
        if (pos + neg > 0) conf += 0.1;
        return Math.min(0.98, conf);
    }

    // ─── 关键词提取（全局）───
    private static List<Object[]> extractKeywords(List<Comment> comments) {
        Map<String, Double> freq = new HashMap<>();
        for (Comment c : comments) {
            String text = c.getText() == null ? "" : c.getText().replaceAll("[^\\u4e00-\\u9fa5A-Za-z0-9]", " ");
            Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5]{2,10}").matcher(text);
            while (matcher.find()) {
                String part = matcher.group();
                for (int size = Math.min(6, part.length()); size >= 2; size--) {
                    for (int i = 0; i <= part.length() - size; i++) {
                        String phrase = part.substring(i, i + size);
                        if (isGenericKeyword(phrase)) continue;
                        double weight = 1.0 + Math.min(3.0, c.getVoteCount() / 30.0);
                        freq.put(phrase, freq.getOrDefault(phrase, 0.0) + weight);
                    }
                }
            }
        }

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(freq.entrySet());
        ranked.removeIf(e -> e.getValue() < 1.5);
        ranked.sort((a, b) -> {
            int byCount = Double.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : Integer.compare(b.getKey().length(), a.getKey().length());
        });

        List<Object[]> keywords = new ArrayList<>();
        for (Map.Entry<String, Double> entry : ranked) {
            if (keywords.size() >= 18) break;
            String word = entry.getKey();
            boolean duplicate = false;
            for (Object[] item : keywords) {
                String existing = String.valueOf(item[0]);
                if (existing.contains(word) || word.contains(existing)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) keywords.add(new Object[]{word, Math.max(1, (int) Math.round(entry.getValue()))});
        }
        if (keywords.isEmpty()) {
            keywords.add(new Object[]{"核心段落", 3});
            keywords.add(new Object[]{"情绪余味", 2});
            keywords.add(new Object[]{"人物动机", 2});
        }
        return keywords;
    }

    // ─── 单条评论关键词 ───
    private static List<String> extractCommentKeywords(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("[\\u4e00-\\u9fa5]{3,8}").matcher(text == null ? "" : text);
        while (matcher.find() && result.size() < 5) {
            String phrase = matcher.group();
            if (!isGenericKeyword(phrase)) result.add(phrase);
        }
        return result;
    }

    private static boolean isGenericKeyword(String phrase) {
        if (phrase == null || phrase.length() < 2) return true;
        if (GENERIC_KEYWORDS.contains(phrase)) return true;
        for (String generic : GENERIC_KEYWORDS) {
            if (phrase.equals(generic) || (phrase.contains(generic) && phrase.length() <= generic.length() + 1)) {
                return true;
            }
        }
        return phrase.matches("^(这部|一部|很多|看完|看过|时候|因为|所以|但是|如果|可以).*$");
    }

    // ─── 星级分布 ───
    private static List<Object[]> calculateRatingDistribution(List<Comment> comments) {
        int total = comments.size() > 0 ? comments.size() : 1;
        List<Object[]> dist = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            final int starVal = star;
            int count = (int) comments.stream().filter(c -> c.getRatingValue() == starVal).count();
            dist.add(new Object[]{starVal + "星", Math.round((count * 100.0) / total)});
        }
        return dist;
    }

    // ─── 同类型对比 ───
    private static List<Object[]> calculateComparison(List<Comment> comments, Movie movie) {
        double avgRating = comments.stream().mapToInt(Comment::getRatingValue).average().orElse(3.0);
        long positive = comments.stream().filter(c -> "正面".equals(c.getSentiment())).count();
        List<Object[]> comp = new ArrayList<>();
        double movieRating = 0;
        try { movieRating = Double.parseDouble(movie.getRating()); } catch (Exception e) {}

        comp.add(new Object[]{"本片评分", (int) Math.round((movieRating > 0 ? movieRating : avgRating * 2) * 10)});
        long votes = 0;
        try { votes = Long.parseLong(movie.getVotes().replaceAll("\\D", "")); } catch (Exception e) {}
        comp.add(new Object[]{"同类型热度", Math.min(96, (int)(50 + Math.log10(Math.max(10000, votes)) * 8))});
        comp.add(new Object[]{"评价人数", Math.min(98, (int)(42 + comments.size() * 2))});
        comp.add(new Object[]{"正向情绪", (int) Math.round((positive * 100.0) / (comments.size() > 0 ? comments.size() : 1))});
        return comp;
    }

    // ─── 情感散点 ───
    private static List<Map<String, Object>> calculateScatter(List<Comment> comments) {
        List<Map<String, Object>> scatter = new ArrayList<>();
        for (int i = 0; i < comments.size(); i++) {
            Comment c = comments.get(i);
            double[] xy = calculateXY(c);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("x", Math.round(xy[0] * 100) / 100.0);
            point.put("y", Math.round(xy[1] * 100) / 100.0);
            point.put("sentiment", c.getSentiment());
            point.put("label", c.getQuadrant());
            point.put("index", i);
            scatter.add(point);
        }
        return scatter;
    }

    // ─── 十维雷达图 ───
    private static List<Object[]> calculateRadar(List<Comment> comments, Movie movie) {
        double avgRating = comments.stream().mapToInt(Comment::getRatingValue).average().orElse(3.5);
        double base = Math.max(4.8, Math.min(8.4, avgRating * 1.45));
        double movieRating = 0;
        try { movieRating = Double.parseDouble(movie.getRating()); } catch (Exception e) {}
        if (movieRating > 0) base = Math.max(base, movieRating - 1.4);

        List<Object[]> radar = new ArrayList<>();
        for (int i = 0; i < RADAR_FULL.length; i++) {
            int hits = 0;
            double pos = 0;
            double neg = 0;
            for (String[] rule : ASPECT_RULES) {
                if (rule[0].equals(RADAR_FULL[i])) {
                    Pattern p = Pattern.compile(rule[1]);
                    for (Comment c : comments) {
                        Matcher m = p.matcher(c.getText());
                        int commentHits = 0;
                        while (m.find()) commentHits++;
                        if (commentHits > 0) {
                            hits += commentHits;
                            if ("正面".equals(c.getSentiment())) pos += 1 + commentHits * 0.2;
                            if ("负面".equals(c.getSentiment())) neg += 1 + commentHits * 0.35;
                        }
                    }
                    break;
                }
            }
            double score = base + pos * 0.58 - neg * 1.0 + Math.min(1.5, hits * 0.20);
            if (hits == 0) score -= 1.2 + (i % 3) * 0.45;
            score = Math.max(2.8, Math.min(9.7, score));
            radar.add(new Object[]{RADAR_LABELS[i], Math.round(score * 10) / 10.0});
        }
        return radar;
    }

    // ─── 情感分布 ───
    private static Map<String, Integer> calculateSentimentDistribution(List<Comment> comments) {
        int total = comments.size() > 0 ? comments.size() : 1;
        long positive = comments.stream().filter(c -> "正面".equals(c.getSentiment())).count();
        long negative = comments.stream().filter(c -> "负面".equals(c.getSentiment())).count();
        long neutral = comments.stream().filter(c -> "中性".equals(c.getSentiment())).count();
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("positive", (int) Math.round(positive * 100.0 / total));
        dist.put("negative", (int) Math.round(negative * 100.0 / total));
        dist.put("neutral", (int) Math.round(neutral * 100.0 / total));
        return dist;
    }

    // ─── 摘要 ───
    private static Map<String, Object> calculateSummary(List<Comment> comments, Movie movie) {
        int total = comments.size() > 0 ? comments.size() : 1;
        long positive = comments.stream().filter(c -> "正面".equals(c.getSentiment())).count();
        long negative = comments.stream().filter(c -> "负面".equals(c.getSentiment())).count();
        long neutral = comments.stream().filter(c -> "中性".equals(c.getSentiment())).count();

        // 关键词摘要
        List<Object[]> keywords = extractKeywords(comments);
        String keywordSummary = keywords.stream().limit(5).map(k -> (String) k[0]).reduce((a, b) -> a + "、" + b).orElse("剧情、演技");

        // 主要争议点
        String controversy = "";
        if (negative > 0) {
            List<Comment> negComments = comments.stream().filter(c -> "负面".equals(c.getSentiment())).toList();
            String negText = negComments.stream().map(Comment::getText).reduce("", (a, b) -> a + " " + b);
            for (String[] rule : ASPECT_RULES) {
                if (Pattern.compile(rule[1]).matcher(negText).find()) {
                    controversy = "部分观众认为" + rule[0] + "存在不足";
                    break;
                }
            }
            if (controversy.isEmpty()) controversy = "评价存在两极分化";
        } else {
            controversy = "整体评价较为一致，无明显争议";
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("positiveRate", Math.round(positive * 100.0 / total));
        summary.put("negativeRate", Math.round(negative * 100.0 / total));
        summary.put("neutralRate", Math.round(neutral * 100.0 / total));
        summary.put("keywordsSummary", keywordSummary);
        summary.put("mainControversy", controversy);
        summary.put("totalComments", comments.size());
        return summary;
    }

    // ─── 工具：统计关键词出现次数 ───
    private static int countMatches(String text, String words) {
        int count = 0;
        for (String word : words.split("\\|")) {
            int idx = 0;
            while ((idx = text.indexOf(word, idx)) != -1) {
                count++;
                idx += word.length();
            }
        }
        return count;
    }
}
