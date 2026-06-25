import type { ReactNode } from "react";
import { Form, Link, NavLink } from "react-router";
import { ThemeToggle } from "./ThemeToggle";
import { subtleButtonClass } from "./ui";

type IconProps = {
  className?: string;
};

const navItems = [
  { to: "/", label: "此刻", end: true, icon: HomeIcon },
  { to: "/timeline", label: "痕迹", icon: TraceIcon },
  { to: "/review", label: "照见", icon: SparkIcon },
  { to: "/ask", label: "探寻", icon: MessageIcon },
  { to: "/settings", label: "设置", icon: SettingsIcon },
] as const;

function iconPath(children: ReactNode, className = "h-[18px] w-[18px]"): ReactNode {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      {children}
    </svg>
  );
}

function HomeIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="m4 11 8-7 8 7" />
      <path d="M6.5 10.5V20h11v-9.5" />
      <path d="M10 20v-5h4v5" />
    </>,
    className,
  );
}

function TraceIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M7 5h5a5 5 0 0 1 0 10H9" />
      <path d="M7 5a2 2 0 1 0 0 4" />
      <path d="M9 15a2 2 0 1 0 0 4" />
      <path d="M12 19h5" />
    </>,
    className,
  );
}

function SparkIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M12 3l1.6 5.1L19 10l-5.4 1.9L12 17l-1.6-5.1L5 10l5.4-1.9L12 3z" />
      <path d="M5 17l.6 1.8L7.5 19l-1.9.7L5 21.5l-.6-1.8L2.5 19l1.9-.7L5 17z" />
      <path d="M19 15l.5 1.5L21 17l-1.5.5L19 19l-.5-1.5L17 17l1.5-.5L19 15z" />
    </>,
    className,
  );
}

function MessageIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M5 6.5A7 7 0 0 1 12 4a7 7 0 0 1 7 6.5A6.6 6.6 0 0 1 12 17a8 8 0 0 1-2-.3L5 20l1.2-4A6.5 6.5 0 0 1 5 6.5z" />
      <path d="M9 10h6" />
      <path d="M9 13h4" />
    </>,
    className,
  );
}

function SettingsIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z" />
      <path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.4 1a7.6 7.6 0 0 0-1.8-1L14.4 3h-4.8l-.3 3.1a7.6 7.6 0 0 0-1.8 1l-2.4-1-2 3.4 2 1.5a7 7 0 0 0 0 2l-2 1.5 2 3.4 2.4-1a7.6 7.6 0 0 0 1.8 1l.3 3.1h4.8l.3-3.1a7.6 7.6 0 0 0 1.8-1l2.4 1 2-3.4-2-1.5c.1-.3.1-.7.1-1z" />
    </>,
    className,
  );
}

function navClass({ isActive }: { isActive: boolean }): string {
  const base =
    "flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:focus-visible:ring-celadon-400/30";
  return isActive
    ? `${base} bg-celadon-50 text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200`
    : `${base} text-gray-500 hover:bg-gray-100 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-100`;
}

export function Wordmark({ onClick }: { onClick?: () => void }) {
  return (
    <Link to="/" onClick={onClick} className="block px-2 pb-5 focus-visible:outline-none">
      <span className="text-xl italic text-gray-900 dark:text-gray-50 [font-family:Palatino,'Iowan_Old_Style',serif]">
        Sillage
      </span>
      <span className="mt-0.5 block font-serif text-[11px] tracking-widest text-gray-400">
        记忆的余迹
      </span>
    </Link>
  );
}

export function Sidebar({
  className = "",
  onNavigate,
}: {
  className?: string;
  onNavigate?: () => void;
}) {
  return (
    <aside
      className={`flex flex-col border-gray-200 border-r bg-white px-3 py-5 dark:border-gray-800 dark:bg-gray-900 ${className}`}
    >
      <Wordmark onClick={onNavigate} />

      <nav className="flex flex-col gap-0.5">
        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={"end" in item ? item.end : undefined}
              className={navClass}
              onClick={onNavigate}
            >
              <Icon />
              <span>{item.label}</span>
            </NavLink>
          );
        })}
      </nav>

      <div className="mt-auto flex items-center justify-between gap-2 border-gray-200 border-t pt-3 dark:border-gray-800">
        <ThemeToggle />
        <Form method="post" action="/logout">
          <button type="submit" className={subtleButtonClass}>
            退出
          </button>
        </Form>
      </div>
    </aside>
  );
}
