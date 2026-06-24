import { env } from "cloudflare:workers";
import { Form, Link, NavLink, Outlet } from "react-router";
import { QuickCapture } from "~/components/QuickCapture";
import { ThemeToggle } from "~/components/ThemeToggle";
import { pageShellClass, subtleButtonClass } from "~/components/ui";
import { requireSession } from "~/lib/auth/session";
import type { Route } from "./+types/app-layout";

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  return null;
}

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive
    ? "font-medium text-gray-950 dark:text-gray-50"
    : "text-gray-500 transition hover:text-gray-950 dark:text-gray-400 dark:hover:text-gray-100";
}

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-gray-50 text-gray-950 dark:bg-gray-950 dark:text-gray-50">
      <header className="border-b border-gray-200 bg-white/90 backdrop-blur dark:border-gray-800 dark:bg-gray-950/90">
        <div
          className={`${pageShellClass} flex flex-col gap-4 py-4 sm:flex-row sm:items-center sm:justify-between`}
        >
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
            <Link
              to="/"
              className="text-sm font-semibold tracking-tight text-gray-950 sm:text-base dark:text-gray-50"
            >
              Sillage
            </Link>
            <nav className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
              <NavLink to="/" end className={navClass}>
                此刻
              </NavLink>
              <NavLink to="/timeline" className={navClass}>
                痕迹
              </NavLink>
              <NavLink to="/memory" className={navClass}>
                微光
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
