import assert from "node:assert/strict";
import test from "node:test";

import { loadChangeMatrix, resolveGates } from "./lib/repo.mjs";

const matrix = loadChangeMatrix();

function gatesFor(...files) {
  return resolveGates(files, matrix).gates;
}

test("web changes include Web and release E2E gates", () => {
  const gates = gatesFor("web/src/App.tsx");
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
  const gates = gatesFor("android/app/src/main/java/app/sillage/MainActivity.kt");
  assert.ok(gates.includes("android"));
  assert.ok(!gates.includes("e2e"));
  assert.ok(!gates.includes("go"));
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
