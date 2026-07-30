#!/usr/bin/env node
/**
 * Conventional Commits validator for a message file, stdin, or git range.
 *
 *   node scripts/check-commit-msg.mjs --message "feat(web): add export"
 *   node scripts/check-commit-msg.mjs --file .git/COMMIT_EDITMSG
 *   BASE_SHA=<ref> node scripts/check-commit-msg.mjs --range
 */
import { readFileSync } from "node:fs";
import { runGit } from "./lib/repo.mjs";

// type(scope)?: subject — scope optional; breaking ! optional
const CONVENTIONAL =
  /^(feat|fix|docs|refactor|test|chore|ci|style|perf|build|revert)(\([a-z0-9][a-z0-9/_-]*\))?(!)?: [^\s].{0,120}$/;

const MERGE = /^Merge /;
const REVERT = /^Revert /;
const DEPENDABOT = /^chore\(deps\): /;

const args = process.argv.slice(2);

function validateSubject(line, label) {
  const subject = line.trim();
  if (!subject) {
    return `${label}: empty commit subject`;
  }
  if (MERGE.test(subject) || REVERT.test(subject) || DEPENDABOT.test(subject)) {
    return null;
  }
  if (!CONVENTIONAL.test(subject)) {
    return `${label}: subject must match Conventional Commits (type(scope)?: description)\n  got: ${subject}`;
  }
  if (/[。.]$/.test(subject)) {
    return `${label}: subject should not end with a period\n  got: ${subject}`;
  }
  return null;
}

const failures = [];

if (args.includes("--range")) {
  const base =
    process.env.BASE_SHA ||
    process.env.BASE_REF ||
    (args.includes("--base") ? args[args.indexOf("--base") + 1] : "");
  if (!base) {
    console.log("check-commit-msg: no BASE_SHA for range; skipping.");
    process.exit(0);
  }
  let log;
  try {
    log = runGit(["log", "--format=%H%x09%s", `${base}..HEAD`]);
  } catch (error) {
    console.error(String(error.stderr || error.message || error));
    process.exit(1);
  }
  if (!log) {
    console.log("check-commit-msg: no commits in range.");
    process.exit(0);
  }
  for (const row of log.split("\n")) {
    const tab = row.indexOf("\t");
    const sha = tab === -1 ? row.slice(0, 7) : row.slice(0, 7);
    const subject = tab === -1 ? row : row.slice(tab + 1);
    const err = validateSubject(subject, sha);
    if (err) failures.push(err);
  }
} else if (args.includes("--message")) {
  const message = args[args.indexOf("--message") + 1] || "";
  const subject = message.split(/\r?\n/, 1)[0];
  const err = validateSubject(subject, "message");
  if (err) failures.push(err);
} else if (args.includes("--file")) {
  const file = args[args.indexOf("--file") + 1];
  const subject = readFileSync(file, "utf8").split(/\r?\n/, 1)[0];
  const err = validateSubject(subject, file);
  if (err) failures.push(err);
} else if (!process.stdin.isTTY) {
  const subject = readFileSync(0, "utf8").split(/\r?\n/, 1)[0];
  const err = validateSubject(subject, "stdin");
  if (err) failures.push(err);
} else {
  console.error(
    "Usage: check-commit-msg.mjs --message <text> | --file <path> | --range | <stdin>",
  );
  process.exit(2);
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log("Commit message checks passed.");
