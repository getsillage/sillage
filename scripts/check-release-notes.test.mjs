import assert from "node:assert/strict";
import test from "node:test";

import { auditReleaseNotes } from "./check-release-notes.mjs";

const version = "0.3.0";
const completeNotes = `
## 主要更新

足够长的主要更新内容，用来说明本版本对用户可见的变化、核心产品行为、数据处理方式以及发布质量保障。

## 兼容性与升级

升级前停止实例并创建完整备份；如需回滚，恢复升级前的完整备份。服务端与 Web 通过同一容器镜像发布，并保持既有 REST API 的兼容性。

## 已知限制

自动化覆盖 Chromium、Firefox、WebKit，发布镜像支持 linux/amd64 与 linux/arm64；公网 HTTPS 入口由部署者管理。

## 验证

自动化门禁已经完成，升级与回滚演练也已记录，结果覆盖初始化、在线登录、记录、附件和生命周期。

https://github.com/getsillage/sillage/compare/v0.2.0...v0.3.0
`;

test("accepts complete publishable release notes", () => {
  assert.deepEqual(
    auditReleaseNotes({
      tag: "v0.3.0",
      notes: completeNotes,
      version,
      previousTag: "v0.2.0",
      release: true,
    }),
    [],
  );
});

test("allows explicit pending evidence only for a candidate", () => {
  const notes = completeNotes.replace("升级与回滚演练也已记录", "升级与回滚演练：待完成");
  assert.deepEqual(
    auditReleaseNotes({ tag: "v0.3.0", notes, version, previousTag: "v0.2.0" }),
    [],
  );
  assert.ok(
    auditReleaseNotes({
      tag: "v0.3.0",
      notes,
      version,
      previousTag: "v0.2.0",
      release: true,
    }).includes("release notes still contain pending acceptance evidence"),
  );
});

test("reports missing compatibility metadata and sections", () => {
  const failures = auditReleaseNotes({
    tag: "v0.3.0",
    notes: "## 主要更新\n\nshort",
    version,
    previousTag: "v0.2.0",
  });
  assert.ok(failures.some((failure) => failure.includes("missing section: 兼容性与升级")));
  assert.ok(failures.some((failure) => failure.includes("must mention Chromium")));
});

test("rejects notes for a version different from the Web manifest", () => {
  assert.ok(auditReleaseNotes({
    tag: "v0.3.0",
    notes: completeNotes,
    version: "0.4.0",
  }).some((failure) => failure.includes("does not match")));
});

test("requires each release browser and image architecture", () => {
  for (const phrase of ["Chromium", "Firefox", "WebKit", "linux/amd64", "linux/arm64"]) {
    assert.ok(auditReleaseNotes({
      tag: "v0.3.0",
      notes: completeNotes.replace(phrase, ""),
      version,
    }).includes(`release notes must mention ${phrase}`));
  }
});
