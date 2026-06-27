import { useEffect, useState } from "react";

type ThemeMode = "light" | "dark";
type ThemePreference = ThemeMode | "system";

const STORAGE_KEY = "sillage-theme";
const MODE_LABELS: Record<ThemeMode, string> = {
  light: "浅色",
  dark: "深色",
};

function readStoredPreference(): ThemePreference {
  const value = window.localStorage.getItem(STORAGE_KEY);
  return value === "light" || value === "dark" ? value : "system";
}

function systemPrefersDark(): boolean {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches === true;
}

function getEffectiveMode(preference: ThemePreference): ThemeMode {
  if (preference !== "system") {
    return preference;
  }
  return systemPrefersDark() ? "dark" : "light";
}

function applyTheme(preference: ThemePreference): ThemeMode {
  const effectiveMode = getEffectiveMode(preference);
  const isDark = effectiveMode === "dark";
  document.documentElement.classList.toggle("dark", isDark);
  document.documentElement.dataset.theme = preference;
  document.documentElement.style.colorScheme = isDark ? "dark" : "light";
  if (preference === "system") {
    window.localStorage.removeItem(STORAGE_KEY);
  } else {
    window.localStorage.setItem(STORAGE_KEY, preference);
  }
  return effectiveMode;
}

function nextMode(mode: ThemeMode): ThemeMode {
  return mode === "dark" ? "light" : "dark";
}

/** Light/dark toggle that mirrors the boot-time `theme-init.js` preference. */
export function ThemeToggle() {
  const [mode, setMode] = useState<ThemeMode>("light");

  useEffect(() => {
    setMode(applyTheme(readStoredPreference()));
  }, []);

  useEffect(() => {
    const media = window.matchMedia?.("(prefers-color-scheme: dark)");
    if (!media) {
      return;
    }
    function onChange() {
      setMode(applyTheme(readStoredPreference()));
    }
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  return (
    <button
      type="button"
      title={`主题：${MODE_LABELS[mode]}`}
      aria-label={`切换主题，当前为${MODE_LABELS[mode]}`}
      className="inline-flex h-10 items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 text-gray-600 text-sm transition hover:bg-gray-100 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:bg-gray-800 dark:hover:text-gray-50 dark:focus-visible:ring-gray-500/40"
      onClick={() => {
        const next = nextMode(mode);
        setMode(next);
        applyTheme(next);
      }}
    >
      <span
        aria-hidden="true"
        className={`h-2.5 w-2.5 rounded-full ring-1 ring-inset ${
          mode === "dark"
            ? "bg-gray-300 ring-gray-400"
            : "bg-gray-800 ring-gray-800"
        }`}
      />
      <span>{MODE_LABELS[mode]}</span>
    </button>
  );
}
