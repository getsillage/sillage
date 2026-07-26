import { Monitor, Moon, Sun } from "lucide-react";
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

function applyAndBroadcastTheme(preference: ThemePreference): ThemePreference {
  applyTheme(preference);
  window.dispatchEvent(
    new CustomEvent<ThemePreference>(THEME_CHANGE_EVENT, {
      detail: preference,
    }),
  );
  return preference;
}

// Tri-state cycle so a user can always return to following the OS theme.
function nextPreference(preference: ThemePreference): ThemePreference {
  switch (preference) {
    case "light":
      return "dark";
    case "dark":
      return "system";
    default:
      return "light";
  }
}

/** Light/dark/system toggle mirroring the boot-time `theme-init.js` preference. */
export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const { t } = useI18n();
  const [preference, setPreference] = useState<ThemePreference>("system");
  const preferenceLabel = t(
    preference === "light"
      ? "theme.light"
      : preference === "dark"
        ? "theme.dark"
        : "theme.system",
  );

  useEffect(() => {
    const stored = readStoredPreference();
    applyTheme(stored);
    setPreference(stored);
  }, []);

  useEffect(() => {
    function onThemeChange(event: Event) {
      setPreference((event as CustomEvent<ThemePreference>).detail);
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
      // Re-derive the effective mode when the OS theme flips under "system".
      applyTheme(readStoredPreference());
    }
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  const Icon =
    preference === "dark" ? Moon : preference === "light" ? Sun : Monitor;

  return (
    <button
      type="button"
      title={t("theme.title", { mode: preferenceLabel })}
      aria-label={t("theme.toggle", { mode: preferenceLabel })}
      className={compact ? iconButtonClass : secondaryButtonClass}
      onClick={() => {
        setPreference(applyAndBroadcastTheme(nextPreference(preference)));
      }}
    >
      <Icon className="h-4 w-4" aria-hidden="true" />
      {compact ? null : <span>{preferenceLabel}</span>}
    </button>
  );
}
