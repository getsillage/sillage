import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
import { setActiveLocale } from "../i18n/messages";

// React Testing Library does not auto-clean between tests under Vitest.
afterEach(() => {
  cleanup();
  setActiveLocale("zh-CN");
  document.documentElement.lang = "zh-CN";
});

// This jsdom build does not expose Web Storage; provide a minimal in-memory
// implementation so token storage and theme preference code runs under test.
function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    get length() {
      return data.size;
    },
    key: (i: number) => Array.from(data.keys())[i] ?? null,
    getItem: (k: string) => (data.has(k) ? (data.get(k) as string) : null),
    setItem: (k: string, v: string) => {
      data.set(k, String(v));
    },
    removeItem: (k: string) => {
      data.delete(k);
    },
    clear: () => {
      data.clear();
    },
  } as Storage;
}

for (const key of ["localStorage", "sessionStorage"] as const) {
  if (!window[key]) {
    Object.defineProperty(window, key, {
      value: memoryStorage(),
      configurable: true,
    });
  }
}

// jsdom lacks matchMedia, which ThemeToggle and others probe defensively.
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
}
