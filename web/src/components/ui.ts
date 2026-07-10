export const readingShellClass =
  "mx-auto w-full max-w-3xl px-4 py-7 sm:px-6 sm:py-9";

export const wideShellClass =
  "mx-auto w-full max-w-6xl px-4 py-7 sm:px-6 sm:py-9";

export const pageSectionClass = "space-y-6 sm:space-y-8";

export const pageTitleClass =
  "text-2xl font-semibold text-gray-900 sm:text-[1.75rem] dark:text-gray-50";

export const pageLeadClass = "mt-1 text-sm text-gray-500 dark:text-gray-400";

/** Secondary / explanatory text (timestamps, captions, meta). */
export const mutedTextClass = "text-gray-500 dark:text-gray-400";

/** Inline ghost link/button: no chrome, hover darkens, visible focus ring. */
export const ghostLinkClass =
  "rounded-md text-gray-600 transition hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-300 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40";

/** Quiet empty-state panel: subtle surface, centered muted message. */
export const emptyStateClass =
  "rounded-lg border border-dashed border-gray-200 bg-gray-100/45 px-4 py-10 text-center text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900/45 dark:text-gray-400";

export const panelClass =
  "rounded-lg border border-gray-200/80 bg-white/80 shadow-sm shadow-gray-900/[0.03] dark:border-gray-800 dark:bg-gray-900/70 dark:shadow-black/10";

export const subtlePanelClass = "rounded-lg bg-gray-100/55 dark:bg-gray-900/55";

export const rowLinkClass =
  "block rounded-lg px-3 py-3 transition-colors hover:bg-gray-100/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 dark:hover:bg-gray-800/70 dark:focus-visible:ring-gray-500/50";

export const inputClass =
  "mt-1 block h-10 w-full rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-900 transition placeholder:text-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-300/55 disabled:bg-gray-100 disabled:text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-50 dark:placeholder:text-gray-500 dark:focus:border-gray-500 dark:focus:ring-gray-600/50 dark:disabled:bg-gray-800 dark:disabled:text-gray-500";

export const selectClass = inputClass;

export const textareaClass =
  "block w-full resize-y rounded-lg border border-gray-200 bg-white px-3 py-3 text-sm text-gray-900 transition placeholder:text-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-300/55 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-50 dark:placeholder:text-gray-500 dark:focus:border-gray-500 dark:focus:ring-gray-600/50";

export const labelClass =
  "block text-sm font-medium text-gray-700 dark:text-gray-300";

export const helperTextClass = "mt-1 text-xs text-gray-500 dark:text-gray-400";

export const primaryButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-gray-900 px-4 text-sm font-medium text-white transition hover:bg-gray-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/45 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-gray-100 dark:text-gray-900 dark:hover:bg-white dark:focus-visible:ring-gray-500/50";

export const secondaryButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-4 text-sm font-medium text-gray-800 transition hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800 dark:focus-visible:ring-gray-500/40";

export const subtleButtonClass =
  "inline-flex h-10 items-center justify-center gap-2 rounded-lg px-3 text-sm font-medium text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40";

/** Familiar icon-only action with a stable 40px target. */
export const iconButtonClass =
  "inline-flex h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40";

export const segmentedControlClass =
  "inline-flex min-h-10 items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-100/70 p-0.5 dark:border-gray-800 dark:bg-gray-950";

export function segmentedItemClass(active: boolean): string {
  const base =
    "inline-flex h-10 items-center justify-center gap-1.5 rounded-md px-3 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40";
  return active
    ? `${base} bg-white font-medium text-gray-900 shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800 dark:text-gray-50`
    : `${base} text-gray-500 hover:bg-white/70 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-100`;
}

export const skeletonClass =
  "animate-pulse rounded-md bg-gray-200/70 dark:bg-gray-800/80";

export const dangerButtonClass =
  "inline-flex h-9 items-center justify-center gap-2 rounded-lg px-3 text-sm font-medium text-red-600 transition hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500/30 disabled:cursor-not-allowed disabled:opacity-50 dark:text-red-400 dark:hover:bg-red-950/30";

/** Inline destructive ghost action (no chrome), with a visible focus ring. */
export const dangerLinkClass =
  "rounded text-red-600 transition hover:text-red-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500/30 disabled:cursor-not-allowed disabled:opacity-50 dark:text-red-400 dark:hover:text-red-300";
