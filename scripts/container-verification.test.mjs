import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { delimiter, join } from "node:path";
import test from "node:test";

const root = new URL("..", import.meta.url);

function fixture(t) {
  const dir = mkdtempSync(join(tmpdir(), "sillage-container-test-"));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const callsPath = join(dir, "calls.jsonl");
  writeFileSync(callsPath, "");
  writeFileSync(join(dir, "docker"), `#!/usr/bin/env node
const { appendFileSync } = require("node:fs");
const args = process.argv.slice(2);
const [command] = args;
appendFileSync(process.env.FAKE_DOCKER_CALLS, JSON.stringify(args) + "\\n");
if (command === "run" && args.at(-1) === "spdx-json") {
  console.log(JSON.stringify({ spdxVersion: "SPDX-2.3", documentNamespace: "test", packages: [{}] }));
} else if (command === "run" && args.at(-1) === "cyclonedx-json") {
  console.log(JSON.stringify({ bomFormat: "CycloneDX", specVersion: "1.6", components: [{}] }));
} else if (command === "create") {
  console.log(args.includes("import") ? "importer" : "scanner");
} else if (command === "start") {
  if (args.at(-1) === process.env.FAKE_DOCKER_FAIL) process.exit(42);
  if (args.at(-1) === "scanner") console.log(JSON.stringify({ matches: [], descriptor: {} }));
}
`, { mode: 0o755 });
  const archive = join(dir, "grype database.tar.zst");
  const contents = "test database archive";
  writeFileSync(archive, contents);
  const checksum = createHash("sha256").update(contents).digest("hex");
  const dbEnv = {
    SILLAGE_GRYPE_DB_ARCHIVE: archive,
    SILLAGE_GRYPE_DB_SHA256: `sha256:${checksum}`,
  };

  function run(command, args, env) {
    writeFileSync(callsPath, "");
    const result = spawnSync(command, args, {
      cwd: root,
      encoding: "utf8",
      timeout: 10000,
      env: {
        ...process.env,
        PATH: `${dir}${delimiter}${process.env.PATH}`,
        FAKE_DOCKER_CALLS: callsPath,
        FAKE_DOCKER_FAIL: "",
        GOPROXY: "file:///host-only-cache",
        SILLAGE_BUILD_GOPROXY: "",
        SILLAGE_GRYPE_DB_ARCHIVE: "",
        SILLAGE_GRYPE_DB_SHA256: "",
        GRYPE_FAIL_ON: "",
        ...env,
      },
    });
    const calls = readFileSync(callsPath, "utf8").trim();
    return { ...result, calls: calls ? calls.split("\n").map(JSON.parse) : [] };
  }

  return {
    archive,
    dbEnv,
    build: (env) => run("make", ["check-container"], env),
    scan: (env) => run("bash", ["scripts/scan-container.sh", "docker:sillage:test", dir, "sillage"], env),
  };
}

test("Docker builds leave the proxy default alone unless explicitly overridden", (t) => {
  const { build } = fixture(t);
  const normal = build();
  assert.equal(normal.status, 0, normal.stderr);
  assert.ok(!normal.calls[0].some((argument) => argument.startsWith("GOPROXY=")));

  const proxy = "https://proxy.golang.org|https://mirror.example.org";
  const override = build({ SILLAGE_BUILD_GOPROXY: proxy });
  assert.equal(override.status, 0, override.stderr);
  const proxyIndex = override.calls[0].indexOf(`GOPROXY=${proxy}`);
  assert.ok(proxyIndex > 0);
  assert.equal(override.calls[0][proxyIndex - 1], "--build-arg");
});

test("the default scan uses the network database and high severity threshold", (t) => {
  const { scan } = fixture(t);
  const outcome = scan();
  assert.equal(outcome.status, 0, outcome.stderr);
  const creates = outcome.calls.filter(([command]) => command === "create");
  assert.equal(creates.length, 1);
  assert.ok(!creates[0].includes("GRYPE_DB_AUTO_UPDATE=false"));
  assert.equal(creates[0][creates[0].indexOf("--fail-on") + 1], "high");
});

test("missing or mismatched archive checksum fails before calling Docker", (t) => {
  const { dbEnv, scan } = fixture(t);
  for (const checksum of ["", "0".repeat(64)]) {
    const outcome = scan({ ...dbEnv, SILLAGE_GRYPE_DB_SHA256: checksum });
    assert.notEqual(outcome.status, 0);
    assert.deepEqual(outcome.calls, []);
    assert.match(outcome.stderr, /SHA-256/);
  }
});

test("imports the verified database before scanning and cleans up both containers", (t) => {
  const { archive, dbEnv, scan } = fixture(t);
  const outcome = scan(dbEnv);
  assert.equal(outcome.status, 0, outcome.stderr);
  const importer = outcome.calls.findIndex(([command, ...args]) => command === "start" && args.includes("importer"));
  const scanner = outcome.calls.findIndex(([command, ...args]) => command === "create" && args.includes("--fail-on"));
  assert.ok(importer >= 0 && importer < scanner);
  assert.ok(outcome.calls.some(([command, source]) => command === "cp" && source === archive));
  const scanArgs = outcome.calls[scanner];
  assert.equal(scanArgs[scanArgs.indexOf("--volumes-from") + 1], "importer");
  assert.ok(scanArgs.includes("GRYPE_DB_AUTO_UPDATE=false"));
  assert.ok(scanArgs.includes("GRYPE_DB_VALIDATE_BY_HASH_ON_START=true"));
  assert.ok(scanArgs.includes("GRYPE_DB_VALIDATE_AGE=true"));
  assert.equal(scanArgs[scanArgs.indexOf("--fail-on") + 1], "high");
  assert.deepEqual(outcome.calls.filter(([command]) => command === "rm").map((args) => args.at(-1)), ["scanner", "importer"]);
});

test("import or scanner failures fail the gate and still clean up", (t) => {
  const { dbEnv, scan } = fixture(t);
  for (const failure of ["importer", "scanner"]) {
    const outcome = scan({ ...dbEnv, FAKE_DOCKER_FAIL: failure });
    assert.equal(outcome.status, 42);
    assert.doesNotMatch(outcome.stdout, /SBOM verified/);
    const removed = outcome.calls.filter(([command]) => command === "rm").map((args) => args.at(-1));
    assert.deepEqual(removed, failure === "importer" ? ["importer"] : ["scanner", "importer"]);
  }
});
