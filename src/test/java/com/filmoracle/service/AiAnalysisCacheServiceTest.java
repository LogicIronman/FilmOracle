package com.filmoracle.service;

import com.filmoracle.model.AnalysisResult;
import com.filmoracle.model.Comment;
import com.filmoracle.model.Movie;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AiAnalysisCacheServiceTest {

    @Test
    void commentFingerprintChangesWhenAnyFetchedCommentChanges() {
        String first = AiAnalysisCacheService.commentFingerprint(List.of(comment("1", "原始评论", 4)));
        String changed = AiAnalysisCacheService.commentFingerprint(List.of(comment("1", "改后的评论", 4)));

        assertNotEquals(first, changed);
    }

    @Test
    void serializesAndRestoresTheCompleteAnalysisResult() {
        AnalysisResult source = AnalysisService.analyze(
                List.of(comment("1", "节奏拖沓又失望，整体体验很差。", 2)), movie());

        AnalysisResult restored = AiAnalysisCacheService.fromJson(AiAnalysisCacheService.toJson(source));

        assertEquals(source.getReview(), restored.getReview());
        assertEquals(source.getAnalyzedComments().size(), restored.getAnalyzedComments().size());
        assertEquals(source.getKeywords().size(), restored.getKeywords().size());
    }

    @Test
    void cacheIdentityIncludesModelAndPrompt() {
        assertNotEquals(
                AiAnalysisCacheService.cacheKey(movie(), List.of(comment("1", "评论", 4)), "deepseek-chat", "prompt A"),
                AiAnalysisCacheService.cacheKey(movie(), List.of(comment("1", "评论", 4)), "deepseek-chat", "prompt B")
        );
    }

    private Movie movie() {
        Movie movie = new Movie();
        movie.setId("cache-test-film");
        movie.setTitle("缓存验收电影");
        movie.setYear("2026");
        return movie;
    }

    private Comment comment(String id, String text, int rating) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setText(text);
        comment.setRatingValue(rating);
        comment.setStar(rating + "星");
        comment.setUser("测试用户");
        comment.setVoteCount(1);
        return comment;
    }
}
