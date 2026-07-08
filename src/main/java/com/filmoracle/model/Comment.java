package com.filmoracle.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 评论实体类
 */
@JsonPropertyOrder({"id","text","ratingValue","star","sentiment","aspect","quadrant","user","createdAt","voteCount"})
public class Comment {
    private String id;
    private String text;
    private int ratingValue;
    private String star;
    private String sentiment;   // 正面 / 负面 / 中性
    private String aspect;      // 十维评价维度
    private String quadrant;    // 情绪象限
    private String user;
    private String createdAt;
    private int voteCount;

    public Comment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getRatingValue() { return ratingValue; }
    public void setRatingValue(int ratingValue) { this.ratingValue = ratingValue; }
    public String getStar() { return star; }
    public void setStar(String star) { this.star = star; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public String getAspect() { return aspect; }
    public void setAspect(String aspect) { this.aspect = aspect; }
    public String getQuadrant() { return quadrant; }
    public void setQuadrant(String quadrant) { this.quadrant = quadrant; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }
}
