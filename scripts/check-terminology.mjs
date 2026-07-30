#!/usr/bin/env node
/**
 * Lightweight product-terminology guards for user-facing catalogs.
 * Code identifiers may use memo; Simplified Chinese UI must not expose it.
 */
import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { root } from "./lib/repo.mjs";

const failures = [];

function read(rel) {
  return readFileSync(resolve(root, rel), "utf8");
}

// --- Web i18n: Chinese message values must not expose backend term "memo" ---
const webMessagesPath = "web/src/i18n/messages.ts";
if (existsSync(resolve(root, webMessagesPath))) {
  const source = read(webMessagesPath);
  // Extract the zh catalog block roughly between zh: { ... } at top level of export.
  const zhMatch = source.match(/\bzh\s*:\s*\{([\s\S]*?)\n\s*\},\s*\n\s*en\s*:|\bzh\s*:\s*\{([\s\S]*?)\n\s*\}\s*\n\}/);
  const zhBody = zhMatch ? zhMatch[1] || zhMatch[2] || "" : "";
  if (!zhBody) {
    // Fallback: scan string literals after a zh locale marker is unreliable; scan whole file
    // only for zh-looking lines is hard. Instead scan all template/string values that look Chinese-heavy.
    const stringLiterals = [...source.matchAll(/(['"`])((?:\\.|(?!\1)[\s\S])*?)\1/g)].map((m) => m[2]);
    for (const value of stringLiterals) {
      if (!/[\u4e00-\u9fff]/.test(value)) continue;
      if (/\bmemo\b/i.test(value)) {
        failures.push(`${webMessagesPath}: Chinese-facing string contains "memo": ${truncate(value)}`);
      }
    }
  } else {
    const stringLiterals = [...zhBody.matchAll(/(['"`])((?:\\.|(?!\1)[\s\S])*?)\1/g)].map((m) => m[2]);
    for (const value of stringLiterals) {
      if (/\bmemo\b/i.test(value)) {
        failures.push(`${webMessagesPath}: zh catalog value contains "memo": ${truncate(value)}`);
      }
      // Product guidance: do not expose English "Ask" as the product surface name in zh UI.
      if (/(^|[^A-Za-z])Ask([^A-Za-z]|$)/.test(value) && !/Ask\s*Page|ask\s*tree/i.test(value)) {
        failures.push(
          `${webMessagesPath}: zh catalog should use 问答, not English "Ask": ${truncate(value)}`,
        );
      }
    }
  }
}

// --- Android zh strings ---
const androidZh = "android/app/src/main/res/values-zh/strings.xml";
const androidZhAlt = "android/app/src/main/res/values-zh-rCN/strings.xml";
for (const rel of [androidZh, androidZhAlt]) {
  if (!existsSync(resolve(root, rel))) continue;
  const xml = read(rel);
  const values = [...xml.matchAll(/<string\b[^>]*>([\s\S]*?)<\/string>/g)].map((m) =>
    m[1].replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1"),
  );
  for (const value of values) {
    if (/\bmemo\b/i.test(value)) {
      failures.push(`${rel}: string contains "memo": ${truncate(value)}`);
    }
  }
}

// --- Forbidden product-scope claims in root READMEs (keep scope honest) ---
const scopeFiles = ["README.md", "README.zh-CN.md"];
const forbiddenClaims = [
  { re: /\bmulti-user collaboration\b/i, hint: "multi-user collaboration is out of scope" },
  { re: /\bpublic profiles?\b/i, hint: "public profiles are out of scope" },
  { re: /官方托管/, hint: "official hosting is out of scope" },
];
for (const rel of scopeFiles) {
  if (!existsSync(resolve(root, rel))) continue;
  const text = read(rel);
  // Only flag if the README claims the product *provides* these, not when it says it does not.
  // Heuristic: lines that assert presence without nearby negation.
  for (const line of text.split(/\r?\n/)) {
    const lower = line.toLowerCase();
    if (/不提供|不属于|not provide|outside|do not|does not|no multi-user|no public|no official/i.test(line)) {
      continue;
    }
    for (const { re, hint } of forbiddenClaims) {
      if (re.test(line)) {
        failures.push(`${rel}: possible out-of-scope product claim (${hint}): ${truncate(line)}`);
      }
    }
    void lower;
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("Terminology checks passed.");

function truncate(value) {
  const oneLine = value.replace(/\s+/g, " ").trim();
  return oneLine.length > 120 ? `${oneLine.slice(0, 117)}...` : oneLine;
}
