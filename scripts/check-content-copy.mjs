#!/usr/bin/env node
/**
 * Stable red-line checks for user-facing data-location and AI copy.
 * These checks intentionally cover durable disclosures and prohibited terms,
 * not complete sentences or editorial preferences.
 */
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { root } from "./lib/repo.mjs";

export const contentPaths = {
  web: "web/src/i18n/messages.ts",
  androidEn: "android/app/src/main/res/values/strings.xml",
  androidZh: "android/app/src/main/res/values-zh-rCN/strings.xml",
};

const forbiddenPatterns = [
  {
    targets: ["web", "androidEn"],
    pattern: /\bAI reflection\b/i,
    message: "describe AI as source-grounded, not as reflection",
  },
  {
    targets: ["web", "androidZh"],
    pattern: /AI\s*反思/,
    message: "不要使用含义模糊的“AI 反思”",
  },
  {
    targets: ["web", "androidEn"],
    pattern: /\b(?:my|your) state changed\b/i,
    message: "suggestions must ask about observable record content, not infer personal state",
  },
  {
    targets: ["web", "androidZh"],
    pattern: /(?:我的|你的)状态有什么变化/,
    message: "示例问题应询问可观察的记录内容，不应推断个人状态",
  },
  {
    targets: ["web", "androidEn"],
    pattern: /\blocal server\b/i,
    message: "use Sillage server because the instance may run remotely",
  },
  {
    targets: ["web", "androidZh"],
    pattern: /本地服务端/,
    message: "使用“Sillage 服务端”，实例可能运行在远程机器上",
  },
  {
    targets: ["androidZh"],
    pattern: /云端/,
    message: "不要把自托管服务端或用户配置的 AI 端点统称为“云端”",
  },
  {
    targets: ["web"],
    pattern: /Preparing the answer|正在整理问答|正在准备回答/,
    message: "Ask progress must name the current operation",
  },
];

const requiredFragments = {
  web: [
    "Send this record to your configured AI endpoint",
    "将这条记录发送到你配置的 AI 端点",
    "default AI profile",
    "默认 AI 档案",
    "Checking whether records are needed",
    "正在判断是否需要查找记录",
    "Finding relevant records",
    "正在查找相关记录",
    "Personal claims cite only relevant records",
    "涉及个人事实时，仅引用",
  ],
  androidEn: [
    "Send this record to your configured AI endpoint",
    "default AI profile",
    "For personal facts, relevant records",
  ],
  androidZh: [
    "将这条记录发送到你配置的 AI 端点",
    "默认 AI 档案",
    "涉及个人事实时，只引用",
  ],
};

export function auditContentCopy(sources) {
  const failures = [];

  for (const key of Object.keys(contentPaths)) {
    if (typeof sources[key] !== "string") {
      failures.push(`${contentPaths[key]}: missing content source`);
    }
  }

  for (const { targets, pattern, message } of forbiddenPatterns) {
    for (const target of targets) {
      const source = sources[target];
      if (typeof source !== "string") continue;
      const match = source.match(pattern);
      if (match) {
        failures.push(`${contentPaths[target]}: ${message}: ${match[0]}`);
      }
    }
  }

  for (const [target, fragments] of Object.entries(requiredFragments)) {
    const source = sources[target];
    if (typeof source !== "string") continue;
    for (const fragment of fragments) {
      if (!source.includes(fragment)) {
        failures.push(
          `${contentPaths[target]}: missing required content disclosure: ${fragment}`,
        );
      }
    }
  }

  return failures;
}

function readSources() {
  const sources = {};
  for (const [key, rel] of Object.entries(contentPaths)) {
    const path = resolve(root, rel);
    if (existsSync(path)) sources[key] = readFileSync(path, "utf8");
  }
  return sources;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const failures = auditContentCopy(readSources());
  if (failures.length > 0) {
    console.error(failures.join("\n"));
    process.exit(1);
  }
  console.log("Content copy checks passed.");
}
