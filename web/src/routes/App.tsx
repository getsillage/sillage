import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "../components/AppShell";
import { type Account, getBootstrap, getMe, signOut } from "../lib/api";
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeAccessToken,
} from "../lib/auth";
import { AskProvider } from "../state/AskContext";
import { MemosProvider } from "../state/MemosContext";
import { AskPage } from "./AskPage";
import { FullPageState, InitializePage, LoginPage } from "./AuthPages";
import { EntryPage } from "./EntryPage";
import { HomePage } from "./HomePage";
import { SettingsPage } from "./SettingsPage";
import { TimelinePage } from "./TimelinePage";

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
    } catch {
      // Even if the request fails, drop local credentials and return to login.
    } finally {
      clearAccessToken();
    }
  }

  if (bootstrap === "loading" || (bootstrap === "ready" && !authResolved)) {
    return <FullPageState text="正在打开 Sillage" />;
  }

  const needsInit = bootstrap === "needs-init";
  const authed = Boolean(account && token);

  return (
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
  );
}
