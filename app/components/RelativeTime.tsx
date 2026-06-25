import { useEffect, useState } from "react";
import { relativeTime, toISODate } from "~/lib/date";

interface RelativeTimeProps {
  value: Date;
  className?: string;
}

/**
 * Renders a gentle "刚刚 / 12 分钟前 / 昨天" phrase. Like `LocalDateTime`, the first
 * (server) render is the deterministic UTC date so hydration matches; after mount it
 * switches to the relative phrase and refreshes each minute so "刚刚" doesn't go stale.
 */
export function RelativeTime({ value, className }: RelativeTimeProps) {
  const iso = Number.isNaN(value.getTime()) ? "" : value.toISOString();
  const [text, setText] = useState(() => (iso ? toISODate(value) : ""));

  useEffect(() => {
    if (!iso) {
      return;
    }
    const update = () => setText(relativeTime(new Date(iso)));
    update();
    const timer = setInterval(update, 60_000);
    return () => clearInterval(timer);
  }, [iso]);

  if (!iso) {
    return null;
  }
  return (
    <time dateTime={iso} className={className}>
      {text}
    </time>
  );
}
