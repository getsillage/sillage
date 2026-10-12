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
});

test("Go dependency changes avoid unrelated client gates", () => {
  const gates = gatesFor("go.mod");
  assert.ok(gates.includes("go"));
  assert.ok(gates.includes("scale"));
  assert.ok(gates.includes("upgrade"));
  assert.ok(gates.includes("supply-chain"));
  assert.ok(!gates.includes("e2e"));
});


test("Proto contracts verify both the server and browser", () => {
  const gates = gatesFor("contracts/proto/api/v1/ask_service.proto");
  for (const gate of ["go", "web", "proto"]) {
    assert.ok(gates.includes(gate));
  }
});

test("the release version manifest verifies the Web build, notes, and upgrade drill", () => {
  const gates = gatesFor("apps/web/package.json");
  for (const gate of ["web", "docs", "upgrade", "e2e", "supply-chain"]) {
    assert.ok(gates.includes(gate));
  }
});

test("authentication changes include browser and server acceptance", () => {
  const gates = gatesFor("server/auth_routes.go");
  for (const gate of ["go", "proto", "web", "e2e"]) {
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
