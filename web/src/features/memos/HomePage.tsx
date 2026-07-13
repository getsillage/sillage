import { useMemo } from "react";
import { Link } from "react-router-dom";
import {
  emptyStateClass,
  ghostLinkClass,
  readingShellClass,
  secondaryButtonClass,
  skeletonClass,
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { Memo } from "../../lib/api";
import { formatEntryDate, todayISO } from "../../lib/date";
import { EntryCard } from "./EntryCard";
import { EntryComposer } from "./EntryComposer";
import { useMemos } from "./MemosContext";
import { isActive, onThisDay } from "./memos";
import { OnThisDay } from "./OnThisDay";

function EntrySection({
  title,
  entries,
  empty,
  showAllLink = false,
  loading = false,
}: {
  title: string;
  entries: Memo[];
  empty: string;
  showAllLink?: boolean;
  loading?: boolean;
}) {
  const { t } = useI18n();
  return (
    <section className="min-w-0 pr-16 sm:pr-0">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="font-medium text-gray-700 text-sm dark:text-gray-300">
          {title}
        </h2>
        {showAllLink ? (
          <Link
            to="/timeline"
            className={`${ghostLinkClass} -my-3 inline-flex h-10 items-center px-2 text-xs`}
          >
            {t("records.viewAll")}
          </Link>
        ) : null}
      </div>
      {loading ? (
        <div
          className="space-y-4 rounded-lg border border-gray-200/70 bg-white/50 p-4 dark:border-gray-800 dark:bg-gray-900/40"
          role="status"
        >
          <span className="sr-only">
            {t("records.loadingSection", { section: title })}
          </span>
          <div className={`${skeletonClass} h-3 w-24`} />
          <div className={`${skeletonClass} h-5 w-4/5`} />
        </div>
      ) : entries.length === 0 ? (
        <div className={emptyStateClass}>{empty}</div>
      ) : (
        <ul className="divide-y divide-gray-200/70 rounded-lg border border-gray-200/80 bg-white/70 p-1 shadow-sm shadow-gray-900/[0.02] dark:divide-gray-800 dark:border-gray-800 dark:bg-gray-900/45">
          {entries.map((memo) => (
            <li key={memo.id}>
              <EntryCard memo={memo} openOnCardClick />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function HomePage() {
  const { locale, t } = useI18n();
  const { memos, loading, error, refresh, create, upload } = useMemos();
  const today = todayISO();
  const { todayEntries, recentEntries, memories } = useMemo(() => {
    const chronological = memos.filter(isActive).sort((a, b) => {
      if (a.entryDate !== b.entryDate) {
        return b.entryDate.localeCompare(a.entryDate);
      }
      return b.createdAt.localeCompare(a.createdAt);
    });
    return {
      todayEntries: chronological.filter((memo) => memo.entryDate === today),
      recentEntries: chronological
        .filter((memo) => memo.entryDate !== today)
        .slice(0, 12),
      memories: onThisDay(memos, today),
    };
  }, [memos, today]);

  return (
    <main className={`${readingShellClass} pb-32`}>
      <div className="space-y-7">
        <header>
          <p className="text-gray-500 text-xs dark:text-gray-400">
            {formatEntryDate(today, today, locale)}
          </p>
          <h1 className="mt-1 font-semibold text-2xl text-gray-900 sm:text-[1.75rem] dark:text-gray-50">
            {t("records.todayQuestion")}
          </h1>
        </header>

        <section aria-label={t("records.new")}>
          <EntryComposer
            draftKey="memo:new"
            submitLabel={t("common.save")}
            onSubmit={async (input) => {
              await create(input);
            }}
            onUpload={upload}
          />
        </section>

        <OnThisDay entries={memories} today={today} />

        {error ? (
          <div className="flex flex-wrap items-center gap-3 rounded-lg border border-red-200 bg-red-50/70 p-3 text-red-700 text-sm dark:border-red-900/60 dark:bg-red-950/25 dark:text-red-300">
            <p role="alert" className="min-w-0 flex-1">
              {error}
            </p>
            <button
              type="button"
              className={secondaryButtonClass}
              disabled={loading}
              onClick={() => void refresh()}
            >
              {loading ? t("records.reloading") : t("records.reload")}
            </button>
          </div>
        ) : null}

        {loading || memos.length > 0 || !error ? (
          <>
            <EntrySection
              title={t("records.today")}
              entries={todayEntries}
              empty={t("records.emptyToday")}
              loading={loading}
            />
            <EntrySection
              title={t("records.recent")}
              entries={recentEntries}
              empty={t("records.emptyRecent")}
              showAllLink
              loading={loading}
            />
          </>
        ) : null}
      </div>
    </main>
  );
}
