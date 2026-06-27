import { Menu, PanelLeftOpen } from "lucide-react";
import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import type { Account } from "../lib/api";
import { todayISO } from "../lib/date";
import { useMemos } from "../state/MemosContext";
import { QuickCapture } from "./QuickCapture";
import { Sidebar, Wordmark } from "./Sidebar";

const SIDEBAR_KEY = "sillage-sidebar";

function readSidebarOpen(): boolean {
  return window.localStorage.getItem(SIDEBAR_KEY) !== "collapsed";
}

export function AppShell({
  account,
  onSignOut,
}: {
  account: Account;
  onSignOut: () => void;
}) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [desktopOpen, setDesktopOpen] = useState(readSidebarOpen);
  const location = useLocation();
  const routeKey = `${location.pathname}?${location.search}`;
  const showQuickCapture = location.pathname !== "/ask";
  const memos = useMemos();

  // biome-ignore lint/correctness/useExhaustiveDependencies: close the drawer on any navigation
  useEffect(() => {
    setDrawerOpen(false);
  }, [routeKey]);

  useEffect(() => {
    window.localStorage.setItem(
      SIDEBAR_KEY,
      desktopOpen ? "open" : "collapsed",
    );
  }, [desktopOpen]);

  useEffect(() => {
    if (!drawerOpen) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setDrawerOpen(false);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [drawerOpen]);

  async function handleCapture(body: string) {
    await memos.create({ content: body, entryDate: todayISO() });
  }

  return (
    <div className="min-h-screen bg-white text-gray-900 dark:bg-gray-900 dark:text-gray-50">
      {desktopOpen ? (
        <Sidebar
          className="fixed inset-y-0 left-0 z-30 hidden w-72 lg:flex"
          account={account}
          onSignOut={onSignOut}
          onCollapse={() => setDesktopOpen(false)}
        />
      ) : (
        <button
          type="button"
          aria-label="展开侧栏"
          title="展开侧栏"
          onClick={() => setDesktopOpen(true)}
          className="fixed top-3 left-3 z-30 hidden h-9 w-9 items-center justify-center rounded-lg border border-gray-200 bg-white text-gray-500 transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 lg:flex dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
        >
          <PanelLeftOpen className="h-4 w-4" />
        </button>
      )}

      <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-gray-200 border-b bg-white/90 px-3 backdrop-blur-xl lg:hidden dark:border-gray-800 dark:bg-gray-900/90">
        <Wordmark />
        <button
          type="button"
          aria-label="打开导航"
          aria-expanded={drawerOpen}
          className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-600 transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
          onClick={() => setDrawerOpen(true)}
        >
          <Menu className="h-5 w-5" />
        </button>
      </header>

      {drawerOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            aria-label="关闭导航"
            className="absolute inset-0 h-full w-full bg-gray-950/30 dark:bg-gray-950/60"
            onClick={() => setDrawerOpen(false)}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label="导航"
            className="absolute inset-y-0 left-0 w-72 max-w-[88vw] shadow-xl shadow-gray-950/10"
          >
            <Sidebar
              className="h-full w-full"
              onNavigate={() => setDrawerOpen(false)}
              account={account}
              onSignOut={onSignOut}
            />
          </div>
        </div>
      ) : null}

      <main className={desktopOpen ? "lg:pl-72" : "lg:pl-0"}>
        <Outlet />
      </main>
      {showQuickCapture ? <QuickCapture onCapture={handleCapture} /> : null}
    </div>
  );
}
