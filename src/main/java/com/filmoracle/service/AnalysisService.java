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
            // ─ 影视/片名泛称 ─
            "电影", "影片", "这部", "一片", "此片", "该片", "此剧", "本片", "整个", "整部", "一部",
            // ─ 人称/指示代词 ─
            "一个", "两个", "三个", "他们", "我们", "你们", "自己", "别人", "大家", "所有",
            "每个", "各自", "互相", "彼此", "其他", "其它", "某些", "任何",
            "这个", "那个", "这些", "那些", "这是", "那是",
            // ─ 疑问词 ─
            "什么", "怎么", "为什么", "怎么样", "到底", "究竟", "难道",
            // ─ 数量词 ─
            "一些", "一下", "一点", "一种", "一次", "一遍", "一场", "一番", "一幕",
            "很多", "很少", "半部",
            // ─ 程度副词 ─
            "非常", "比较", "有点", "特别", "确实", "相对", "一般", "普通",
            "真的", "简直", "似乎", "果然", "居然", "竟然", "毕竟",
            // ─ 泛泛评价（过于笼统） ─
            "好看", "不好", "不错", "还行", "太差", "太好", "说是", "来说",
            // ─ 介词/连词 ─
            "因为", "所以", "但是", "可以", "对于", "关于", "由于", "基于",
            "随着", "等于", "属于", "至于", "如果", "虽然", "不过", "其实",
            "而且", "然后", "或者", "还是", "不仅", "不但", "何况", "以及",
            "或是", "要么", "不论", "不管", "无论", "与其", "不如", "以便",
            "除非", "一旦", "万一", "即便", "哪怕", "即使", "尽管", "并且",
            // ─ 时间词 ─
            "时候", "一直", "已经", "后来", "最后", "开始", "出现", "曾经",
            "以前", "以后", "之前", "之后", "刚刚", "马上", "立刻",
            "忽然", "突然", "逐渐", "终于",
            // ─ 助动词/否定 ─
            "不得", "不到", "不会", "不能", "不要", "不用", "不行", "无关", "无法",
            // ─ 结构助词/语气词 ─
            "的话", "似的", "这么", "看的", "的是", "有的", "一样", "有些", "也是",
            "都是", "不算", "而是", "本来", "原来", "出来", "起来", "下去",
            "过来", "过去", "回来",
            // ─ 认知动词（泛义） ─
            "觉得", "感觉", "认为", "以为", "知道", "明白", "理解", "发现", "看到",
            // ─ 复合功能短语 ─
            "我觉得", "我感觉", "我认为", "我想说", "说实话", "讲真", "客观说",
            "个人觉", "总体来", "整体来", "综合来", "总结来", "一句话",
            "只能说", "不知道", "不觉得", "不至于", "完全不", "并不",
            "并非", "并非是", "并不是", "不算太", "不算很",
            // ─ 影视评价维度泛称（通用，不作为关键词） ─
            "剧情", "演技", "画面", "导演", "演员", "故事", "角色", "配乐",
            "镜头", "表演", "主题", "节奏", "音乐", "观众",
            // ─ 方位/处所词 ─
            "地方", "东西", "当中", "其中", "以上", "以下", "以外", "以内",
            // ─ 情态词 ─
            "可能", "应该", "也许", "或许",
            // ─ 看过/看了相关 ─
            "看了", "看完", "看过", "看着",
            "看了一", "看了两", "看了三", "看完这", "看完那",
            // ─ 观影相关短语 ─
            "演技在", "画面在", "剧情在", "节奏在", "配乐在", "主题在",
            "第一遍", "第二次", "第一次", "一开始", "第一眼",
            "整个电", "整个故", "整部片", "整部电",
            "且不", "而且不", "但也不", "可是不", "却不", "却没",
            // ─ 其他高频功能词 ─
            "就是", "没有", "不是", "还有", "这样", "那样"
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

        // 5b. 情绪分布图
        result.setEmotionMap(calculateEmotionMap(comments));

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

        // ── 复合词加权：4字短语若由两个有意义的2字词组成，则提升50%权重 ──
        for (String word : new ArrayList<>(freq.keySet())) {
            if (word.length() == 4) {
                String first2 = word.substring(0, 2);
                String last2 = word.substring(2, 4);
                if (freq.containsKey(first2) && freq.containsKey(last2)
                        && !isGenericKeyword(first2) && !isGenericKeyword(last2)) {
                    freq.merge(word, freq.get(word) * 0.5, Double::sum);
                }
            }
        }

        // ── 排序：先按频率降序，再按词长降序 ──
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(freq.entrySet());
        ranked.removeIf(e -> e.getValue() < 1.5);
        ranked.sort((a, b) -> {
            int byCount = Double.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : Integer.compare(b.getKey().length(), a.getKey().length());
        });

        // ── 自适应门槛：不足30个时降低到1.0重新提取 ──
        if (ranked.size() < 30) {
            ranked = new ArrayList<>(freq.entrySet());
            ranked.removeIf(e -> e.getValue() < 1.0);
            ranked.sort((a, b) -> {
                int byCount = Double.compare(b.getValue(), a.getValue());
                return byCount != 0 ? byCount : Integer.compare(b.getKey().length(), a.getKey().length());
            });
        }

        // ── 去重：仅当词长差<2且互为子串时才去重，避免长词吞掉短词 ──
        List<Object[]> keywords = new ArrayList<>();
        for (Map.Entry<String, Double> entry : ranked) {
            if (keywords.size() >= 30) break;
            String word = entry.getKey();
            boolean duplicate = false;
            for (Object[] item : keywords) {
                String existing = String.valueOf(item[0]);
                int lenDiff = Math.abs(existing.length() - word.length());
                if (lenDiff < 2 && (existing.contains(word) || word.contains(existing))) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) keywords.add(new Object[]{word, Math.max(1, (int) Math.round(entry.getValue()))});
        }

        // ── 兜底：空结果时从评论中取高频2字词 ──
        if (keywords.isEmpty()) {
            Map<String, Integer> twoCharFreq = new HashMap<>();
            for (Comment c : comments) {
                String text = c.getText() == null ? "" : c.getText().replaceAll("[^\\u4e00-\\u9fa5]", " ");
                Matcher m = Pattern.compile("[\\u4e00-\\u9fa5]{2}").matcher(text);
                while (m.find()) {
                    String w = m.group();
                    if (!isGenericKeyword(w)) {
                        twoCharFreq.merge(w, 1, Integer::sum);
                    }
                }
            }
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(twoCharFreq.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());
            for (Map.Entry<String, Integer> e : sorted) {
                if (keywords.size() >= 10) break;
                keywords.add(new Object[]{e.getKey(), Math.max(1, e.getValue())});
            }
            if (keywords.isEmpty()) {
                keywords.add(new Object[]{"观影体验", 3});
                keywords.add(new Object[]{"情感表达", 2});
            }
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

    // ─── 方面级情感分析 ───
    private static final String[][] ASPECT_PATTERNS = {
        {"剧情", "故事|情节|叙事|剧本|线索|伏笔|反转|逻辑|设定|桥段|套路|剧情|开头|铺垫|前半|后半|世界观|人设|动机|结局走向|梗|虐心|催泪|狗血|俗套|脉络|骨架|人物弧|起承转合|悬念|暗线|明线|支线|主线|剧情线|叙事节奏|叙事结构|叙事方式|故事性|故事内核|故事走向|情节推动|转折点|高潮|波折|跌宕|信息量|叙事密度|剧本扎实|剧本薄弱|剧本硬伤|剧本漏洞|剧本逻辑|剧本结构|剧本完成度|剧情流畅|剧情紧凑|剧情松散|剧情推进|剧情发展|剧情走向|剧情转折|剧情反转|剧情高潮|剧情起伏|弧光"},
        {"演技", "演技|表演|演员|角色|饰演|演绎|主角|配角|群像|表演力|刻画|塑造|眼神|微表情|出戏|入戏|台词功|台词功底|演技在线|演得好|演得|代入感|感染力|表演层次|情绪递进|共情|信念感|撑起|扛住|演技炸裂|演技派|实力派|老戏骨|面瘫|油腻|用力过猛|表演痕迹|浮夸|做作|呆板|灵动|传神|到位|精准|拿捏|分寸感|张力|爆发力|控制力|表现力|角色贴合|人戏合一|角色塑造|人物刻画|人物塑造|形象立体|形象饱满|有血有肉|演活了|演技碾压|演技吊打"},
        {"视听", "画面|镜头|摄影|视效|特效|色彩|构图|光影|视觉|画面感|大片感|视觉冲击|美学|质感|帧|截图|名场面|视觉盛宴|镜头美学|色彩美学|光影美学|每一帧|壁纸级|绝美画面|视觉体验|视觉享受|视觉奇观|画面精致|画面考究|画面细腻|画面质感|画面唯美|画面大气|调色|滤镜|色调|景深|慢镜头|长镜头|特写|远景|全景|构图精妙|运镜|机位|取景|布光|打光|画面构图|镜头调度|镜头语言|画面语言|视觉语言|影像语言|电影感|大片质感|画面冲击力|视觉表现力|画面表现力|画面张力|画面感染力|画面沉浸感|画面代入感"},
        {"节奏", "节奏|拖沓|紧凑|缓慢|快|慢|剪辑|转折|推进|松散|冗长|注水|拖戏|拖拉|太长|太慢|太快|高能|停不下来|一气呵成|节奏感|节奏控制|张弛有度|行云流水|丝滑|顺畅|流畅|割裂|突兀|拖泥带水|磨叽|注水剧|水剧|水分|凑数|拖时长|精简|利落|一口气|追剧|熬夜|欲罢不能|沉浸|节奏明快|节奏流畅|节奏紧凑|节奏松散|节奏拖沓|节奏混乱|节奏失调|收放自如|松紧有度|干脆|凝练|节奏拉满|全程高能|全程无尿点|紧凑感|松弛感|节奏断裂|节奏跳跃"},
        {"主题", "主题|思想|深度|内涵|隐喻|象征|哲学|意义|表达|探讨|女性|男权|社会|现实|阶层|批判|反思|启示|价值观|立场|议题|社会议题|现实议题|人文关怀|人性|人伦|伦理|道德|救赎|善恶|正义|公平|自由|平等|抗争|反抗|觉醒|女性觉醒|女性意识|父权|阶层固化|阶级|贫富|城乡|文化冲突|代际|移民|弱势群体|边缘|歧视|偏见|刻板印象|身份认同|家国情怀|民族|时代缩影|寓言|讽刺|荒诞|黑色幽默|悲悯|终极关怀|存在主义|异化|体制|规则|丛林法则|文明|野蛮|思想性|思想深度|思想内核|主题表达|主题深度|主题内核|社会批判|现实批判|社会反思|现实反思|人文思考|哲学思考"},
        {"配乐", "配乐|音乐|音效|BGM|主题曲|背景音|原声|OST|配乐感|音乐性|旋律|曲调|编曲|作曲|交响|弦乐|钢琴|鼓点|和声|片尾曲|插曲|片头曲|背景音乐|声音设计|声效|环境音|混音|声场|环绕|立体声|杜比|听觉体验|音乐品味|音乐张力|音乐情绪|音乐感染力|音乐烘托|音乐渲染|音乐铺垫|音乐高潮|音乐留白|静默|配乐大师|配乐绝|配乐封神|配乐拉胯|配乐出戏|配乐违和|配乐加分|配乐减分|音乐响起|BGM响起|配乐到位|配乐精准|配乐出色"},
        {"美术", "美术|布景|服装|道具|场景|美术设计|造型|服化道|审美|服化|妆造|美术风格|置景|年代感|服化精美|美术指导|场景设计|场景搭建|场景布置|场景氛围|场景质感|场景细节|场景还原|场景沉浸|场景真实|场景考究|场景精致|服装设计|服装造型|服装质感|道具设计|道具制作|道具质感|化妆|妆容|特效化妆|造型设计|实景|棚拍|外景|内景|年代还原|年代质感|服化道精美|服化道考究|美术质感|美术审美|美术水准|美术完成度|视觉风格|美术品位"},
        {"结尾", "结尾|结局|最后|收尾|尾声|终章|收场|落幕|结局走向|结尾好|结尾烂|HE|BE|开放式|开放式结局|圆满|意难平|烂尾|神结尾|反转结局|意料之外|情理之中|首尾呼应|闭环|留白|余味|回味|意犹未尽|戛然而止|仓促|草率|虎头蛇尾|画蛇添足|狗尾续貂|画龙点睛|升华|收束|终结|余韵|耐人寻味|引人深思|发人深省|结尾反转|结局反转|结局圆满|结局遗憾|结局仓促|结尾升华|结尾点睛|结尾留白|结尾余味|结尾回味|结尾意犹未尽"}
    };
    private static final Pattern STRONG_POSITIVE = Pattern.compile("封神|绝了|震撼|神作|标杆|教科书|天花板|无可挑剔|完美|杰作| masterpiece |大师|天花板|封顶|绝赞|叹为观止");
    private static final Pattern STRONG_NEGATIVE = Pattern.compile("崩坏|败笔|烂尾|灾难|一塌糊涂|惨不忍睹|狗屁|垃圾|烂片|圾|拉胯|稀烂|车祸|魔幻|离谱|恶心");
    private static final Pattern MODERATE_POSITIVE = Pattern.compile("好看|出色|精彩|到位|扎实|细腻|惊艳|亮眼|加分|不错|扎实|舒服|流畅|自然|真诚|用心");
    private static final Pattern MODERATE_NEGATIVE = Pattern.compile("不行|不足|欠缺|薄弱|生硬|尴尬|刻意|拖沓|无聊|失望|减分|违和|出戏|可惜|遗憾|平庸|俗套|老套");

    private static double calculateAspectPolarity(Comment c, String aspect) {
        String text = c.getText();
        int rating = c.getRatingValue();
        double base = (rating - 3) * 1.2;
        if (STRONG_POSITIVE.matcher(text).find()) base += 2.0;
        if (STRONG_NEGATIVE.matcher(text).find()) base -= 2.0;
        if (MODERATE_POSITIVE.matcher(text).find()) base += 0.8;
        if (MODERATE_NEGATIVE.matcher(text).find()) base -= 0.8;

        // 情感词数量的影响：更多情感词→更极端的polarity
        int posCount = countMatches(text, POSITIVE_WORDS);
        int negCount = countMatches(text, NEGATIVE_WORDS);
        base += (posCount - negCount) * 0.3;

        // 评论长度的影响：长评论polarity更极端
        int textLen = text.length();
        if (textLen > 50) base += base > 0 ? 0.3 : (base < 0 ? -0.3 : 0);
        if (textLen > 100) base += base > 0 ? 0.2 : (base < 0 ? -0.2 : 0);

        // 否定词的处理："不好"vs"好"应区分
        int negationCount = countMatches(text, "不|没|未|别|勿|莫|无|非");
        if (negationCount > 0 && posCount > negCount) {
            base -= negationCount * 0.15;
        }

        return Math.max(-5.0, Math.min(5.0, Math.round(base * 10) / 10.0));
    }

    private static double calculateAspectIntensity(Comment c) {
        String text = c.getText();
        int count = 0;
        if (STRONG_POSITIVE.matcher(text).find()) count += 2;
        if (STRONG_NEGATIVE.matcher(text).find()) count += 2;

        // 具体情绪词的权重区分（"封神"权重高于"好看"）
        count += countMatches(text, "封神|绝了|神作|天花板|教科书|无可挑剔|崩坏|败笔|烂尾|灾难|一塌糊涂|惨不忍睹|垃圾|拉胯|稀烂") * 2;
        count += countMatches(text, "震撼|绝|崩|烂|败|哭|泪|破防|窒息|压抑|愤怒|爽|燃|惊艳|泪目");

        // 评论长度的加权（长评论通常情绪更投入）
        int textLen = text.length();
        double lenBonus = Math.min(1.5, textLen / 100.0);

        // 标点符号（感叹号、问号多=情绪强烈）
        int punctCount = countMatches(text, "！|!|？|?");
        double punctBonus = Math.min(1.5, punctCount * 0.4);

        double intensity = 1.0 + count * 0.6 + lenBonus + punctBonus;
        return Math.max(0.5, Math.min(5.0, Math.round(intensity * 10) / 10.0));
    }

    private static String getCommentExcerpt(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        String t = text.trim();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "...";
    }

    // ─── 方面级散点（聚合相似情感评论）───
    // 策略：对每条评论检测提及的方面，计算该方面的polarity和intensity，
    // 然后将相同方面+相近情感(polarity四舍五入到0.1，intensity四舍五入到0.1)的评论聚合为一个点，
    // votes = 聚合的评论条数（表示有多少条评论表达了相似情感），点越大代表越多评论持相同看法
    public static List<Map<String, Object>> calculateScatter(List<Comment> comments) {
        Map<String, Map<String, Object>> clusters = new LinkedHashMap<>();

        for (Comment c : comments) {
            String text = c.getText();
            if (text == null || text.isEmpty()) continue;
            boolean anyAspect = false;

            for (String[] aspectRule : ASPECT_PATTERNS) {
                String aspect = aspectRule[0];
                Pattern p = Pattern.compile(aspectRule[1]);
                if (!p.matcher(text).find()) continue;
                anyAspect = true;

                double polarity = calculateAspectPolarity(c, aspect);
                double intensity = calculateAspectIntensity(c);
                // 四舍五入到0.1精度，使相似但不同的情感能形成不同的点，增加散点数量
                double rp = Math.round(polarity * 10) / 10.0;
                double ri = Math.round(intensity * 10) / 10.0;
                String key = aspect + "_" + rp + "_" + ri;

                aggregateCluster(clusters, key, aspect, rp, ri, c, text);
            }

            // 如果没有匹配到任何方面，归入"剧情"默认方面
            if (!anyAspect) {
                double polarity = calculateAspectPolarity(c, "剧情");
                double intensity = calculateAspectIntensity(c);
                double rp = Math.round(polarity * 10) / 10.0;
                double ri = Math.round(intensity * 10) / 10.0;
                String key = "剧情_" + rp + "_" + ri;
                aggregateCluster(clusters, key, "剧情", rp, ri, c, text);
            }
        }

        // 转为列表，加微抖动防完全重叠
        List<Map<String, Object>> scatter = new ArrayList<>();
        java.util.Random jitter = new java.util.Random(42);
        for (Map<String, Object> cluster : clusters.values()) {
            double jx = (jitter.nextDouble() - 0.5) * 0.3;
            double jy = (jitter.nextDouble() - 0.5) * 0.2;
            double pol = Math.max(-5.0, Math.min(5.0, Number(cluster.get("polarity")) + jx));
            double inten = Math.max(0.0, Math.min(5.0, Number(cluster.get("intensity")) + jy));
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("aspect", cluster.get("aspect"));
            point.put("polarity", Math.round(pol * 10) / 10.0);
            point.put("intensity", Math.round(inten * 10) / 10.0);
            point.put("votes", cluster.get("votes"));
            point.put("text", cluster.get("text"));
            scatter.add(point);
        }

        // 确保至少有数据
        if (scatter.isEmpty()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("aspect", "剧情");
            point.put("polarity", 0.0);
            point.put("intensity", 1.0);
            point.put("votes", 1);
            point.put("text", "");
            scatter.add(point);
        }

        System.out.println("[SCATTER] " + comments.size() + " comments -> " + scatter.size() + " aggregated clusters");
        return scatter;
    }

    @SuppressWarnings("unchecked")
    private static void aggregateCluster(Map<String, Map<String, Object>> clusters,
            String key, String aspect, double rp, double ri, Comment c, String text) {
        Map<String, Object> cluster = clusters.get(key);
        if (cluster == null) {
            cluster = new LinkedHashMap<>();
            cluster.put("aspect", aspect);
            cluster.put("polarity", rp);
            cluster.put("intensity", ri);
            cluster.put("votes", 0);
            cluster.put("_maxVotes", -1);
            cluster.put("text", "");
            clusters.put(key, cluster);
        }
        // votes = 聚合的评论条数
        int prevCount = ((Number) cluster.get("votes")).intValue();
        cluster.put("votes", prevCount + 1);
        // 选点赞最多的评论作为代表文本
        int maxV = ((Number) cluster.get("_maxVotes")).intValue();
        if (c.getVoteCount() > maxV) {
            cluster.put("_maxVotes", c.getVoteCount());
            cluster.put("text", getCommentExcerpt(text, 60));
        }
    }

    // ─── 情绪分布图（新四象限，加权统计）───
    // 使用散点聚合数据，按 votes（聚合评论数）加权统计象限，使统计结果反映真实评论分布
    public static Map<String, Object> calculateEmotionMap(List<Comment> comments) {
        Map<String, Object> emotionMap = new LinkedHashMap<>();
        Map<String, String> axis = new LinkedHashMap<>();
        axis.put("x", "差评 ← polarity → 好评");
        axis.put("y", "平淡 → intensity → 强烈");
        emotionMap.put("axis", axis);

        // 用散点数据统计象限（按votes加权）
        List<Map<String, Object>> scatter = calculateScatter(comments);
        int totalWeight = 0;
        int rt = 0, lt = 0, lb = 0, rb = 0;
        double sumX = 0, sumY = 0;
        for (Map<String, Object> pt : scatter) {
            double pol = Number(pt.get("polarity"));
            double inten = Number(pt.get("intensity"));
            int weight = ((Number) pt.getOrDefault("votes", 1)).intValue();
            totalWeight += weight;
            sumX += pol * weight;
            sumY += inten * weight;
            if (pol >= 0 && inten >= 2.5) rt += weight;
            else if (pol < 0 && inten >= 2.5) lt += weight;
            else if (pol < 0 && inten < 2.5) lb += weight;
            else rb += weight;
        }
        if (totalWeight == 0) totalWeight = 1;

        List<Map<String, Object>> quadrants = new ArrayList<>();
        quadrants.add(buildQuadrant("狂热好评", "rightTop", rt, totalWeight, "高强度正面评价，观众对特定方面高度认可"));
        quadrants.add(buildQuadrant("强烈差评", "leftTop", lt, totalWeight, "高强度负面评价，观众对特定方面强烈不满"));
        quadrants.add(buildQuadrant("差强人意", "leftBottom", lb, totalWeight, "低强度负面评价，观众有保留意见但情绪不激烈"));
        quadrants.add(buildQuadrant("比较推荐", "rightBottom", rb, totalWeight, "低到中强度正面评价，观众温和认可"));
        emotionMap.put("quadrants", quadrants);

        Map<String, Object> centroid = new LinkedHashMap<>();
        double cx = sumX / totalWeight;
        double cy = sumY / totalWeight;
        centroid.put("x", Math.round(cx * 100) / 100.0);
        centroid.put("y", Math.round(cy * 100) / 100.0);
        String centroidLabel;
        if (cx >= 0 && cy >= 2.5) centroidLabel = "整体偏正面且情绪强烈，观众热情高涨";
        else if (cx >= 0 && cy < 2.5) centroidLabel = "整体偏正面但情绪温和，观众认可度平稳";
        else if (cx < 0 && cy >= 2.5) centroidLabel = "整体偏负面且情绪强烈，争议较大";
        else centroidLabel = "整体偏负面但情绪克制，观众有保留意见";
        centroid.put("label", centroidLabel);
        emotionMap.put("centroid", centroid);

        // 主导方面（加权统计）
        Map<String, Integer> aspectWeight = new LinkedHashMap<>();
        for (Map<String, Object> pt : scatter) {
            String asp = String.valueOf(pt.get("aspect"));
            int w = ((Number) pt.getOrDefault("votes", 1)).intValue();
            aspectWeight.merge(asp, w, Integer::sum);
        }
        String dominantAspect = "剧情";
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : aspectWeight.entrySet()) {
            if (e.getValue() > maxCount) { maxCount = e.getValue(); dominantAspect = e.getKey(); }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dominantAspect", dominantAspect);
        summary.put("distributionSummary", String.format("讨论最多的是「%s」，狂热好评%d%%，强烈差评%d%%", dominantAspect, rt*100/totalWeight, lt*100/totalWeight));
        summary.put("interpretation", String.format("情绪重心(%.1f, %.1f)，%s", cx, cy, centroidLabel));
        emotionMap.put("summary", summary);

        return emotionMap;
    }

    private static double Number(Object obj) {
        if (obj == null) return 0;
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0; }
    }

    private static Map<String, Object> buildQuadrant(String name, String position, int count, int total, String desc) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("name", name);
        q.put("position", position);
        q.put("count", count);
        q.put("percent", Math.round(count * 100.0 / total));
        q.put("description", desc);
        return q;
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
