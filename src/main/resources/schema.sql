-- FilmOracle 数据库脚本
-- 使用 utf8mb4 支持完整中文和 emoji

CREATE DATABASE IF NOT EXISTS filmoracle DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE filmoracle;

-- 用户表
CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(20) DEFAULT 'user',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 电影缓存表
CREATE TABLE IF NOT EXISTS movie_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    douban_id VARCHAR(20) UNIQUE,
    title VARCHAR(200) NOT NULL,
    year VARCHAR(10),
    genre VARCHAR(200),
    rating DECIMAL(3,1),
    votes INT DEFAULT 0,
    director VARCHAR(500),
    cast TEXT,
    region VARCHAR(200),
    language VARCHAR(200),
    pubdate VARCHAR(100),
    duration VARCHAR(50),
    summary TEXT,
    poster_url VARCHAR(500),
    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_douban (douban_id),
    INDEX idx_title (title)
);

-- 搜索缓存表
CREATE TABLE IF NOT EXISTS search_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(200) NOT NULL,
    results_json TEXT,
    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_keyword (keyword)
);

-- 评论表
CREATE TABLE IF NOT EXISTS comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id BIGINT NOT NULL,
    douban_comment_id VARCHAR(50),
    content TEXT NOT NULL,
    rating INT DEFAULT 0,
    star VARCHAR(10),
    user_name VARCHAR(100),
    created_at VARCHAR(50),
    vote_count INT DEFAULT 0,
    source VARCHAR(20) DEFAULT 'douban',
    dedup_hash VARCHAR(64),
    sentiment VARCHAR(10),
    aspect VARCHAR(50),
    quadrant VARCHAR(20),
    FOREIGN KEY (movie_id) REFERENCES movie_cache(id),
    INDEX idx_movie (movie_id),
    INDEX idx_sentiment (sentiment),
    INDEX idx_dedup (dedup_hash)
);

-- AI 分析任务表
CREATE TABLE IF NOT EXISTS ai_analysis_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    batch_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (movie_id) REFERENCES movie_cache(id)
);

-- 电影分析摘要表
CREATE TABLE IF NOT EXISTS movie_analysis_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id BIGINT NOT NULL UNIQUE,
    keywords_json TEXT,
    rating_dist_json TEXT,
    comparison_json TEXT,
    scatter_json TEXT,
    radar_json TEXT,
    sentiment_dist_json TEXT,
    summary_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (movie_id) REFERENCES movie_cache(id)
);

-- 用户浏览历史表（直接存储电影信息，无需外键关联）
CREATE TABLE IF NOT EXISTS user_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    movie_id VARCHAR(50) NOT NULL,
    title VARCHAR(200),
    year VARCHAR(10),
    rating VARCHAR(10),
    poster_url VARCHAR(500),
    poster_local VARCHAR(200),
    genre VARCHAR(200),
    director VARCHAR(500),
    source VARCHAR(50),
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (username),
    UNIQUE KEY uk_user_movie (username, movie_id)
);

-- 系统设置表
CREATE TABLE IF NOT EXISTS app_setting (
    id BIGINT PRIMARY KEY DEFAULT 1,
    ai_model VARCHAR(100) DEFAULT 'rule-based',
    api_key VARCHAR(200),
    ai_prompt TEXT,
    crawler_api_url VARCHAR(500) DEFAULT 'https://m.douban.com/rexxar/api/v2',
    comment_count INT DEFAULT 20,
    request_timeout INT DEFAULT 10,
    fallback_enabled BOOLEAN DEFAULT TRUE
);

-- 初始化默认设置
INSERT INTO app_setting (id, ai_model, crawler_api_url, comment_count, request_timeout, fallback_enabled)
VALUES (1, 'rule-based', 'https://m.douban.com/rexxar/api/v2', 20, 10, TRUE)
ON DUPLICATE KEY UPDATE id = id;

-- 默认管理员账号 (密码: admin123, SHA-256)
INSERT INTO user_account (username, password_hash, role)
VALUES ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'admin')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash);
