package com.filmoracle.service;

import com.filmoracle.model.AnalysisResult;
import com.filmoracle.model.Comment;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServicePromptTest {

    @Test
    void defaultPromptKeepsTheRequiredAiAnalysisContract() {
        String prompt = AiService.DEFAULT_PROMPT;

        assertTrue(prompt.contains("positive、negative、neutral"));
        assertTrue(prompt.contains("关键词"));
        assertTrue(prompt.contains("scatter"));
        assertTrue(prompt.contains("emotionMap"));
        assertTrue(prompt.contains("ratingDistribution"));
        assertTrue(prompt.contains("只输出一个合法 JSON 对象"));
    }

    @Test
    void deterministicEmotionChartsReplaceAiGeneratedCounts() {
        Comment comment = new Comment();
        comment.setId("chart-source");
        comment.setText("剧情紧凑，表演自然，画面很精彩。");
        comment.setRatingValue(5);

        AnalysisResult result = new AnalysisResult();
        result.setScatter(List.of(new LinkedHashMap<>(Map.of(
                "aspect", "主题", "polarity", -5.0, "intensity", 5.0, "votes", 47
        ))));
        result.setEmotionMap(new LinkedHashMap<>(Map.of("quadrants", List.of(
                Map.of("name", "强烈差评", "position", "leftTop", "count", 99, "percent", 99)
        ))));

        AiService.applyDeterministicEmotionCharts(result, List.of(comment));

        assertEquals(1, result.getEmotionMap().get("quadrants") instanceof List<?> quadrants
                ? quadrants.stream().mapToInt(q -> ((Number) ((Map<?, ?>) q).get("count")).intValue()).sum()
                : 0);
        assertTrue(result.getScatter().stream().noneMatch(point -> ((Number) point.get("votes")).intValue() == 47));
    }
}
