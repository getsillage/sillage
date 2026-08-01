import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { ToastProvider } from "../components/Toast";
import {
  isLocale,
  type Locale,
  setActiveLocale,
  type TranslationKey,
  type TranslationValues,
  translate,
} from "./messages";

const STORAGE_KEY = "sillage-language";

type I18nContextValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, values?: TranslationValues) => string;
};

const defaultValue: I18nContextValue = {
  locale: "zh-CN",
  setLocale: () => undefined,
  t: (key, values) => translate("zh-CN", key, values),
};

const I18nContext = createContext<I18nContextValue>(defaultValue);

function readStoredLocale(): Locale {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return isLocale(stored) ? stored : "zh-CN";
  } catch {
    return "zh-CN";
  }
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(readStoredLocale);
  const localeRef = useRef(locale);
  localeRef.current = locale;
  setActiveLocale(locale);

  useLayoutEffect(() => {
    document.documentElement.lang = locale;
    document
      .querySelector<HTMLLinkElement>('link[rel="manifest"]')
      ?.setAttribute(
        "href",
        locale === "en" ? "/manifest.en.webmanifest" : "/manifest.webmanifest",
      );
    try {
      window.localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      // The in-memory preference still works when storage is unavailable.
    }
  }, [locale]);

  useEffect(() => {
    function onStorage(event: StorageEvent) {
      if (event.key === STORAGE_KEY && isLocale(event.newValue)) {
        setLocaleState(event.newValue);
      }
    }
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const setLocale = useCallback((next: Locale) => setLocaleState(next), []);
  const t = useCallback(
    (key: TranslationKey, values?: TranslationValues) =>
      translate(localeRef.current, key, values),
    [],
  );
  const value = useMemo(
    () => ({ locale, setLocale, t }),
    [locale, setLocale, t],
  );

  return (
    <I18nContext.Provider value={value}>
      <ToastProvider closeLabel={t("toast.close")}>{children}</ToastProvider>
    </I18nContext.Provider>
  );
}

export function useI18n(): I18nContextValue {
  return useContext(I18nContext);
}
