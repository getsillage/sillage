import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";
import { useI18n } from "../i18n/I18nProvider";
import { iconButtonClass, secondaryButtonClass } from "./ui";

type ThemeMode = "light" | "dark";
type ThemePreference = ThemeMode | "system";

const STORAGE_KEY = "sillage-theme";
const THEME_CHANGE_EVENT = "sillage:theme-change";
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
  const { t } = useI18n();
  const [mode, setMode] = useState<ThemeMode>("light");
  const modeLabel = t(mode === "light" ? "theme.light" : "theme.dark");

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
      title={t("theme.title", { mode: modeLabel })}
      aria-label={t("theme.toggle", { mode: modeLabel })}
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
      {compact ? null : <span>{modeLabel}</span>}
    </button>
  );
}
