import { useFetcher } from "react-router";
import type { BackupFormat, BackupItem } from "~/lib/backup/list";

interface ExportResult {
  intent?: string;
  ok: boolean;
  message: string;
}

const FORMAT_LABEL: Record<BackupFormat, string> = {
  json: "JSON",
  markdown: "Markdown",
  other: "文件",
};

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

const buttonClass =
  "rounded-lg border border-gray-400 bg-white px-3 py-2 font-medium text-gray-900 text-sm hover:bg-gray-100 disabled:opacity-60 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800";
const statusClass = (ok: boolean) =>
  `mt-3 rounded-lg border px-3 py-2 text-sm ${
    ok
      ? "border-green-300 bg-green-50 text-green-800 dark:border-green-900/70 dark:bg-green-950/50 dark:text-green-200"
      : "border-red-300 bg-red-50 text-red-800 dark:border-red-900/70 dark:bg-red-950/50 dark:text-red-200"
  }`;

/**
 * 数据与备份 section: lists daily R2 backups with per-format download links, plus a
 * manual "立即备份一次" trigger. Posts to the settings action (intent=export); the
 * fetcher submission revalidates the loader so a fresh backup appears in the list.
 */
export function BackupSection({ backups }: { backups: BackupItem[] }) {
  const fetcher = useFetcher<ExportResult>();
  const exporting = fetcher.state !== "idle";

  return (
    <section className="rounded-lg border border-gray-200/80 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900/90">
      <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">数据与备份</h2>
      <p className="mt-1 text-gray-700 text-sm dark:text-gray-400">
        每天会自动备份一份到服务端；可在这里下载，或立即手动导出一份。
      </p>

      <fetcher.Form method="post" className="mt-3">
        <input type="hidden" name="intent" value="export" />
        <button type="submit" disabled={exporting} className={buttonClass}>
          {exporting ? "导出中…" : "立即备份一次"}
        </button>
      </fetcher.Form>

      {fetcher.data?.intent === "export" ? (
        <p className={statusClass(fetcher.data.ok)}>{fetcher.data.message}</p>
      ) : null}

      {backups.length === 0 ? (
        <p className="mt-4 text-gray-500 text-sm dark:text-gray-500">
          还没有备份。点上面「立即备份一次」生成第一份。
        </p>
      ) : (
        <ul className="mt-4 space-y-2">
          {backups.map((item) => (
            <li
              key={item.base}
              className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-gray-200 bg-gray-50/70 px-4 py-3 dark:border-gray-800 dark:bg-gray-950"
            >
              <div>
                <span className="font-medium text-gray-950 text-sm dark:text-gray-50">
                  {item.date}
                </span>
                <span className="ml-2 text-gray-400 text-xs dark:text-gray-500">
                  {new Date(item.uploaded).toLocaleString("zh-CN")}
                </span>
              </div>
              <div className="flex flex-wrap gap-3">
                {item.files.map((file) => (
                  <a
                    key={file.key}
                    href={`/download-backup?key=${encodeURIComponent(file.key)}`}
                    download
                    className="text-gray-600 text-sm hover:text-gray-950 dark:text-gray-300 dark:hover:text-gray-50"
                  >
                    {FORMAT_LABEL[file.format]} · {formatSize(file.size)}
                  </a>
                ))}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
