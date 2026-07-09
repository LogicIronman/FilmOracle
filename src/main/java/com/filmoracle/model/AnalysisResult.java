package com.filmoracle.model;

import java.util.List;
import java.util.Map;

/**
 * AI分析结果（含图表数据）
 */
public class AnalysisResult {
    private List<Object[]> keywords;           // [[word, count], ...]
    private List<Object[]> ratingDistribution; // [[label, percent], ...]
    private List<Object[]> comparison;         // [[label, value], ...]
    private List<Map<String, Object>> scatter; // [{x, y, sentiment, emotionLabel, quadrant, weight, quote, index}, ...]
    private Map<String, Object> emotionMap;   // {quadrants, centroid, summary, axis}
    private List<Object[]> radar;              // [[label, score], ...]
    private Map<String, Integer> sentimentDistribution; // {positive, negative, neutral}
    private Map<String, Object> summary;       // {positiveRate, negativeRate, neutralRate, keywordsSummary, mainControversy}
    private List<Map<String, Object>> analyzedComments; // 每条评论的分析结果
    private String review;                     // 400字AI电影评析
    private String engine;                     // "ai" 或 "rule-based"

    // Getters and setters
    public List<Object[]> getKeywords() { return keywords; }
    public void setKeywords(List<Object[]> keywords) { this.keywords = keywords; }
    public List<Object[]> getRatingDistribution() { return ratingDistribution; }
    public void setRatingDistribution(List<Object[]> ratingDistribution) { this.ratingDistribution = ratingDistribution; }
    public List<Object[]> getComparison() { return comparison; }
    public void setComparison(List<Object[]> comparison) { this.comparison = comparison; }
    public List<Map<String, Object>> getScatter() { return scatter; }
    public void setScatter(List<Map<String, Object>> scatter) { this.scatter = scatter; }
    public Map<String, Object> getEmotionMap() { return emotionMap; }
    public void setEmotionMap(Map<String, Object> emotionMap) { this.emotionMap = emotionMap; }
    public List<Object[]> getRadar() { return radar; }
    public void setRadar(List<Object[]> radar) { this.radar = radar; }
    public Map<String, Integer> getSentimentDistribution() { return sentimentDistribution; }
    public void setSentimentDistribution(Map<String, Integer> sentimentDistribution) { this.sentimentDistribution = sentimentDistribution; }
    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }
    public List<Map<String, Object>> getAnalyzedComments() { return analyzedComments; }
    public void setAnalyzedComments(List<Map<String, Object>> analyzedComments) { this.analyzedComments = analyzedComments; }
    public String getReview() { return review; }
    public void setReview(String review) { this.review = review; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
}
