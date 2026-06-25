import { env } from "cloudflare:workers";
import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router";
import { QuickCapture } from "~/components/QuickCapture";
import { Sidebar, Wordmark } from "~/components/Sidebar";
import { requireSession } from "~/lib/auth/session";
import type { Route } from "./+types/app-layout";

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  return null;
}

export default function AppLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const location = useLocation();
  const routeId = `${location.pathname}?${location.search}`;
  const showQuickCapture = location.pathname !== "/ask";

  useEffect(() => {
    setDrawerOpen((open) => (open && routeId ? false : open));
  }, [routeId]);

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

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 dark:bg-gray-950 dark:text-gray-50">
      <Sidebar className="fixed inset-y-0 left-0 z-30 hidden w-64 lg:flex" />

      <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-gray-200 border-b bg-white/90 px-3 backdrop-blur-xl dark:border-gray-800 dark:bg-gray-950/90 lg:hidden">
        <Wordmark />
        <button
          type="button"
          aria-label="打开导航"
          aria-expanded={drawerOpen}
          className="flex h-10 w-10 items-center justify-center rounded-lg text-gray-600 transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-celadon-400/30"
          onClick={() => setDrawerOpen(true)}
        >
          <span aria-hidden="true" className="text-2xl leading-none">
            ☰
          </span>
        </button>
      </header>

      {drawerOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            aria-label="关闭导航"
            className="absolute inset-0 h-full w-full bg-gray-950/20 dark:bg-gray-950/60"
            onClick={() => setDrawerOpen(false)}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-label="导航"
            className="absolute inset-y-0 left-0 w-64 max-w-[85vw] shadow-xl shadow-gray-950/10"
          >
            <Sidebar className="h-full w-full" onNavigate={() => setDrawerOpen(false)} />
          </div>
        </div>
      ) : null}

      <main className="lg:pl-64">
        <Outlet />
      </main>
      {showQuickCapture ? <QuickCapture /> : null}
    </div>
  );
}
