import { useCallback, useEffect, useRef } from "react";
import { type BlockerFunction, useBlocker } from "react-router-dom";
import { useI18n } from "../i18n/I18nProvider";
import { dangerButtonClass, secondaryButtonClass } from "./ui";

const activeUnsavedGuards = new Set<symbol>();

export function hasUnsavedChanges(): boolean {
  return activeUnsavedGuards.size > 0;
}

export function useUnsavedChangesRegistration(when: boolean): void {
  const registrationRef = useRef(Symbol("unsaved-changes-registration"));

  useEffect(() => {
    const registration = registrationRef.current;
    if (when) {
      activeUnsavedGuards.add(registration);
    }
    return () => {
      activeUnsavedGuards.delete(registration);
    };
  }, [when]);
}

interface UnsavedNavigationGuardProps {
  when: boolean;
  title: string;
  description: string;
}

export function UnsavedNavigationGuard({
  when,
  title,
  description,
}: UnsavedNavigationGuardProps) {
  const { t } = useI18n();
  const shouldBlock = useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) =>
      when &&
      `${currentLocation.pathname}${currentLocation.search}${currentLocation.hash}` !==
        `${nextLocation.pathname}${nextLocation.search}${nextLocation.hash}`,
    [when],
  );
  const blocker = useBlocker(shouldBlock);
  const dialogRef = useRef<HTMLDivElement>(null);
  const stayButtonRef = useRef<HTMLButtonElement>(null);
  const previousFocusRef = useRef<HTMLElement | null>(null);
  const proceedingRef = useRef(false);
  useUnsavedChangesRegistration(when);

  useEffect(() => {
    if (blocker.state === "blocked" && !when) {
      blocker.proceed?.();
    }
  }, [blocker, when]);

  useEffect(() => {
    if (blocker.state !== "blocked") {
      return;
    }
    proceedingRef.current = false;
    previousFocusRef.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    stayButtonRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        blocker.reset?.();
        return;
      }
      if (event.key !== "Tab" || !dialogRef.current) {
        return;
      }
      const focusable = dialogRef.current.querySelectorAll<HTMLButtonElement>(
        "button:not([disabled])",
      );
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) {
        return;
      }
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      if (!proceedingRef.current) {
        previousFocusRef.current?.focus();
      }
    };
  }, [blocker]);

  if (blocker.state !== "blocked") {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[70] grid place-items-center px-4">
      <button
        type="button"
        aria-label={t("unsaved.stay")}
        className="absolute inset-0 h-full w-full bg-gray-950/35 dark:bg-gray-950/70"
        onClick={() => blocker.reset?.()}
      />
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="unsaved-navigation-title"
        aria-describedby="unsaved-navigation-description"
        className="surface-enter relative w-full max-w-sm rounded-xl border border-gray-200 bg-white p-5 shadow-xl shadow-gray-950/15 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/35"
      >
        <h2
          id="unsaved-navigation-title"
          className="font-semibold text-gray-900 text-lg dark:text-gray-50"
        >
          {title}
        </h2>
        <p
          id="unsaved-navigation-description"
          className="mt-2 text-gray-500 text-sm leading-6 dark:text-gray-400"
        >
          {description}
        </p>
        <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button
            ref={stayButtonRef}
            type="button"
            className={secondaryButtonClass}
            onClick={() => blocker.reset?.()}
          >
            {t("unsaved.keepEditing")}
          </button>
          <button
            type="button"
            className={dangerButtonClass}
            onClick={() => {
              proceedingRef.current = true;
              blocker.proceed?.();
            }}
          >
            {t("unsaved.leave")}
          </button>
        </div>
      </div>
    </div>
  );
}
