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

vi.mock("./UnsavedNavigationGuard", () => ({
  UnsavedNavigationGuard: () => null,
  useUnsavedChangesRegistration: () => undefined,
}));

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

function renderSettings() {
  return render(<SettingsWorkspace token="t" />);
}

describe("SettingsWorkspace", () => {
  async function openDefaultProfile(user: ReturnType<typeof userEvent.setup>) {
    await user.click(await screen.findByRole("button", { name: "配置" }));
  }

  it("shows a locked load error state and retries successfully", async () => {
    const user = userEvent.setup();
    vi.mocked(getAISettings).mockRejectedValueOnce(
      new Error("读取失败：网络异常"),
    );
    renderSettings();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "读取失败：网络异常",
    );
    expect(
      screen.queryByRole("button", { name: "新增档案" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("switch", { name: "新建记录后自动总结" }),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "重新加载" }));
    expect(await screen.findByRole("button", { name: "配置" })).toBeEnabled();
    expect(getAISettings).toHaveBeenCalledTimes(2);
  });

  it("loads profiles and saves edits", async () => {
    const user = userEvent.setup();
    renderSettings();

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

  it("keeps the save bar available after switching to appearance", async () => {
    const user = userEvent.setup();
    vi.mocked(patchAISettings).mockResolvedValue({
      profiles: [profile({ name: "跨标签保存" })],
      autoSummary: false,
    });
    renderSettings();

    await openDefaultProfile(user);
    const nameInput = screen.getByDisplayValue("默认");
    await user.clear(nameInput);
    await user.type(nameInput, "跨标签保存");
    await user.click(screen.getByRole("button", { name: "外观" }));

    expect(screen.getByText("主题色")).toBeInTheDocument();
    expect(screen.getByText("有未保存更改")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    expect(await screen.findByText("已保存")).toBeInTheDocument();
    expect(screen.getByText("主题色")).toBeInTheDocument();
    expect(vi.mocked(patchAISettings).mock.calls[0][1].profiles[0].name).toBe(
      "跨标签保存",
    );
  });

  it("warns before unload only while settings differ from the server baseline", async () => {
    const user = userEvent.setup();
    vi.mocked(patchAISettings).mockResolvedValue({
      profiles: [profile({ name: "工作档案" })],
      autoSummary: false,
    });
    renderSettings();

    await openDefaultProfile(user);
    const cleanUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(cleanUnload)).toBe(true);

    const nameInput = screen.getByDisplayValue("默认");
    await user.clear(nameInput);
    await user.type(nameInput, "工作档案");
    expect(screen.getByText("有未保存更改")).toBeInTheDocument();
    const dirtyUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(dirtyUnload)).toBe(false);

    await user.click(screen.getByRole("button", { name: "保存设置" }));
    await screen.findByText("已保存");
    expect(screen.queryByText("有未保存更改")).not.toBeInTheDocument();
    const savedUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(savedUnload)).toBe(true);
  });

  it("keeps settings dirty when saving fails", async () => {
    const user = userEvent.setup();
    vi.mocked(patchAISettings).mockRejectedValue(
      new Error("保存失败：网络异常"),
    );
    renderSettings();

    await openDefaultProfile(user);
    await user.type(screen.getByDisplayValue("默认"), "更新");
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "保存失败：网络异常",
    );
    expect(screen.getByText("有未保存更改")).toBeInTheDocument();
    const failedSaveUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(failedSaveUnload)).toBe(false);
  });

  it("locks mutable settings while a save response is pending", async () => {
    const user = userEvent.setup();
    let finishSave:
      | ((value: { profiles: AIProfile[]; autoSummary: boolean }) => void)
      | undefined;
    vi.mocked(patchAISettings).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishSave = resolve;
        }),
    );
    renderSettings();

    await openDefaultProfile(user);
    const nameInput = screen.getByDisplayValue("默认");
    await user.clear(nameInput);
    await user.type(nameInput, "保存快照");
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    expect(nameInput).toBeDisabled();
    expect(
      screen.getByRole("switch", { name: "新建记录后自动总结" }),
    ).toBeDisabled();
    finishSave?.({
      profiles: [profile({ name: "保存快照" })],
      autoSummary: false,
    });
    expect(await screen.findByText("已保存")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "配置" }));
    expect(screen.getByDisplayValue("保存快照")).toBeEnabled();
  });

  it("tracks auto-summary changes against the loaded value", async () => {
    const user = userEvent.setup();
    renderSettings();
    await screen.findByRole("button", { name: "配置" });

    const autoSummarySwitch = screen.getByRole("switch", {
      name: "新建记录后自动总结",
    });
    expect(
      screen.queryByRole("button", { name: "保存设置" }),
    ).not.toBeInTheDocument();
    await user.click(autoSummarySwitch);
    expect(screen.getByText("有未保存更改")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "保存设置" }),
    ).toBeInTheDocument();

    await user.click(autoSummarySwitch);
    expect(screen.queryByText("有未保存更改")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "保存设置" }),
    ).not.toBeInTheDocument();
    const revertedUnload = new Event("beforeunload", { cancelable: true });
    expect(window.dispatchEvent(revertedUnload)).toBe(true);
  });

  it("saves auto-summary as a global setting", async () => {
    const user = userEvent.setup();
    renderSettings();
    await screen.findByRole("button", { name: "配置" });

    const checkbox = screen.getByRole("switch", {
      name: "新建记录后自动总结",
    });
    await user.click(checkbox);
    expect(checkbox).toHaveAttribute("aria-checked", "true");
    await user.click(screen.getByRole("button", { name: "保存设置" }));
    await waitFor(() => expect(patchAISettings).toHaveBeenCalled());
    expect(vi.mocked(patchAISettings).mock.calls[0][1].autoSummary).toBe(true);
    expect(
      vi.mocked(patchAISettings).mock.calls[0][1].profiles[0],
    ).not.toHaveProperty("autoSummary");
  });

  it("tracks and clears a newly saved API key", async () => {
    const user = userEvent.setup();
    renderSettings();
    await openDefaultProfile(user);

    await user.type(screen.getByLabelText("API 密钥"), "sk-new");
    expect(screen.getByText("有未保存更改")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "保存设置" }));

    await waitFor(() => expect(patchAISettings).toHaveBeenCalledTimes(1));
    expect(vi.mocked(patchAISettings).mock.calls[0][1].profiles[0].apiKey).toBe(
      "sk-new",
    );
    expect(await screen.findByText("已保存")).toBeInTheDocument();
    expect(screen.queryByText("有未保存更改")).not.toBeInTheDocument();
    await openDefaultProfile(user);
    expect(screen.getByLabelText("API 密钥")).toHaveValue("");
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
    renderSettings();

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

  it("requires saving other edits before setting a default profile", async () => {
    const user = userEvent.setup();
    vi.mocked(getAISettings).mockResolvedValue({
      profiles: [
        profile({ id: "p1", name: "工作", active: true }),
        profile({ id: "p2", name: "生活", active: false }),
      ],
      autoSummary: false,
    });
    renderSettings();

    await user.click(
      (await screen.findAllByRole("button", { name: "配置" }))[0],
    );
    const nameInput = screen.getByDisplayValue("工作");
    await user.clear(nameInput);
    await user.type(nameInput, "工作更新");

    const lifeCard = screen.getByText("生活").closest("article");
    expect(lifeCard).not.toBeNull();
    await user.click(
      within(lifeCard as HTMLElement).getByRole("button", { name: "设为默认" }),
    );

    expect(patchAISettings).not.toHaveBeenCalled();
    expect(
      screen.getByText("请先保存当前更改，再设置默认档案。"),
    ).toBeInTheDocument();
    expect(screen.getByText("有未保存更改")).toBeInTheDocument();
  });

  it("requires confirmation before deleting a saved profile", async () => {
    const user = userEvent.setup();
    vi.mocked(patchAISettings).mockResolvedValue({
      profiles: [],
      autoSummary: false,
    });
    renderSettings();
    await openDefaultProfile(user);

    await user.click(screen.getByRole("button", { name: "删除" }));
    expect(patchAISettings).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "确认删除" }));
    await waitFor(() => expect(patchAISettings).toHaveBeenCalledTimes(1));
    expect(vi.mocked(patchAISettings).mock.calls[0][1].profiles).toEqual([]);
    expect(await screen.findByText("已删除")).toBeInTheDocument();
  });

  it("does not delete a saved profile while other settings are dirty", async () => {
    const user = userEvent.setup();
    vi.mocked(getAISettings).mockResolvedValue({
      profiles: [
        profile({ id: "p1", name: "工作", active: true }),
        profile({ id: "p2", name: "生活", active: false }),
      ],
      autoSummary: false,
    });
    renderSettings();

    const configureButtons = await screen.findAllByRole("button", {
      name: "配置",
    });
    await user.click(configureButtons[0]);
    await user.type(screen.getByDisplayValue("工作"), "更新");
    await user.click(configureButtons[1]);
    await user.click(screen.getByRole("button", { name: "删除" }));

    expect(patchAISettings).not.toHaveBeenCalled();
    expect(
      screen.getByText("请先保存当前更改，再删除档案。"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "确认删除" }),
    ).not.toBeInTheDocument();
  });

  it("fetches models while keeping manual model input editable", async () => {
    const user = userEvent.setup();
    renderSettings();
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
    renderSettings();
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
    renderSettings();
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
    renderSettings();
    await screen.findByText(/还没有 AI 档案/);

    await user.click(screen.getByRole("button", { name: "新增档案" }));
    await user.type(screen.getByLabelText("接口地址"), "https://ai.example/v1");
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
    renderSettings();
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
    renderSettings();
    await openDefaultProfile(user);

    await user.click(screen.getByRole("button", { name: "测试连接" }));
    expect(await screen.findByText("连接失败：401")).toBeInTheDocument();
  });
});
