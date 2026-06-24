export type BackupFormat = "json" | "markdown" | "other";

export interface BackupFile {
  key: string;
  format: BackupFormat;
  size: number;
}

export interface BackupItem {
  /** Stable grouping key (the shared base path of a json/markdown pair). */
  base: string;
  date: string;
  uploaded: string;
  files: BackupFile[];
}

function formatFromKey(key: string): BackupFormat {
  if (key.endsWith(".json")) {
    return "json";
  }
  if (key.endsWith(".md")) {
    return "markdown";
  }
  return "other";
}

/**
 * Lists daily backups from R2, grouping each export's `.json` + `.md` pair into a
 * single item (newest first). Backups are written as plaintext by
 * `exportSillageBackup`, so callers can stream them straight to download.
 */
export async function listBackups(env: Env, limit = 60): Promise<BackupItem[]> {
  const listed = await env.BLOBS.list({ prefix: "backups/", limit: 1000 });
  const groups = new Map<string, { date: string; uploaded: number; files: BackupFile[] }>();

  for (const object of listed.objects) {
    const base = object.key.replace(/\.(json|md)$/, "");
    const date = object.key.split("/")[1] ?? "";
    const group = groups.get(base) ?? { date, uploaded: 0, files: [] };
    group.files.push({ key: object.key, format: formatFromKey(object.key), size: object.size });
    group.uploaded = Math.max(group.uploaded, object.uploaded.getTime());
    groups.set(base, group);
  }

  return [...groups.entries()]
    .map(([base, group]) => ({
      base,
      date: group.date,
      uploaded: new Date(group.uploaded).toISOString(),
      files: group.files.sort((a, b) => a.format.localeCompare(b.format)),
    }))
    .sort((a, b) => b.uploaded.localeCompare(a.uploaded))
    .slice(0, limit);
}
