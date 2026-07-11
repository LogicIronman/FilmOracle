package com.filmoracle.service;

import com.filmoracle.model.AnalysisResult;
import com.filmoracle.model.Comment;
import com.filmoracle.model.Movie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisServiceTest {

    @Test
    void generatesSentimentKeywordsAndStatisticsForImportedComments() {
        Movie movie = new Movie();
        movie.setId("test-film");
        movie.setTitle("统计验收电影");
        movie.setGenre("剧情");
        movie.setRating("8.8");
        movie.setVotes("10000");

        List<Comment> comments = List.of(
                comment("c1", "剧情紧凑，演员表演自然，摄影很有质感。", 5),
                comment("c2", "节奏拖沓，结尾仓促，让人有些失望。", 2),
                comment("c3", "配乐舒服，整体完成度不错。", 4)
        );

        AnalysisResult result = AnalysisService.analyze(comments, movie);

        assertEquals(3, result.getAnalyzedComments().size());
        assertEquals("正面", comments.get(0).getSentiment());
        assertEquals("负面", comments.get(1).getSentiment());
        assertFalse(result.getKeywords().isEmpty());
        assertEquals(5, result.getRatingDistribution().size());
        assertEquals(10, result.getRadar().size());
        assertFalse(result.getScatter().isEmpty());
        assertNotNull(result.getSentimentDistribution());
        assertTrue(((Number) result.getSummary().get("positiveRate")).intValue() > 0);
    }

    @Test
    void classifiesConflictingHighRatingAndNegativeWordingAsNeutral() {
        Movie movie = new Movie();
        movie.setTitle("情感规则验收电影");
        Comment comment = comment("high-star-negative", "画面很好，但中段拖沓又尴尬，结尾尤其失望。", 4);

        AnalysisService.analyze(List.of(comment), movie);

        assertEquals("中性", comment.getSentiment());
    }

    @Test
    void classifiesReportedAndRhetoricalNegativeOpinionsAsNeutral() {
        Movie movie = new Movie();
        movie.setTitle("转述语境验收电影");
        Comment comment = comment(
                "reported-negative",
                "重映后，居然好多人说是烂片😅 这到底是《指环王》烂了，还是这个时代越来越烂了",
                5
        );

        AnalysisService.analyze(List.of(comment), movie);

        assertEquals("中性", comment.getSentiment());
    }

    @Test
    void classifiesMixedNegativeContextAndPositiveConclusionAsNeutral() {
        Movie movie = new Movie();
        movie.setTitle("先抑后扬验收电影");
        Comment comment = comment(
                "positive-conclusion",
                "有些电影大烂配不上观众，但也有些观众大烂配不上电影。指环王，yyds。迟到了五年的好评。不管怎么说，有生之年还能在大银幕上看到你，就是足够让我开心的一件事了。",
                5
        );

        AnalysisService.analyze(List.of(comment), movie);

        assertEquals("中性", comment.getSentiment());
    }

    @Test
    void classifiesPraiseFollowedByCriticismAsNeutral() {
        Movie movie = new Movie();
        movie.setTitle("褒贬混合验收电影");
        Comment comment = comment(
                "praise-then-criticism",
                "场景好美！！特效好棒！！但是剧情我真的不是很感冒，差点看睡着了。",
                4
        );

        AnalysisService.analyze(List.of(comment), movie);

        assertEquals("中性", comment.getSentiment());
    }

    @Test
    void keepsPureDissatisfactionNegative() {
        Movie movie = new Movie();
        movie.setTitle("纯负面验收电影");
        Comment comment = comment(
                "pure-negative",
                "剧情混乱，表演尴尬，节奏拖沓，完全不满意。",
                2
        );

        AnalysisService.analyze(List.of(comment), movie);

        assertEquals("负面", comment.getSentiment());
    }

    @Test
    void classifiesUnratedCommentsWithClearPraiseAsPositive() {
        Movie movie = new Movie();
        movie.setTitle("未评分验收电影");
        Comment comment = comment("unrated", "那个世界上最美的精灵啊。", 0);

        AnalysisService.analyze(List.of(comment), movie);

        assertEquals("正面", comment.getSentiment());
    }

    @Test
    void keepsUnratedCommentsWithoutClearEvidenceNeutral() {
        Movie movie = new Movie();
        movie.setTitle("未评分中性验收电影");
        Comment comment = comment("unrated-neutral", "二十年后重新看了一遍，想法和以前不一样。", 0);

        AnalysisService.analyze(List.of(comment), movie);

        assertEquals("中性", comment.getSentiment());
    }

    @Test
    void appliesRuleLabelsForAiCommentDetails() {
        Comment comment = comment("ai-high-star-negative", "中段拖沓又尴尬，结尾尤其失望。", 4);

        AnalysisService.applyRuleLabels(List.of(comment));

        assertEquals("中性", comment.getSentiment());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiDetailBuilderUsesTheSameNegativeRule() throws Exception {
        Comment comment = comment("ai-detail-negative", "中段拖沓又尴尬，结尾尤其失望。", 4);
        Method builder = AiService.class.getDeclaredMethod("buildAnalyzedComments", List.class);
        builder.setAccessible(true);

        List<Map<String, Object>> details = (List<Map<String, Object>>) builder.invoke(null, List.of(comment));

        assertEquals("中性", details.getFirst().get("sentiment"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emotionQuadrantCountsUseOneVotePerComment() {
        List<Comment> comments = List.of(
                comment("multi-aspect-positive", "剧情紧凑，表演自然，画面和配乐都很精彩。", 5),
                comment("multi-aspect-negative", "剧情混乱，表演尴尬，画面糟糕，配乐也很差。", 1)
        );

        Map<String, Object> emotionMap = AnalysisService.calculateEmotionMap(comments);
        List<Map<String, Object>> quadrants = (List<Map<String, Object>>) emotionMap.get("quadrants");
        int total = quadrants.stream().mapToInt(q -> ((Number) q.get("count")).intValue()).sum();

        assertEquals(comments.size(), total);
    }

    private Comment comment(String id, String text, int rating) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setText(text);
        comment.setRatingValue(rating);
        comment.setStar(rating + "星");
        comment.setUser("测试用户");
        return comment;
    }
}
