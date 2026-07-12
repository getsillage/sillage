import { CircleAlert, CircleCheck, Info, X } from "lucide-react";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";

export type ToastMessage = {
  kind: "success" | "error" | "info";
  message: string;
};

const TOAST_DURATION_MS = 3_200;
const ERROR_TOAST_DURATION_MS = 6_000;
const MAX_TOASTS = 3;
const DEFAULT_CLOSE_LABEL = "关闭通知";

type QueuedToast = ToastMessage & { id: string };

type ToastContextValue = {
  available: boolean;
  showToast: (toast: ToastMessage) => string;
  dismissToast: (id: string) => void;
};

const ToastContext = createContext<ToastContextValue>({
  available: false,
  showToast: () => "",
  dismissToast: () => undefined,
});
const ToastCloseLabelContext = createContext(DEFAULT_CLOSE_LABEL);

function toastRole(kind: ToastMessage["kind"]): "alert" | "status" {
  return kind === "error" ? "alert" : "status";
}

function toastDuration(kind: ToastMessage["kind"]): number {
  return kind === "error" ? ERROR_TOAST_DURATION_MS : TOAST_DURATION_MS;
}

function selectPendingToasts(
  toasts: QueuedToast[],
  limit: number,
): QueuedToast[] {
  if (toasts.length <= limit) {
    return toasts;
  }
  const selectedIds = new Set(
    toasts
      .map((toast, index) => ({ toast, index }))
      .sort((left, right) => {
        const kindPriority =
          Number(right.toast.kind === "error") -
          Number(left.toast.kind === "error");
        return kindPriority || right.index - left.index;
      })
      .slice(0, limit)
      .map(({ toast }) => toast.id),
  );
  return toasts.filter((toast) => selectedIds.has(toast.id));
}

function enqueueToast(
  current: QueuedToast[],
  incoming: QueuedToast,
): QueuedToast[] {
  const active = current[0];
  if (incoming.kind === "error" && active && active.kind !== "error") {
    return [incoming, ...selectPendingToasts(current.slice(1), MAX_TOASTS - 1)];
  }

  const next = [...current, incoming];
  if (next.length <= MAX_TOASTS) {
    return next;
  }
  return [next[0], ...selectPendingToasts(next.slice(1), MAX_TOASTS - 1)];
}

function ToastSurface({
  toast,
  onClose,
}: {
  toast: ToastMessage;
  onClose: () => void;
}) {
  const closeLabel = useContext(ToastCloseLabelContext);
  const Icon =
    toast.kind === "success"
      ? CircleCheck
      : toast.kind === "error"
        ? CircleAlert
        : Info;

  return (
    <div className="surface-enter pointer-events-auto flex w-full items-center gap-3 rounded-lg border border-gray-200 bg-white/95 py-2 pr-2 pl-3 shadow-xl shadow-gray-950/15 backdrop-blur-xl dark:border-gray-700 dark:bg-gray-900/95 dark:shadow-black/35">
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
        aria-label={closeLabel}
        title={closeLabel}
      >
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}

function QueuedToastItem({
  toast,
  onClose,
}: {
  toast: QueuedToast;
  onClose: (id: string) => void;
}) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const timeout = window.setTimeout(
      () => onCloseRef.current(toast.id),
      toastDuration(toast.kind),
    );
    return () => window.clearTimeout(timeout);
  }, [toast.id, toast.kind]);

  return (
    <div role={toastRole(toast.kind)}>
      <ToastSurface toast={toast} onClose={() => onClose(toast.id)} />
    </div>
  );
}

function ToastQueue({
  toasts,
  onClose,
}: {
  toasts: QueuedToast[];
  onClose: (id: string) => void;
}) {
  const toast = toasts[0];
  if (!toast) {
    return null;
  }
  return createPortal(
    <div className="pointer-events-none fixed top-[calc(4.25rem+env(safe-area-inset-top))] right-4 left-4 z-[90] flex justify-center lg:top-[max(1rem,calc(env(safe-area-inset-top)+0.75rem))]">
      <div className="w-full max-w-sm">
        <QueuedToastItem key={toast.id} toast={toast} onClose={onClose} />
      </div>
    </div>,
    document.body,
  );
}

export function ToastProvider({
  children,
  closeLabel = DEFAULT_CLOSE_LABEL,
}: {
  children: ReactNode;
  closeLabel?: string;
}) {
  const [toasts, setToasts] = useState<QueuedToast[]>([]);
  const nextIdRef = useRef(0);
  const closeLabelRef = useRef(closeLabel);

  useEffect(() => {
    if (closeLabelRef.current === closeLabel) {
      return;
    }
    closeLabelRef.current = closeLabel;
    setToasts([]);
  }, [closeLabel]);

  const dismissToast = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback((toast: ToastMessage) => {
    const id = `toast-${++nextIdRef.current}`;
    setToasts((current) => enqueueToast(current, { ...toast, id }));
    return id;
  }, []);

  const value = useMemo(
    () => ({ available: true, showToast, dismissToast }),
    [showToast, dismissToast],
  );

  return (
    <ToastCloseLabelContext.Provider value={closeLabel}>
      <ToastContext.Provider value={value}>
        {children}
        {toasts.length > 0 ? (
          <ToastQueue toasts={toasts} onClose={dismissToast} />
        ) : null}
      </ToastContext.Provider>
    </ToastCloseLabelContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  return useContext(ToastContext);
}

export function Toast({
  toast,
  onClose,
}: {
  toast: ToastMessage;
  onClose: () => void;
}) {
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!toast.message) {
      return;
    }
    const timeout = window.setTimeout(
      () => onCloseRef.current(),
      toastDuration(toast.kind),
    );
    return () => window.clearTimeout(timeout);
  }, [toast]);

  return createPortal(
    <div
      role={toastRole(toast.kind)}
      className="pointer-events-none fixed top-[calc(4.25rem+env(safe-area-inset-top))] right-4 left-4 z-[90] flex justify-center lg:top-[max(1rem,calc(env(safe-area-inset-top)+0.75rem))]"
    >
      <div className="w-full max-w-sm">
        <ToastSurface toast={toast} onClose={onClose} />
      </div>
    </div>,
    document.body,
  );
}
