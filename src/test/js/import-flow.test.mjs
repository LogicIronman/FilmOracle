import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const html = readFileSync("src/main/webapp/index.html", "utf8");
const app = readFileSync("src/main/webapp/app.js", "utf8");

test("import page has one import button and does not analyze on submit", () => {
  const form = html.match(/<form class="import-layout" id="import-form">([\s\S]*?)<\/form>/)?.[1] || "";
  const submitButtons = [...form.matchAll(/<button[^>]+type="submit"[^>]*>([^<]+)<\/button>/g)];
  assert.equal(submitButtons.length, 1);
  assert.equal(submitButtons[0][1].trim(), "导入");
  assert.doesNotMatch(form, /导入并本地分析|导入并 AI 分析/);

  const importFunction = app.match(/async function importMovieFromForm[\s\S]*?(?=async function enrichImportedMovieSummary)/)?.[0] || "";
  assert.doesNotMatch(importFunction, /analyzeImportedComments/);
  assert.match(importFunction, /currentAnalysis = emptyAnalysis\(\)/);
  assert.match(importFunction, /showView\("detail"\)/);
});

test("detail analysis reuses imported comments instead of fetching Douban interests", () => {
  assert.match(app, /currentMovie\?\.source === "导入评论文件"/);
  assert.match(app, /analyzeImportedComments\("local"\)/);
  assert.match(app, /analyzeImportedComments\("ai"\)/);
});
