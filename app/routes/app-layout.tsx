import { env } from "cloudflare:workers";
import { Form, Link, NavLink, Outlet } from "react-router";
import { QuickCapture } from "~/components/QuickCapture";
import { ThemeToggle } from "~/components/ThemeToggle";
import { subtleButtonClass } from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import type { Route } from "./+types/app-layout";

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  return null;
}

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive
    ? "rounded-full bg-gray-950 px-3 py-1.5 font-medium text-white dark:bg-gray-100 dark:text-gray-950"
    : "rounded-full px-3 py-1.5 text-gray-500 transition hover:bg-gray-100 hover:text-gray-950 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100";
}

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-gray-50 text-gray-950 dark:bg-gray-950 dark:text-gray-50">
      <header className="sticky top-0 z-30 border-gray-200/80 border-b bg-white/90 backdrop-blur-xl dark:border-gray-800 dark:bg-gray-950/90">
        <div className="mx-auto flex w-full max-w-[1680px] flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8 xl:px-10 2xl:px-12">
          <div className="flex min-w-0 flex-wrap items-center gap-x-5 gap-y-3">
            <Link
              to="/"
              className="inline-flex shrink-0 items-center gap-2 text-sm font-semibold tracking-tight text-gray-950 sm:text-base dark:text-gray-50"
            >
              <img src="/sillage-icon.svg" alt="" className="h-6 w-6 shrink-0" />
              <span>Sillage</span>
            </Link>
            <nav className="flex min-w-0 flex-wrap items-center gap-1 text-sm">
              <NavLink to="/" end className={navClass}>
                此刻
              </NavLink>
              <NavLink to="/timeline" className={navClass}>
                痕迹
              </NavLink>
              <NavLink to="/review" className={navClass}>
                照见
              </NavLink>
              <NavLink to="/ask" className={navClass}>
                探寻
              </NavLink>
              <NavLink to="/settings" className={navClass}>
                设置
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Form method="post" action="/logout">
              <button type="submit" className={subtleButtonClass}>
                退出
              </button>
            </Form>
          </div>
        </div>
      </header>
      <Outlet />
      <QuickCapture />
    </div>
  );
}
