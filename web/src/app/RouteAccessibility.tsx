import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigationType } from "react-router-dom";
import { useI18n } from "../i18n/I18nProvider";
import type { TranslationKey } from "../i18n/messages";
import { hasVisibleModal } from "../lib/modal";

function normalizePathname(pathname: string): string {
  return pathname.replace(/\/+$/, "") || "/";
}

function pageLabelKey(pathname: string): TranslationKey | null {
  const normalized = normalizePathname(pathname);
  if (normalized === "/") {
    return "nav.writeRecord";
  }
  if (normalized === "/timeline") {
    return "timeline.title";
  }
  if (normalized.startsWith("/entries/")) {
    return "entry.detailTitle";
  }
  if (normalized === "/ask" || normalized === "/review") {
    return "ask.section";
  }
  if (normalized === "/settings") {
    return "settings.title";
  }
  if (normalized === "/initialize") {
    return "auth.initializeTitle";
  }
  if (normalized === "/login") {
    return "auth.loginTitle";
  }
  return null;
}

export function routeAccessibilityIdentity(
  pathname: string,
  search: string,
): string {
  const normalized = normalizePathname(pathname);
  if (normalized !== "/ask") {
    return normalized;
  }
  const conversationId = new URLSearchParams(search).get("conversation") ?? "";
  return `${normalized}?conversation=${conversationId}`;
}

function afterRouteCommit(callback: () => void): () => void {
  if (typeof window.requestAnimationFrame === "function") {
    const frame = window.requestAnimationFrame(callback);
    return () => window.cancelAnimationFrame(frame);
  }
  const timer = window.setTimeout(callback, 0);
  return () => window.clearTimeout(timer);
}

export function RouteAccessibility() {
  const { locale, t } = useI18n();
  const location = useLocation();
  const navigationType = useNavigationType();
  const identity = routeAccessibilityIdentity(
    location.pathname,
    location.search,
  );
  const labelKey = pageLabelKey(location.pathname);
  const pageLabel = labelKey ? t(labelKey) : "";
  const pageLabelRef = useRef(pageLabel);
  const previousIdentityRef = useRef(identity);
  const routeNavigationTypeRef = useRef(navigationType);
  const [announcement, setAnnouncement] = useState("");
  pageLabelRef.current = pageLabel;
  if (previousIdentityRef.current !== identity) {
    routeNavigationTypeRef.current = navigationType;
  }

  useEffect(() => {
    void locale;
    setAnnouncement("");
  }, [locale]);

  useEffect(() => {
    if (previousIdentityRef.current === identity) {
      return;
    }
    previousIdentityRef.current = identity;
    setAnnouncement("");

    let cancelled = false;
    let cancelFrame: (() => void) | null = null;
    let modalObserver: MutationObserver | null = null;
    const preserveScroll = routeNavigationTypeRef.current === "POP";

    function focusPage() {
      if (cancelled) {
        return;
      }
      if (hasVisibleModal()) {
        if (!modalObserver) {
          modalObserver = new MutationObserver(() => {
            if (hasVisibleModal()) {
              return;
            }
            modalObserver?.disconnect();
            modalObserver = null;
            cancelFrame = afterRouteCommit(() => {
              cancelFrame = afterRouteCommit(focusPage);
            });
          });
          modalObserver.observe(document.body, {
            attributes: true,
            attributeFilter: ["aria-hidden", "aria-modal", "hidden"],
            childList: true,
            subtree: true,
          });
        }
        return;
      }

      const target =
        document.querySelector<HTMLElement>("main h1") ??
        document.querySelector<HTMLElement>("main");
      if (target) {
        if (!target.hasAttribute("tabindex")) {
          target.setAttribute("tabindex", "-1");
        }
        target.setAttribute("data-route-focus-target", "");
        target.focus({ preventScroll: true });
        if (!preserveScroll) {
          target.scrollIntoView?.({ behavior: "instant", block: "start" });
        }
      }
    }

    // Let route-driven drawers and dialogs restore focus before choosing the
    // final page target for the completed navigation.
    cancelFrame = afterRouteCommit(() => {
      const label = pageLabelRef.current;
      if (!label) {
        return;
      }
      setAnnouncement(label);
      focusPage();
    });

    return () => {
      cancelled = true;
      cancelFrame?.();
      modalObserver?.disconnect();
    };
  }, [identity]);

  return (
    <>
      <title>{pageLabel ? `${pageLabel} | Sillage` : "Sillage"}</title>
      <div
        className="sr-only"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        {announcement}
      </div>
    </>
  );
}
