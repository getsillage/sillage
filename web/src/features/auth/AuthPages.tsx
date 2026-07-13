import { CircleAlert, Eye, EyeOff, LoaderCircle } from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { useToast } from "../../components/Toast";
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
  controlsDisabled = false,
  children,
}: {
  title: string;
  lead?: string;
  controlsDisabled?: boolean;
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
          <LanguageSwitcher compact disabled={controlsDisabled} />
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

function AuthError({ id, message }: { id: string; message: string }) {
  return (
    <div
      id={id}
      role="alert"
      className="flex items-start gap-2.5 rounded-lg border border-red-200 bg-red-50/70 px-3 py-2.5 text-red-700 text-sm dark:border-red-900/60 dark:bg-red-950/25 dark:text-red-300"
    >
      <CircleAlert className="mt-0.5 h-4 w-4 flex-none" aria-hidden="true" />
      <p className="min-w-0 leading-5">{message}</p>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  autoComplete,
  required = true,
  disabled = false,
  describedBy,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
  required?: boolean;
  disabled?: boolean;
  describedBy?: string;
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
          disabled={disabled}
          aria-describedby={describedBy}
          spellCheck={password ? false : undefined}
          autoCapitalize={password ? "none" : undefined}
          onChange={(event) => onChange(event.target.value)}
        />
        {password ? (
          <button
            type="button"
            disabled={disabled}
            onClick={() => setShowPassword((current) => !current)}
            className="absolute right-0 bottom-0 inline-flex h-10 w-10 items-center justify-center rounded-lg text-gray-500 transition-colors hover:text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 disabled:cursor-not-allowed disabled:opacity-50 dark:text-gray-400 dark:hover:text-gray-200"
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
  const toast = useToast();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const errorId = useId();

  useEffect(() => {
    void locale;
    setError((current) => (current ? t("auth.initializeFailed") : current));
  }, [locale, t]);

  async function submit() {
    if (busyRef.current) {
      return;
    }
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      const res = await initializeAccount({ username, displayName, password });
      toast.showToast({ kind: "success", message: t("auth.initialized") });
      onDone(res.accessToken, res.account);
      navigate("/", { replace: true });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : t("auth.initializeFailed");
      setError(message);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  return (
    <AuthSurface
      title={t("auth.initializeTitle")}
      lead={t("auth.initializeLead")}
      controlsDisabled={busy}
    >
      <form
        className="space-y-4"
        aria-busy={busy}
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <Field
          label={t("auth.account")}
          value={username}
          onChange={(value) => {
            setUsername(value);
            setError("");
          }}
          autoComplete="username"
          disabled={busy}
          describedBy={error ? errorId : undefined}
        />
        <Field
          label={t("auth.displayName")}
          value={displayName}
          onChange={(value) => {
            setDisplayName(value);
            setError("");
          }}
          autoComplete="name"
          required={false}
          disabled={busy}
        />
        <Field
          label={t("auth.password")}
          type="password"
          value={password}
          onChange={(value) => {
            setPassword(value);
            setError("");
          }}
          autoComplete="new-password"
          disabled={busy}
          describedBy={error ? errorId : undefined}
        />
        {error ? <AuthError id={errorId} message={error} /> : null}
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
  const toast = useToast();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const errorId = useId();

  useEffect(() => {
    void locale;
    setError((current) => (current ? t("auth.loginFailed") : current));
  }, [locale, t]);

  async function submit() {
    if (busyRef.current) {
      return;
    }
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      const res = await signIn({ username, password });
      toast.showToast({ kind: "success", message: t("auth.signedIn") });
      onDone(res.accessToken, res.account);
      navigate("/", { replace: true });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : t("auth.loginFailed");
      setError(message);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  return (
    <AuthSurface title={t("auth.loginTitle")} controlsDisabled={busy}>
      <form
        className="space-y-4"
        aria-busy={busy}
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <Field
          label={t("auth.account")}
          value={username}
          onChange={(value) => {
            setUsername(value);
            setError("");
          }}
          autoComplete="username"
          disabled={busy}
          describedBy={error ? errorId : undefined}
        />
        <Field
          label={t("auth.password")}
          type="password"
          value={password}
          onChange={(value) => {
            setPassword(value);
            setError("");
          }}
          autoComplete="current-password"
          disabled={busy}
          describedBy={error ? errorId : undefined}
        />
        {error ? <AuthError id={errorId} message={error} /> : null}
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
