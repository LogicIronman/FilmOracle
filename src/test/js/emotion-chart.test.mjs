import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const app = readFileSync("src/main/webapp/app.js", "utf8");

test("emotion bubbles use cluster votes without repeating aspect frequency", () => {
  assert.doesNotMatch(app, /const allVotesSmall/);
  assert.doesNotMatch(app, /aspectFreq/);
  assert.match(app, /const votes = Math\.max\(1, Number\(point\.votes \|\| 1\)\)/);
});
