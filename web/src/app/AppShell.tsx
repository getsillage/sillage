import { Menu, PanelLeftOpen } from "lucide-react";
import {
  type KeyboardEvent as ReactKeyboardEvent,
  useEffect,
  useRef,
  useState,
} from "react";
import { Outlet, useLocation } from "react-router-dom";
import { Toast } from "../components/Toast";
import { useAsk } from "../features/ask/AskContext";
import { useMemos } from "../features/memos/MemosContext";
import { QuickCapture } from "../features/memos/QuickCapture";
import { useI18n } from "../i18n/I18nProvider";
import type { Account } from "../lib/api";
import { todayISO } from "../lib/date";
import { Sidebar, Wordmark } from "./Sidebar";

const SIDEBAR_KEY = "sillage-sidebar";
const DRAWER_FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), summary, [tabindex]:not([tabindex="-1"])';

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
  const { t } = useI18n();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [desktopOpen, setDesktopOpen] = useState(readSidebarOpen);
  const drawerRef = useRef<HTMLDivElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const location = useLocation();
  const routeKey = `${location.pathname}?${location.search}`;
  const showQuickCapture = location.pathname !== "/ask";
  const memos = useMemos();
  const { notification, dismissNotification } = useAsk();

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
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    drawerRef.current
      ?.querySelector<HTMLElement>("[data-drawer-initial-focus]")
      ?.focus();

    function onKeyDown(event: globalThis.KeyboardEvent) {
      if (event.key === "Escape") {
        const visibleAlertDialog = Array.from(
          document.querySelectorAll<HTMLElement>(
            '[role="alertdialog"][aria-modal="true"]',
          ),
        ).some(
          (dialog) =>
            !dialog.hidden && dialog.getAttribute("aria-hidden") !== "true",
        );
        if (visibleAlertDialog) {
          return;
        }
        setDrawerOpen(false);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      menuButtonRef.current?.focus();
    };
  }, [drawerOpen]);

  function handleDrawerKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    if (event.key !== "Tab" || !drawerRef.current) {
      return;
    }
    const focusable = Array.from(
      drawerRef.current.querySelectorAll<HTMLElement>(
        DRAWER_FOCUSABLE_SELECTOR,
      ),
    ).filter((element) => {
      const closedDetails = element.closest("details:not([open])");
      return !closedDetails || element.tagName === "SUMMARY";
    });
    if (focusable.length === 0) {
      event.preventDefault();
      drawerRef.current.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  async function handleCapture(body: string) {
    await memos.create({ content: body, entryDate: todayISO() });
  }

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 dark:bg-gray-950 dark:text-gray-50">
      {desktopOpen ? (
        <Sidebar
          className="fixed inset-y-0 left-0 z-30 hidden w-[18rem] lg:flex"
          account={account}
          onSignOut={onSignOut}
          onCollapse={() => setDesktopOpen(false)}
        />
      ) : (
        <button
          type="button"
          aria-label={t("nav.expandSidebar")}
          title={t("nav.expandSidebar")}
          onClick={() => setDesktopOpen(true)}
          className="fixed top-3 left-3 z-30 hidden h-10 w-10 items-center justify-center rounded-lg border border-gray-200 bg-white/90 text-gray-500 shadow-sm shadow-gray-900/[0.03] transition-colors hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 lg:flex dark:border-gray-800 dark:bg-gray-900/90 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
        >
          <PanelLeftOpen className="h-4 w-4" />
        </button>
      )}

      <header className="sticky top-0 z-20 flex h-[calc(3.5rem+env(safe-area-inset-top))] items-center justify-between border-gray-200/80 border-b bg-gray-50/90 px-3 pt-[env(safe-area-inset-top)] backdrop-blur-xl lg:hidden dark:border-gray-800 dark:bg-gray-950/90">
        <Wordmark compact />
        <button
          ref={menuButtonRef}
          type="button"
          aria-label={t("nav.open")}
          aria-expanded={drawerOpen}
          aria-controls="mobile-navigation-dialog"
          className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-600 transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
          onClick={() => setDrawerOpen(true)}
        >
          <Menu className="h-5 w-5" />
        </button>
      </header>

      {drawerOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            aria-label={t("nav.closeBackdrop")}
            tabIndex={-1}
            className="absolute inset-0 h-full w-full bg-gray-950/30 dark:bg-gray-950/60"
            onClick={() => setDrawerOpen(false)}
          />
          <div
            ref={drawerRef}
            id="mobile-navigation-dialog"
            role="dialog"
            aria-modal="true"
            aria-label={t("nav.navigation")}
            tabIndex={-1}
            onKeyDown={handleDrawerKeyDown}
            className="absolute inset-y-0 left-0 w-[18rem] max-w-[88vw] shadow-xl shadow-gray-950/10"
          >
            <Sidebar
              className="h-full w-full"
              onNavigate={() => setDrawerOpen(false)}
              onClose={() => setDrawerOpen(false)}
              account={account}
              onSignOut={onSignOut}
            />
          </div>
        </div>
      ) : null}

      <div
        className={`transition-[padding] duration-200 ${showQuickCapture ? "pb-20 lg:pb-0" : ""} ${desktopOpen ? "lg:pl-[18rem]" : "lg:pl-0"}`}
      >
        <Outlet />
      </div>
      <QuickCapture visible={showQuickCapture} onCapture={handleCapture} />
      {notification ? (
        <Toast toast={notification} onClose={dismissNotification} />
      ) : null}
    </div>
  );
}
