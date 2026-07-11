# 导入评论专用分析 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make imported CSV/TXT comments the sole source for local and AI analysis.

**Architecture:** The import endpoint parses, persists, and returns the uploaded comments. The import page receives that exact list and invokes either a local-only analyzer or `/api/analyze`, without using the Douban interests endpoint.

**Tech Stack:** Java 21, Jakarta Servlet, MySQL, vanilla JavaScript/CSS, JUnit 5.

## Global Constraints

- Only modify import-comment UI and its analysis path.
- Do not fetch Douban comments after a file import.
- Allow title-based detail lookup only for filling a missing summary.

### Task 1: Return parsed import comments from the backend

**Files:**
- Modify: `src/main/java/com/filmoracle/web/ApiServlet.java:241-274`
- Test: `src/test/java/com/filmoracle/web/ApiServletImportTest.java`

- [ ] Add a failing reflection test asserting CSV input returns the same text, rating, and user as parsed comments.
- [ ] Extend `/api/comments/import` response with `movie` and `comments` after `CommentService.saveBatch` succeeds.
- [ ] Run: `mvn -Dtest=ApiServletImportTest test`.

### Task 2: Build the import-only analysis UI and flow

**Files:**
- Modify: `src/main/webapp/index.html:169-207`
- Modify: `src/main/webapp/app.js:1808-1934`
- Modify: `src/main/webapp/styles.css:801-875`

- [ ] Replace the single submit action with `导入并本地分析` and `导入并 AI 分析`.
- [ ] Submit `FormData` to `/api/comments/import`; assign only returned comments to `currentComments`.
- [ ] Add `analyzeImportedComments(mode)` that calls `buildAnalysis` or `/api/analyze`, never `/interests`.
- [ ] Add a title-only summary lookup; retain blank/user summary when no movie detail is found.
- [ ] Add `import-spec` view with UTF-8 TXT/CSV contract and examples.
- [ ] Run: `node --check src/main/webapp/app.js`.

### Task 3: Verify import provenance and regression safety

- [ ] Run `mvn test` and `git diff --check`.
- [ ] Rebuild Docker, upload a UTF-8 TXT fixture, verify local and AI results report the fixture comment count and do not call `/api/movie/.../interests`.
- [ ] Commit all implementation and test files with `feat(import): analyze uploaded comments only`.
