import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const root = fileURLToPath(new URL("..", import.meta.url));
const gitignorePath = resolve(root, ".gitignore");
const dockerignorePath = resolve(root, ".dockerignore");

function readRules(path) {
  return readFileSync(path, "utf8")
    .split(/\r?\n/)
    .map((line, index) => ({ line: index + 1, value: line.trim() }))
    .filter(({ value }) => value && !value.startsWith("#"));
}

const requiredDockerRules = [
  ".git",
  ".env",
  ".env.*",
  "**/.env",
  "**/.env.*",
  "/.claude/",
  "/.thumbnail_cache/",
  "/assets/attachments/",
  "/runtime/",
  "/.sillage/",
  "/.data-dev/",
  "/sillage",
  "web/node_modules/",
  "**/node_modules/",
  "*.db",
  "*.db-shm",
  "*.db-wal",
  "*.db.bak-*",
  "**/*.db",
  "**/*.db-shm",
  "**/*.db-wal",
  "**/*.db.bak-*",
  "android/local.properties",
  "android/signing.properties",
  "*.keystore",
  "*.jks",
  "**/*.keystore",
  "**/*.jks",
];

const gitRules = readRules(gitignorePath)
  .map(({ value }) => value)
  .filter((value) => !value.startsWith("!"));
const dockerRules = readRules(dockerignorePath);
const dockerRuleSet = new Set(dockerRules.map(({ value }) => value));
const missingRules = [...new Set([...gitRules, ...requiredDockerRules])].filter(
  (rule) => !dockerRuleSet.has(rule),
);
const negatedRules = dockerRules.filter(({ value }) => value.startsWith("!"));

if (missingRules.length > 0 || negatedRules.length > 0) {
  if (missingRules.length > 0) {
    console.error(
      `Docker context can include ignored or sensitive paths; add these rules to .dockerignore:\n${missingRules
        .map((rule) => `- ${rule}`)
        .join("\n")}`,
    );
  }

  if (negatedRules.length > 0) {
    console.error(
      `Docker context policy does not allow exception rules:\n${negatedRules
        .map(({ line, value }) => `- .dockerignore:${line}: ${value}`)
        .join("\n")}`,
    );
  }

  process.exit(1);
}

console.log(
  `Docker context policy covers ${gitRules.length} Git ignore rules and ${requiredDockerRules.length} required sensitive rules.`,
);
