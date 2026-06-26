import type { ReactNode } from "react";
import { Form, Link, NavLink, useLocation, useMatches } from "react-router";
import type { AppVersionBadge as AppVersionBadgeData } from "~/lib/app-channel";
import type { AskConversationSummary, AskConversationView } from "~/lib/db/ask-conversations";

type IconProps = {
  className?: string;
};

type AskSidebarData = {
  conversationQuery: string;
  conversations: AskConversationSummary[];
  currentConversation: AskConversationView | null;
  includeArchived: boolean;
};

const navItems = [
  { to: "/", label: "记录", end: true, icon: HomeIcon },
  { to: "/timeline", label: "历史", icon: TraceIcon },
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

function PlusIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </>,
    className,
  );
}

function SearchIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="m21 21-4.3-4.3" />
      <circle cx="11" cy="11" r="6" />
    </>,
    className,
  );
}

function PinIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M12 17v5" />
      <path d="M8 3h8l-1 7 3 3v2H6v-2l3-3z" />
    </>,
    className,
  );
}

function ArchiveIcon({ className }: IconProps) {
  return iconPath(
    <>
      <path d="M4 7h16" />
      <path d="M6 7v12h12V7" />
      <path d="M9 11h6" />
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
    "flex h-10 items-center gap-2 rounded-xl px-3 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:focus-visible:ring-celadon-400/30";
  return isActive
    ? `${base} bg-white text-gray-950 shadow-sm shadow-gray-900/5 dark:bg-gray-800 dark:text-gray-50 dark:shadow-black/20`
    : `${base} text-gray-600 hover:bg-white/80 hover:text-gray-950 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50`;
}

function isAskSidebarData(data: unknown): data is AskSidebarData {
  return (
    !!data &&
    typeof data === "object" &&
    "conversationQuery" in data &&
    "conversations" in data &&
    "currentConversation" in data &&
    "includeArchived" in data
  );
}

function askSidebarData(matches: Array<{ loaderData?: unknown }>): AskSidebarData | null {
  return matches.map((match) => match.loaderData).find(isAskSidebarData) ?? null;
}

function conversationLabel(conversation: AskConversationSummary): string {
  return conversation.title || conversation.lastMessagePreview || "新的问答";
}

function askArchiveUrl(includeArchived: boolean): string {
  return includeArchived ? "/ask" : "/ask?archived=1";
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
  compact = false,
}: {
  onClick?: () => void;
  appBadge?: AppVersionBadgeData | null;
  compact?: boolean;
}) {
  return (
    <Link
      to="/"
      onClick={onClick}
      className={`block px-2 focus-visible:outline-none ${compact ? "py-1" : "pb-4"}`}
    >
      <span className="flex items-center gap-2">
        <span className="font-serif text-xl italic text-gray-900 dark:text-gray-50 [font-family:Palatino,'Iowan_Old_Style',serif]">
          Sillage
        </span>
        <AppVersionBadge badge={appBadge} />
      </span>
      <span className="mt-0.5 block font-serif text-[11px] tracking-widest text-gray-400">
        个人记录
      </span>
    </Link>
  );
}

export function Sidebar({
  className = "",
  onNavigate,
  appBadge = null,
  authBypassed = false,
  conversations,
}: {
  className?: string;
  onNavigate?: () => void;
  appBadge?: AppVersionBadgeData | null;
  authBypassed?: boolean;
  conversations: AskConversationSummary[];
}) {
  const location = useLocation();
  const askData = askSidebarData(useMatches());
  const onAskPage = location.pathname === "/ask";
  const currentConversationId = onAskPage ? (askData?.currentConversation?.id ?? null) : null;
  const visibleConversations = onAskPage && askData ? askData.conversations : conversations;
  const conversationQuery = onAskPage ? (askData?.conversationQuery ?? "") : "";
  const includeArchived = onAskPage ? (askData?.includeArchived ?? false) : false;
  const pinnedConversations = visibleConversations.filter((conversation) => conversation.pinnedAt);
  const recentConversations = visibleConversations.filter((conversation) => !conversation.pinnedAt);
  const newAskActive = onAskPage && !currentConversationId;

  return (
    <aside
      className={`flex min-h-0 flex-col border-gray-200 border-r bg-gray-100/80 px-3 py-4 dark:border-gray-800 dark:bg-gray-900 ${className}`}
    >
      <Wordmark onClick={onNavigate} appBadge={appBadge} />

      <div className="space-y-2">
        <Link
          to="/ask"
          onClick={onNavigate}
          aria-current={newAskActive ? "page" : undefined}
          className={`flex h-11 items-center gap-2 rounded-2xl px-3 font-medium text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:focus-visible:ring-celadon-400/30 ${
            newAskActive
              ? "bg-gray-950 text-white shadow-sm shadow-gray-900/10 dark:bg-gray-50 dark:text-gray-950"
              : "bg-white text-gray-900 shadow-sm shadow-gray-900/5 hover:bg-celadon-50 hover:text-celadon-800 dark:bg-gray-800 dark:text-gray-50 dark:shadow-black/20 dark:hover:bg-celadon-900/40 dark:hover:text-celadon-100"
          }`}
        >
          <span className="flex h-7 w-7 flex-none items-center justify-center rounded-full bg-gray-100 text-gray-700 dark:bg-gray-900 dark:text-gray-200">
            <PlusIcon className="h-4 w-4" />
          </span>
          <span>新问答</span>
        </Link>

        <nav className="flex flex-col gap-1">
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
                <Icon className="h-4 w-4" />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </div>

      <section className="mt-4 flex min-h-0 flex-1 flex-col border-gray-200 border-t pt-3 dark:border-gray-800">
        <div className="flex items-center justify-between gap-2 px-2">
          <h2 className="font-medium text-gray-500 text-xs dark:text-gray-400">对话</h2>
          <Link
            to={askArchiveUrl(includeArchived)}
            onClick={onNavigate}
            className={`inline-flex h-7 items-center gap-1 rounded-lg px-2 text-xs transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:focus-visible:ring-celadon-400/30 ${
              includeArchived
                ? "bg-celadon-50 text-celadon-800 dark:bg-celadon-900/40 dark:text-celadon-200"
                : "text-gray-400 hover:bg-white hover:text-gray-900 dark:text-gray-500 dark:hover:bg-gray-800 dark:hover:text-gray-50"
            }`}
          >
            <ArchiveIcon className="h-3.5 w-3.5" />
            <span>归档</span>
          </Link>
        </div>

        <Form method="get" action="/ask" className="relative mt-2" onSubmit={onNavigate}>
          {includeArchived ? <input type="hidden" name="archived" value="1" /> : null}
          <SearchIcon className="-translate-y-1/2 pointer-events-none absolute top-1/2 left-3 h-4 w-4 text-gray-400 dark:text-gray-500" />
          <input
            key={conversationQuery}
            type="search"
            name="cq"
            defaultValue={conversationQuery}
            placeholder="搜索对话"
            className="h-9 w-full rounded-xl border border-transparent bg-white px-9 text-sm text-gray-900 outline-none transition placeholder:text-gray-400 focus:border-celadon-300 focus:ring-2 focus:ring-celadon-600/15 dark:bg-gray-800 dark:text-gray-50 dark:placeholder:text-gray-500 dark:focus:border-celadon-700 dark:focus:ring-celadon-400/20"
          />
        </Form>

        <nav className="mt-3 min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
          <ConversationGroup
            title="置顶"
            conversations={pinnedConversations}
            currentConversationId={currentConversationId}
            onNavigate={onNavigate}
          />
          <ConversationGroup
            title={includeArchived ? "归档对话" : "最近"}
            conversations={recentConversations}
            currentConversationId={currentConversationId}
            onNavigate={onNavigate}
          />
          {visibleConversations.length === 0 ? (
            <p className="px-2.5 py-3 text-gray-400 text-sm dark:text-gray-500">
              {conversationQuery ? "没有找到对话。" : "还没有对话。"}
            </p>
          ) : null}
        </nav>
      </section>

      <details className="group relative mt-4 border-gray-200 border-t pt-3 dark:border-gray-800">
        <summary className="flex cursor-pointer list-none items-center gap-2 rounded-2xl px-2 py-2 transition hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:hover:bg-gray-800 dark:focus-visible:ring-celadon-400/30">
          <span className="flex h-8 w-8 flex-none items-center justify-center rounded-full bg-gray-950 font-medium text-white text-sm dark:bg-gray-100 dark:text-gray-950">
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

        <div className="absolute right-0 bottom-full left-0 z-20 mb-2 rounded-2xl border border-gray-200 bg-white p-1.5 shadow-xl shadow-gray-900/10 dark:border-gray-800 dark:bg-gray-900 dark:shadow-black/30">
          <Link
            to="/settings"
            onClick={onNavigate}
            className="block rounded-xl px-3 py-2 text-gray-700 text-sm transition hover:bg-gray-100 hover:text-gray-950 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50"
          >
            设置
          </Link>
          {authBypassed ? null : (
            <Form method="post" action="/logout">
              <button
                type="submit"
                className="flex w-full items-center gap-2 rounded-xl px-3 py-2 text-left text-red-600 text-sm transition hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/30"
              >
                <LogOutIcon className="h-4 w-4" />
                <span>退出登录</span>
              </button>
            </Form>
          )}
        </div>
      </details>
    </aside>
  );
}

function ConversationGroup({
  title,
  conversations,
  currentConversationId,
  onNavigate,
}: {
  title: string;
  conversations: AskConversationSummary[];
  currentConversationId: string | null;
  onNavigate?: () => void;
}) {
  if (conversations.length === 0) {
    return null;
  }
  return (
    <div>
      <h3 className="px-2.5 pb-1 font-medium text-[11px] text-gray-400 dark:text-gray-500">
        {title}
      </h3>
      <div className="space-y-0.5">
        {conversations.map((conversation) => {
          const active = currentConversationId === conversation.id;
          const label = conversationLabel(conversation);
          return (
            <Link
              key={conversation.id}
              to={`/ask?conversation=${conversation.id}`}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              title={label}
              className={`group flex min-h-10 min-w-0 items-center gap-2 rounded-xl px-2.5 py-2 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-celadon-600/20 dark:focus-visible:ring-celadon-400/30 ${
                active
                  ? "bg-white text-gray-950 shadow-sm shadow-gray-900/5 dark:bg-gray-800 dark:text-gray-50 dark:shadow-black/20"
                  : "text-gray-600 hover:bg-white/80 hover:text-gray-950 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-50"
              }`}
            >
              <span className="min-w-0 flex-1 truncate">{label}</span>
              {conversation.pinnedAt ? (
                <PinIcon className="h-3.5 w-3.5 flex-none text-celadon-600 dark:text-celadon-300" />
              ) : null}
            </Link>
          );
        })}
      </div>
    </div>
  );
}
