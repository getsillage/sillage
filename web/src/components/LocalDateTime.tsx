import { pad2 } from "../lib/date";

interface LocalDateTimeProps {
  value: string | number | Date;
  /** Include hour:minute (default) or render the date only. */
  withTime?: boolean;
  className?: string;
}

function format(date: Date, withTime: boolean): string {
  const day = `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  return withTime
    ? `${day} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
    : day;
}

/** Renders a timestamp in the viewer's local time zone, to the minute. */
export function LocalDateTime({
  value,
  withTime = true,
  className,
}: LocalDateTimeProps) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return (
    <time dateTime={date.toISOString()} className={className}>
      {format(date, withTime)}
    </time>
  );
}
