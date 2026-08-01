import { lazy, Suspense, useEffect, useRef, useState } from "react";
import { Navigate, Route, Routes } from "react-router";
import { useToast } from "../components/Toast";
import { AskProvider } from "../features/ask/AskContext";
import {
  FullPageErrorState,
  FullPageState,
  InitializePage,
  LoginPage,
} from "../features/auth/AuthPages";
import { MemosProvider } from "../features/memos/MemosContext";
import { useI18n } from "../i18n/I18nProvider";
import {
  type Account,
  ApiError,
  type BootstrapInfo,
  getBootstrap,
  getMe,
  signOut,
} from "../lib/api";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeAccessToken,
} from "../lib/auth";
import { AppShell } from "./AppShell";
import { RouteAccessibility } from "./RouteAccessibility";

const HomePage = lazy(() =>
  import("../features/memos/HomePage").then((module) => ({
    default: module.HomePage,
  })),
);
const TimelinePage = lazy(() =>
  import("../features/memos/TimelinePage").then((module) => ({
    default: module.TimelinePage,
  })),
);
const EntryPage = lazy(() =>
  import("../features/memos/EntryPage").then((module) => ({
    default: module.EntryPage,
  })),
);
const AskPage = lazy(() =>
  import("../features/ask/AskPage").then((module) => ({
    default: module.AskPage,
  })),
);
const SettingsPage = lazy(() =>
  import("../features/settings/SettingsPage").then((module) => ({
    default: module.SettingsPage,
  })),
);

type BootstrapState = "loading" | "needs-init" | "ready" | "error";

function AuthedArea({
  account,
  token,
  onSignOut,
}: {
  account: Account;
  token: string;
  onSignOut: () => void;
}) {
  return (
    <MemosProvider token={token}>
      <AskProvider token={token}>
        <AppShell account={account} onSignOut={onSignOut} />
      </AskProvider>
    </MemosProvider>
  );
}

export function App() {
  const { t } = useI18n();
  const toast = useToast();
  const [bootstrap, setBootstrap] = useState<BootstrapState>("loading");
  const [buildInfo, setBuildInfo] = useState<BootstrapInfo | null>(null);
  const [account, setAccount] = useState<Account | null>(null);
  const [token, setToken] = useState(() => getAccessToken());
  const [authResolved, setAuthResolved] = useState(false);
  const [bootstrapAttempt, setBootstrapAttempt] = useState(0);
  // Once the session is resolved (signed in or definitely signed out), later
  // token refreshes must not replay bootstrap/getMe or kick the user out.
  const accountResolvedRef = useRef(false);

  useEffect(
    () =>
      subscribeAccessToken((next) => {
        setToken(next);
        if (!next) {
          setAccount(null);
        }
      }),
    [],
  );

  // biome-ignore lint/correctness/useExhaustiveDependencies: bootstrapAttempt is the explicit retry trigger
  useEffect(() => {
    if (accountResolvedRef.current) {
      return;
    }
    let cancelled = false;
    async function load() {
      const state = await getBootstrap();
      if (cancelled) {
        return;
      }
      if (!state.initialized) {
        setBuildInfo(state);
        setBootstrap("needs-init");
        setAuthResolved(true);
        return;
      }
      setBuildInfo(state);
      setBootstrap("ready");
      try {
        // Access tokens are memory-only, but a reload may still hold a valid
        // HttpOnly refresh cookie; request() transparently refreshes and retries.
        const me = await getMe(getAccessToken() ?? "");
        if (!cancelled) {
          accountResolvedRef.current = true;
          setAccount(me.account);
        }
      } catch (cause) {
        if (cancelled) {
          return;
        }
        // Only a definite 401 (the refresh also failed) means signed out;
        // transient network or server errors keep the current session state.
        if (cause instanceof ApiError && cause.status === 401) {
          accountResolvedRef.current = true;
          setAccount(null);
        }
      } finally {
        if (!cancelled) {
          setAuthResolved(true);
        }
      }
    }
    load().catch(() => {
      if (!cancelled) {
        setBootstrap("error");
      }
    });
    return () => {
      cancelled = true;
    };
  }, [bootstrapAttempt]);

  function handleAuthed(nextToken: string, nextAccount: Account) {
    accountResolvedRef.current = true;
    setAccessToken(nextToken);
    setAccount(nextAccount);
    setAuthResolved(true);
    setBootstrap("ready");
  }

  async function handleSignOut() {
    try {
      await signOut();
      toast.showToast({ kind: "success", message: t("auth.signedOut") });
    } catch {
      toast.showToast({
        kind: "error",
        message: t("auth.signOutLocalOnly"),
      });
    } finally {
      clearAccessToken();
    }
  }

  if (bootstrap === "error") {
    return (
      <FullPageErrorState
        text={t("app.bootstrapFailed")}
        retryLabel={t("common.retry")}
        onRetry={() => {
          setBootstrap("loading");
          setBootstrapAttempt((current) => current + 1);
        }}
      />
    );
  }

  if (bootstrap === "loading" || (bootstrap === "ready" && !authResolved)) {
    return <FullPageState text={t("app.opening")} />;
  }

  const needsInit = bootstrap === "needs-init";
  const authed = Boolean(account && token);

  return (
    <>
      <RouteAccessibility />
      <Suspense fallback={<FullPageState text={t("app.opening")} />}>
        <Routes>
          <Route
            path="/initialize"
            element={
              needsInit ? (
                <InitializePage onDone={handleAuthed} />
              ) : (
                <Navigate to="/" replace />
              )
            }
          />
          <Route
            path="/login"
            element={
              needsInit ? (
                <Navigate to="/initialize" replace />
              ) : (
                <LoginPage onDone={handleAuthed} />
              )
            }
          />
          {authed && account && token ? (
            <Route
              element={
                <AuthedArea
                  account={account}
                  token={token}
                  onSignOut={handleSignOut}
                />
              }
            >
              <Route index element={<HomePage />} />
              <Route path="timeline" element={<TimelinePage />} />
              <Route path="entries/:id" element={<EntryPage />} />
              <Route path="ask" element={<AskPage />} />
              {/* Legacy path: 照见/回顾 became 问答. */}
              <Route path="review" element={<Navigate to="/ask" replace />} />
              <Route
                path="settings"
                element={<SettingsPage token={token} buildInfo={buildInfo} />}
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          ) : (
            <Route
              path="*"
              element={
                <Navigate to={needsInit ? "/initialize" : "/login"} replace />
              }
            />
          )}
        </Routes>
      </Suspense>
    </>
  );
}
