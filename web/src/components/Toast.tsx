import { CircleAlert, CircleCheck, X } from "lucide-react";
import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { useI18n } from "../i18n/I18nProvider";

export type ToastMessage = {
  kind: "success" | "error";
  message: string;
};

const TOAST_DURATION_MS = 3_200;

export function Toast({
  toast,
  onClose,
}: {
  toast: ToastMessage;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!toast.message) {
      return;
    }
    const timeout = window.setTimeout(
      () => onCloseRef.current(),
      TOAST_DURATION_MS,
    );
    return () => window.clearTimeout(timeout);
  }, [toast]);

  const Icon = toast.kind === "success" ? CircleCheck : CircleAlert;
  return createPortal(
    <div
      role={toast.kind === "success" ? "status" : "alert"}
      className="pointer-events-none fixed top-[max(1rem,calc(env(safe-area-inset-top)+0.75rem))] right-4 left-4 z-[90] flex justify-center"
    >
      <div className="surface-enter pointer-events-auto flex w-full max-w-sm items-center gap-3 rounded-lg border border-gray-200 bg-white/95 py-2 pr-2 pl-3 shadow-xl shadow-gray-950/15 backdrop-blur-xl dark:border-gray-700 dark:bg-gray-900/95 dark:shadow-black/35">
        <Icon
          className={`h-5 w-5 flex-none ${
            toast.kind === "error"
              ? "text-red-600 dark:text-red-400"
              : "text-gray-600 dark:text-gray-300"
          }`}
          aria-hidden="true"
        />
        <p
          className={`min-w-0 flex-1 text-sm ${
            toast.kind === "error"
              ? "text-red-600 dark:text-red-400"
              : "text-gray-800 dark:text-gray-100"
          }`}
        >
          {toast.message}
        </p>
        <button
          type="button"
          onClick={onClose}
          className="flex h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
          aria-label={t("toast.close")}
          title={t("toast.close")}
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
    </div>,
    document.body,
  );
}
