import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const app = readFileSync("src/main/webapp/app.js", "utf8");

function loadFunction(name, dependencies = {}) {
  const start = app.indexOf(`function ${name}(`);
  const nextFunction = app.indexOf("\nfunction ", start + 1);
  const nextAsyncFunction = app.indexOf("\nasync function ", start + 1);
  const candidates = [nextFunction, nextAsyncFunction].filter((index) => index > start);
  const end = candidates.length ? Math.min(...candidates) : app.length;
  const source = start >= 0 ? app.slice(start, end).trim() : "";
  assert.ok(source, `${name} should exist`);
  const names = Object.keys(dependencies);
  return Function(...names, `${source}; return ${name};`)(...names.map((key) => dependencies[key]));
}

const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
})[char]);

test("formatReviewHtml renders structured review fields without JSON syntax", () => {
  const formatReviewHtml = loadFunction("formatReviewHtml", { escapeHtml });
  const html = formatReviewHtml({
    overallReception: "整体 <很好>",
    highlightPoints: ["摄影优秀"],
    weaknesses: ["节奏偏慢"],
    finalJudgement: "值得观看",
    fullText: "完整评析"
  });

  assert.match(html, /整体 &lt;很好&gt;/);
  assert.match(html, /出彩：/);
  assert.match(html, /摄影优秀/);
  assert.match(html, /不足：/);
  assert.match(html, /节奏偏慢/);
  assert.match(html, /值得观看/);
  assert.match(html, /完整评析/);
  assert.doesNotMatch(html, /overallReception|highlightPoints|\{\"/);
});

test("formatReviewHtml supports JSON strings, plain text, and empty input", () => {
  const formatReviewHtml = loadFunction("formatReviewHtml", { escapeHtml });
  assert.match(formatReviewHtml('{"fullText":"JSON内的正文"}'), /JSON内的正文/);
  assert.doesNotMatch(formatReviewHtml('{"fullText":"JSON内的正文"}'), /fullText/);
  assert.equal(formatReviewHtml("普通评析"), "<p>普通评析</p>");
  assert.match(formatReviewHtml(null), /暂无评析/);
});

test("formatElapsedTime displays minutes, seconds, and tenths", () => {
  const formatElapsedTime = loadFunction("formatElapsedTime");
  assert.equal(formatElapsedTime(0), "00:00.0");
  assert.equal(formatElapsedTime(12_345), "00:12.3");
  assert.equal(formatElapsedTime(61_987), "01:01.9");
});

test("analysis flows start and stop the shared task timer", () => {
  const fetchedFlow = app.match(/async function loadCommentsAndAnalyze[\s\S]*?(?=async function importMovieFromForm)/)?.[0] || "";
  const importedFlow = app.match(/async function analyzeImportedComments[\s\S]*?(?=\/\/ 解析评论文件)/)?.[0] || "";
  assert.match(fetchedFlow, /startTaskTimer\(/);
  assert.match(fetchedFlow, /stopTaskTimer\(/);
  assert.match(importedFlow, /startTaskTimer\(/);
  assert.match(importedFlow, /stopTaskTimer\(/);
});

test("stopTaskTimer keeps the frozen elapsed time after appending the log", () => {
  const start = app.indexOf("function stopTaskTimer(");
  const end = app.indexOf("\nfunction ", start + 1);
  const source = app.slice(start, end > start ? end : app.length);
  assert.ok(source.indexOf("appendTask(`> 总耗时:") < source.indexOf("state.textContent ="));
});
