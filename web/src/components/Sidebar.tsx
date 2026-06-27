import {
  History,
  Home,
  LogOut,
  MoreHorizontal,
  PanelLeftClose,
  Plus,
  Settings,
} from "lucide-react";
import { useEffect, useRef } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import type { Account } from "../lib/api";
import { useAsk } from "../state/AskContext";
import { ThemeToggle } from "./ThemeToggle";

const navItems = [
  { to: "/", label: "记录", end: true, icon: Home },
  { to: "/timeline", label: "历史", end: false, icon: History },
] as const;

function navClass({ isActive }: { isActive: boolean }): string {
  const base =
    "flex h-10 items-center gap-2.5 rounded-lg px-3 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 dark:focus-visible:ring-gray-500/40";
  return isActive
    ? `${base} bg-gray-200 font-medium text-gray-900 dark:bg-gray-700/70 dark:text-gray-50`
    : `${base} text-gray-600 hover:bg-gray-200/70 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50`;
}

export function Wordmark({ onClick }: { onClick?: () => void }) {
  return (
    <Link
      to="/"
      onClick={onClick}
      className="block px-2 focus-visible:outline-none"
    >
      <span className="font-semibold text-gray-900 text-lg tracking-tight dark:text-gray-50">
        Sillage
      </span>
      <span className="mt-0.5 block text-[11px] text-gray-400 tracking-wide">
        个人记录
      </span>
    </Link>
  );
}

export function Sidebar({
  className = "",
  onNavigate,
  onCollapse,
  account,
  onSignOut,
}: {
  className?: string;
  onNavigate?: () => void;
  onCollapse?: () => void;
  account: Account;
  onSignOut: () => void;
}) {
  const location = useLocation();
  const { conversations, activeId, startNew } = useAsk();
  const onAskPage = location.pathname === "/ask";
  const accountMenuRef = useRef<HTMLDetailsElement>(null);

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
      if (menu?.open && !menu.contains(event.target as Node)) {
        close();
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        close();
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, []);

  return (
    <aside
      className={`flex min-h-0 flex-col border-gray-200 border-r bg-gray-50 px-3 py-4 dark:border-gray-800 dark:bg-gray-950 ${className}`}
    >
      <div className="flex items-center justify-between gap-2 pb-3">
        <Wordmark onClick={onNavigate} />
        {onCollapse ? (
          <button
            type="button"
            onClick={onCollapse}
            aria-label="收起侧栏"
            title="收起侧栏"
            className="hidden h-8 w-8 flex-none items-center justify-center rounded-lg text-gray-500 transition hover:bg-gray-200 hover:text-gray-900 lg:flex dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50"
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
          className="flex h-10 items-center gap-2.5 rounded-lg border border-gray-300 bg-white px-3 font-medium text-gray-800 text-sm transition hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800 dark:focus-visible:ring-gray-500/40"
        >
          <Plus className="h-4 w-4" />
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

      <section className="mt-4 flex min-h-0 flex-1 flex-col border-gray-200 border-t pt-3 dark:border-gray-800">
        <h2 className="px-3 font-medium text-gray-400 text-xs">问答</h2>
        <nav className="mt-2 min-h-0 flex-1 space-y-0.5 overflow-y-auto">
          {conversations.length === 0 ? (
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
                  className={`block truncate rounded-lg px-3 py-2 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 dark:focus-visible:ring-gray-500/40 ${
                    active
                      ? "bg-gray-200 text-gray-900 dark:bg-gray-700/70 dark:text-gray-50"
                      : "text-gray-600 hover:bg-gray-200/70 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50"
                  }`}
                >
                  {label}
                </Link>
              );
            })
          )}
        </nav>
      </section>

      <div className="mt-3 flex items-center justify-between gap-2 border-gray-200 border-t pt-3 dark:border-gray-800">
        <details ref={accountMenuRef} className="group relative min-w-0">
          <summary className="flex min-w-0 cursor-pointer list-none items-center gap-2 rounded-lg px-2 py-1.5 transition hover:bg-gray-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 dark:hover:bg-gray-800 dark:focus-visible:ring-gray-500/40">
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
          <div className="absolute right-0 bottom-full z-20 mb-2 w-44 rounded-xl border border-gray-200 bg-white p-1.5 shadow-lg shadow-gray-900/10 dark:border-gray-700 dark:bg-gray-800 dark:shadow-black/30">
            <Link
              to="/settings"
              onClick={() => {
                if (accountMenuRef.current) {
                  accountMenuRef.current.open = false;
                }
                onNavigate?.();
              }}
              className="flex items-center gap-2 rounded-lg px-3 py-2 text-gray-700 text-sm transition hover:bg-gray-100 hover:text-gray-900 dark:text-gray-200 dark:hover:bg-gray-700 dark:hover:text-gray-50"
            >
              <Settings className="h-4 w-4" />
              设置
            </Link>
            <button
              type="button"
              onClick={onSignOut}
              className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-red-600 text-sm transition hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/30"
            >
              <LogOut className="h-4 w-4" />
              退出登录
            </button>
          </div>
        </details>
        <ThemeToggle />
      </div>
    </aside>
  );
}
