import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AIProfile } from "../lib/api";
import { SettingsWorkspace } from "./SettingsWorkspace";

vi.mock("../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/api")>();
  return {
    ...actual,
    getAISettings: vi.fn(),
    listAIModels: vi.fn(),
    patchAISettings: vi.fn(),
    testAIConnection: vi.fn(),
  };
});

import {
  getAISettings,
  listAIModels,
  patchAISettings,
  testAIConnection,
} from "../lib/api";

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
  vi.mocked(getAISettings).mockResolvedValue({
    profiles: [profile()],
    autoSummary: false,
  });
  vi.mocked(patchAISettings).mockResolvedValue({
    profiles: [profile()],
    autoSummary: false,
  });
  vi.mocked(listAIModels).mockResolvedValue({
    models: ["claude-opus-4-8", "claude-sonnet-4-5"],
  });
  vi.mocked(testAIConnection).mockResolvedValue({
    ok: true,
    model: "claude-opus-4-8",
  });
});

describe("SettingsWorkspace", () => {
  async function openDefaultProfile(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole("button", { name: "配置" }));
  }

  it("loads profiles and saves edits", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);

    await openDefaultProfile(user);
    const nameInput = screen.getByDisplayValue("默认");
    await user.clear(nameInput);
    await user.type(nameInput, "工作档案");
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    await waitFor(() => expect(patchAISettings).toHaveBeenCalledTimes(1));
    expect(vi.mocked(patchAISettings).mock.calls[0][1].profiles[0].name).toBe(
      "工作档案",
    );
    expect(await screen.findByText("已保存")).toBeInTheDocument();
  });

  it("saves auto-summary as a global setting", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);
    await screen.findByRole("button", { name: "配置" });

    const checkbox = screen.getByRole("checkbox", {
      name: "新建记录后自动总结",
    });
    await user.click(checkbox);
    expect(checkbox).toBeChecked();
    await user.click(screen.getByRole("button", { name: "保存设置" }));
    await waitFor(() => expect(patchAISettings).toHaveBeenCalled());
    expect(vi.mocked(patchAISettings).mock.calls[0][1].autoSummary).toBe(true);
    expect(
      vi.mocked(patchAISettings).mock.calls[0][1].profiles[0],
    ).not.toHaveProperty("autoSummary");
  });

  it("sets a collapsed profile card as the only default", async () => {
    const user = userEvent.setup();
    vi.mocked(getAISettings).mockResolvedValue({
      profiles: [
        profile({ id: "p1", name: "工作", active: true }),
        profile({ id: "p2", name: "生活", active: false, enabled: true }),
      ],
      autoSummary: false,
    });
    vi.mocked(patchAISettings).mockResolvedValue({
      profiles: [
        profile({ id: "p2", name: "生活", active: true }),
        profile({ id: "p1", name: "工作", active: false }),
      ],
      autoSummary: false,
    });
    render(<SettingsWorkspace token="t" />);

    const lifeCard = (await screen.findByText("生活")).closest("article");
    expect(lifeCard).not.toBeNull();
    await user.click(
      within(lifeCard as HTMLElement).getByRole("button", { name: "设为默认" }),
    );

    await waitFor(() => expect(patchAISettings).toHaveBeenCalledTimes(1));
    const payload = vi.mocked(patchAISettings).mock.calls[0][1].profiles;
    expect(payload).toMatchObject([
      { id: "p1", active: false, enabled: true },
      { id: "p2", active: true, enabled: true },
    ]);
    expect(payload.filter((item) => item.active)).toHaveLength(1);
    expect(await screen.findByText("已设为默认")).toBeInTheDocument();
  });

  it("requires confirmation before deleting a saved profile", async () => {
    const user = userEvent.setup();
    vi.mocked(patchAISettings).mockResolvedValue({
      profiles: [],
      autoSummary: false,
    });
    render(<SettingsWorkspace token="t" />);
    await openDefaultProfile(user);

    await user.click(screen.getByRole("button", { name: "删除" }));
    expect(patchAISettings).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(patchAISettings).toHaveBeenCalledTimes(1));
    expect(vi.mocked(patchAISettings).mock.calls[0][1].profiles).toEqual([]);
    expect(await screen.findByText("已删除")).toBeInTheDocument();
  });

  it("fetches models while keeping manual model input editable", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);
    await openDefaultProfile(user);

    await user.click(screen.getByRole("button", { name: "获取模型" }));
    await waitFor(() =>
      expect(listAIModels).toHaveBeenCalledWith(
        "t",
        {
          id: "p1",
          provider: "anthropic",
          baseUrl: "",
          apiKey: undefined,
        },
        expect.any(AbortSignal),
      ),
    );
    await user.selectOptions(screen.getByLabelText("选择模型"), [
      "claude-sonnet-4-5",
    ]);
    const manualInput = screen.getByRole("textbox", { name: "模型" });
    expect(manualInput).toHaveValue("claude-sonnet-4-5");

    await user.clear(manualInput);
    await user.type(manualInput, "custom-model");
    expect(manualInput).toHaveValue("custom-model");
  });

  it("tests a saved profile's connection", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);
    await openDefaultProfile(user);

    await user.click(screen.getByRole("button", { name: "测试连接" }));
    await waitFor(() =>
      expect(testAIConnection).toHaveBeenCalledWith(
        "t",
        {
          id: "p1",
          provider: "anthropic",
          baseUrl: "",
          model: "claude-opus-4-8",
          temperature: 0.3,
          maxTokens: 1000,
          apiKey: undefined,
        },
        expect.any(AbortSignal),
      ),
    );
    expect(await screen.findByText(/连接成功/)).toBeInTheDocument();
  });

  it("preserves an explicit zero temperature", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);
    await openDefaultProfile(user);

    const temperature = screen.getByLabelText("温度");
    await user.clear(temperature);
    await user.type(temperature, "0");
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    await waitFor(() => expect(patchAISettings).toHaveBeenCalledTimes(1));
    expect(
      vi.mocked(patchAISettings).mock.calls[0][1].profiles[0].temperature,
    ).toBe(0);
  });

  it("tests a new unsaved profile with current form values", async () => {
    const user = userEvent.setup();
    vi.mocked(getAISettings).mockResolvedValue({
      profiles: [],
      autoSummary: false,
    });
    render(<SettingsWorkspace token="t" />);
    await screen.findByText(/还没有 AI 档案/);

    await user.click(screen.getByRole("button", { name: "新增档案" }));
    await user.type(screen.getByLabelText("Base URL"), "https://ai.example/v1");
    await user.type(screen.getByRole("textbox", { name: "模型" }), "gpt-test");
    await user.type(screen.getByLabelText("API 密钥"), "sk-test");
    await user.click(screen.getByRole("button", { name: "测试连接" }));

    await waitFor(() =>
      expect(testAIConnection).toHaveBeenCalledWith(
        "t",
        {
          id: undefined,
          provider: "anthropic",
          baseUrl: "https://ai.example/v1",
          model: "gpt-test",
          temperature: 0.3,
          maxTokens: 1000,
          apiKey: "sk-test",
        },
        expect.any(AbortSignal),
      ),
    );
    expect(
      screen.queryByText("请先保存后再测试连接。"),
    ).not.toBeInTheDocument();
  });

  it("shows the theme switcher under the appearance tab", async () => {
    const user = userEvent.setup();
    render(<SettingsWorkspace token="t" />);
    await screen.findByRole("button", { name: "配置" });

    await user.click(screen.getByRole("button", { name: "外观" }));
    expect(screen.getByText("主题色")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /切换主题/ }),
    ).toBeInTheDocument();
  });

  it("surfaces a failed connection test", async () => {
    const user = userEvent.setup();
    vi.mocked(testAIConnection).mockRejectedValue(new Error("连接失败：401"));
    render(<SettingsWorkspace token="t" />);
    await openDefaultProfile(user);

    await user.click(screen.getByRole("button", { name: "测试连接" }));
    expect(await screen.findByText("连接失败：401")).toBeInTheDocument();
  });
});
