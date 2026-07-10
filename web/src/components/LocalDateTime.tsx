import { pad2 } from "../lib/date";

interface LocalDateTimeProps {
  value: string | number | Date;
  className?: string;
  variant?: "full" | "short" | "time";
}

function format(
  date: Date,
  variant: NonNullable<LocalDateTimeProps["variant"]>,
): string {
  const time = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  if (variant === "time") {
    return time;
  }
  if (variant === "short") {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${time}`;
  }
  const day = `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  return `${day} ${time}`;
}

/** Renders a timestamp in the viewer's local time zone, to the minute. */
export function LocalDateTime({
  value,
  className,
  variant = "full",
}: LocalDateTimeProps) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return (
    <time dateTime={date.toISOString()} className={className}>
      {format(date, variant)}
    </time>
  );
}
