# Deterministic Emotion Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让观众情绪分布图完全基于本次完整评论列表确定性计算，消除AI自造数量和前端重复放大。

**Architecture:** `AnalysisService`负责评论粒度象限和方面聚类散点，`AiService`在解析AI文字结果后强制覆盖图表字段，前端仅渲染后端真实聚类权重。所有改动沿用现有Java 21、Maven和原生JavaScript技术栈。

**Tech Stack:** Java 21、JUnit 5、Maven、原生JavaScript、Node内置测试运行器、Docker Compose。

## Global Constraints

- AI继续生成文字评析和非情绪图数据。
- 不修改评论获取、数据库结构和导入逻辑。
- 不增加第三方依赖。

---

### Task 1: 锁定错误统计口径

**Files:**
- Modify: `src/test/java/com/filmoracle/service/AnalysisServiceTest.java`
- Modify: `src/test/java/com/filmoracle/service/AiServicePromptTest.java`
- Create: `src/test/js/emotion-chart.test.mjs`

**Interfaces:**
- Consumes: `AnalysisService.calculateEmotionMap(List<Comment>)`、`AiService.applyDeterministicEmotionCharts(AnalysisResult, List<Comment>)`、`app.js`
- Produces: 象限总数等于评论数、AI图表被覆盖、前端不含方面频次替代逻辑的回归约束

- [ ] **Step 1: 写入三个失败测试**

Java测试构造同时提及多个方面的评论，断言四象限计数合计等于评论数量；构造带伪造AI图表的`AnalysisResult`，断言调用覆盖方法后使用本地结果；Node测试断言前端不再定义`allVotesSmall`和`aspectFreq`。

- [ ] **Step 2: 验证测试失败**

Run: `mvn -Dtest=AnalysisServiceTest,AiServicePromptTest test`

Expected: 象限总数测试失败，AI覆盖方法尚不存在导致测试编译失败。

Run: `node --test src/test/js/emotion-chart.test.mjs`

Expected: 因`app.js`仍包含`allVotesSmall`或`aspectFreq`而失败。

### Task 2: 实现确定性后端图表

**Files:**
- Modify: `src/main/java/com/filmoracle/service/AiService.java`
- Modify: `src/main/java/com/filmoracle/service/AnalysisService.java`

**Interfaces:**
- Produces: `static void applyDeterministicEmotionCharts(AnalysisResult result, List<Comment> comments)`

- [ ] **Step 1: AI结果统一覆盖为本地图表**

在AI响应映射完成时调用`applyDeterministicEmotionCharts`，方法分别设置`calculateScatter(comments)`和`calculateEmotionMap(comments)`结果。

- [ ] **Step 2: 象限按评论粒度统计**

遍历评论列表，每条评论依据整体极性与强度进入唯一象限；`count`合计使用评论总数，不再用多方面散点权重统计象限。

- [ ] **Step 3: 运行Java聚焦测试**

Run: `mvn -Dtest=AnalysisServiceTest,AiServicePromptTest test`

Expected: 全部通过。

### Task 3: 移除前端重复放大

**Files:**
- Modify: `src/main/webapp/app.js`
- Test: `src/test/js/emotion-chart.test.mjs`

**Interfaces:**
- Consumes: 后端散点中的`votes`
- Produces: 气泡半径和提示内容只使用当前聚类真实评论数

- [ ] **Step 1: 删除方面频次替代逻辑**

删除`allVotesSmall`、`aspectFreq`及其替换分支，保留`const votes = Math.max(1, Number(point.votes || 1))`。

- [ ] **Step 2: 运行Node聚焦测试**

Run: `node --test src/test/js/emotion-chart.test.mjs`

Expected: 通过。

- [ ] **Step 3: 完整验证与部署**

Run: `mvn test`

Expected: `BUILD SUCCESS`。

Run: `node --test src/test/js/*.test.mjs`

Expected: 失败数为0。

Run: `docker compose up -d --build tomcat`

Expected: 容器启动且`/api/health`返回`"ok":true`。
