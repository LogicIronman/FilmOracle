package com.filmoracle.service;

import com.filmoracle.model.AnalysisResult;
import com.filmoracle.model.Comment;
import com.filmoracle.model.Movie;
import org.junit.jupiter.api.Test;

import java.util.List;

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
