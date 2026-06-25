import type { ReactNode } from "react";

export function TraceThread({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <ol className={`relative pl-6 ${className}`}>
      <span
        aria-hidden="true"
        className="absolute top-2 bottom-2 left-[5px] w-px bg-gray-200 dark:bg-gray-800"
      />
      {children}
    </ol>
  );
}

export function TraceThreadItem({
  children,
  memory = false,
  className = "",
}: {
  children: ReactNode;
  memory?: boolean;
  className?: string;
}) {
  return (
    <li className={`relative py-4 ${className}`}>
      <span
        aria-hidden="true"
        className={
          memory
            ? "absolute top-[1.4rem] -left-[3px] h-3 w-3 rounded-full bg-gray-50 ring-[1.5px] ring-clay-400 dark:bg-gray-950"
            : "absolute top-[1.4rem] -left-[1px] h-2.5 w-2.5 rounded-full bg-gray-50 ring-[1.5px] ring-celadon-500 dark:bg-gray-950"
        }
      />
      {children}
    </li>
  );
}
