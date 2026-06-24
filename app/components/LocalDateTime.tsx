import { useEffect, useState } from "react";

interface LocalDateTimeProps {
  value: string | number | Date;
  /** Include hour:minute (default) or render the date only. */
  withTime?: boolean;
  className?: string;
}

function pad2(value: number): string {
  return String(value).padStart(2, "0");
}

function formatUtc(date: Date, withTime: boolean): string {
  const day = `${date.getUTCFullYear()}-${pad2(date.getUTCMonth() + 1)}-${pad2(date.getUTCDate())}`;
  return withTime ? `${day} ${pad2(date.getUTCHours())}:${pad2(date.getUTCMinutes())}` : day;
}

function formatLocal(date: Date, withTime: boolean): string {
  const day = `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  return withTime ? `${day} ${pad2(date.getHours())}:${pad2(date.getMinutes())}` : day;
}

/**
 * Renders a timestamp in the viewer's local time zone, to the minute. The first
 * (server) render is deterministic UTC so hydration matches; after mount it
 * re-formats in the browser's zone. Worker SSR has no client zone, so this is
 * the simplest way to show correct local times for every entry, old or new.
 */
export function LocalDateTime({ value, withTime = true, className }: LocalDateTimeProps) {
  const date = value instanceof Date ? value : new Date(value);
  const iso = Number.isNaN(date.getTime()) ? "" : date.toISOString();
  const [text, setText] = useState(() => (iso ? formatUtc(date, withTime) : ""));

  useEffect(() => {
    if (iso) {
      setText(formatLocal(new Date(iso), withTime));
    }
  }, [iso, withTime]);

  if (!iso) {
    return null;
  }
  return (
    <time dateTime={iso} className={className}>
      {text}
    </time>
  );
}
