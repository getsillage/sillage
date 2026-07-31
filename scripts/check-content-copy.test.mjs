import assert from "node:assert/strict";
import test from "node:test";
import { auditContentCopy } from "./check-content-copy.mjs";

function compliantSources() {
  return {
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
    ].join("\n"),
    androidEn: [
      "Send this record to your configured AI endpoint",
      "default AI profile",
      "For personal facts, relevant records",
    ].join("\n"),
    androidZh: [
      "将这条记录发送到你配置的 AI 端点",
      "默认 AI 档案",
      "涉及个人事实时，只引用",
    ].join("\n"),
  };
}

test("accepts precise, source-grounded copy with AI data disclosure", () => {
  assert.deepEqual(auditContentCopy(compliantSources()), []);
});

test("rejects cloud terminology for a self-hosted Android server", () => {
  const sources = compliantSources();
  sources.androidZh += "\n同步到云端";
  assert.match(auditContentCopy(sources).join("\n"), /不要把自托管服务端/);
});

test("rejects vague personal-state suggestions", () => {
  const sources = compliantSources();
  sources.web += "\nHow has my state changed recently?";
  assert.match(auditContentCopy(sources).join("\n"), /observable record content/);
});

test("requires point-of-action AI data disclosure", () => {
  const sources = compliantSources();
  sources.androidEn = sources.androidEn.replace(
    "Send this record to your configured AI endpoint",
    "Create a summary",
  );
  assert.match(
    auditContentCopy(sources).join("\n"),
    /missing required content disclosure/,
  );
});
