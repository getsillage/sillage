import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AIProfile } from "../lib/api";
import { SettingsWorkspace } from "./SettingsWorkspace";

vi.mock("../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/api")>();
  return {
    ...actual,
    getAISettings: vi.fn(),
    patchAISettings: vi.fn(),
    testAIConnection: vi.fn(),
  };
});

import { getAISettings, patchAISettings, testAIConnection } from "../lib/api";

function profile(overrides: Partial<AIProfile> = {}): AIProfile {
  return {
    id: "p1",
    name: "默认",
    provider: "anthropic",
    baseUrl: "",
    model: "claude-opus-4-8",
    temperature: 0.3,
    maxTokens: 1000,
    enabled: true,
    active: true,
    hasApiKey: true,
    keyUnavailable: false,
    autoSummary: false,
    createdAt: "1",
    updatedAt: "1",
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getAISettings).mockResolvedValue({ profiles: [profile()] });
  vi.mocked(patchAISettings).mockResolvedValue({ profiles: [profile()] });
  vi.mocked(testAIConnection).mockResolvedValue({
    ok: true,
    model: "claude-opus-4-8",
  });
});

describe("SettingsWorkspace", () => {
  it("loads profiles and saves edits", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);

    const nameInput = await screen.findByDisplayValue("默认");
    await user.clear(nameInput);
    await user.type(nameInput, "工作档案");
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    await waitFor(() => expect(patchAISettings).toHaveBeenCalledTimes(1));
    expect(vi.mocked(patchAISettings).mock.calls[0][1][0].name).toBe(
      "工作档案",
    );
    expect(await screen.findByText("已保存")).toBeInTheDocument();
  });

  it("toggles auto-summary and includes it in the saved payload", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);
    await screen.findByDisplayValue("默认");

    const checkbox = screen.getByRole("checkbox", {
      name: "新建记录后自动总结",
    });
    await user.click(checkbox);
    expect(checkbox).toBeChecked();
    await user.click(screen.getByRole("button", { name: "保存设置" }));
    await waitFor(() => expect(patchAISettings).toHaveBeenCalled());
    expect(vi.mocked(patchAISettings).mock.calls[0][1][0].autoSummary).toBe(
      true,
    );
  });

  it("tests a saved profile's connection", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);
    await screen.findByDisplayValue("默认");

    await user.click(screen.getByRole("button", { name: "测试连接" }));
    await waitFor(() =>
      expect(testAIConnection).toHaveBeenCalledWith("t", "p1"),
    );
    expect(await screen.findByText(/连接成功/)).toBeInTheDocument();
  });

  it("surfaces a failed connection test", async () => {
    const user = userEvent.setup();
    vi.mocked(testAIConnection).mockRejectedValue(new Error("连接失败：401"));
    render(<SettingsWorkspace token="t" />);
    await screen.findByDisplayValue("默认");

    await user.click(screen.getByRole("button", { name: "测试连接" }));
    expect(await screen.findByText("连接失败：401")).toBeInTheDocument();
  });
});
