import {
  History,
  Home,
  LogOut,
  MessageSquarePlus,
  MoreHorizontal,
  PanelLeftClose,
  Settings,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, NavLink, useLocation } from "react-router-dom";
import type { Account } from "../lib/api";
import { useAsk } from "../state/AskContext";
import { ThemeToggle } from "./ThemeToggle";
import { hasUnsavedChanges } from "./UnsavedNavigationGuard";
import { dangerButtonClass, secondaryButtonClass } from "./ui";

const navItems = [
  { to: "/", label: "记录", end: true, icon: Home },
  { to: "/timeline", label: "历史", end: false, icon: History },
] as const;

function navClass({ isActive }: { isActive: boolean }): string {
  const base =
    "flex h-10 items-center gap-2.5 rounded-lg px-3 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40";
  return isActive
    ? `${base} bg-white font-medium text-gray-900 shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800 dark:text-gray-50 dark:shadow-black/10`
    : `${base} text-gray-600 hover:bg-white/70 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-900 dark:hover:text-gray-50`;
}

export function Wordmark({
  onClick,
  compact = false,
}: {
  onClick?: () => void;
  compact?: boolean;
}) {
  return (
    <Link
      to="/"
      onClick={onClick}
      className="flex items-center gap-2 rounded-lg px-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40"
    >
      <img src="/sillage-icon.svg" alt="" className="h-8 w-8 flex-none" />
      <span className="min-w-0">
        <span className="block font-semibold text-gray-900 text-lg dark:text-gray-50">
          Sillage
        </span>
        {compact ? null : (
          <span className="block text-[11px] text-gray-500 dark:text-gray-400">
            个人记录
          </span>
        )}
      </span>
    </Link>
  );
}

export function Sidebar({
  className = "",
  onNavigate,
  onCollapse,
  onClose,
  account,
  onSignOut,
}: {
  className?: string;
  onNavigate?: () => void;
  onCollapse?: () => void;
  onClose?: () => void;
  account: Account;
  onSignOut: () => void;
}) {
  const location = useLocation();
  const { conversations, loadingConversations, activeId, startNew } = useAsk();
  const onAskPage = location.pathname === "/ask";
  const accountMenuRef = useRef<HTMLDetailsElement>(null);
  const signOutButtonRef = useRef<HTMLButtonElement>(null);
  const signOutDialogRef = useRef<HTMLDivElement>(null);
  const stayButtonRef = useRef<HTMLButtonElement>(null);
  const signingOutRef = useRef(false);
  const [confirmingSignOut, setConfirmingSignOut] = useState(false);

  // Close the native <details> account menu on outside click or Escape, which
  // it does not do on its own.
  useEffect(() => {
    function close() {
      if (accountMenuRef.current) {
        accountMenuRef.current.open = false;
      }
    }
    function onPointerDown(event: PointerEvent) {
      const menu = accountMenuRef.current;
      if (
        !confirmingSignOut &&
        menu?.open &&
        !menu.contains(event.target as Node)
      ) {
        close();
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !confirmingSignOut) {
        close();
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [confirmingSignOut]);

  useEffect(() => {
    if (!confirmingSignOut) {
      return;
    }
    signingOutRef.current = false;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    stayButtonRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        setConfirmingSignOut(false);
        return;
      }
      if (event.key !== "Tab" || !signOutDialogRef.current) {
        return;
      }
      const focusable = signOutDialogRef.current.querySelectorAll<HTMLElement>(
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
      if (!signingOutRef.current) {
        signOutButtonRef.current?.focus();
      }
    };
  }, [confirmingSignOut]);

  function requestSignOut() {
    if (hasUnsavedChanges()) {
      setConfirmingSignOut(true);
      return;
    }
    if (accountMenuRef.current) {
      accountMenuRef.current.open = false;
    }
    onSignOut();
  }

  return (
    <aside
      className={`flex min-h-0 flex-col border-gray-200/80 border-r bg-gray-100 px-3 pt-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))] dark:border-gray-800 dark:bg-gray-950 ${className}`}
    >
      <div className="flex items-center justify-between gap-2 pb-3">
        <Wordmark onClick={onNavigate} />
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭导航"
            title="关闭导航"
            data-drawer-initial-focus
            className="flex h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-500 transition hover:bg-white hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
          >
            <X className="h-5 w-5" />
          </button>
        ) : onCollapse ? (
          <button
            type="button"
            onClick={onCollapse}
            aria-label="收起侧栏"
            title="收起侧栏"
            className="hidden h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-white hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 lg:flex dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
          >
            <PanelLeftClose className="h-4 w-4" />
          </button>
        ) : null}
      </div>

      <div className="space-y-1">
        <Link
          to="/ask"
          onClick={() => {
            startNew();
            onNavigate?.();
          }}
          className="flex h-11 items-center gap-2.5 rounded-lg border border-gray-200 bg-white px-3 font-medium text-gray-800 text-sm shadow-sm shadow-gray-900/[0.03] transition-colors hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800 dark:focus-visible:ring-gray-500/40"
        >
          <MessageSquarePlus className="h-4 w-4" />
          <span>新问答</span>
        </Link>

        <nav className="flex flex-col gap-0.5 pt-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={navClass}
                onClick={onNavigate}
              >
                <Icon className="h-4 w-4" />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </div>

      <section className="mt-4 flex min-h-0 flex-1 flex-col border-gray-200/80 border-t pt-3 dark:border-gray-800">
        <h2 className="px-3 font-medium text-gray-500 text-xs dark:text-gray-500">
          问答
        </h2>
        <nav className="mt-2 min-h-0 flex-1 space-y-0.5 overflow-y-auto pr-1">
          {loadingConversations ? (
            <div className="space-y-2 px-3 py-2" role="status">
              <span className="sr-only">正在读取对话</span>
              <div className="h-3 w-4/5 animate-pulse rounded bg-gray-200 dark:bg-gray-800" />
              <div className="h-3 w-3/5 animate-pulse rounded bg-gray-200 dark:bg-gray-800" />
            </div>
          ) : conversations.length === 0 ? (
            <p className="px-3 py-2 text-gray-400 text-sm">还没有对话。</p>
          ) : (
            conversations.map((conversation) => {
              const active = onAskPage && activeId === conversation.id;
              const label = conversation.title || "新的问答";
              return (
                <Link
                  key={conversation.id}
                  to={`/ask?conversation=${conversation.id}`}
                  onClick={onNavigate}
                  aria-current={active ? "page" : undefined}
                  title={label}
                  className={`block h-10 truncate rounded-lg px-3 py-2.5 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40 ${
                    active
                      ? "bg-white font-medium text-gray-900 shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800 dark:text-gray-50"
                      : "text-gray-600 hover:bg-white/70 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-900 dark:hover:text-gray-50"
                  }`}
                >
                  {label}
                </Link>
              );
            })
          )}
        </nav>
      </section>

      <div className="mt-3 flex items-center border-gray-200/80 border-t pt-3 dark:border-gray-800">
        <details ref={accountMenuRef} className="group relative min-w-0 flex-1">
          <summary className="flex h-10 min-w-0 cursor-pointer list-none items-center gap-2 rounded-lg px-2 transition hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:hover:bg-gray-900 dark:focus-visible:ring-gray-500/40">
            <span className="flex h-7 w-7 flex-none items-center justify-center rounded-full bg-gray-900 font-medium text-white text-xs dark:bg-gray-100 dark:text-gray-900">
              {(account.displayName || account.username || "S")
                .slice(0, 1)
                .toUpperCase()}
            </span>
            <span className="min-w-0 flex-1 truncate text-gray-700 text-sm dark:text-gray-200">
              {account.displayName || account.username}
            </span>
            <MoreHorizontal className="h-4 w-4 flex-none text-gray-400" />
          </summary>
          <div className="absolute right-0 bottom-full z-20 mb-2 w-44 rounded-xl border border-gray-200 bg-white p-1.5 shadow-lg shadow-gray-900/10 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/30">
            <Link
              to="/settings"
              onClick={() => {
                if (accountMenuRef.current) {
                  accountMenuRef.current.open = false;
                }
                onNavigate?.();
              }}
              className="flex h-10 items-center gap-2 rounded-lg px-3 text-gray-700 text-sm transition-colors hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-200 dark:hover:bg-gray-800 dark:hover:text-gray-50"
            >
              <Settings className="h-4 w-4" />
              设置
            </Link>
            <button
              ref={signOutButtonRef}
              type="button"
              onClick={requestSignOut}
              className="flex h-10 w-full items-center gap-2 rounded-lg px-3 text-left text-red-600 text-sm transition-colors hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500/30 dark:text-red-400 dark:hover:bg-red-950/30"
            >
              <LogOut className="h-4 w-4" />
              退出登录
            </button>
          </div>
        </details>
        <ThemeToggle compact />
      </div>
      {confirmingSignOut
        ? createPortal(
            <div className="fixed inset-0 z-[80] grid place-items-center px-4">
              <button
                type="button"
                aria-label="继续编辑"
                className="absolute inset-0 h-full w-full bg-gray-950/35 dark:bg-gray-950/70"
                onClick={() => setConfirmingSignOut(false)}
              />
              <div
                ref={signOutDialogRef}
                role="alertdialog"
                aria-modal="true"
                aria-labelledby="sign-out-confirmation-title"
                aria-describedby="sign-out-confirmation-description"
                className="surface-enter relative w-full max-w-sm rounded-xl border border-gray-200 bg-white p-5 shadow-xl shadow-gray-950/15 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/35"
              >
                <h2
                  id="sign-out-confirmation-title"
                  className="font-semibold text-gray-900 text-lg dark:text-gray-50"
                >
                  仍要退出登录？
                </h2>
                <p
                  id="sign-out-confirmation-description"
                  className="mt-2 text-gray-500 text-sm leading-6 dark:text-gray-400"
                >
                  当前有未保存更改，退出可能中断编辑或上传。确认仍要退出吗？
                </p>
                <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <button
                    ref={stayButtonRef}
                    type="button"
                    className={secondaryButtonClass}
                    onClick={() => setConfirmingSignOut(false)}
                  >
                    继续编辑
                  </button>
                  <button
                    type="button"
                    className={dangerButtonClass}
                    onClick={() => {
                      signingOutRef.current = true;
                      setConfirmingSignOut(false);
                      if (accountMenuRef.current) {
                        accountMenuRef.current.open = false;
                      }
                      onSignOut();
                    }}
                  >
                    仍然退出
                  </button>
                </div>
              </div>
            </div>,
            document.body,
          )
        : null}
    </aside>
  );
}
