import assert from "node:assert/strict";
import test from "node:test";

import { loadChangeMatrix, resolveGates } from "./lib/repo.mjs";

const matrix = loadChangeMatrix();

function gatesFor(...files) {
  return resolveGates(files, matrix).gates;
}

test("web changes include Web and release E2E gates", () => {
  const gates = gatesFor("apps/web/src/App.tsx");
  assert.ok(gates.includes("web"));
  assert.ok(gates.includes("e2e"));
  assert.ok(!gates.includes("android"));
});

test("Go dependency changes avoid unrelated client gates", () => {
  const gates = gatesFor("go.mod");
  assert.ok(gates.includes("go"));
  assert.ok(gates.includes("scale"));
  assert.ok(gates.includes("upgrade"));
  assert.ok(gates.includes("supply-chain"));
  assert.ok(!gates.includes("android"));
  assert.ok(!gates.includes("e2e"));
});

test("Android changes stay within the Android gate", () => {
  const gates = gatesFor("apps/native/androidApp/src/main/java/app/sillage/MainActivity.kt");
  assert.ok(gates.includes("android"));
  assert.ok(!gates.includes("e2e"));
  assert.ok(!gates.includes("go"));
});

test("shared Kotlin modules use Android as the current compilation gate", () => {
  const gates = gatesFor("packages/kmp-core/sync/src/commonMain/SyncEngine.kt");
  assert.ok(gates.includes("android"));
  assert.ok(gates.includes("docs"));
  assert.ok(!gates.includes("web"));
});

test("native build conventions use the Android gate", () => {
  const gates = gatesFor("apps/native/build-logic/src/main/kotlin/sillage.kmp-library.gradle.kts");
  assert.ok(gates.includes("android"));
  assert.ok(!gates.includes("web"));
});

test("reserved native hosts require architecture documentation", () => {
  assert.deepEqual(gatesFor("apps/native/iosApp/README.md"), ["docs"]);
});

test("extended wire contracts cover every current client", () => {
  const gates = gatesFor("contracts/events/ask-stream.yaml");
  for (const gate of ["go", "web", "android", "docs", "e2e"]) {
    assert.ok(gates.includes(gate));
  }
});

test("CI workflow changes force every release-relevant gate", () => {
  const gates = gatesFor(".github/workflows/ci.yml");
  for (const gate of Object.keys(matrix.gates)) {
    assert.ok(gates.includes(gate), `missing ${gate}`);
  }
});

test("documentation-only changes use the Docs gate", () => {
  assert.deepEqual(gatesFor("docs/user/deployment.md"), ["docs"]);
});
