import {
  BookOpenText,
  History,
  LogIn,
  MessageSquareText,
  Settings,
} from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import {
  type Account,
  getBootstrap,
  getMe,
  initializeAccount,
  signIn,
} from "../lib/api";
import { getAccessToken, saveAccessToken } from "../lib/auth";

type BootstrapState = "loading" | "needs-init" | "ready";

export function App() {
  const [bootstrap, setBootstrap] = useState<BootstrapState>("loading");
  const [account, setAccount] = useState<Account | null>(null);
  const [token, setToken] = useState(() => getAccessToken());

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const state = await getBootstrap();
      if (cancelled) {
        return;
      }
      setBootstrap(state.initialized ? "ready" : "needs-init");
      if (state.initialized && token) {
        try {
          const me = await getMe(token);
          if (!cancelled) {
            setAccount(me.account);
          }
        } catch {
          if (!cancelled) {
            setAccount(null);
          }
        }
      }
    }
    load().catch(() => setBootstrap("ready"));
    return () => {
      cancelled = true;
    };
  }, [token]);

  if (bootstrap === "loading") {
    return <FullPageState text="正在打开 Sillage" />;
  }

  return (
    <Routes>
      <Route
        path="/initialize"
        element={
          bootstrap === "needs-init" ? (
            <InitializePage
              onDone={(nextToken, nextAccount) => {
                saveAccessToken(nextToken);
                setToken(nextToken);
                setAccount(nextAccount);
                setBootstrap("ready");
              }}
            />
          ) : (
            <Navigate to="/" replace />
          )
        }
      />
      <Route
        path="/login"
        element={
          bootstrap === "needs-init" ? (
            <Navigate to="/initialize" replace />
          ) : (
            <LoginPage
              onDone={(nextToken, nextAccount) => {
                saveAccessToken(nextToken);
                setToken(nextToken);
                setAccount(nextAccount);
              }}
            />
          )
        }
      />
      <Route
        path="*"
        element={
          bootstrap === "needs-init" ? (
            <Navigate to="/initialize" replace />
          ) : account ? (
            <Shell account={account} />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
    </Routes>
  );
}

function InitializePage({
  onDone,
}: {
  onDone: (token: string, account: Account) => void;
}) {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  return (
    <AuthSurface title="创建唯一账号" icon={<LogIn size={20} />}>
      <form
        className="auth-form"
        onSubmit={async (event) => {
          event.preventDefault();
          setError("");
          try {
            const res = await initializeAccount({
              username,
              displayName,
              password,
            });
            onDone(res.accessToken, res.account);
            navigate("/", { replace: true });
          } catch (err) {
            setError(err instanceof Error ? err.message : "初始化失败");
          }
        }}
      >
        <TextInput
          label="账号"
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <TextInput
          label="显示名"
          value={displayName}
          onChange={setDisplayName}
          autoComplete="name"
        />
        <TextInput
          label="密码"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
        />
        {error && <p className="form-error">{error}</p>}
        <button className="primary-button" type="submit">
          创建并进入
        </button>
      </form>
    </AuthSurface>
  );
}

function LoginPage({
  onDone,
}: {
  onDone: (token: string, account: Account) => void;
}) {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  return (
    <AuthSurface title="登录 Sillage" icon={<LogIn size={20} />}>
      <form
        className="auth-form"
        onSubmit={async (event) => {
          event.preventDefault();
          setError("");
          try {
            const res = await signIn({ username, password });
            onDone(res.accessToken, res.account);
            navigate("/", { replace: true });
          } catch (err) {
            setError(err instanceof Error ? err.message : "登录失败");
          }
        }}
      >
        <TextInput
          label="账号"
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <TextInput
          label="密码"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />
        {error && <p className="form-error">{error}</p>}
        <button className="primary-button" type="submit">
          登录
        </button>
      </form>
    </AuthSurface>
  );
}

function Shell({ account }: { account: Account }) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">Sillage</div>
        <nav className="nav-list">
          <a className="nav-item active" href="/">
            <BookOpenText size={18} />
            记录
          </a>
          <a className="nav-item" href="/history">
            <History size={18} />
            历史
          </a>
          <a className="nav-item" href="/ask">
            <MessageSquareText size={18} />
            问答
          </a>
        </nav>
        <div className="account-card">
          <span>{account.displayName || account.username}</span>
          <Settings size={16} />
        </div>
      </aside>
      <main className="workspace">
        <section className="memo-composer">
          <p className="date-label">{new Date().toLocaleDateString("zh-CN")}</p>
          <textarea placeholder="写下想记录的内容…" />
          <div className="composer-actions">
            <button className="secondary-button" type="button">
              保存
            </button>
          </div>
        </section>
        <section className="empty-list">还没有记录。可以先写一条记录。</section>
      </main>
    </div>
  );
}

function AuthSurface({
  title,
  icon,
  children,
}: {
  title: string;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <main className="auth-page">
      <section className="auth-panel">
        <div className="auth-title">
          {icon}
          <h1>{title}</h1>
        </div>
        {children}
      </section>
    </main>
  );
}

function TextInput({
  label,
  value,
  onChange,
  type = "text",
  autoComplete,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        autoComplete={autoComplete}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function FullPageState({ text }: { text: string }) {
  return <main className="full-page-state">{text}</main>;
}
