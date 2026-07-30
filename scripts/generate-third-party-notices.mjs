#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import {
  copyFileSync,
  cpSync,
  existsSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const policyPath = path.join(root, "scripts", "third-party-license-policy.json");
const noticePath = path.join(root, "THIRD_PARTY_NOTICES.md");
const licenseRoot = path.join(root, "third_party", "licenses");
const args = new Set(process.argv.slice(2));

if (
  args.size !== 1 ||
  (!args.has("--check") && !args.has("--write"))
) {
  console.error(
    "Usage: node scripts/generate-third-party-notices.mjs --check|--write",
  );
  process.exit(2);
}

const policy = JSON.parse(readFileSync(policyPath, "utf8"));
const stagingRoot = mkdtempSync(path.join(tmpdir(), "sillage-licenses-"));
const stagingLicenses = path.join(stagingRoot, "third_party", "licenses");
const stagingNotice = path.join(stagingRoot, "THIRD_PARTY_NOTICES.md");

function command(commandName, commandArgs, cwd = root) {
  return execFileSync(commandName, commandArgs, {
    cwd,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    stdio: ["ignore", "pipe", "inherit"],
  });
}

function markdown(value) {
  return String(value ?? "")
    .replaceAll("\\", "\\\\")
    .replaceAll("|", "\\|")
    .replaceAll("\n", " ");
}

function licenseFiles(directory) {
  const matcher = /(^|[-_.])(licen[cs]e|copying|notice|copyright)([-_.]|$)/i;
  return readdirSync(directory, { withFileTypes: true })
    .filter((entry) => entry.isFile() && matcher.test(entry.name))
    .map((entry) => entry.name)
    .sort((left, right) => left.localeCompare(right));
}

function copyLicenses(sourceDirectory, destinationDirectory, files) {
  mkdirSync(destinationDirectory, { recursive: true });
  for (const file of files) {
    copyFileSync(
      path.join(sourceDirectory, file),
      path.join(destinationDirectory, file),
    );
  }
}

function linkList(base, files) {
  return files
    .map((file) => `[${markdown(file)}](${encodeURI(path.posix.join(base, file))})`)
    .join(", ");
}

function goDependencies() {
  const output = command("go", [
    "list",
    "-deps",
    "-f",
    "{{with .Module}}{{if not .Main}}{{.Path}}\t{{.Version}}\t{{.Dir}}{{end}}{{end}}",
    "./cmd/sillage",
  ]);
  const modules = new Map();
  for (const line of output.split("\n")) {
    if (!line.trim()) continue;
    const [modulePath, version, directory] = line.split("\t");
    if (!modulePath || !version || !directory) {
      throw new Error(`Could not parse Go dependency line: ${line}`);
    }
    const existing = modules.get(modulePath);
    if (
      existing &&
      (existing.version !== version || existing.directory !== directory)
    ) {
      throw new Error(`Go dependency ${modulePath} resolved inconsistently`);
    }
    modules.set(modulePath, { modulePath, version, directory });
  }

  const expected = Object.keys(policy.goModules).sort();
  const actual = [...modules.keys()].sort();
  if (JSON.stringify(expected) !== JSON.stringify(actual)) {
    const missingPolicy = actual.filter((name) => !policy.goModules[name]);
    const stalePolicy = expected.filter((name) => !modules.has(name));
    throw new Error(
      [
        "Go runtime dependency policy is out of date.",
        missingPolicy.length > 0
          ? `Missing policy: ${missingPolicy.join(", ")}`
          : "",
        stalePolicy.length > 0
          ? `Stale policy: ${stalePolicy.join(", ")}`
          : "",
      ]
        .filter(Boolean)
        .join("\n"),
    );
  }

  return actual.map((modulePath) => ({
    ...modules.get(modulePath),
    license: policy.goModules[modulePath],
  }));
}

function webDependencies() {
  const output = command("pnpm", [
    "--dir",
    "web",
    "licenses",
    "list",
    "--prod",
    "--json",
  ]);
  const groups = JSON.parse(output);
  const dependencies = new Map();
  const allowed = new Set(policy.allowedWebLicenses);

  for (const [groupLicense, packages] of Object.entries(groups)) {
    for (const packageInfo of packages) {
      const declaredLicense = packageInfo.license || groupLicense;
      if (!allowed.has(declaredLicense)) {
        throw new Error(
          `Web dependency ${packageInfo.name} uses unapproved license ${declaredLicense}`,
        );
      }
      for (const directory of packageInfo.paths) {
        const packageJson = JSON.parse(
          readFileSync(path.join(directory, "package.json"), "utf8"),
        );
        const key = `${packageJson.name}@${packageJson.version}`;
        dependencies.set(key, {
          name: packageJson.name,
          version: packageJson.version,
          directory,
          license: declaredLicense,
          homepage:
            packageInfo.homepage ||
            packageJson.homepage ||
            packageJson.repository?.url ||
            "",
        });
      }
    }
  }
  return [...dependencies.values()].sort((left, right) =>
    `${left.name}@${left.version}`.localeCompare(`${right.name}@${right.version}`),
  );
}

function generate() {
  const goRows = [];
  for (const dependency of goDependencies()) {
    const files = licenseFiles(dependency.directory);
    if (files.length === 0) {
      throw new Error(
        `Go dependency ${dependency.modulePath}@${dependency.version} has no root license or notice file`,
      );
    }
    const relativeDirectory = path.posix.join(
      "go",
      `${dependency.modulePath}@${dependency.version}`,
    );
    copyLicenses(
      dependency.directory,
      path.join(stagingLicenses, relativeDirectory),
      files,
    );
    goRows.push(
      `| ${markdown(dependency.modulePath)} | ${markdown(dependency.version)} | ${markdown(dependency.license)} | ${linkList(path.posix.join("third_party/licenses", relativeDirectory), files)} |`,
    );
  }

  const webRows = [];
  for (const dependency of webDependencies()) {
    const files = licenseFiles(dependency.directory);
    if (files.length === 0) {
      throw new Error(
        `Web dependency ${dependency.name}@${dependency.version} has no root license or notice file`,
      );
    }
    const relativeDirectory = path.posix.join(
      "web",
      `${dependency.name}@${dependency.version}`,
    );
    copyLicenses(
      dependency.directory,
      path.join(stagingLicenses, relativeDirectory),
      files,
    );
    const name = dependency.homepage
      ? `[${markdown(dependency.name)}](${dependency.homepage})`
      : markdown(dependency.name);
    webRows.push(
      `| ${name} | ${markdown(dependency.version)} | ${markdown(dependency.license)} | ${linkList(path.posix.join("third_party/licenses", relativeDirectory), files)} |`,
    );
  }

  const notice = `# Third-party notices

Sillage is licensed under the [MIT License](LICENSE). The official server binary and container image also include the Go and Web runtime dependencies listed below. Each dependency remains subject to its own license; the exact license and notice files shipped by that dependency are preserved under \`third_party/licenses/\` and copied into the official container image at \`/usr/share/licenses/sillage/\`.

This inventory is generated from the resolved Go build graph and the production pnpm dependency graph. Regenerate it with \`node scripts/generate-third-party-notices.mjs --write\`; CI rejects dependency or notice drift.

## Go runtime dependencies

| Module | Version | Declared license | Preserved files |
| --- | --- | --- | --- |
${goRows.join("\n")}

## Web runtime dependencies

| Package | Version | Declared license | Preserved files |
| --- | --- | --- | --- |
${webRows.join("\n")}
`;
  writeFileSync(stagingNotice, notice);
}

function listFiles(directory, prefix = "") {
  if (!existsSync(directory)) return [];
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const relative = path.join(prefix, entry.name);
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...listFiles(absolute, relative));
    } else if (entry.isFile()) {
      files.push(relative);
    } else {
      throw new Error(`Unexpected generated license entry: ${absolute}`);
    }
  }
  return files.sort((left, right) => left.localeCompare(right));
}

function compareFile(expected, actual) {
  return (
    existsSync(actual) &&
    lstatSync(actual).isFile() &&
    readFileSync(expected).equals(readFileSync(actual))
  );
}

function checkGenerated() {
  const expectedFiles = listFiles(stagingLicenses);
  const actualFiles = listFiles(licenseRoot);
  const noticeMatches = compareFile(stagingNotice, noticePath);
  if (
    noticeMatches &&
    JSON.stringify(expectedFiles) === JSON.stringify(actualFiles) &&
    expectedFiles.every((file) =>
      compareFile(path.join(stagingLicenses, file), path.join(licenseRoot, file)),
    )
  ) {
    console.log(
      `Third-party notices are current (${expectedFiles.length} preserved files).`,
    );
    return;
  }
  throw new Error(
    "Third-party notices are stale. Run: node scripts/generate-third-party-notices.mjs --write",
  );
}

function writeGenerated() {
  rmSync(licenseRoot, { force: true, recursive: true });
  mkdirSync(path.dirname(licenseRoot), { recursive: true });
  cpSync(stagingLicenses, licenseRoot, { recursive: true });
  copyFileSync(stagingNotice, noticePath);
  console.log(
    `Wrote third-party notices (${listFiles(licenseRoot).length} preserved files).`,
  );
}

try {
  generate();
  if (args.has("--check")) {
    checkGenerated();
  } else {
    writeGenerated();
  }
} catch (error) {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
} finally {
  rmSync(stagingRoot, { force: true, recursive: true });
}
