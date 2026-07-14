import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode, useEffect, useRef, useState } from "react";
import {
  Link,
  MemoryRouter,
  Route,
  Routes,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LanguageSwitcher } from "../components/LanguageSwitcher";
import { I18nProvider } from "../i18n/I18nProvider";
import { RouteAccessibility } from "./RouteAccessibility";

function HomeRoute() {
  const navigate = useNavigate();
  return (
    <main>
      <h1>今天想记录什么？</h1>
      <Link to="/timeline">打开全部记录</Link>
      <button type="button" onClick={() => navigate(-1)}>
        返回上一页
      </button>
      <LanguageSwitcher compact />
    </main>
  );
}

function TimelineRoute() {
  const navigate = useNavigate();
  return (
    <main>
      <h1>全部记录</h1>
      <button type="button" onClick={() => navigate("/timeline?view=calendar")}>
        切换日历视图
      </button>
      <LanguageSwitcher compact />
    </main>
  );
}

function AskRoute() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const conversation = searchParams.get("conversation") ?? "new";
  return (
    <main>
      <h1>{conversation === "c2" ? "另一个问答" : "睡眠变化"}</h1>
      <button type="button" onClick={() => navigate("/ask?conversation=c2")}>
        打开另一个问答
      </button>
    </main>
  );
}

function PersistentModalNavigation() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const wasOpenRef = useRef(false);

  useEffect(() => {
    if (open) {
      wasOpenRef.current = true;
    } else if (wasOpenRef.current) {
      triggerRef.current?.focus();
      wasOpenRef.current = false;
    }
  }, [open]);

  return (
    <>
      <button ref={triggerRef} type="button" onClick={() => setOpen(true)}>
        打开全局弹窗
      </button>
      {open ? (
        <div role="dialog" aria-modal="true" aria-label="全局弹窗">
          <button type="button" onClick={() => navigate("/timeline")}>
            在弹窗中打开全部记录
          </button>
          <button type="button" onClick={() => setOpen(false)}>
            关闭全局弹窗
          </button>
        </div>
      ) : null}
    </>
  );
}

function renderRoutes(initialEntry: string | string[], initialIndex?: number) {
  const user = userEvent.setup();
  render(
    <StrictMode>
      <I18nProvider>
        <MemoryRouter
          initialEntries={
            typeof initialEntry === "string" ? [initialEntry] : initialEntry
          }
          initialIndex={initialIndex}
        >
          <RouteAccessibility />
          <PersistentModalNavigation />
          <Routes>
            <Route path="/" element={<HomeRoute />} />
            <Route path="/timeline" element={<TimelineRoute />} />
            <Route path="/ask" element={<AskRoute />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    </StrictMode>,
  );
  return user;
}

function controlAnimationFrames() {
  const originalRequestAnimationFrame = window.requestAnimationFrame;
  const originalCancelAnimationFrame = window.cancelAnimationFrame;
  const callbacks = new Map<number, FrameRequestCallback>();
  let nextId = 1;

  window.requestAnimationFrame = (callback) => {
    const id = nextId;
    nextId += 1;
    callbacks.set(id, callback);
    return id;
  };
  window.cancelAnimationFrame = (id) => {
    callbacks.delete(id);
  };

  return {
    pendingCount: () => callbacks.size,
    flushNextFrame: () => {
      const current = Array.from(callbacks.values());
      callbacks.clear();
      for (const callback of current) {
        callback(performance.now());
      }
    },
    restore: () => {
      window.requestAnimationFrame = originalRequestAnimationFrame;
      window.cancelAnimationFrame = originalCancelAnimationFrame;
    },
  };
}

describe("RouteAccessibility", () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it("sets a localized title without stealing focus on initial render", async () => {
    const user = renderRoutes("/");

    expect(document.title).toBe("写记录 | Sillage");
    expect(screen.getByRole("heading", { level: 1 })).not.toHaveFocus();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();

    const english = screen.getByRole("button", { name: "English" });
    await user.click(english);

    await waitFor(() =>
      expect(document.title).toBe("Write a record | Sillage"),
    );
    expect(english).toHaveFocus();
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("focuses and announces a new page but ignores in-page filters", async () => {
    const user = renderRoutes("/");
    await user.click(screen.getByRole("link", { name: "打开全部记录" }));

    const heading = await screen.findByRole("heading", { name: "全部记录" });
    await waitFor(() => expect(heading).toHaveFocus());
    expect(heading).toHaveAttribute("data-route-focus-target");
    expect(screen.getByRole("status")).toHaveTextContent("全部记录");
    expect(document.title).toBe("全部记录 | Sillage");

    const viewButton = screen.getByRole("button", { name: "切换日历视图" });
    await user.click(viewButton);

    expect(viewButton).toHaveFocus();
    expect(heading).not.toHaveFocus();
  });

  it("treats an Ask conversation change as page navigation", async () => {
    const user = renderRoutes("/ask?conversation=c1");

    await user.click(screen.getByRole("button", { name: "打开另一个问答" }));

    const heading = await screen.findByRole("heading", { name: "另一个问答" });
    await waitFor(() => expect(heading).toHaveFocus());
    expect(screen.getByRole("status")).toHaveTextContent("问答");
    expect(document.title).toBe("问答 | Sillage");
  });

  it("clears a completed route announcement when the language changes", async () => {
    const user = renderRoutes("/");
    await user.click(screen.getByRole("link", { name: "打开全部记录" }));

    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveTextContent("全部记录"),
    );
    const english = screen.getByRole("button", { name: "English" });
    await user.click(english);

    await waitFor(() => expect(document.title).toBe("All records | Sillage"));
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    expect(english).toHaveFocus();
  });

  it("preserves scroll when focusing after browser-history navigation", async () => {
    const frames = controlAnimationFrames();
    try {
      const user = renderRoutes(["/timeline", "/"], 1);
      await user.click(screen.getByRole("button", { name: "返回上一页" }));

      const heading = await screen.findByRole("heading", { name: "全部记录" });
      const focus = vi.spyOn(heading, "focus");
      const scrollIntoView = vi.fn();
      heading.scrollIntoView = scrollIntoView;
      expect(frames.pendingCount()).toBe(1);
      await act(async () => frames.flushNextFrame());

      expect(focus).toHaveBeenCalledWith({ preventScroll: true });
      expect(scrollIntoView).not.toHaveBeenCalled();
      expect(heading).toHaveFocus();
    } finally {
      frames.restore();
    }
  });

  it("waits for a persistent modal to close before focusing the page", async () => {
    const frames = controlAnimationFrames();
    try {
      const user = renderRoutes("/");
      await user.click(screen.getByRole("button", { name: "打开全局弹窗" }));
      const navigateButton = screen.getByRole("button", {
        name: "在弹窗中打开全部记录",
      });

      await user.click(navigateButton);

      const heading = await screen.findByRole("heading", { name: "全部记录" });
      expect(frames.pendingCount()).toBe(1);
      await act(async () => frames.flushNextFrame());
      expect(screen.getByRole("status")).toHaveTextContent("全部记录");
      expect(navigateButton).toHaveFocus();
      expect(heading).not.toHaveFocus();

      await user.click(screen.getByRole("button", { name: "关闭全局弹窗" }));

      const trigger = screen.getByRole("button", { name: "打开全局弹窗" });
      await waitFor(() => expect(frames.pendingCount()).toBe(1));
      expect(trigger).toHaveFocus();

      await act(async () => frames.flushNextFrame());
      expect(frames.pendingCount()).toBe(1);
      expect(trigger).toHaveFocus();
      expect(heading).not.toHaveFocus();

      const focus = vi.spyOn(heading, "focus");
      const scrollIntoView = vi.fn();
      heading.scrollIntoView = scrollIntoView;
      await act(async () => frames.flushNextFrame());
      expect(frames.pendingCount()).toBe(0);
      expect(focus).toHaveBeenCalledWith({ preventScroll: true });
      expect(scrollIntoView).toHaveBeenCalledWith({
        behavior: "instant",
        block: "start",
      });
      expect(heading).toHaveFocus();
      expect(trigger).not.toHaveFocus();
    } finally {
      frames.restore();
    }
  });
});
