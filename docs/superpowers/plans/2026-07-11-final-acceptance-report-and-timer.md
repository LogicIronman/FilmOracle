# FilmOracle Final Acceptance, Report Formatting, and Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix structured review export, add observable analysis elapsed time, and produce a requirement-by-requirement final acceptance result.

**Architecture:** Keep both changes in the existing vanilla JavaScript client. A pure review formatter is shared by on-page rendering and report export; a single lifecycle-managed timer updates the existing task state and appends a frozen duration at completion.

**Tech Stack:** Java 21, Jakarta Servlet 6, Maven/JUnit 5, vanilla JavaScript, Node built-in test runner, Docker Compose.

## Global Constraints

- Preserve all pre-existing uncommitted changes.
- Escape every review field before inserting it into HTML.
- Never allow more than one active timer interval.
- Do not add dependencies.

---

### Task 1: Shared review formatter

**Files:**
- Modify: `src/test/js/report-and-timer.test.mjs`
- Modify: `src/main/webapp/app.js`

**Interfaces:**
- Produces: `formatReviewHtml(review): string`
- Consumes: existing `escapeHtml(value): string`

- [ ] Write tests proving object, JSON string, plain string, and empty input formatting.
- [ ] Run `node --test src/test/js/report-and-timer.test.mjs` and confirm failure because `formatReviewHtml` is absent.
- [ ] Add the minimal formatter and export it through the existing test export mechanism.
- [ ] Reuse it from `renderReview()` and `exportPdfReport()`.
- [ ] Re-run the focused test and confirm it passes.

### Task 2: Analysis elapsed-time lifecycle

**Files:**
- Modify: `src/test/js/report-and-timer.test.mjs`
- Modify: `src/main/webapp/app.js`

**Interfaces:**
- Produces: `formatElapsedTime(milliseconds): string`, `startTaskTimer(label)`, `stopTaskTimer(outcome)`, `resetTaskTimer()`.
- Consumes: existing `setTask(lines, state)` and `appendTask(line, state)` task-panel helpers.

- [ ] Add tests for elapsed formatting and required lifecycle wiring.
- [ ] Run the focused test and confirm it fails for missing timer behavior.
- [ ] Implement one interval backed by monotonic time and update `#task-state` every 100 ms.
- [ ] Start/stop it in fetched-comment and imported-comment analysis paths, including failures.
- [ ] Re-run the focused test and confirm it passes.

### Task 3: Full verification and final acceptance

**Files:**
- Modify: `docs/superpowers/specs/2026-07-11-final-acceptance-report-and-timer-design.md` only if verification exposes a requirement clarification.

**Interfaces:**
- Consumes: Obsidian `开发/FilmOracle/题目要求.md`, repository source, tests, Git history, and Docker configuration.
- Produces: final chat acceptance matrix and missing/risk list.

- [ ] Run `node --test src/test/js/*.test.mjs` and require zero failures.
- [ ] Run `mvn test` and require zero failures.
- [ ] Run `mvn clean package` and require `BUILD SUCCESS` plus a generated WAR.
- [ ] Inspect `git diff --check` and the final diff for accidental unrelated edits.
- [ ] Reconcile every题目 requirement with concrete source, test, Git, or Docker evidence.
