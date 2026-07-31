const exactDependencyPaths = new Set([
  "NOTICE",
  "THIRD_PARTY_NOTICES.md",
  "go.mod",
  "go.sum",
  "scripts/Dockerfile",
  "web/package.json",
  "web/pnpm-lock.yaml",
  "android/build.gradle.kts",
  "android/settings.gradle.kts",
  "android/gradle.properties",
  "android/app/build.gradle.kts",
  "android/app/gradle.lockfile",
  "android/gradle/verification-metadata.xml",
  "android/gradle/wrapper/gradle-wrapper.jar",
  "android/gradle/wrapper/gradle-wrapper.properties",
  "android/app/src/main/res/raw/third_party_notices.txt",
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
