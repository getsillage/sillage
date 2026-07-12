import { useI18n } from "../../i18n/I18nProvider";
import type { Locale } from "../../i18n/messages";

interface LocalDateTimeProps {
  value: string | number | Date;
  className?: string;
  variant?: "full" | "short" | "time";
}

export function formatLocalDateTime(
  date: Date,
  variant: NonNullable<LocalDateTimeProps["variant"]>,
  locale: Locale,
): string {
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const dateOptions: Intl.DateTimeFormatOptions =
    variant === "time"
      ? {}
      : variant === "short"
        ? { month: "short", day: "numeric" }
        : { year: "numeric", month: "short", day: "numeric" };
  return new Intl.DateTimeFormat(locale, {
    ...dateOptions,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).format(date);
}

/** Renders a timestamp in the viewer's local time zone, to the minute. */
export function LocalDateTime({
  value,
  className,
  variant = "full",
}: LocalDateTimeProps) {
  const { locale } = useI18n();
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return (
    <time dateTime={date.toISOString()} className={className}>
      {formatLocalDateTime(date, variant, locale)}
    </time>
  );
}
