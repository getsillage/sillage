import { useEffect, useState } from "react";

type ThemeMode = "system" | "light" | "dark";

const STORAGE_KEY = "sillage-theme";
const MODES: ThemeMode[] = ["system", "light", "dark"];
const MODE_LABELS: Record<ThemeMode, string> = {
  system: "系统",
  light: "浅色",
  dark: "深色",
};

function readStoredMode(): ThemeMode {
  if (typeof window === "undefined") {
    return "system";
  }
  const value = window.localStorage.getItem(STORAGE_KEY);
  return value === "light" || value === "dark" ? value : "system";
}

function systemPrefersDark(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia?.("(prefers-color-scheme: dark)").matches === true
  );
}

function applyTheme(mode: ThemeMode): boolean {
  const isDark = mode === "dark" || (mode === "system" && systemPrefersDark());
  document.documentElement.classList.toggle("dark", isDark);
  document.documentElement.dataset.theme = mode;
  document.documentElement.style.colorScheme = isDark ? "dark" : "light";
  if (mode === "system") {
    window.localStorage.removeItem(STORAGE_KEY);
  } else {
    window.localStorage.setItem(STORAGE_KEY, mode);
  }
  return isDark;
}

function nextMode(mode: ThemeMode): ThemeMode {
  return MODES[(MODES.indexOf(mode) + 1) % MODES.length];
}

export function ThemeToggle() {
  const [mode, setMode] = useState<ThemeMode>("system");
  const [isDark, setIsDark] = useState(false);

  useEffect(() => {
    const stored = readStoredMode();
    setMode(stored);
    setIsDark(applyTheme(stored));
  }, []);

  useEffect(() => {
    const media = window.matchMedia?.("(prefers-color-scheme: dark)");
    if (!media) {
      return;
    }
    function onChange() {
      setIsDark(applyTheme(readStoredMode()));
    }
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  return (
    <button
      type="button"
      title={`主题：${MODE_LABELS[mode]}`}
      aria-label={`切换主题，当前为${MODE_LABELS[mode]}`}
      className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-2 text-gray-600 text-sm shadow-sm transition hover:border-gray-300 hover:bg-gray-50 hover:text-gray-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-900/10 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-300 dark:hover:border-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-100/20"
      onClick={() => {
        const next = nextMode(mode);
        setMode(next);
        setIsDark(applyTheme(next));
      }}
    >
      <span
        aria-hidden="true"
        className={`h-2.5 w-2.5 rounded-full ring-1 ring-inset ${
          isDark ? "bg-gray-100 ring-gray-400" : "bg-gray-900 ring-gray-900"
        }`}
      />
      <span>{MODE_LABELS[mode]}</span>
    </button>
  );
}
