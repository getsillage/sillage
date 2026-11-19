import {
  type ReactNode,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Link, useLocation } from "react-router";
import { secondaryButtonClass } from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { Memo } from "../../lib/api";
import {
  formatEntryDate,
  monthGrid,
  normalizeYearMonth,
  pad2,
} from "../../lib/date";
import { CalendarView } from "./CalendarView";
import { useMemos } from "./MemosContext";
import { excerpt, mergeMemos } from "./memos";

const messageClass = "text-gray-500 text-sm dark:text-gray-400";

function queryInteger(value: string | null, fallback: number): number {
  const number = Number(value);
  return value?.trim() && Number.isSafeInteger(number) ? number : fallback;
}

type MonthResult = {
  key: string;
  counts?: Record<string, number>;
};

export function TimelineCalendar({
  searchParams,
  today,
}: {
  searchParams: URLSearchParams;
  today: string;
}) {
  const { t } = useI18n();
  const { calendarCounts, calendarRevision } = useMemos();
  const dateParam = searchParams.get("date");
  const defaultDate =
    dateParam && /^\d{4}-\d{2}-\d{2}$/.test(dateParam) ? dateParam : today;
  const [defaultYear, defaultMonth] = defaultDate.split("-").map(Number);
  const requested = normalizeYearMonth(
    queryInteger(searchParams.get("y"), defaultYear),
    queryInteger(searchParams.get("m"), defaultMonth),
  );
  const { year, month } =
    requested.year >= 100 && requested.year <= 9999
      ? requested
      : { year: Number(today.slice(0, 4)), month: Number(today.slice(5, 7)) };
  const monthKey = `${String(year).padStart(4, "0")}-${pad2(month)}`;
  const weeks = useMemo(() => monthGrid(year, month), [year, month]);
  const selectedDate =
    dateParam && weeks.some((week) => week.includes(dateParam))
      ? dateParam
      : null;
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<MonthResult>();
  const requestKey = `${monthKey}:${calendarRevision}:${attempt}`;
  const current = result?.key === requestKey ? result : undefined;

  useEffect(() => {
    const request = new AbortController();
    void calendarCounts(monthKey, request.signal)
      .then((counts) => {
        if (!request.signal.aborted) {
          setResult({ key: requestKey, counts });
        }
      })
      .catch(() => {
        if (!request.signal.aborted) {
          setResult({ key: requestKey });
        }
      });
    return () => request.abort();
  }, [calendarCounts, monthKey, requestKey]);

  let monthFeedback: ReactNode;
  if (!current) {
    monthFeedback = <p role="status">{t("calendar.loadingMonth")}</p>;
  } else if (!current.counts) {
    monthFeedback = (
      <CalendarRetry
        message={t("calendar.monthLoadFailed")}
        label={t("calendar.retryMonth")}
        onRetry={() => setAttempt((value) => value + 1)}
      />
    );
  } else if (Object.keys(current.counts).length === 0) {
    monthFeedback = <p role="status">{t("calendar.noRecordsMonth")}</p>;
  }

  return (
    <CalendarView
      year={year}
      month={month}
      today={today}
      selectedDate={selectedDate}
      weeks={weeks}
      counts={current?.counts}
      monthFeedback={
        monthFeedback ? (
          <div className={`mt-3 ${messageClass}`}>{monthFeedback}</div>
        ) : null
      }
      dayPanel={
        selectedDate ? (
          <CalendarDay
            key={`${selectedDate}:${calendarRevision}`}
            date={selectedDate}
            today={today}
          />
        ) : (
          <p className={messageClass}>{t("calendar.selectDay")}</p>
        )
      }
    />
  );
}

function CalendarRetry({
  message,
  label,
  onRetry,
}: {
  message: string;
  label: string;
  onRetry: () => void;
}) {
  return (
    <div className="space-y-2">
      <p role="alert" className="text-red-700 dark:text-red-300">
        {message}
      </p>
      <button type="button" className={secondaryButtonClass} onClick={onRetry}>
        {label}
      </button>
    </div>
  );
}

type DayPage = { memos: Memo[]; nextCursor?: string };
type DayRequest = { status: "loading" | "failed"; cursor?: string };

function CalendarDay({ date, today }: { date: string; today: string }) {
  const { locale, t } = useI18n();
  const { calendarDay } = useMemos();
  const location = useLocation();
  const [page, setPage] = useState<DayPage>();
  const [feedback, setFeedback] = useState<DayRequest | null>({
    status: "loading",
  });
  const requestRef = useRef<AbortController | null>(null);

  const loadPage = useCallback(
    async (cursor?: string) => {
      if (requestRef.current) {
        return;
      }
      const request = new AbortController();
      requestRef.current = request;
      setFeedback({ status: "loading", cursor });
      try {
        const next = await calendarDay(date, cursor, request.signal);
        if (request.signal.aborted) {
          return;
        }
        setPage((current) =>
          cursor && current
            ? {
                ...next,
                memos: mergeMemos(current.memos, next.memos),
              }
            : next,
        );
        setFeedback(null);
      } catch {
        if (!request.signal.aborted) {
          setFeedback({ status: "failed", cursor });
        }
      } finally {
        if (requestRef.current === request) {
          requestRef.current = null;
        }
      }
    },
    [calendarDay, date],
  );

  useEffect(() => {
    void loadPage();
    return () => {
      requestRef.current?.abort();
      requestRef.current = null;
    };
  }, [loadPage]);

  return (
    <>
      <h3 className="mb-2 font-medium text-gray-700 text-sm dark:text-gray-300">
        {formatEntryDate(date, today, locale)}
      </h3>
      {page && page.memos.length > 0 ? (
        <ul className="space-y-2">
          {page.memos.map((memo) => (
            <li key={memo.id}>
              <Link
                to={`/entries/${memo.id}`}
                state={{
                  returnTo: `${location.pathname}${location.search}${location.hash}`,
                  memoSnapshot: memo,
                }}
                className="block rounded-lg px-3 py-3 text-gray-800 text-sm transition [overflow-wrap:anywhere] hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/50 dark:text-gray-100 dark:hover:bg-gray-800 dark:focus-visible:ring-gray-500/60"
              >
                {excerpt(memo.content, 40) || t("records.blank")}
              </Link>
            </li>
          ))}
        </ul>
      ) : null}

      {feedback?.status === "loading" ? (
        <p role="status" className={messageClass}>
          {t(feedback.cursor ? "timeline.loadingMore" : "calendar.loadingDay")}
        </p>
      ) : feedback?.status === "failed" ? (
        <CalendarRetry
          message={t(
            feedback.cursor
              ? "records.loadMoreFailed"
              : "calendar.dayLoadFailed",
          )}
          label={t(
            feedback.cursor ? "timeline.retryMore" : "calendar.retryDay",
          )}
          onRetry={() => void loadPage(feedback.cursor)}
        />
      ) : page?.memos.length === 0 ? (
        <p role="status" className={messageClass}>
          {t("calendar.noRecordsDay")}
        </p>
      ) : page?.nextCursor ? (
        <button
          type="button"
          className={`${secondaryButtonClass} mt-3`}
          onClick={() => void loadPage(page.nextCursor)}
        >
          {t("calendar.loadMoreDay")}
        </button>
      ) : null}
    </>
  );
}
