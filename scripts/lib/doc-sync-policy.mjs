const exactDependencyPaths = new Set([
  "NOTICE",
  "THIRD_PARTY_NOTICES.md",
  "go.mod",
  "go.sum",
  "scripts/Dockerfile",
  "apps/web/package.json",
  "apps/web/pnpm-lock.yaml",
  "apps/native/build.gradle.kts",
  "apps/native/settings.gradle.kts",
  "apps/native/gradle.properties",
  "apps/native/androidApp/build.gradle.kts",
  "apps/native/androidApp/gradle.lockfile",
  "apps/native/gradle/verification-metadata.xml",
  "apps/native/gradle/wrapper/gradle-wrapper.jar",
  "apps/native/gradle/wrapper/gradle-wrapper.properties",
  "apps/native/androidApp/src/main/res/raw/third_party_notices.txt",
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
