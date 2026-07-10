package com.filmoracle.service;

import org.junit.jupiter.api.Test;

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
}
