import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useId, useState } from "react";
import { useNavigate } from "react-router-dom";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import {
  helperTextClass,
  inputClass,
  labelClass,
  primaryButtonClass,
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import { type Account, initializeAccount, signIn } from "../../lib/api";

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
      <section className="surface-enter w-full max-w-sm rounded-xl border border-gray-200/80 bg-white/90 p-6 shadow-xl shadow-gray-900/[0.06] backdrop-blur dark:border-gray-800 dark:bg-gray-900/85 dark:shadow-black/20">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <img
              src="/sillage-icon.svg"
              alt=""
              className="h-8 w-8"
              aria-hidden="true"
            />
            <p className="font-semibold text-lg text-gray-900 dark:text-gray-50">
              Sillage
            </p>
          </div>
          <LanguageSwitcher compact />
        </div>
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
  required = true,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
  required?: boolean;
}) {
  const { t } = useI18n();
  const [showPassword, setShowPassword] = useState(false);
  const id = useId();
  const password = type === "password";
  return (
    <div>
      <label htmlFor={id} className={labelClass}>
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          className={`${inputClass} ${password ? "pr-11" : ""}`}
          type={password && showPassword ? "text" : type}
          value={value}
          autoComplete={autoComplete}
          required={required}
          onChange={(event) => onChange(event.target.value)}
        />
        {password ? (
          <button
            type="button"
            onClick={() => setShowPassword((current) => !current)}
            className="absolute right-0 bottom-0 inline-flex h-10 w-10 items-center justify-center rounded-lg text-gray-400 transition-colors hover:text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:text-gray-500 dark:hover:text-gray-200"
            aria-label={t(
              showPassword ? "auth.hidePassword" : "auth.showPassword",
            )}
            title={t(showPassword ? "auth.hidePassword" : "auth.showPassword")}
          >
            {showPassword ? (
              <EyeOff className="h-4 w-4" />
            ) : (
              <Eye className="h-4 w-4" />
            )}
          </button>
        ) : null}
      </div>
    </div>
  );
}

export function InitializePage({
  onDone,
}: {
  onDone: (token: string, account: Account) => void;
}) {
  const { locale, t } = useI18n();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void locale;
    setError((current) => (current ? t("auth.initializeFailed") : current));
  }, [locale, t]);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      const res = await initializeAccount({ username, displayName, password });
      onDone(res.accessToken, res.account);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : t("auth.initializeFailed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthSurface
      title={t("auth.initializeTitle")}
      lead={t("auth.initializeLead")}
    >
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <Field
          label={t("auth.account")}
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <Field
          label={t("auth.displayName")}
          value={displayName}
          onChange={setDisplayName}
          autoComplete="name"
          required={false}
        />
        <Field
          label={t("auth.password")}
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
        />
        {error ? (
          <p role="alert" className="text-red-600 text-sm dark:text-red-400">
            {error}
          </p>
        ) : null}
        <button
          type="submit"
          disabled={busy}
          className={`${primaryButtonClass} w-full`}
        >
          {busy ? (
            <>
              <LoaderCircle className="h-4 w-4 animate-spin" />
              {t("auth.initializing")}
            </>
          ) : (
            t("auth.initializeAction")
          )}
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
  const { locale, t } = useI18n();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void locale;
    setError((current) => (current ? t("auth.loginFailed") : current));
  }, [locale, t]);

  async function submit() {
    setBusy(true);
    setError("");
    try {
      const res = await signIn({ username, password });
      onDone(res.accessToken, res.account);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : t("auth.loginFailed"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthSurface title={t("auth.loginTitle")}>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <Field
          label={t("auth.account")}
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <Field
          label={t("auth.password")}
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />
        {error ? (
          <p role="alert" className="text-red-600 text-sm dark:text-red-400">
            {error}
          </p>
        ) : null}
        <button
          type="submit"
          disabled={busy}
          className={`${primaryButtonClass} w-full`}
        >
          {busy ? (
            <>
              <LoaderCircle className="h-4 w-4 animate-spin" />
              {t("auth.loggingIn")}
            </>
          ) : (
            t("auth.loginAction")
          )}
        </button>
      </form>
    </AuthSurface>
  );
}

export function FullPageState({ text }: { text: string }) {
  return (
    <main className="grid min-h-screen place-items-center bg-gray-50 text-gray-500 dark:bg-gray-950 dark:text-gray-400">
      <div className="flex flex-col items-center gap-3" role="status">
        <img src="/sillage-icon.svg" alt="" className="h-10 w-10" />
        <span className="inline-flex items-center gap-2 text-sm">
          <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
          {text}
        </span>
      </div>
    </main>
  );
}
