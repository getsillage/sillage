import {
  Archive,
  ArchiveRestore,
  Library,
  LogOut,
  MessageSquarePlus,
  MoreHorizontal,
  NotebookPen,
  PanelLeftClose,
  Search,
  Settings,
  X,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { ThemeToggle } from "../components/ThemeToggle";
import { hasUnsavedChanges } from "../components/UnsavedNavigationGuard";
import { dangerButtonClass, secondaryButtonClass } from "../components/ui";
import { useAsk } from "../features/ask/AskContext";
import { useI18n } from "../i18n/I18nProvider";
import type { TranslationKey } from "../i18n/messages";
import type { Account, AskConversation } from "../lib/api";

const navItems = [
  { to: "/", labelKey: "nav.writeRecord", end: true, icon: NotebookPen },
  {
    to: "/timeline",
    labelKey: "nav.allRecords",
    end: false,
    icon: Library,
  },
] as const satisfies readonly {
  to: string;
  labelKey: TranslationKey;
  end: boolean;
  icon: typeof NotebookPen;
}[];

const newAskClass =
  "flex h-11 w-full items-center gap-2.5 rounded-lg border border-gray-200 bg-white px-3 font-medium text-gray-800 text-sm shadow-sm shadow-gray-900/[0.03] transition-colors hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800 dark:focus-visible:ring-gray-500/40";

function navClass({ isActive }: { isActive: boolean }): string {
  const base =
    "flex h-10 items-center gap-2.5 rounded-lg px-3 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40";
  return isActive
    ? `${base} bg-white font-medium text-gray-900 shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800 dark:text-gray-50 dark:shadow-black/10`
    : `${base} text-gray-600 hover:bg-white/70 hover:text-gray-900 dark:text-gray-300 dark:hover:bg-gray-900 dark:hover:text-gray-50`;
}

export function Wordmark({
  onClick,
  compact = false,
}: {
  onClick?: () => void;
  compact?: boolean;
}) {
  const { t } = useI18n();
  return (
    <Link
      to="/"
      onClick={onClick}
      className="-my-1 flex h-10 items-center gap-2 rounded-lg px-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40"
    >
      <img src="/sillage-icon.svg" alt="" className="h-8 w-8 flex-none" />
      <span className="min-w-0">
        <span className="block font-semibold text-gray-900 text-lg dark:text-gray-50">
          Sillage
        </span>
        {compact ? null : (
          <span className="block text-[11px] text-gray-500 dark:text-gray-400">
            {t("app.personalRecords")}
          </span>
        )}
      </span>
    </Link>
  );
}

export function Sidebar({
  className = "",
  onNavigate,
  onCollapse,
  onClose,
  account,
  onSignOut,
}: {
  className?: string;
  onNavigate?: () => void;
  onCollapse?: () => void;
  onClose?: () => void;
  account: Account;
  onSignOut: () => void;
}) {
  const { locale, t } = useI18n();
  const location = useLocation();
  const navigate = useNavigate();
  const {
    conversations,
    loadingConversations,
    conversationsLoadError,
    activeId,
    busy,
    variantLoading,
    streaming,
    selectConversation,
    startNew,
    listConversations,
    retryConversations,
    setConversationArchived,
  } = useAsk();
  const onAskPage = location.pathname === "/ask";
  const controlsDisabled = busy || variantLoading || streaming;
  const activeIdRef = useRef(activeId);
  const pathnameRef = useRef(location.pathname);
  const routeConversationRef = useRef(
    new URLSearchParams(location.search).get("conversation"),
  );
  const accountMenuRef = useRef<HTMLDetailsElement>(null);
  const searchButtonRef = useRef<HTMLButtonElement>(null);
  const archiveViewButtonRef = useRef<HTMLButtonElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const searchAbortRef = useRef<AbortController | null>(null);
  const searchRequestRef = useRef(0);
  const archiveMutationRef = useRef(false);
  const archiveFocusPendingRef = useRef(false);
  const signOutButtonRef = useRef<HTMLButtonElement>(null);
  const signOutDialogRef = useRef<HTMLDivElement>(null);
  const stayButtonRef = useRef<HTMLButtonElement>(null);
  const signingOutRef = useRef(false);
  const [confirmingSignOut, setConfirmingSignOut] = useState(false);
  const [archivedView, setArchivedView] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [remoteConversations, setRemoteConversations] = useState<
    AskConversation[]
  >([]);
  const [loadingRemote, setLoadingRemote] = useState(false);
  const [remoteError, setRemoteError] = useState("");
  const [retryGeneration, setRetryGeneration] = useState(0);
  const [archivingId, setArchivingId] = useState<string | null>(null);
  const askControlsDisabled = controlsDisabled || archivingId !== null;
  const trimmedQuery = query.trim();
  const remoteListActive = archivedView || Boolean(trimmedQuery);
  const visibleConversations = remoteListActive
    ? remoteConversations
    : conversations;
  const listLoading = remoteListActive ? loadingRemote : loadingConversations;
  const listError = remoteListActive ? remoteError : conversationsLoadError;
  activeIdRef.current = activeId;
  pathnameRef.current = location.pathname;
  routeConversationRef.current = new URLSearchParams(location.search).get(
    "conversation",
  );

  function retryList() {
    if (remoteListActive) {
      setRetryGeneration((current) => current + 1);
      return;
    }
    retryConversations();
  }

  useEffect(() => {
    void locale;
    setRemoteError((current) => (current ? t("ask.loadFailed") : current));
  }, [locale, t]);

  useEffect(() => {
    if (searchOpen) {
      searchInputRef.current?.focus();
    }
  }, [searchOpen]);

  useEffect(() => {
    if (archivingId === null && archiveFocusPendingRef.current) {
      archiveFocusPendingRef.current = false;
      archiveViewButtonRef.current?.focus();
    }
  }, [archivingId]);

  useEffect(() => {
    void retryGeneration;
    if (!remoteListActive) {
      searchAbortRef.current?.abort();
      searchAbortRef.current = null;
      searchRequestRef.current += 1;
      setLoadingRemote(false);
      setRemoteError("");
      return;
    }

    const requestId = searchRequestRef.current + 1;
    searchRequestRef.current = requestId;
    searchAbortRef.current?.abort();
    const controller = new AbortController();
    searchAbortRef.current = controller;
    setLoadingRemote(true);
    setRemoteError("");
    const delay = trimmedQuery ? 300 : 0;
    const timeout = window.setTimeout(() => {
      void listConversations(
        { query: trimmedQuery || undefined, archived: archivedView },
        controller.signal,
      )
        .then((found) => {
          if (
            !controller.signal.aborted &&
            searchRequestRef.current === requestId
          ) {
            setRemoteConversations(found);
          }
        })
        .catch((cause) => {
          if (
            !controller.signal.aborted &&
            searchRequestRef.current === requestId
          ) {
            setRemoteError(
              cause instanceof Error ? cause.message : t("ask.loadFailed"),
            );
          }
        })
        .finally(() => {
          if (
            !controller.signal.aborted &&
            searchRequestRef.current === requestId
          ) {
            setLoadingRemote(false);
          }
        });
    }, delay);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [
    archivedView,
    listConversations,
    remoteListActive,
    retryGeneration,
    t,
    trimmedQuery,
  ]);

  useEffect(
    () => () => {
      searchRequestRef.current += 1;
      searchAbortRef.current?.abort();
    },
    [],
  );

  function closeSearch() {
    setQuery("");
    setSearchOpen(false);
    searchButtonRef.current?.focus();
  }

  async function toggleConversationArchived(conversation: AskConversation) {
    if (controlsDisabled || archiveMutationRef.current) {
      return;
    }
    const archived = !conversation.archivedAt;
    archiveMutationRef.current = true;
    setArchivingId(conversation.id);
    try {
      await setConversationArchived(conversation.id, archived);
      searchRequestRef.current += 1;
      searchAbortRef.current?.abort();
      searchAbortRef.current = null;
      setLoadingRemote(false);
      setRemoteConversations((current) =>
        current.filter((item) => item.id !== conversation.id),
      );
      if (remoteListActive) {
        setRetryGeneration((current) => current + 1);
      }
      if (
        archived &&
        activeIdRef.current === conversation.id &&
        pathnameRef.current === "/ask" &&
        (routeConversationRef.current === null ||
          routeConversationRef.current === conversation.id)
      ) {
        activeIdRef.current = "";
        startNew();
        navigate("/ask");
        onNavigate?.();
      } else {
        archiveFocusPendingRef.current = true;
      }
    } catch {
      // The context keeps the row intact and presents the server error in a toast.
    } finally {
      archiveMutationRef.current = false;
      setArchivingId(null);
    }
  }

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
      if (
        !confirmingSignOut &&
        menu?.open &&
        !menu.contains(event.target as Node)
      ) {
        close();
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !confirmingSignOut) {
        close();
      }
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [confirmingSignOut]);

  useEffect(() => {
    if (!confirmingSignOut) {
      return;
    }
    signingOutRef.current = false;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    stayButtonRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        setConfirmingSignOut(false);
        return;
      }
      if (event.key !== "Tab" || !signOutDialogRef.current) {
        return;
      }
      const focusable = signOutDialogRef.current.querySelectorAll<HTMLElement>(
        "button:not([disabled])",
      );
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) {
        return;
      }
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
      if (!signingOutRef.current) {
        signOutButtonRef.current?.focus();
      }
    };
  }, [confirmingSignOut]);

  function requestSignOut() {
    if (hasUnsavedChanges()) {
      setConfirmingSignOut(true);
      return;
    }
    if (accountMenuRef.current) {
      accountMenuRef.current.open = false;
    }
    onSignOut();
  }

  return (
    <aside
      className={`flex min-h-0 flex-col border-gray-200/80 border-r bg-gray-100 px-3 pt-[max(1rem,env(safe-area-inset-top))] pb-[max(1rem,env(safe-area-inset-bottom))] dark:border-gray-800 dark:bg-gray-950 ${className}`}
    >
      <div className="flex items-center justify-between gap-2 pb-3">
        <Wordmark onClick={onNavigate} />
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            aria-label={t("nav.close")}
            title={t("nav.close")}
            data-drawer-initial-focus
            className="flex h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-500 transition hover:bg-white hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
          >
            <X className="h-5 w-5" />
          </button>
        ) : onCollapse ? (
          <button
            type="button"
            onClick={onCollapse}
            aria-label={t("nav.collapseSidebar")}
            title={t("nav.collapseSidebar")}
            className="hidden h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-white hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 lg:flex dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
          >
            <PanelLeftClose className="h-4 w-4" />
          </button>
        ) : null}
      </div>

      <div className="space-y-1">
        {askControlsDisabled ? (
          <button
            type="button"
            disabled
            className={`${newAskClass} cursor-not-allowed opacity-50`}
          >
            <MessageSquarePlus className="h-4 w-4" />
            <span>{t("ask.start")}</span>
          </button>
        ) : (
          <Link
            to="/ask"
            onClick={() => {
              activeIdRef.current = "";
              startNew();
              onNavigate?.();
            }}
            className={newAskClass}
          >
            <MessageSquarePlus className="h-4 w-4" />
            <span>{t("ask.start")}</span>
          </Link>
        )}

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
                <span>{t(item.labelKey)}</span>
              </NavLink>
            );
          })}
        </nav>
      </div>

      <section className="mt-4 flex min-h-0 flex-1 flex-col border-gray-200/80 border-t pt-3 dark:border-gray-800">
        <div className="flex min-h-10 items-center justify-between gap-2 pl-3">
          <h2 className="font-medium text-gray-500 text-xs dark:text-gray-500">
            {t(archivedView ? "ask.archivedSection" : "ask.section")}
          </h2>
          <div className="flex items-center">
            <button
              ref={searchButtonRef}
              type="button"
              onClick={() => {
                if (searchOpen) {
                  closeSearch();
                } else {
                  setSearchOpen(true);
                }
              }}
              disabled={askControlsDisabled}
              aria-label={t(searchOpen ? "ask.collapseSearch" : "ask.search")}
              aria-expanded={searchOpen}
              title={t(searchOpen ? "ask.collapseSearchTitle" : "ask.search")}
              className="flex h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-white/70 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
            >
              <Search className="h-4 w-4" aria-hidden="true" />
            </button>
            <button
              ref={archiveViewButtonRef}
              type="button"
              onClick={() => {
                setRemoteConversations([]);
                setRemoteError("");
                setArchivedView((current) => !current);
              }}
              disabled={askControlsDisabled}
              aria-label={t(
                archivedView ? "ask.backToAsk" : "ask.viewArchived",
              )}
              aria-pressed={archivedView}
              title={t(archivedView ? "ask.backToAsk" : "ask.viewArchived")}
              className={`flex h-10 w-10 flex-none items-center justify-center rounded-lg transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:focus-visible:ring-gray-500/40 ${
                archivedView
                  ? "bg-white text-gray-900 shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800 dark:text-gray-50"
                  : "text-gray-500 hover:bg-white/70 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-50"
              }`}
            >
              {archivedView ? (
                <ArchiveRestore className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Archive className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
          </div>
        </div>

        {searchOpen ? (
          <div className="relative mt-1 px-1">
            <Search
              className="pointer-events-none absolute top-1/2 left-4 h-4 w-4 -translate-y-1/2 text-gray-400"
              aria-hidden="true"
            />
            <input
              ref={searchInputRef}
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Escape") {
                  event.preventDefault();
                  event.stopPropagation();
                  closeSearch();
                }
              }}
              disabled={askControlsDisabled}
              aria-label={t("ask.search")}
              placeholder={t("ask.searchPlaceholder")}
              className="h-10 w-full rounded-lg border border-gray-200 bg-white pr-10 pl-9 text-gray-900 text-sm transition placeholder:text-gray-400 focus:border-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-300/55 disabled:bg-gray-100 disabled:text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-50 dark:placeholder:text-gray-500 dark:focus:border-gray-500 dark:focus:ring-gray-600/50 dark:disabled:bg-gray-800"
            />
            {query ? (
              <button
                type="button"
                onClick={() => {
                  setQuery("");
                  searchInputRef.current?.focus();
                }}
                disabled={askControlsDisabled}
                aria-label={t("ask.clearSearch")}
                title={t("ask.clearSearchTitle")}
                className="absolute top-0 right-1 flex h-10 w-10 items-center justify-center rounded-lg text-gray-500 transition-colors hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:text-gray-400 dark:hover:text-gray-50"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            ) : null}
          </div>
        ) : null}

        <nav className="mt-2 min-h-0 flex-1 space-y-0.5 overflow-y-auto pr-1">
          {listLoading && visibleConversations.length === 0 ? (
            <div className="space-y-2 px-3 py-2" role="status">
              <span className="sr-only">
                {t(trimmedQuery ? "ask.searching" : "ask.loading")}
              </span>
              <div className="h-3 w-4/5 animate-pulse rounded bg-gray-200 dark:bg-gray-800" />
              <div className="h-3 w-3/5 animate-pulse rounded bg-gray-200 dark:bg-gray-800" />
            </div>
          ) : listError && visibleConversations.length === 0 ? (
            <div className="space-y-2 px-3 py-2 text-sm">
              <p role="alert" className="text-red-600 dark:text-red-400">
                {listError}
              </p>
              <button
                type="button"
                onClick={retryList}
                className="rounded-md text-gray-600 text-xs transition hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-300 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
              >
                {t("common.retry")}
              </button>
            </div>
          ) : visibleConversations.length === 0 ? (
            <p className="px-3 py-2 text-gray-400 text-sm">
              {trimmedQuery
                ? t("ask.noSearchResults")
                : archivedView
                  ? t("ask.noArchived")
                  : t("ask.noConversations")}
            </p>
          ) : (
            <>
              {listError ? (
                <div className="mb-1 flex items-center gap-2 px-3 py-1 text-xs">
                  <p
                    role="alert"
                    className="min-w-0 flex-1 text-red-600 dark:text-red-400"
                  >
                    {listError}
                  </p>
                  <button
                    type="button"
                    onClick={retryList}
                    className="flex-none rounded-md text-gray-600 transition hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-300 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
                  >
                    {t("common.retry")}
                  </button>
                </div>
              ) : null}
              {visibleConversations.map((conversation) => {
                const active = onAskPage && activeId === conversation.id;
                const label = conversation.title || t("ask.newConversation");
                const archived = Boolean(conversation.archivedAt);
                const conversationTargetClass = `h-10 min-w-0 flex-1 truncate rounded-lg px-3 py-2.5 text-left text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40 ${
                  active
                    ? "font-medium text-gray-900 dark:text-gray-50"
                    : "text-gray-600 hover:text-gray-900 dark:text-gray-300 dark:hover:text-gray-50"
                }`;
                return (
                  <div
                    key={conversation.id}
                    className={`flex min-w-0 items-center rounded-lg transition-colors ${
                      active
                        ? "bg-white shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800"
                        : "hover:bg-white/70 dark:hover:bg-gray-900"
                    }`}
                  >
                    {askControlsDisabled ? (
                      <button
                        type="button"
                        disabled
                        aria-current={active ? "page" : undefined}
                        title={label}
                        className={`${conversationTargetClass} cursor-not-allowed opacity-50`}
                      >
                        {label}
                      </button>
                    ) : (
                      <Link
                        to={`/ask?conversation=${conversation.id}`}
                        onClick={() => {
                          activeIdRef.current = conversation.id;
                          selectConversation(conversation.id, conversation);
                          onNavigate?.();
                        }}
                        aria-current={active ? "page" : undefined}
                        title={label}
                        className={conversationTargetClass}
                      >
                        {label}
                      </Link>
                    )}
                    <button
                      type="button"
                      onClick={() =>
                        void toggleConversationArchived(conversation)
                      }
                      disabled={askControlsDisabled}
                      aria-label={`${t(archived ? "ask.unarchive" : "ask.archive")}：${label}`}
                      title={t(archived ? "ask.unarchive" : "ask.archive")}
                      className="flex h-10 w-10 flex-none items-center justify-center rounded-lg text-gray-400 transition-colors hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:text-gray-500 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
                    >
                      {archived ? (
                        <ArchiveRestore
                          className={`h-4 w-4 ${archivingId === conversation.id ? "animate-pulse" : ""}`}
                          aria-hidden="true"
                        />
                      ) : (
                        <Archive
                          className={`h-4 w-4 ${archivingId === conversation.id ? "animate-pulse" : ""}`}
                          aria-hidden="true"
                        />
                      )}
                    </button>
                  </div>
                );
              })}
            </>
          )}
        </nav>
      </section>

      <div className="mt-3 flex items-center border-gray-200/80 border-t pt-3 dark:border-gray-800">
        <details ref={accountMenuRef} className="group relative min-w-0 flex-1">
          <summary className="flex h-10 min-w-0 cursor-pointer list-none items-center gap-2 rounded-lg px-2 transition hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:hover:bg-gray-900 dark:focus-visible:ring-gray-500/40">
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
          <div className="absolute right-0 bottom-full z-20 mb-2 w-44 rounded-xl border border-gray-200 bg-white p-1.5 shadow-lg shadow-gray-900/10 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/30">
            <Link
              to="/settings"
              onClick={() => {
                if (accountMenuRef.current) {
                  accountMenuRef.current.open = false;
                }
                onNavigate?.();
              }}
              className="flex h-10 items-center gap-2 rounded-lg px-3 text-gray-700 text-sm transition-colors hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-200 dark:hover:bg-gray-800 dark:hover:text-gray-50"
            >
              <Settings className="h-4 w-4" />
              {t("nav.settings")}
            </Link>
            <button
              ref={signOutButtonRef}
              type="button"
              onClick={requestSignOut}
              className="flex h-10 w-full items-center gap-2 rounded-lg px-3 text-left text-red-600 text-sm transition-colors hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500/30 dark:text-red-400 dark:hover:bg-red-950/30"
            >
              <LogOut className="h-4 w-4" />
              {t("nav.signOut")}
            </button>
          </div>
        </details>
        <ThemeToggle compact />
      </div>
      {confirmingSignOut
        ? createPortal(
            <div className="fixed inset-0 z-[80] grid place-items-center px-4">
              <button
                type="button"
                aria-label={t("nav.keepEditing")}
                className="absolute inset-0 h-full w-full bg-gray-950/35 dark:bg-gray-950/70"
                onClick={() => setConfirmingSignOut(false)}
              />
              <div
                ref={signOutDialogRef}
                role="alertdialog"
                aria-modal="true"
                aria-labelledby="sign-out-confirmation-title"
                aria-describedby="sign-out-confirmation-description"
                className="surface-enter relative w-full max-w-sm rounded-xl border border-gray-200 bg-white p-5 shadow-xl shadow-gray-950/15 dark:border-gray-700 dark:bg-gray-900 dark:shadow-black/35"
              >
                <h2
                  id="sign-out-confirmation-title"
                  className="font-semibold text-gray-900 text-lg dark:text-gray-50"
                >
                  {t("nav.signOutTitle")}
                </h2>
                <p
                  id="sign-out-confirmation-description"
                  className="mt-2 text-gray-500 text-sm leading-6 dark:text-gray-400"
                >
                  {t("nav.signOutDescription")}
                </p>
                <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
                  <button
                    ref={stayButtonRef}
                    type="button"
                    className={secondaryButtonClass}
                    onClick={() => setConfirmingSignOut(false)}
                  >
                    {t("nav.keepEditing")}
                  </button>
                  <button
                    type="button"
                    className={dangerButtonClass}
                    onClick={() => {
                      signingOutRef.current = true;
                      setConfirmingSignOut(false);
                      if (accountMenuRef.current) {
                        accountMenuRef.current.open = false;
                      }
                      onSignOut();
                    }}
                  >
                    {t("nav.signOutAnyway")}
                  </button>
                </div>
              </div>
            </div>,
            document.body,
          )
        : null}
    </aside>
  );
}
