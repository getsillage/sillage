import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { iconButtonClass, secondaryButtonClass } from "./ui";

type ThemeMode = "light" | "dark";
type ThemePreference = ThemeMode | "system";

const STORAGE_KEY = "sillage-theme";
const THEME_CHANGE_EVENT = "sillage:theme-change";
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

function applyAndBroadcastTheme(preference: ThemePreference): ThemeMode {
  const effectiveMode = applyTheme(preference);
  window.dispatchEvent(
    new CustomEvent<ThemeMode>(THEME_CHANGE_EVENT, {
      detail: effectiveMode,
    }),
  );
  return effectiveMode;
}

function nextMode(mode: ThemeMode): ThemeMode {
  return mode === "dark" ? "light" : "dark";
}

/** Light/dark toggle that mirrors the boot-time `theme-init.js` preference. */
export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const [mode, setMode] = useState<ThemeMode>("light");

  useEffect(() => {
    setMode(applyTheme(readStoredPreference()));
  }, []);

  useEffect(() => {
    function onThemeChange(event: Event) {
      setMode((event as CustomEvent<ThemeMode>).detail);
    }
    window.addEventListener(THEME_CHANGE_EVENT, onThemeChange);
    return () => window.removeEventListener(THEME_CHANGE_EVENT, onThemeChange);
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
      className={compact ? iconButtonClass : secondaryButtonClass}
      onClick={() => {
        const next = nextMode(mode);
        setMode(applyAndBroadcastTheme(next));
      }}
    >
      {mode === "dark" ? (
        <Moon className="h-4 w-4" aria-hidden="true" />
      ) : (
        <Sun className="h-4 w-4" aria-hidden="true" />
      )}
      {compact ? null : <span>{MODE_LABELS[mode]}</span>}
    </button>
  );
}
