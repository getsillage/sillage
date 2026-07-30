import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

export function runGit(args, options = {}) {
  return execFileSync("git", args, {
    cwd: root,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    ...options,
  }).trim();
}

export function readText(relativePath) {
  return readFileSync(resolve(root, relativePath), "utf8");
}

/** Minimal YAML subset loader for change-matrix.yml (no external deps). */
export function loadChangeMatrix() {
  const text = readText("scripts/change-matrix.yml");
  return parseSimpleYaml(text);
}

/**
 * Parse a constrained YAML subset used by change-matrix.yml.
 * Supports nested maps, string lists, and `#` comments. Not a general YAML parser.
 */
export function parseSimpleYaml(text) {
  const lines = text
    .split(/\r?\n/)
    .map((line, index) => ({ index: index + 1, raw: line }))
    .filter(({ raw }) => {
      const trimmed = raw.trim();
      return trimmed && !trimmed.startsWith("#");
    });

  const rootObj = {};
  const stack = [{ indent: -1, value: rootObj }];

  for (const { index, raw } of lines) {
    const indent = raw.match(/^ */)[0].length;
    const content = raw.trim();

    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) {
      stack.pop();
    }
    const parent = stack[stack.length - 1].value;

    if (content.startsWith("- ")) {
      if (!Array.isArray(parent)) {
        throw new Error(`scripts/change-matrix.yml:${index}: list item under non-list parent`);
      }
      const itemBody = content.slice(2).trim();
      if (!itemBody) {
        const obj = {};
        parent.push(obj);
        stack.push({ indent, value: obj });
        continue;
      }
      if (itemBody.includes(":")) {
        const obj = {};
        parent.push(obj);
        applyKeyValue(obj, itemBody, index);
        stack.push({ indent, value: obj });
      } else {
        parent.push(parseScalar(itemBody));
      }
      continue;
    }

    const colon = content.indexOf(":");
    if (colon === -1) {
      throw new Error(`scripts/change-matrix.yml:${index}: expected key:`);
    }
    const key = content.slice(0, colon).trim();
    const rest = content.slice(colon + 1).trim();

    if (!rest) {
      // Peek next non-empty to decide array vs object — default object; arrays use `-`.
      const next = lines.find((entry) => entry.index > index);
      const nextIndent = next ? next.raw.match(/^ */)[0].length : indent;
      const nextTrim = next ? next.raw.trim() : "";
      const child = next && nextIndent > indent && nextTrim.startsWith("- ") ? [] : {};
      if (Array.isArray(parent)) {
        throw new Error(`scripts/change-matrix.yml:${index}: map key under list without item`);
      }
      parent[key] = child;
      stack.push({ indent, value: child });
      continue;
    }

    if (rest.startsWith("[") && rest.endsWith("]")) {
      parent[key] = rest
        .slice(1, -1)
        .split(",")
        .map((part) => part.trim())
        .filter(Boolean)
        .map(parseScalar);
      continue;
    }

    parent[key] = parseScalar(rest);
  }

  return rootObj;
}

function applyKeyValue(obj, body, index) {
  const colon = body.indexOf(":");
  if (colon === -1) {
    throw new Error(`scripts/change-matrix.yml:${index}: expected key: value in list item`);
  }
  const key = body.slice(0, colon).trim();
  const rest = body.slice(colon + 1).trim();
  if (!rest) {
    obj[key] = {};
    return;
  }
  if (rest.startsWith("[") && rest.endsWith("]")) {
    obj[key] = rest
      .slice(1, -1)
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean)
      .map(parseScalar);
    return;
  }
  obj[key] = parseScalar(rest);
}

function parseScalar(value) {
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  if (value === "true") return true;
  if (value === "false") return false;
  if (/^-?\d+$/.test(value)) return Number(value);
  return value;
}

export function changedFiles(baseRef) {
  if (!baseRef) {
    const out = runGit(["diff", "--name-only", "--diff-filter=ACMRD", "HEAD"]);
    const staged = runGit(["diff", "--name-only", "--diff-filter=ACMRD", "--cached"]);
    const untracked = runGit(["ls-files", "--others", "--exclude-standard"]);
    return uniqueLines([out, staged, untracked].join("\n"));
  }

  try {
    runGit(["rev-parse", "--verify", `${baseRef}^{commit}`]);
  } catch {
    return uniqueLines(runGit(["diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD"]));
  }

  return uniqueLines(runGit(["diff", "--name-only", "--diff-filter=ACMRD", `${baseRef}...HEAD`]));
}

function uniqueLines(text) {
  return [...new Set(text.split("\n").map((line) => line.trim()).filter(Boolean))].sort();
}

export function matchGlob(path, pattern) {
  // Convert a limited glob (** and *) to RegExp.
  let i = 0;
  let re = "^";
  while (i < pattern.length) {
    const ch = pattern[i];
    if (ch === "*" && pattern[i + 1] === "*") {
      if (pattern[i + 2] === "/") {
        re += "(?:.*/)?";
        i += 3;
      } else {
        re += ".*";
        i += 2;
      }
      continue;
    }
    if (ch === "*") {
      re += "[^/]*";
      i += 1;
      continue;
    }
    if ("+?^${}()|[]\\.".includes(ch)) {
      re += `\\${ch}`;
    } else {
      re += ch;
    }
    i += 1;
  }
  re += "$";
  return new RegExp(re).test(path);
}

export function resolveGates(files, matrix = loadChangeMatrix()) {
  const gates = new Set();
  const matchedRules = [];

  for (const rule of matrix.rules || []) {
    const hit = files.some((file) => (rule.paths || []).some((pattern) => matchGlob(file, pattern)));
    if (!hit) continue;
    matchedRules.push(rule.id || "(anonymous)");
    for (const gate of rule.gates || []) {
      gates.add(gate);
    }
  }

  if (gates.size === 0) {
    for (const gate of matrix.default_gates || ["go", "docs"]) {
      gates.add(gate);
    }
  }

  return { gates: [...gates].sort(), matchedRules };
}
