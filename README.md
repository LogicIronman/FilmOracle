# FilmOracle — AI 电影评论智能分析平台

> JDK 21 · Tomcat 11 · Jakarta Servlet 6.0 · JDBC · MySQL 8.4 · Maven · Docker

## 项目简介

FilmOracle 是一个面向 JavaWeb 实训展示的电影评论智能分析平台。核心流程：

```
电影搜索 → 选择影片 → 详情展示 → 获取评论 → AI 分析 → 图表展示 → 评论检索 → 历史记录
```

支持两条等价入口：豆瓣搜索获取评论，或直接导入本地评论文件。两条路径最终进入同一套分析展示界面。

## 技术栈

| 层级 | 技术 |
|------|------|
| JDK | 21 (LTS) |
| Servlet | Jakarta Servlet 6.0（非 javax） |
| 容器 | Tomcat 11 |
| 数据库 | MySQL 8.4 (utf8mb4) |
| 连接池 | HikariCP 5.1 |
| JSON | Jackson 2.17 |
| 构建 | Maven 3.9 (WAR) |
| 部署 | Docker + Docker Compose |
| 前端 | HTML + CSS + 原生 JavaScript（无框架） |

## 项目结构

```
FilmOracle/
├── pom.xml                              Maven 构建配置
├── Dockerfile                           多阶段构建（Maven 编译 → Tomcat 运行）
├── docker-compose.yml                   MySQL + Tomcat 容器编排
│
├── src/main/java/com/filmoracle/
│   ├── model/
│   │   ├── Movie.java                   电影实体
│   │   ├── Comment.java                 评论实体
│   │   └── AnalysisResult.java          分析结果（含图表数据）
│   │
│   ├── util/
│   │   ├── HttpUtil.java                豆瓣 API HTTP 客户端（超时+重试+多请求头）
│   │   ├── JsonUtil.java                JSON 序列化/解析工具
│   │   └── FallbackData.java            演示兜底数据（8部电影 + 12条评论）
│   │
│   ├── service/
│   │   ├── DoubanApiService.java        豆瓣数据服务（搜索/详情/评论/标签/Top）
│   │   ├── AnalysisService.java         评论分析引擎（情感/关键词/雷达/散点）
│   │   ├── CommentService.java          评论单条/批量持久化与分类查询
│   │   └── CacheService.java            内存缓存（带 TTL）
│   │
│   └── web/
│       ├── ApiServlet.java              API 路由（/api/* 全部请求）
│       └── CorsFilter.java              CORS 跨域过滤器
│
├── src/main/resources/
│   └── schema.sql                       数据库建表脚本（7张表）
│
└── src/main/webapp/
    ├── WEB-INF/web.xml                  Servlet 映射 + 过滤器配置
    ├── index.jsp                        JSP 服务端展示入口（复用既有页面）
    ├── index.html                       单页应用（6个视图）
    ├── styles.css                       设计系统（Claude/Anthropic 风格）
    ├── app.js                           前端逻辑（搜索/详情/分析/筛选）
    ├── assets/                          Logo + Favicon
    └── pic/                             15张电影海报
```

## 快速启动

### 方式一：Docker Compose（推荐）

```bash
cd FilmOracle
docker compose up -d --build
```

启动后访问：

| 服务 | 地址 |
|------|------|
| 前端界面 | http://localhost:8080 |
| API 健康检查 | http://localhost:8080/api/health |
| MySQL | localhost:3307 |

### 方式二：本地运行

```bash
# 1. 编译 WAR
cd FilmOracle
mvn clean package

# 2. 部署到 Tomcat 11
# 将 target/FilmOracle.war 复制到 Tomcat 的 webapps/ROOT.war
# 启动 Tomcat 后访问 http://localhost:8080
```

## API 接口

### 数据接口（第二阶段）

| 方法 | 路径 | 说明 | 数据源 |
|------|------|------|--------|
| GET | `/api/health` | 健康检查，返回缓存状态 | — |
| GET | `/api/hot?start=0&limit=20` | 热门高分电影 | m.douban.com |
| GET | `/api/search?q=电影名` | 搜索电影（多策略） | search.douban.com + subject_suggest |
| GET | `/api/tags?tag=热门&sort=recommend` | 按标签浏览 | movie.douban.com |
| GET | `/api/top?type=24` | Top 排行榜 | movie.douban.com |
| GET | `/api/movie/{id}` | 电影详情 | m.douban.com |
| GET | `/api/movie/{id}/interests?count=20` | 电影短评 | m.douban.com |

### 分析接口（第三阶段）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/analyze` | 评论分析，返回情感/关键词/雷达/散点/摘要 |

### 评论管理接口（第四阶段）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/comments?movie=&keyword=&sentiment=&limit=` | 按电影名称、关键词、情感类型查询已入库评论 |
| POST | `/api/comments` | 单条录入或 JSON 批量写入评论 |
| POST | `/api/comments/import` | 上传 CSV/TXT，文件保存到 Docker 卷并批量写入 MySQL |

**POST /api/analyze 请求体：**

```json
{
  "movie": { "id": "1292052", "title": "肖申克的救赎", "rating": "9.7", "votes": "2900000", "genre": "剧情" },
  "comments": [
    { "id": "c1", "text": "导演对节奏的把控太绝了", "ratingValue": 5, "user": "用户A" },
    { "id": "c2", "text": "部分人物动机有点薄弱", "ratingValue": 2, "user": "用户B" }
  ]
}
```

**返回结果包含：**

- `keywords` — 关键词词频（词云数据）
- `ratingDistribution` — 星级分布（5星~1星占比）
- `comparison` — 同类型对比（评分/热度/评价人数/正向情绪）
- `scatter` — 情感散点（x:-1~1 情绪正负, y:0~1 情绪强度, 四象限标签）
- `radar` — 十维雷达图（剧本/导演/表演/摄影/剪辑/声音/美术/特效/主题/完成度）
- `sentimentDistribution` — 情感分布（正面/负面/中性占比）
- `summary` — 摘要（好评率/关键词摘要/主要争议点）
- `analyzedComments` — 每条评论的分析结果（情感/维度/象限/坐标/关键词/置信度）

## 豆瓣 API 说明

本项目使用以下豆瓣接口（仅供学习研究）：

| 接口域名 | 用途 | 验证方式 | 状态 |
|----------|------|----------|------|
| m.douban.com/rexxar/api/v2 | 热门/详情/短评 | User-Agent + Referer | ✅ 可用 |
| movie.douban.com/j/ | 搜索建议/标签/Top | User-Agent | ✅ 可用 |
| search.douban.com/movie/ | 搜索页 HTML 解析 | User-Agent | ✅ 可用 |
| api.douban.com/v2 | 官方接口 | apikey | ❌ 失效(code 109) |
| frodo.douban.com/api/v2 | 微信小程序 | apiKey + Referer + UA | ✅ 可用（备用） |

关键：请求头 `User-Agent` 必须用带连字符的标准写法，`Referer` 必须携带，否则返回 418 反爬。

## 数据库

MySQL 数据库包含 7 张表：

- `user_account` — 用户和管理员账号
- `movie_cache` — 电影详情缓存
- `search_cache` — 搜索结果缓存
- `comment` — 评论（带去重指纹、情感标签）
- `ai_analysis_task` — AI 分析任务记录
- `movie_analysis_summary` — 电影分析摘要
- `app_setting` — 系统设置

默认管理员：`admin` / `admin123`

评论通过 `CommentService` 使用 JDBC 事务批量写入 `comment` 表；电影信息以 `movie_cache` 为分类主表，支持按电影名称、关键词和情感类型查询。

## 导入文件持久化

Docker Compose 使用 `upload_data` 卷挂载到 Tomcat 容器的 `/app/uploads`。通过 `/api/comments/import` 上传的 CSV/TXT 会先写入该目录，再解析并批量保存；容器重建后导入文件仍可保留。

## 稳定性保障

- 每个豆瓣 API 请求都有指数退避重试（最多 2 次）
- 内存缓存按端点设置不同 TTL（3~15 分钟）
- 所有 API 失败时自动返回兜底数据，展示不中断
- 搜索使用多策略：HTML 解析 → subject_suggest → 兜底
- 详情使用多端点：m.douban.com → frodo.douban.com → 兜底

## 开发阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| 第一阶段 | 前端原型（6个视图 + mock 数据） | ✅ 完成 |
| 第二阶段 | Java 后端 + 豆瓣 API 对接 + 缓存 + 兜底 | ✅ 完成 |
| 第三阶段 | 服务端分析引擎 + 图表数据生成 + 前端联调 | ✅ 完成 |
| 第四阶段 | MySQL 持久化 + JDBC 评论管理 + Docker 导入卷 + 验收测试 | ✅ 完成 |
