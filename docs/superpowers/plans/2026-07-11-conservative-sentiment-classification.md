# Conservative Sentiment Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将复杂、矛盾或评分与文本冲突的评论保守归入中性，只将方向明确的评论归入正面或负面。

**Architecture:** 保留 `AnalysisService` 作为唯一标签规则入口，在现有正负词证据基础上增加“混合证据”和“转折语境”判定。AI 评论明细继续通过 `applyRuleLabels` 获得相同标签，不引入新依赖或前端改动。

**Tech Stack:** Java 21、JUnit 5、Maven、Jakarta Servlet、现有 Node 内置测试运行器。

## Global Constraints

- 只修改后端本地规则分类和对应回归测试。
- 不修改前端、数据库结构、导入流程或评论检索界面。
- 不增加第三方依赖。

---

### Task 1: 用回归测试锁定保守三分类边界

**Files:**
- Modify: `src/test/java/com/filmoracle/service/AnalysisServiceTest.java`

**Interfaces:**
- Consumes: `AnalysisService.analyze(List<Comment>, Movie)` 和 `AnalysisService.applyRuleLabels(List<Comment>)`
- Produces: 复杂评论为“中性”、纯负面为“负面”、纯正面为“正面”的行为约束

- [x] **Step 1: 写入失败测试**

新增三个测试：截图中的先夸后贬 4 星评论应为中性；已有高星强负面样例调整为中性；纯负面 2 星样例应保持负面。

- [x] **Step 2: 验证测试按预期失败**

Run: `mvn -Dtest=AnalysisServiceTest test`

Expected: 两个复杂评论测试因当前返回“负面”而失败，纯负面测试通过。

### Task 2: 实现保守三分类规则

**Files:**
- Modify: `src/main/java/com/filmoracle/service/AnalysisService.java`
- Test: `src/test/java/com/filmoracle/service/AnalysisServiceTest.java`

**Interfaces:**
- Consumes: 评论文本、`ratingValue`、现有正负词表和转折模式
- Produces: `classifySentiment(Comment)` 返回“正面”“中性”或“负面”

- [x] **Step 1: 实现最小规则调整**

在情感证据计算后应用以下顺序：同时存在正负证据或存在转折且转折两侧方向不同则返回“中性”；高分与强负面文本冲突或低分与强正面文本冲突则返回“中性”；只有单向强证据才返回正面或负面；无文本证据时才按星级兜底。

- [x] **Step 2: 验证聚焦测试通过**

Run: `mvn -Dtest=AnalysisServiceTest test`

Expected: `AnalysisServiceTest` 全部通过。

- [x] **Step 3: 验证完整项目**

Run: `mvn test`

Expected: Maven 输出 `BUILD SUCCESS` 且失败数为 0。

Run: `node --test src/test/js/*.test.mjs`

Expected: Node 测试失败数为 0。

- [x] **Step 4: 更新本地运行环境**

Run: `docker compose up -d --build tomcat`

Expected: Tomcat 容器构建并启动，访问 `http://127.0.0.1:8080/api/health` 返回 `"ok":true`。

- [x] **Step 5: 提交改动**

```bash
git add docs/superpowers/specs/2026-07-11-conservative-sentiment-classification-design.md docs/superpowers/plans/2026-07-11-conservative-sentiment-classification.md src/main/java/com/filmoracle/service/AnalysisService.java src/test/java/com/filmoracle/service/AnalysisServiceTest.java
git commit -m "fix(sentiment): classify ambiguous reviews as neutral"
```
