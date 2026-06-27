import type { ReactNode } from "react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  helperTextClass,
  inputClass,
  labelClass,
  primaryButtonClass,
} from "../components/ui";
import { type Account, initializeAccount, signIn } from "../lib/api";

function AuthSurface({
  title,
  lead,
  children,
}: {
  title: string;
  lead?: string;
  children: ReactNode;
}) {
  return (
    <main className="grid min-h-screen place-items-center bg-gray-50 px-4 dark:bg-gray-950">
      <section className="w-full max-w-sm rounded-2xl border border-gray-200/80 bg-white/85 p-6 shadow-xl shadow-gray-900/[0.06] backdrop-blur dark:border-gray-800 dark:bg-gray-900/80 dark:shadow-black/20">
        <p className="font-semibold text-lg text-gray-900 tracking-tight dark:text-gray-50">
          Sillage
        </p>
        <h1 className="mt-4 font-semibold text-xl text-gray-900 dark:text-gray-50">
          {title}
        </h1>
        {lead ? <p className={helperTextClass}>{lead}</p> : null}
        <div className="mt-5">{children}</div>
      </section>
    </main>
  );
}

function Field({
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
    <label className="block">
      <span className={labelClass}>{label}</span>
      <input
        className={inputClass}
        type={type}
        value={value}
        autoComplete={autoComplete}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

export function InitializePage({
  onDone,
}: {
  onDone: (token: string, account: Account) => void;
}) {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      const res = await initializeAccount({ username, displayName, password });
      onDone(res.accessToken, res.account);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "初始化失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthSurface
      title="创建唯一账号"
      lead="这是你的私密记录空间，仅此一个账号。"
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <Field
          label="账号"
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <Field
          label="显示名"
          value={displayName}
          onChange={setDisplayName}
          autoComplete="name"
        />
        <Field
          label="密码"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
        />
        {error ? (
          <p className="text-red-600 text-sm dark:text-red-400">{error}</p>
        ) : null}
        <button
          type="submit"
          disabled={busy}
          className={`${primaryButtonClass} w-full`}
        >
          {busy ? "创建中…" : "创建并进入"}
        </button>
      </form>
    </AuthSurface>
  );
}

export function LoginPage({
  onDone,
}: {
  onDone: (token: string, account: Account) => void;
}) {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      const res = await signIn({ username, password });
      onDone(res.accessToken, res.account);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthSurface title="登录 Sillage">
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <Field
          label="账号"
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <Field
          label="密码"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />
        {error ? (
          <p className="text-red-600 text-sm dark:text-red-400">{error}</p>
        ) : null}
        <button
          type="submit"
          disabled={busy}
          className={`${primaryButtonClass} w-full`}
        >
          {busy ? "登录中…" : "登录"}
        </button>
      </form>
    </AuthSurface>
  );
}

export function FullPageState({ text }: { text: string }) {
  return (
    <main className="grid min-h-screen place-items-center bg-gray-50 text-gray-500 dark:bg-gray-950 dark:text-gray-400">
      {text}
    </main>
  );
}
