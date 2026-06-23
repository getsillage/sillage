import { env } from "cloudflare:workers";
import { Form, Link, NavLink, Outlet } from "react-router";
import { requireSession } from "~/lib/auth/session";
import type { Route } from "./+types/app-layout";

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  return null;
}

function navClass({ isActive }: { isActive: boolean }): string {
  return isActive ? "font-medium text-gray-900" : "text-gray-500 hover:text-gray-900";
}

export default function AppLayout() {
  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="border-gray-200 border-b bg-white">
        <div className="mx-auto flex max-w-2xl items-center justify-between p-4">
          <Link to="/" className="font-semibold text-gray-900">
            我的日记
          </Link>
          <div className="flex items-center gap-4 text-sm">
            <NavLink to="/" end className={navClass}>
              时间线
            </NavLink>
            <NavLink to="/calendar" className={navClass}>
              日历
            </NavLink>
            <NavLink to="/search" className={navClass}>
              搜索
            </NavLink>
            <Link
              to="/new"
              className="rounded-lg bg-gray-900 px-3 py-1.5 font-medium text-white hover:bg-gray-800"
            >
              写日记
            </Link>
            <Form method="post" action="/logout">
              <button type="submit" className="text-gray-500 hover:text-gray-900">
                退出
              </button>
            </Form>
          </div>
        </div>
      </nav>
      <Outlet />
    </div>
  );
}
