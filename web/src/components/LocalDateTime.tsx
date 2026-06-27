import { pad2 } from "../lib/date";

interface LocalDateTimeProps {
  value: string | number | Date;
  className?: string;
}

function format(date: Date): string {
  const day = `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
  return `${day} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

/** Renders a timestamp in the viewer's local time zone, to the minute. */
export function LocalDateTime({ value, className }: LocalDateTimeProps) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return (
    <time dateTime={date.toISOString()} className={className}>
      {format(date)}
    </time>
  );
}
