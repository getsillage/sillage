#!/usr/bin/env node

import { spawn } from "node:child_process";
import {
  closeSync,
  mkdtempSync,
  openSync,
  readFileSync,
  rmSync,
} from "node:fs";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const supportedProjects = ["chromium", "firefox", "webkit"];
const requestedProjects = (process.env.E2E_PROJECTS || supportedProjects.join(","))
  .split(",")
  .map((value) => value.trim())
  .filter(Boolean);

for (const project of requestedProjects) {
  if (!supportedProjects.includes(project)) {
    fail(`unsupported E2E project ${project}; choose ${supportedProjects.join(", ")}`);
  }
}
if (requestedProjects.length === 0) {
  fail("E2E_PROJECTS did not select any browser project");
}

const workspace = mkdtempSync(join(tmpdir(), "sillage-e2e-"));
const binary = join(workspace, "sillage");
const activeChildren = new Set();

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.once(signal, () => {
    for (const child of activeChildren) child.kill("SIGKILL");
    rmSync(workspace, { recursive: true, force: true });
    process.exit(signal === "SIGINT" ? 130 : 143);
  });
}

try {
  await run("go", ["build", "-o", binary, "./cmd/sillage"], {
    cwd: root,
    stdio: "inherit",
  });
  for (const project of requestedProjects) {
    await runProject(project);
  }
} finally {
  rmSync(workspace, { recursive: true, force: true });
}

console.log(`E2E passed in ${requestedProjects.join(", ")}.`);

async function runProject(project) {
  const dataDirectory = mkdtempSync(join(workspace, `${project}-data-`));
  const serverLogPath = join(workspace, `${project}-server.log`);
  const aiLogPath = join(workspace, `${project}-ai.log`);
  const [serverPort, aiPort] = await Promise.all([freePort(), freePort()]);
  const server = startLogged(binary, [], serverLogPath, {
    ...process.env,
    SILLAGE_ADDR: "127.0.0.1",
    SILLAGE_DATA: dataDirectory,
    SILLAGE_PORT: String(serverPort),
  });
  const ai = startLogged(
    process.execPath,
    [join(root, "scripts/e2e-mock-ai.mjs")],
    aiLogPath,
    {
      ...process.env,
      E2E_MOCK_AI_PORT: String(aiPort),
    },
  );

  try {
    await Promise.all([
      waitForService(
        `http://127.0.0.1:${serverPort}/readyz`,
        server,
        "Sillage",
        serverLogPath,
      ),
      waitForService(
        `http://127.0.0.1:${aiPort}/healthz`,
        ai,
        "mock AI",
        aiLogPath,
      ),
    ]);
    console.log(`Running fresh-instance release journeys in ${project}...`);
    await run(
      "pnpm",
      ["--dir", "web", "exec", "playwright", "test", `--project=${project}`],
      {
        cwd: root,
        env: {
          ...process.env,
          E2E_FRESH_INSTANCE: "1",
          E2E_MOCK_AI_BASE_URL: `http://127.0.0.1:${aiPort}`,
          PLAYWRIGHT_BASE_URL: `http://127.0.0.1:${serverPort}`,
        },
        stdio: "inherit",
      },
    );
  } catch (error) {
    printLog("Sillage", serverLogPath);
    printLog("mock AI", aiLogPath);
    throw error;
  } finally {
    await Promise.all([stopLogged(server), stopLogged(ai)]);
  }
}

function startLogged(command, args, logPath, env) {
  const fd = openSync(logPath, "w");
  const child = spawn(command, args, {
    cwd: root,
    env,
    stdio: ["ignore", fd, fd],
  });
  activeChildren.add(child);
  return { child, fd };
}

async function stopLogged(handle) {
  const { child, fd } = handle;
  if (child.exitCode === null && child.signalCode === null) {
    child.kill("SIGTERM");
    const stopped = await Promise.race([
      onceExit(child).then(() => true),
      delay(2_000).then(() => false),
    ]);
    if (!stopped) {
      child.kill("SIGKILL");
      await onceExit(child);
    }
  }
  activeChildren.delete(child);
  closeSync(fd);
}

async function waitForService(url, handle, label, logPath) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    if (handle.child.exitCode !== null || handle.child.signalCode !== null) {
      printLog(label, logPath);
      throw new Error(`${label} exited before becoming ready`);
    }
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1_000) });
      if (response.ok) return;
    } catch {
      // Service startup is allowed to race the first probes.
    }
    await delay(250);
  }
  printLog(label, logPath);
  throw new Error(`${label} did not become ready at ${url}`);
}

function run(command, args, options) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, options);
    activeChildren.add(child);
    child.once("error", (error) => {
      activeChildren.delete(child);
      reject(error);
    });
    child.once("exit", (code, signal) => {
      activeChildren.delete(child);
      if (code === 0) {
        resolve();
        return;
      }
      reject(
        new Error(
          `${command} ${args.join(" ")} failed with ${signal ? `signal ${signal}` : `exit ${code}`}`,
        ),
      );
    });
  });
}

function onceExit(child) {
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve();
  return new Promise((resolve) => child.once("exit", resolve));
}

function freePort() {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      if (!address || typeof address === "string") {
        server.close();
        reject(new Error("could not resolve an ephemeral TCP port"));
        return;
      }
      const { port } = address;
      server.close((error) => (error ? reject(error) : resolve(port)));
    });
  });
}

function printLog(label, path) {
  const body = readFileSync(path, "utf8").trim();
  console.error(`\n--- ${label} log ---`);
  console.error(body || "(empty)");
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function fail(message) {
  console.error(message);
  process.exit(2);
}
