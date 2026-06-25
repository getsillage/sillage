import type { ReactNode } from "react";
import { Form, Link, NavLink, useLocation, useMatches } from "react-router";
import type { AppVersionBadge as AppVersionBadgeData } from "~/lib/app-channel";
import type { AskConversationSummary, AskConversationView } from "~/lib/db/ask-conversations";
import { inputClass } from "./ui";

type IconProps = {
  className?: string;
};

type AskSidebarData = {
  conversations: AskConversationSummary[];
  currentConversation: AskConversationView | null;
  conversationQuery: string;
  includeArchived: boolean;
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

function PlusIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </>,
    className,
  );
}

function DotsIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M7 12h.01" />
      <path d="M12 12h.01" />
      <path d="M17 12h.01" />
    </>,
    className,
  );
}

function LogOutIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M10 17l5-5-5-5" />
      <path d="M15 12H3" />
      <path d="M14 4h5v16h-5" />
    </>,
    className,
  );
}

function navClass({ isActive }: { isActive: boolean }): string {
  const base =
    "flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:focus-visible:ring-celadon-400/30";
  return isActive
    ? `${base} bg-gray-100 text-gray-950 dark:bg-gray-800 dark:text-gray-50`
    : `${base} text-gray-600 hover:bg-gray-100 hover:text-gray-950 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50`;
}

function isAskSidebarData(data: unknown): data is AskSidebarData {
  if (!data || typeof data !== "object") {
    return false;
  }
  const value = data as Partial<AskSidebarData>;
  return (
    Array.isArray(value.conversations) &&
    typeof value.conversationQuery === "string" &&
    typeof value.includeArchived === "boolean" &&
    "currentConversation" in value
  );
}

function conversationHref(conversationId: string, includeArchived: boolean): string {
  return `/ask?conversation=${conversationId}${includeArchived ? "&archived=1" : ""}`;
}

export function AppVersionBadge({ badge }: { badge: AppVersionBadgeData | null }) {
  if (!badge) {
    return null;
  }
  const toneClass =
    badge.tone === "development"
      ? "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-200"
      : "border-celadon-200 bg-celadon-50 text-celadon-800 dark:border-celadon-800/60 dark:bg-celadon-900/40 dark:text-celadon-200";
  return (
    <span
      className={`inline-flex flex-none items-center rounded-md border px-1.5 py-0.5 font-medium text-[11px] leading-none ${toneClass}`}
    >
      {badge.label}
    </span>
  );
}

export function Wordmark({
  onClick,
  appBadge = null,
}: {
  onClick?: () => void;
  appBadge?: AppVersionBadgeData | null;
}) {
  return (
    <Link to="/" onClick={onClick} className="block px-2 pb-3 focus-visible:outline-none">
      <span className="flex items-center gap-2">
        <span className="text-xl italic text-gray-900 dark:text-gray-50 [font-family:Palatino,'Iowan_Old_Style',serif]">
          Sillage
        </span>
        <AppVersionBadge badge={appBadge} />
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
  appBadge = null,
  authBypassed = false,
}: {
  className?: string;
  onNavigate?: () => void;
  appBadge?: AppVersionBadgeData | null;
  authBypassed?: boolean;
}) {
  const location = useLocation();
  const matches = useMatches();
  const askData =
    location.pathname === "/ask"
      ? matches.map((match) => match.loaderData).find(isAskSidebarData)
      : null;

  return (
    <aside
      className={`flex min-h-0 flex-col border-gray-200 border-r bg-white px-3 py-4 dark:border-gray-800 dark:bg-gray-900 ${className}`}
    >
      <Wordmark onClick={onNavigate} appBadge={appBadge} />

      <Link
        to="/ask"
        onClick={onNavigate}
        className="mb-3 flex items-center gap-2 rounded-lg px-2.5 py-2 text-gray-700 text-sm transition hover:bg-gray-100 hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-celadon-400/30"
      >
        <PlusIcon />
        <span>新探寻</span>
      </Link>

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

      {askData ? <AskConversationSection data={askData} onNavigate={onNavigate} /> : null}

      <UserMenu onNavigate={onNavigate} authBypassed={authBypassed} />
    </aside>
  );
}

function AskConversationSection({
  data,
  onNavigate,
}: {
  data: AskSidebarData;
  onNavigate?: () => void;
}) {
  return (
    <section className="mt-4 flex min-h-0 flex-1 flex-col border-gray-200 border-t pt-3 dark:border-gray-800">
      <div className="flex items-center justify-between gap-2 px-2">
        <h2 className="font-medium text-gray-500 text-xs dark:text-gray-400">最近探寻</h2>
        <Link
          to="/ask"
          onClick={onNavigate}
          className="rounded-md p-1 text-gray-400 transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:text-gray-500 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-celadon-400/30"
          aria-label="新对话"
        >
          <PlusIcon className="h-4 w-4" />
        </Link>
      </div>

      <ConversationSearch
        conversationQuery={data.conversationQuery}
        includeArchived={data.includeArchived}
        onNavigate={onNavigate}
      />

      <ConversationList
        conversations={data.conversations}
        currentConversation={data.currentConversation}
        includeArchived={data.includeArchived}
        onNavigate={onNavigate}
      />
    </section>
  );
}

function ConversationSearch({
  conversationQuery,
  includeArchived,
  onNavigate,
}: {
  conversationQuery: string;
  includeArchived: boolean;
  onNavigate?: () => void;
}) {
  return (
    <Form
      method="get"
      action="/ask"
      className="mt-2 space-y-2 px-1"
      onSubmit={() => onNavigate?.()}
    >
      {includeArchived ? <input type="hidden" name="archived" value="1" /> : null}
      <input
        type="search"
        name="cq"
        defaultValue={conversationQuery}
        placeholder="搜索会话"
        className={`${inputClass} mt-0 h-9 rounded-lg bg-gray-50 px-2.5 py-1.5 text-xs dark:bg-gray-950`}
      />
      <div className="flex items-center justify-between px-1">
        <button
          type="submit"
          className="text-gray-500 text-xs transition hover:text-gray-950 dark:text-gray-400 dark:hover:text-gray-50"
        >
          搜索
        </button>
        <Link
          to={includeArchived ? "/ask" : "/ask?archived=1"}
          onClick={onNavigate}
          className="text-gray-500 text-xs transition hover:text-gray-950 dark:text-gray-400 dark:hover:text-gray-50"
        >
          {includeArchived ? "隐藏归档" : "查看归档"}
        </Link>
      </div>
    </Form>
  );
}

function ConversationList({
  conversations,
  currentConversation,
  includeArchived,
  onNavigate,
}: {
  conversations: AskConversationSummary[];
  currentConversation: AskConversationView | null;
  includeArchived: boolean;
  onNavigate?: () => void;
}) {
  return (
    <nav className="mt-2 min-h-0 flex-1 space-y-0.5 overflow-y-auto pr-1">
      {conversations.map((conversation) => {
        const active = currentConversation?.id === conversation.id;
        return (
          <Link
            key={conversation.id}
            to={conversationHref(conversation.id, includeArchived)}
            onClick={onNavigate}
            aria-current={active ? "page" : undefined}
            title={conversation.title || conversation.lastMessagePreview || "新的探寻"}
            className={`flex min-w-0 items-center gap-2 rounded-lg px-2.5 py-2 text-sm transition ${
              active
                ? "bg-gray-100 text-gray-950 dark:bg-gray-800 dark:text-gray-50"
                : "text-gray-600 hover:bg-gray-100 hover:text-gray-950 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50"
            }`}
          >
            <span
              aria-hidden="true"
              className={`h-1.5 w-1.5 flex-none rounded-full ${
                conversation.pinnedAt ? "bg-celadon-500 dark:bg-celadon-300" : "bg-transparent"
              }`}
            />
            <span className="truncate">{conversation.title || "新的探寻"}</span>
          </Link>
        );
      })}
      {conversations.length === 0 ? (
        <p className="px-2.5 py-3 text-gray-400 text-sm dark:text-gray-500">没有会话。</p>
      ) : null}
    </nav>
  );
}

function UserMenu({
  onNavigate,
  authBypassed,
}: {
  onNavigate?: () => void;
  authBypassed: boolean;
}) {
  return (
    <details className="group relative mt-auto border-gray-200 border-t pt-3 dark:border-gray-800">
      <summary className="flex cursor-pointer list-none items-center gap-2 rounded-lg px-2 py-2 transition hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:hover:bg-gray-800 dark:focus-visible:ring-celadon-400/30">
        <span className="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-gray-900 font-medium text-white text-sm dark:bg-gray-100 dark:text-gray-950">
          S
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium text-gray-900 text-sm dark:text-gray-50">
            Sillage
          </span>
          <span className="block truncate text-gray-400 text-xs dark:text-gray-500">
            {authBypassed ? "开放测试" : "本地空间"}
          </span>
        </span>
        <DotsIcon className="h-4 w-4 flex-none text-gray-400 dark:text-gray-500" />
      </summary>

      <div className="absolute right-0 bottom-full left-0 z-20 mb-2 rounded-xl border border-gray-200 bg-white p-1.5 shadow-xl shadow-gray-900/10 dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/30">
        <Link
          to="/settings"
          onClick={onNavigate}
          className="block rounded-lg px-3 py-2 text-gray-700 text-sm transition hover:bg-gray-100 hover:text-gray-950 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50"
        >
          设置
        </Link>
        <Link
          to="/settings#appearance"
          onClick={onNavigate}
          className="block rounded-lg px-3 py-2 text-gray-700 text-sm transition hover:bg-gray-100 hover:text-gray-950 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50"
        >
          外观
        </Link>
        {authBypassed ? null : (
          <Form method="post" action="/logout">
            <button
              type="submit"
              className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-red-600 text-sm transition hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/30"
            >
              <LogOutIcon className="h-4 w-4" />
              <span>退出登录</span>
            </button>
          </Form>
        )}
      </div>
    </details>
  );
}
