import assert from "node:assert/strict";
import test from "node:test";

import { auditReleaseNotes } from "./check-release-notes.mjs";

const build = `
minSdk = 26
targetSdk = 35
versionCode = 10
versionName = "0.3.0"
`;
const completeNotes = `
## 主要更新

足够长的主要更新内容，用来说明本版本对用户可见的变化、核心产品行为、数据处理方式以及发布质量保障。

## 兼容性与升级

升级前停止实例并创建完整备份；如需回滚，恢复升级前的完整备份。Android versionName \`0.3.0\`、versionCode \`10\`、minSdk \`26\`、targetSdk \`35\`。

## 已知限制

自动同步仍未提供，API 26 与 API 35 是自动化边界，其他系统版本不在发布矩阵内。

## 验证

自动化门禁已经完成，物理设备验收也已记录，结果覆盖安装、同步、附件和生命周期。

https://github.com/getsillage/sillage/compare/v0.2.0...v0.3.0
`;

test("accepts complete publishable release notes", () => {
  assert.deepEqual(
    auditReleaseNotes({
      tag: "v0.3.0",
      notes: completeNotes,
      build,
      previousTag: "v0.2.0",
      release: true,
    }),
    [],
  );
});

test("allows explicit pending evidence only for a candidate", () => {
  const notes = completeNotes.replace("物理设备验收也已记录", "物理设备：待完成");
  assert.deepEqual(
    auditReleaseNotes({ tag: "v0.3.0", notes, build, previousTag: "v0.2.0" }),
    [],
  );
  assert.ok(
    auditReleaseNotes({
      tag: "v0.3.0",
      notes,
      build,
      previousTag: "v0.2.0",
      release: true,
    }).includes("release notes still contain pending acceptance evidence"),
  );
});

test("reports missing compatibility metadata and sections", () => {
  const failures = auditReleaseNotes({
    tag: "v0.3.0",
    notes: "## 主要更新\n\nshort",
    build,
    previousTag: "v0.2.0",
  });
  assert.ok(failures.some((failure) => failure.includes("missing section: 兼容性与升级")));
  assert.ok(failures.some((failure) => failure.includes("versionCode 10")));
});
