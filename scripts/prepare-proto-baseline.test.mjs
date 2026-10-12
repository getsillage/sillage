import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { prepareProtoBaseline } from "./prepare-proto-baseline.mjs";

const header = 'syntax = "proto3";\npackage fixture.v1;\n';

function fixture(run) {
  const directory = mkdtempSync(join(tmpdir(), "sillage-proto-retirement-"));
  try {
    const baseline = join(directory, "baseline");
    const current = join(directory, "current");
    for (const root of [baseline, current]) {
      mkdirSync(join(root, "api/v1"), { recursive: true });
      writeFileSync(join(root, "buf.yaml"), "version: v2\nbreaking:\n  use: [FILE]\n");
      writeFileSync(join(root, "api/v1/retained.proto"), `${header}
message Record { string content = 1; string title = 2; }
service Records { rpc Get(Record) returns (Record); }
`);
    }
    writeFileSync(join(baseline, "api/v1/sync_service.proto"), `${header}
message RetiredRequest {}
service Retired { rpc Send(RetiredRequest) returns (RetiredRequest); }
`);
    for (const root of [baseline, current]) {
      const previous = root === baseline;
      writeFileSync(join(root, "api/v1/auth_service.proto"), `${header}
message BootstrapResponse {
  string server_version = 2;
  ${previous ? "int32 retired = 5;" : "reserved 5;"}
}
`);
      writeFileSync(join(root, "api/v1/memo_service.proto"), `${header}
message CreateMemoRequest {
  ${previous ? "string retired = 1;" : "reserved 1;"}
  string content = 2;
}
`);
    }
    const fingerprints = {
      service: createHash("sha256").update(readFileSync(join(baseline, "api/v1/sync_service.proto"))).digest("hex"),
      "BootstrapResponse.5": createHash("sha256").update("int32 retired = 5;").digest("hex"),
      "CreateMemoRequest.1": createHash("sha256").update("string retired = 1;").digest("hex"),
    };
    run({ baseline, current, prepare: () => prepareProtoBaseline(baseline, current, fingerprints) });
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

function breaking(baseline, current) {
  const result = spawnSync("buf", ["breaking", current, "--against", baseline], {
    encoding: "utf8",
  });
  assert.ifError(result.error);
  return result;
}

test("accepts exactly the retired service and reserved message slots", () => {
  fixture(({ baseline, current, prepare }) => {
    assert.notEqual(breaking(baseline, current).status, 0);
    assert.deepEqual(prepare(), [
      "BootstrapResponse.5", "CreateMemoRequest.1", "api/v1/sync_service.proto",
    ]);
    const result = breaking(baseline, current);
    assert.equal(result.status, 0, result.stderr + result.stdout);
    assert.deepEqual(prepare(), []);
  });
});

test("keeps rejecting deletion, type changes, and service removal in retained APIs", () => {
  for (const replacement of [
    "message Record { string content = 1; }\nservice Records { rpc Get(Record) returns (Record); }",
    "message Record { int32 content = 1; string title = 2; }\nservice Records { rpc Get(Record) returns (Record); }",
    "message Record { string content = 1; string title = 2; }",
  ]) {
    fixture(({ baseline, current, prepare }) => {
      prepare();
      writeFileSync(join(current, "api/v1/retained.proto"), header + replacement);
      const result = breaking(baseline, current);
      assert.notEqual(result.status, 0, "retained API changes must fail FILE compatibility");
      assert.match(result.stdout + result.stderr, /Previously present|changed type/);
    });
  }
});

test("requires reservations and leaves the baseline untouched when absent", () => {
  fixture(({ baseline, current, prepare }) => {
    const path = join(current, "api/v1/auth_service.proto");
    writeFileSync(path, readFileSync(path, "utf8").replace("reserved 5;", ""));
    assert.deepEqual(prepare(), []);
    assert.notEqual(breaking(baseline, current).status, 0);
  });
});

test("ignores reservation text in comments and strings while rejecting field reuse", () => {
  for (const replacement of [
    '// reserved 5;\n  string changed = 5;',
    '/* reserved 5; */\n  string changed = 5;',
    'string changed = 5 [json_name = "reserved 5;"];',
  ]) {
    fixture(({ baseline, current, prepare }) => {
      const path = join(current, "api/v1/auth_service.proto");
      writeFileSync(path, readFileSync(path, "utf8").replace("reserved 5;", replacement));
      assert.deepEqual(prepare(), []);
      assert.notEqual(breaking(baseline, current).status, 0);
    });
  }
});

test("refuses to retire a service or field different from the reviewed baseline", () => {
  for (const path of ["api/v1/sync_service.proto", "api/v1/auth_service.proto", "api/v1/memo_service.proto"]) {
    fixture(({ baseline, current, prepare }) => {
      const filename = join(baseline, path);
      writeFileSync(filename, readFileSync(filename, "utf8").replace(/retired|Retired/g, "Different"));
      assert.deepEqual(prepare(), []);
      assert.notEqual(breaking(baseline, current).status, 0);
    });
  }
});
