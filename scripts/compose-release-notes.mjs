#!/usr/bin/env node

import { readFileSync, writeFileSync } from "node:fs";

const args = parseArgs(process.argv.slice(2));
for (const name of ["tag", "image", "digest", "output"]) {
  if (!args[name]) {
    console.error(`missing --${name}`);
    process.exit(2);
  }
}

const existing = args.existing ? readFileSync(args.existing, "utf8") : "";
const generated = args.generated ? readFileSync(args.generated, "utf8") : "See the commit history for this release.";
const rest = removeInstallBlock(existing).trim() || generated.trim();
const start = "<!-- sillage-install:start -->";
const end = "<!-- sillage-install:end -->";
const install = `${start}
## Install

The release image is pinned by digest for reproducible installs:

\`\`\`bash
docker run --rm \\
  -p 127.0.0.1:5231:5231 \\
  -v "\$HOME/.sillage:/var/opt/sillage" \\
  ${args.image}@${args.digest}
\`\`\`

This release is also tagged \`${args.image}:${args.tag}\` (digest \`${args.digest}\`).

Upgrade and rollback: [Data, Backup, and Recovery](https://github.com/getsillage/sillage/blob/${args.tag}/docs/user/data.md) · [Deployment](https://github.com/getsillage/sillage/blob/${args.tag}/docs/user/deployment.md)
${end}`;

writeFileSync(args.output, `${install}\n\n---\n\n${rest}\n`);

function parseArgs(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index];
    if (!value.startsWith("--")) continue;
    const name = value.slice(2);
    result[name] = values[index + 1] ?? "";
    index += 1;
  }
  return result;
}

function removeInstallBlock(body) {
  if (!body.trim()) return "";
  const markerPattern = /<!-- sillage-install:start -->[\s\S]*?<!-- sillage-install:end -->\s*/m;
  const marked = body.replace(markerPattern, "");
  if (marked !== body) return marked;

  // Remove the legacy block generated before the stable markers existed.
  return body.replace(/^## (?:Install|Container image)\n[\s\S]*?^---\s*\n?/m, "");
}
