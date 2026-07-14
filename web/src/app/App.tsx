import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useToast } from "../components/Toast";
import { AskProvider } from "../features/ask/AskContext";
import { AskPage } from "../features/ask/AskPage";
import {
  FullPageState,
  InitializePage,
  LoginPage,
} from "../features/auth/AuthPages";
import { EntryPage } from "../features/memos/EntryPage";
import { HomePage } from "../features/memos/HomePage";
import { MemosProvider } from "../features/memos/MemosContext";
import { TimelinePage } from "../features/memos/TimelinePage";
import { SettingsPage } from "../features/settings/SettingsPage";
import { useI18n } from "../i18n/I18nProvider";
import { type Account, getBootstrap, getMe, signOut } from "../lib/api";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeAccessToken,
} from "../lib/auth";
import { AppShell } from "./AppShell";
import { RouteAccessibility } from "./RouteAccessibility";

type BootstrapState = "loading" | "needs-init" | "ready";

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
  const [account, setAccount] = useState<Account | null>(null);
  const [token, setToken] = useState(() => getAccessToken());
  const [authResolved, setAuthResolved] = useState(false);

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

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const state = await getBootstrap();
      if (cancelled) {
        return;
      }
      if (!state.initialized) {
        setBootstrap("needs-init");
        setAuthResolved(true);
        return;
      }
      setBootstrap("ready");
      try {
        // A reopened tab has empty sessionStorage but may still hold a valid
        // refresh cookie; request() transparently refreshes and retries on 401.
        const me = await getMe(token ?? "");
        if (!cancelled) {
          setAccount(me.account);
        }
      } catch {
        if (!cancelled) {
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
        setBootstrap("ready");
        setAuthResolved(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [token]);

  function handleAuthed(nextToken: string, nextAccount: Account) {
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

  if (bootstrap === "loading" || (bootstrap === "ready" && !authResolved)) {
    return <FullPageState text={t("app.opening")} />;
  }

  const needsInit = bootstrap === "needs-init";
  const authed = Boolean(account && token);

  return (
    <>
      <RouteAccessibility />
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
            <Route path="settings" element={<SettingsPage token={token} />} />
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
    </>
  );
}
