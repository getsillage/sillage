import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { I18nProvider, useI18n } from "../i18n/I18nProvider";
import { Toast, useToast } from "./Toast";

function ToastHarness() {
  const { setLocale } = useI18n();
  const { dismissToast, showToast } = useToast();

  return (
    <div>
      <button
        type="button"
        onClick={() => showToast({ kind: "success", message: "保存成功" })}
      >
        success
      </button>
      <button
        type="button"
        onClick={() => showToast({ kind: "error", message: "保存失败" })}
      >
        error
      </button>
      <button
        type="button"
        onClick={() => showToast({ kind: "info", message: "正在同步" })}
      >
        info
      </button>
      <button
        type="button"
        onClick={() => showToast({ kind: "info", message: "第四条" })}
      >
        fourth
      </button>
      <button
        type="button"
        onClick={() => {
          const id = showToast({ kind: "info", message: "由调用方关闭" });
          dismissToast(id);
        }}
      >
        dismiss
      </button>
      <button type="button" onClick={() => setLocale("en")}>
        English
      </button>
    </div>
  );
}

function renderHarness() {
  render(
    <I18nProvider>
      <ToastHarness />
    </I18nProvider>,
  );
}

afterEach(() => {
  vi.useRealTimers();
  window.localStorage.clear();
});

describe("ToastProvider", () => {
  it("is mounted by I18nProvider and renders every toast kind", () => {
    renderHarness();

    fireEvent.click(screen.getByRole("button", { name: "success" }));
    expect(screen.getByRole("status")).toHaveTextContent("保存成功");
    fireEvent.click(screen.getByRole("button", { name: "关闭通知" }));

    fireEvent.click(screen.getByRole("button", { name: "error" }));
    expect(screen.getByRole("alert")).toHaveTextContent("保存失败");
    fireEvent.click(screen.getByRole("button", { name: "关闭通知" }));

    fireEvent.click(screen.getByRole("button", { name: "info" }));
    expect(screen.getByRole("status")).toHaveTextContent("正在同步");
  });

  it("keeps queued errors ahead of newer routine feedback", () => {
    renderHarness();

    fireEvent.click(screen.getByRole("button", { name: "error" }));
    fireEvent.click(screen.getByRole("button", { name: "error" }));
    fireEvent.click(screen.getByRole("button", { name: "info" }));
    fireEvent.click(screen.getByRole("button", { name: "fourth" }));

    expect(screen.getByText("保存失败")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "关闭通知" }));
    expect(screen.getByText("保存失败")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "关闭通知" }));
    expect(screen.getByText("第四条")).toBeInTheDocument();
    expect(screen.queryByText("正在同步")).not.toBeInTheDocument();
  });

  it("lets errors preempt an active routine toast", () => {
    renderHarness();

    fireEvent.click(screen.getByRole("button", { name: "success" }));
    expect(screen.getByText("保存成功")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "error" }));

    expect(screen.getByRole("alert")).toHaveTextContent("保存失败");
    expect(screen.queryByText("保存成功")).not.toBeInTheDocument();
  });

  it("queues repeated messages as separate events", () => {
    renderHarness();

    fireEvent.click(screen.getByRole("button", { name: "success" }));
    fireEvent.click(screen.getByRole("button", { name: "success" }));

    expect(screen.getAllByText("保存成功")).toHaveLength(1);
    fireEvent.click(screen.getByRole("button", { name: "关闭通知" }));
    expect(screen.getByText("保存成功")).toBeInTheDocument();
  });

  it("supports manual closing and caller dismissal", () => {
    renderHarness();
    fireEvent.click(screen.getByRole("button", { name: "info" }));

    const toast = screen.getByRole("status");
    fireEvent.click(within(toast).getByRole("button", { name: "关闭通知" }));
    expect(screen.queryByText("正在同步")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "dismiss" }));
    expect(screen.queryByText("由调用方关闭")).not.toBeInTheDocument();
  });

  it("automatically closes queued toasts", () => {
    vi.useFakeTimers();
    renderHarness();
    fireEvent.click(screen.getByRole("button", { name: "success" }));

    act(() => vi.advanceTimersByTime(3_199));
    expect(screen.getByText("保存成功")).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(1));
    expect(screen.queryByText("保存成功")).not.toBeInTheDocument();
  });

  it("keeps errors visible longer than routine feedback", () => {
    vi.useFakeTimers();
    renderHarness();
    fireEvent.click(screen.getByRole("button", { name: "error" }));

    act(() => vi.advanceTimersByTime(3_200));
    expect(screen.getByText("保存失败")).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(2_800));
    expect(screen.queryByText("保存失败")).not.toBeInTheDocument();
  });

  it("clears stale-language toasts and uses the new close label", () => {
    renderHarness();
    fireEvent.click(screen.getByRole("button", { name: "info" }));
    expect(
      screen.getByRole("button", { name: "关闭通知" }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "English" }));
    expect(screen.queryByText("正在同步")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "info" }));

    expect(
      screen.getByRole("button", { name: "Close notification" }),
    ).toBeInTheDocument();
  });
});

describe("Toast", () => {
  it("keeps the single-toast API and auto-close behavior", () => {
    vi.useFakeTimers();
    const onClose = vi.fn();
    render(
      <I18nProvider>
        <Toast
          toast={{ kind: "info", message: "兼容单条通知" }}
          onClose={onClose}
        />
      </I18nProvider>,
    );

    const toast = screen.getByRole("status");
    expect(toast).toHaveClass("fixed", "z-[90]");
    fireEvent.click(within(toast).getByRole("button", { name: "关闭通知" }));
    expect(onClose).toHaveBeenCalledTimes(1);

    act(() => vi.advanceTimersByTime(3_200));
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
