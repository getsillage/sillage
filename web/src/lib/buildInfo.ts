export const webBuildInfo = {
  version: import.meta.env.VITE_SILLAGE_VERSION?.trim() || "dev",
  revision: import.meta.env.VITE_SILLAGE_REVISION?.trim() || "unknown",
} as const;

export function shortRevision(revision: string | undefined): string {
  const value = revision?.trim() || "unknown";
  return value === "unknown" ? value : value.slice(0, 12);
}

export function webServerBuildsMatch(
  serverVersion: string | undefined,
  serverRevision: string | undefined,
): boolean {
  const server = serverRevision?.trim();
  if (
    !server ||
    server === "unknown" ||
    webBuildInfo.revision === "unknown" ||
    serverVersion === "dev" ||
    webBuildInfo.version === "dev"
  ) {
    return true;
  }
  return server === webBuildInfo.revision;
}
