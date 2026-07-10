# 关键词云与 AI 数据库缓存 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent top local-rule keywords from being obscured, cache successful AI analysis results in MySQL, and classify strong negative wording correctly even on high-star comments.

**Architecture:** A dedicated `AiAnalysisCacheService` owns MySQL schema bootstrap, stable cache keys, JSON serialization, and cache lookup/upsert. `ApiServlet` uses it before calling the remote AI and marks cache hits in response metadata. The browser keeps its existing layout and adds deterministic non-overlapping keyword placement plus a cache-hit task message.

**Tech Stack:** Java 21, Jakarta Servlet, MySQL 8.4, Jackson, JUnit 5, vanilla JavaScript/CSS.

## Global Constraints

- Do not add dependencies or change the existing Java Servlet/MySQL/native-JS stack.
- Store AI cache only in MySQL; do not use localStorage or an in-memory cache.
- Cache identity is movie key + complete comment fingerprint + AI model + prompt fingerprint.
- Only successful remote-AI results are stored; rule-based fallback results are never stored as AI cache.
- Preserve the existing analysis-page layout, visual palette, and action flow.

---

### Task 1: Make local sentiment respect strong text evidence

**Files:**
- Modify: `src/main/java/com/filmoracle/service/AnalysisService.java:305-314`
- Modify: `src/test/java/com/filmoracle/service/AnalysisServiceTest.java`

**Interfaces:**
- Produces: `AnalysisService.analyze(List<Comment>, Movie)` labels a 4–5-star comment containing clearly stronger negative terms as `负面`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void classifiesStrongNegativeWordingAsNegativeEvenWithHighStarRating() {
    Movie movie = new Movie();
    movie.setTitle("情感规则验收电影");
    Comment comment = comment("high-star-negative", "画面很好，但中段拖沓又尴尬，结尾尤其失望。", 4);

    AnalysisService.analyze(List.of(comment), movie);

    assertEquals("负面", comment.getSentiment());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=AnalysisServiceTest#classifiesStrongNegativeWordingAsNegativeEvenWithHighStarRating test`

Expected: assertion failure because the current implementation returns `正面` solely from the four-star rating.

- [ ] **Step 3: Write minimal classification logic**

```java
int pos = countMatches(c.getText(), POSITIVE_WORDS);
int neg = countMatches(c.getText(), NEGATIVE_WORDS);
if (neg >= pos + 2) return "负面";
if (pos >= neg + 2) return "正面";
if (c.getRatingValue() >= 4) return "正面";
if (c.getRatingValue() <= 2) return "负面";
return "中性";
```

- [ ] **Step 4: Run the focused and full tests**

Run: `mvn test`

Expected: all existing tests pass and the new test reports `负面`.

### Task 2: Add MySQL-backed AI analysis cache service

**Files:**
- Create: `src/main/java/com/filmoracle/service/AiAnalysisCacheService.java`
- Modify: `src/main/resources/schema.sql:95`
- Create: `src/test/java/com/filmoracle/service/AiAnalysisCacheServiceTest.java`

**Interfaces:**
- Produces: `AiAnalysisCacheService.find(movie, comments, model, prompt)` returning an optional cached `AnalysisResult` and `save(movie, comments, model, prompt, analysis)` storing the complete serialized result.
- Consumes: `DatabaseUtil`, `JsonUtil`, `Movie`, `Comment`, `AnalysisResult`.

- [ ] **Step 1: Write failing cache-key and serialization tests**

```java
@Test
void commentFingerprintChangesWhenAnyFetchedCommentChanges() {
    String first = AiAnalysisCacheService.commentFingerprint(List.of(comment("1", "原始评论", 4)));
    String changed = AiAnalysisCacheService.commentFingerprint(List.of(comment("1", "改后的评论", 4)));
    assertNotEquals(first, changed);
}

@Test
void serializesAndRestoresTheCompleteAnalysisResult() {
    AnalysisResult source = AnalysisService.analyze(List.of(comment("1", "节奏拖沓又失望", 2)), movie());
    AnalysisResult restored = AiAnalysisCacheService.fromJson(AiAnalysisCacheService.toJson(source));
    assertEquals(source.getReview(), restored.getReview());
    assertEquals(source.getAnalyzedComments().size(), restored.getAnalyzedComments().size());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=AiAnalysisCacheServiceTest test`

Expected: compilation failure because `AiAnalysisCacheService` does not exist.

- [ ] **Step 3: Implement the service and database table**

```sql
CREATE TABLE IF NOT EXISTS ai_analysis_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_key VARCHAR(255) NOT NULL,
    comment_fingerprint CHAR(64) NOT NULL,
    ai_model VARCHAR(100) NOT NULL,
    prompt_fingerprint CHAR(64) NOT NULL,
    analysis_json LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_analysis_cache (movie_key, comment_fingerprint, ai_model, prompt_fingerprint)
);
```

Use SHA-256 over deterministic comment fields (`id`, `text`, `ratingValue`, `user`, `voteCount`) and prompt content. Call `CREATE TABLE IF NOT EXISTS` in the service before query/upsert so existing Docker volumes receive the new table without a reset.

- [ ] **Step 4: Run focused and full tests**

Run: `mvn test`

Expected: cache-key/serialization tests and all existing tests pass.

### Task 3: Use the cache in the AI endpoint and surface cache restoration

**Files:**
- Modify: `src/main/java/com/filmoracle/web/ApiServlet.java:397-439`
- Modify: `src/main/webapp/app.js:1693-1748`
- Modify: `src/test/java/com/filmoracle/service/AiAnalysisCacheServiceTest.java`

**Interfaces:**
- Produces: `/api/analyze` response metadata with `cacheHit` and `cachedAt`; cached success skips `AiService.analyzeWithAi`.
- Consumes: `AiAnalysisCacheService.find/save`.

- [ ] **Step 1: Write failing persistence behavior test**

```java
@Test
void cacheIdentityIncludesModelAndPrompt() {
    assertNotEquals(
        AiAnalysisCacheService.cacheKey(movie(), comments(), "deepseek-chat", "prompt A"),
        AiAnalysisCacheService.cacheKey(movie(), comments(), "deepseek-chat", "prompt B")
    );
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=AiAnalysisCacheServiceTest#cacheIdentityIncludesModelAndPrompt test`

Expected: compilation failure because `cacheKey` is not implemented yet.

- [ ] **Step 3: Integrate cache lookup/save**

```java
Optional<CachedAnalysis> cached = AiAnalysisCacheService.find(movie, analysisComments, aiModel, aiPrompt);
if (cached.isPresent()) {
    analysis = cached.get().analysis();
    engine = "ai-cache:" + aiModel;
    cacheHit = true;
} else {
    analysis = AiService.analyzeWithAi(...);
    if (analysis != null) AiAnalysisCacheService.save(movie, analysisComments, aiModel, aiPrompt, analysis);
}
```

Keep the existing rule fallback unchanged. In the browser, append `已恢复数据库缓存结果，未重复调用 AI` when `analysisData.meta.cacheHit` is true.

- [ ] **Step 4: Run full Java and browser checks**

Run: `mvn test` then `node --check src/main/webapp/app.js`

Expected: all tests pass and Node reports no syntax errors.

### Task 4: Prevent keyword overlap while preserving style

**Files:**
- Modify: `src/main/webapp/app.js:1204-1223`
- Modify: `src/main/webapp/styles.css:648-660`

**Interfaces:**
- Produces: `renderKeywordCloud` layout that places high-frequency words first, gives top five words higher z-index, and keeps labels inside the keyword-cloud bounds.

- [ ] **Step 1: Add a browser-visible regression fixture**

Use a local-rule result with long high-frequency keywords and verify the five largest labels remain readable in the cloud. This is a visual regression because element dimensions depend on rendered fonts.

- [ ] **Step 2: Implement bounded collision-aware placement**

Render keywords in descending count order. Measure each hidden label, try its dedicated core or outer-ring slots, and accept the first rectangle that does not intersect an already placed rectangle. If all preferred slots collide, use the first in-bounds slot and assign a lower z-index; the top five always receive `z-index: 100` downwards.

- [ ] **Step 3: Verify in browser and syntax check**

Run: `node --check src/main/webapp/app.js`

Then load a local-rule result with at least 20 keywords; confirm the top five are not obscured and all labels remain inside the panel.

### Task 5: Deploy and verify database cache behavior

**Files:**
- Modify: no additional files.

- [ ] **Step 1: Rebuild local Docker service**

Run: `docker compose up -d --build tomcat`

- [ ] **Step 2: Verify first request writes MySQL cache and second request restores it**

Register a temporary normal user, submit one fixed movie and comment payload to `/api/analyze` twice, and assert the second response has `meta.cacheHit=true`. Query MySQL for the matching `ai_analysis_cache` row, then delete the temporary user/movie/comment/cache rows.

- [ ] **Step 3: Run final checks and commit**

Run: `mvn test`, `node --check src/main/webapp/app.js`, `git diff --check`, and `git status --short`.

Commit: `git add src/main src/test docs/superpowers/plans && git commit -m "feat(ai): cache analysis results in mysql"`
