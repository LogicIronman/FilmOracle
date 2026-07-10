package com.filmoracle.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * 电影实体类
 */
@JsonPropertyOrder({"id","title","year","genre","rating","votes","director","cast","region","language","date","duration","summary","posterUrl","poster","source"})
public class Movie {
    private String id = "";
    private String title = "";
    private String year = "";
    private String genre = "";
    private String rating = "";
    private String votes = "";
    private String director = "";
    private String cast = "";
    private String region = "";
    private String language = "";
    private String date = "";
    private String duration = "";
    private String summary = "";
    private String posterUrl = "";
    private String poster = "";
    private String source = "";

    public Movie() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getVotes() { return votes; }
    public void setVotes(String votes) { this.votes = votes; }
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    public String getCast() { return cast; }
    public void setCast(String cast) { this.cast = cast; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    public String getPoster() { return poster; }
    public void setPoster(String poster) { this.poster = poster; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
