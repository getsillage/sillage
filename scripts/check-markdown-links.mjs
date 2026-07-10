import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const files = execFileSync(
  "git",
  ["ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", "*.md"],
  { encoding: "utf8" },
)
  .split("\0")
  .filter(Boolean);

const failures = [];
const linkPattern = /!?\[[^\]]*\]\(([^)]+)\)/g;
const htmlTargetPattern = /\b(?:href|src)=["']([^"']+)["']/gi;

for (const file of files) {
  if (!existsSync(file)) {
    continue;
  }

  const markdown = readFileSync(file, "utf8");
  const targets = [
    ...Array.from(markdown.matchAll(linkPattern), (match) => match[1]),
    ...Array.from(markdown.matchAll(htmlTargetPattern), (match) => match[1]),
  ];

  for (const rawTarget of targets) {
    let target = rawTarget.trim();
    if (target.startsWith("<") && target.endsWith(">")) {
      target = target.slice(1, -1);
    } else {
      target = target.split(/\s+/, 1)[0];
    }

    if (
      !target ||
      target.startsWith("#") ||
      target.startsWith("//") ||
      /^[a-z][a-z0-9+.-]*:/i.test(target)
    ) {
      continue;
    }

    const path = target.split("#", 1)[0].split("?", 1)[0];
    let decodedPath;
    try {
      decodedPath = decodeURIComponent(path);
    } catch {
      failures.push(`${file}: invalid URL encoding in ${target}`);
      continue;
    }

    if (decodedPath && !existsSync(resolve(dirname(file), decodedPath))) {
      failures.push(`${file}: missing ${target}`);
    }
  }
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log(`Checked ${files.length} Markdown files.`);
