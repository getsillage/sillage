const exactDependencyPaths = new Set([
  "NOTICE",
  "THIRD_PARTY_NOTICES.md",
  "go.mod",
  "go.sum",
  "scripts/Dockerfile",
  "apps/web/package.json",
  "apps/web/pnpm-lock.yaml",
]);

const dependencyPathPrefixes = [".github/workflows/", "third_party/licenses/"];

export function isDependabotMaintenance(files, enabled) {
  if (!["1", "true", "yes"].includes(String(enabled || "").toLowerCase())) return false;
  if (files.length === 0) return false;
  return files.every(
    (file) =>
      exactDependencyPaths.has(file) ||
      dependencyPathPrefixes.some((prefix) => file.startsWith(prefix)),
  );
}
