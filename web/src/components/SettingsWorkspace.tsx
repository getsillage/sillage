import { useEffect, useState } from "react";
import {
  type AIProfile,
  type AIProfileInput,
  getAISettings,
  listAIModels,
  patchAISettings,
  testAIConnection,
} from "../lib/api";
import { ThemeToggle } from "./ThemeToggle";
import {
  dangerButtonClass,
  emptyStateClass,
  helperTextClass,
  inputClass,
  labelClass,
  panelClass,
  primaryButtonClass,
  secondaryButtonClass,
  selectClass,
  subtleButtonClass,
} from "./ui";

const PROVIDER_OPTIONS = [
  { value: "anthropic", label: "Anthropic Claude" },
  { value: "openai", label: "OpenAI" },
  { value: "workers-ai", label: "Cloudflare Workers AI" },
];

// Local editing copy: apiKeyInput holds a freshly typed key (empty keeps the
// stored key untouched). temperatureText/maxTokensText keep the raw input as a
// string so clearing or typing "0." never snaps back to a coerced number; they
// are parsed only at save time.
type EditableProfile = AIProfile & {
  apiKeyInput: string;
  temperatureText: string;
  maxTokensText: string;
};

function toEditable(profile: AIProfile): EditableProfile {
  return {
    ...profile,
    apiKeyInput: "",
    temperatureText: String(profile.temperature),
    maxTokensText: String(profile.maxTokens),
  };
}

function blankProfile(): EditableProfile {
  return {
    id: "",
    name: "新档案",
    provider: "anthropic",
    baseUrl: "",
    model: "",
    temperature: 0.3,
    maxTokens: 1000,
    enabled: true,
    active: false,
    hasApiKey: false,
    keyUnavailable: false,
    autoSummary: false,
    createdAt: "",
    updatedAt: "",
    apiKeyInput: "",
    temperatureText: "0.3",
    maxTokensText: "1000",
  };
}

// An empty or invalid field means "let the server default apply"; an explicit 0
// temperature is preserved.
function parseTemperature(text: string): number | undefined {
  const trimmed = text.trim();
  if (trimmed === "") {
    return undefined;
  }
  const value = Number.parseFloat(trimmed);
  return Number.isFinite(value) ? value : undefined;
}

function parseMaxTokens(text: string): number | undefined {
  const trimmed = text.trim();
  if (trimmed === "") {
    return undefined;
  }
  const value = Number.parseInt(trimmed, 10);
  return Number.isFinite(value) && value > 0 ? value : undefined;
}

function normalizeProfilesForSave(
  profiles: EditableProfile[],
): EditableProfile[] {
  if (profiles.length === 0) {
    return profiles;
  }
  const activeIndex = profiles.findIndex((profile) => profile.active);
  const defaultIndex = activeIndex >= 0 ? activeIndex : 0;
  return profiles.map((profile, index) => ({
    ...profile,
    enabled: true,
    active: index === defaultIndex,
  }));
}

type TestState = { status: "ok" | "error"; message: string };
type ModelState = {
  loading: boolean;
  models: string[];
  status?: "ok" | "error";
  message?: string;
};
type SettingsTab = "ai" | "appearance";

const ACTION_TIMEOUT_MS = 65_000;
const SETTINGS_TABS: { value: SettingsTab; label: string }[] = [
  { value: "ai", label: "AI" },
  { value: "appearance", label: "外观" },
];

function profileKey(profile: EditableProfile, index: number): string {
  return profile.id || `new-${index}`;
}

function providerLabel(provider: string): string {
  return (
    PROVIDER_OPTIONS.find((option) => option.value === provider)?.label ??
    provider
  );
}

async function withTimeout<T>(
  run: (signal: AbortSignal) => Promise<T>,
): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(
    () => controller.abort(),
    ACTION_TIMEOUT_MS,
  );
  try {
    return await run(controller.signal);
  } finally {
    window.clearTimeout(timeout);
  }
}

function actionErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof DOMException && cause.name === "AbortError") {
    return "请求超时，请稍后重试。";
  }
  return cause instanceof Error ? cause.message : fallback;
}

export function SettingsWorkspace({ token }: { token: string }) {
  const [activeTab, setActiveTab] = useState<SettingsTab>("ai");
  const [profiles, setProfiles] = useState<EditableProfile[]>([]);
  const [selectedProfileKey, setSelectedProfileKey] = useState<string | null>(
    null,
  );
  const [autoSummary, setAutoSummary] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [confirmDeleteKey, setConfirmDeleteKey] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [testingId, setTestingId] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, TestState>>({});
  const [modelResults, setModelResults] = useState<Record<string, ModelState>>(
    {},
  );

  useEffect(() => {
    let cancelled = false;
    getAISettings(token)
      .then((res) => {
        if (!cancelled) {
          setProfiles(res.profiles.map(toEditable));
          setAutoSummary(
            res.autoSummary ??
              res.profiles.some((profile) => profile.autoSummary),
          );
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "读取 AI 设置失败");
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  function updateProfile(index: number, patch: Partial<EditableProfile>) {
    setConfirmDeleteKey(null);
    setProfiles((current) =>
      current.map((profile, i) =>
        i === index ? { ...profile, ...patch } : profile,
      ),
    );
  }

  function setDefaultProfile(index: number) {
    const nextProfiles = profiles.map((profile, i) => ({
      ...profile,
      enabled: true,
      active: i === index,
    }));
    setConfirmDeleteKey(null);
    setProfiles(nextProfiles);
    setSaving(true);
    setNotice("");
    setError("");
    saveProfiles(nextProfiles, "已设为默认")
      .catch((err) => {
        setProfiles(profiles);
        setError(err instanceof Error ? err.message : "设置默认档案失败");
      })
      .finally(() => setSaving(false));
  }

  async function saveProfiles(
    nextProfiles: EditableProfile[],
    successNotice: string,
  ) {
    const normalizedProfiles = normalizeProfilesForSave(nextProfiles);
    const payload: AIProfileInput[] = normalizedProfiles.map((profile) => ({
      id: profile.id || undefined,
      name: profile.name,
      provider: profile.provider,
      baseUrl: profile.baseUrl,
      model: profile.model,
      temperature: parseTemperature(profile.temperatureText),
      maxTokens: parseMaxTokens(profile.maxTokensText),
      enabled: true,
      active: profile.active,
      apiKey: profile.apiKeyInput.trim() ? profile.apiKeyInput : undefined,
    }));
    const res = await patchAISettings(token, {
      profiles: payload,
      autoSummary,
    });
    setProfiles(res.profiles.map(toEditable));
    setAutoSummary(res.autoSummary ?? autoSummary);
    setConfirmDeleteKey(null);
    setNotice(successNotice);
  }

  async function removeProfile(index: number) {
    const profile = profiles[index];
    const key = profile ? profileKey(profile, index) : null;
    if (!profile) {
      return;
    }
    if (confirmDeleteKey !== key) {
      setConfirmDeleteKey(key);
      setNotice("");
      setError("");
      return;
    }
    if (!profile.id) {
      setProfiles((current) =>
        normalizeProfilesForSave(current.filter((_, i) => i !== index)),
      );
      setConfirmDeleteKey(null);
      if (selectedProfileKey === key) {
        setSelectedProfileKey(null);
      }
      return;
    }
    setDeletingId(profile.id);
    setNotice("");
    setError("");
    try {
      await saveProfiles(
        profiles.filter((_, i) => i !== index),
        "已删除",
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeletingId((current) => (current === profile.id ? null : current));
    }
    setConfirmDeleteKey(null);
    if (selectedProfileKey === key) {
      setSelectedProfileKey(null);
    }
  }

  async function save() {
    setSaving(true);
    setNotice("");
    setError("");
    try {
      await saveProfiles(profiles, "已保存");
      setSelectedProfileKey(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function testConnection(profile: EditableProfile, index: number) {
    const key = profileKey(profile, index);
    setTestingId(key);
    try {
      const res = await withTimeout((signal) =>
        testAIConnection(
          token,
          {
            id: profile.id || undefined,
            provider: profile.provider,
            baseUrl: profile.baseUrl,
            model: profile.model,
            temperature: parseTemperature(profile.temperatureText),
            maxTokens: parseMaxTokens(profile.maxTokensText),
            apiKey: profile.apiKeyInput.trim() || undefined,
          },
          signal,
        ),
      );
      setTestResults((current) => ({
        ...current,
        [key]: { status: "ok", message: `连接成功（${res.model}）` },
      }));
    } catch (cause) {
      setTestResults((current) => ({
        ...current,
        [key]: {
          status: "error",
          message: actionErrorMessage(cause, "连接失败"),
        },
      }));
    } finally {
      setTestingId((current) => (current === key ? null : current));
    }
  }

  async function fetchModels(profile: EditableProfile, index: number) {
    const key = profileKey(profile, index);
    setModelResults((current) => ({
      ...current,
      [key]: { ...(current[key] ?? { models: [] }), loading: true },
    }));
    try {
      const res = await withTimeout((signal) =>
        listAIModels(
          token,
          {
            id: profile.id || undefined,
            provider: profile.provider,
            baseUrl: profile.baseUrl,
            apiKey: profile.apiKeyInput.trim() || undefined,
          },
          signal,
        ),
      );
      setModelResults((current) => ({
        ...current,
        [key]: {
          loading: false,
          models: res.models,
          status: "ok",
          message: res.models.length > 0 ? "已获取模型列表" : "没有可用模型",
        },
      }));
    } catch (cause) {
      setModelResults((current) => ({
        ...current,
        [key]: {
          ...(current[key] ?? { models: [] }),
          loading: false,
          status: "error",
          message: actionErrorMessage(cause, "获取模型失败"),
        },
      }));
    }
  }

  const selectedProfileIndex = selectedProfileKey
    ? profiles.findIndex(
        (profile, index) => profileKey(profile, index) === selectedProfileKey,
      )
    : -1;
  const selectedProfile =
    selectedProfileIndex >= 0 ? profiles[selectedProfileIndex] : null;

  if (loading) {
    return (
      <p className="text-gray-400 text-sm dark:text-gray-500">正在读取设置…</p>
    );
  }

  return (
    <div className="space-y-5">
      <div className="inline-flex gap-0.5 rounded-lg border border-gray-200 bg-gray-100/70 p-0.5 dark:border-gray-800 dark:bg-gray-950">
        {SETTINGS_TABS.map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setActiveTab(tab.value)}
            className={`h-8 rounded-md px-3 text-sm transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:focus-visible:ring-gray-500/40 ${
              activeTab === tab.value
                ? "bg-white font-medium text-gray-900 shadow-sm shadow-gray-900/[0.03] dark:bg-gray-800 dark:text-gray-50"
                : "text-gray-500 hover:bg-white/70 hover:text-gray-900 dark:text-gray-400 dark:hover:bg-gray-900 dark:hover:text-gray-100"
            }`}
            aria-current={activeTab === tab.value ? "page" : undefined}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === "appearance" ? (
        <section className={`${panelClass} p-4 sm:p-5`}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="font-medium text-gray-900 text-sm dark:text-gray-50">
                主题色
              </h2>
              <p className={helperTextClass}>切换浅色和深色界面。</p>
            </div>
            <ThemeToggle />
          </div>
        </section>
      ) : null}

      {activeTab === "ai" ? (
        <>
          <div className="flex items-center justify-between gap-3">
            <p className={helperTextClass}>
              密钥加密保存在本地服务端，不会回显。
            </p>
            <button
              type="button"
              onClick={() => {
                setSelectedProfileKey(`new-${profiles.length}`);
                setProfiles((current) => [
                  ...current,
                  { ...blankProfile(), active: current.length === 0 },
                ]);
              }}
              className={secondaryButtonClass}
            >
              新增档案
            </button>
          </div>

          <section className={`${panelClass} p-4 sm:p-5`}>
            <label className="inline-flex items-center gap-2 text-gray-700 text-sm dark:text-gray-300">
              <input
                type="checkbox"
                className="accent-gray-900 dark:accent-gray-100"
                checked={autoSummary}
                onChange={(event) => setAutoSummary(event.target.checked)}
              />
              新建记录后自动总结
            </label>
          </section>

          {profiles.length === 0 ? (
            <div className={emptyStateClass}>
              还没有 AI 档案。点击「新增档案」添加一个。
            </div>
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {profiles.map((profile, index) => {
                  const key = profileKey(profile, index);
                  const isSelected = key === selectedProfileKey;
                  return (
                    <article
                      key={key}
                      className={`${panelClass} min-h-32 w-full p-4 text-left transition hover:border-gray-300 hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:hover:border-gray-700 dark:hover:bg-gray-900 ${
                        isSelected
                          ? "border-gray-400 bg-gray-50 dark:border-gray-500 dark:bg-gray-900"
                          : ""
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <h3 className="truncate font-medium text-gray-900 text-sm dark:text-gray-50">
                            {profile.name || "未命名档案"}
                          </h3>
                          <p className="mt-1 truncate text-gray-500 text-xs dark:text-gray-400">
                            {providerLabel(profile.provider)}
                          </p>
                        </div>
                        {profile.active ? (
                          <span className="shrink-0 rounded-full bg-gray-900 px-2 py-0.5 text-[11px] font-medium text-white dark:bg-gray-100 dark:text-gray-900">
                            默认
                          </span>
                        ) : null}
                      </div>
                      <p className="mt-4 truncate text-gray-700 text-sm dark:text-gray-200">
                        {profile.model || "未设置模型"}
                      </p>
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500 dark:bg-gray-800 dark:text-gray-400">
                          {profile.hasApiKey || profile.apiKeyInput
                            ? "有密钥"
                            : "无密钥"}
                        </span>
                        {profile.keyUnavailable ? (
                          <span className="rounded-full bg-red-50 px-2 py-0.5 text-[11px] text-red-600 dark:bg-red-950/35 dark:text-red-300">
                            密钥异常
                          </span>
                        ) : null}
                      </div>
                      <div className="mt-4 flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setSelectedProfileKey(key)}
                          className={subtleButtonClass}
                        >
                          配置
                        </button>
                        <button
                          type="button"
                          onClick={() => setDefaultProfile(index)}
                          disabled={profile.active || saving}
                          className={secondaryButtonClass}
                        >
                          {profile.active ? "当前默认" : "设为默认"}
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>

              {selectedProfile ? (
                (() => {
                  const index = selectedProfileIndex;
                  const profile = selectedProfile;
                  const key = profileKey(profile, index);
                  const modelState = modelResults[key];
                  const testState = testResults[key];
                  return (
                    <article key={key} className={`${panelClass} p-4 sm:p-5`}>
                      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <h3 className="font-medium text-gray-900 text-sm dark:text-gray-50">
                            详细配置
                          </h3>
                          <p className={helperTextClass}>
                            修改当前档案后点击保存设置生效。
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() => setSelectedProfileKey(null)}
                          className={subtleButtonClass}
                        >
                          收起
                        </button>
                      </div>
                      <div className="grid gap-4 sm:grid-cols-2">
                        <label className="block">
                          <span className={labelClass}>名称</span>
                          <input
                            className={inputClass}
                            value={profile.name}
                            onChange={(event) =>
                              updateProfile(index, { name: event.target.value })
                            }
                          />
                        </label>
                        <label className="block">
                          <span className={labelClass}>Provider</span>
                          <select
                            className={selectClass}
                            value={profile.provider}
                            onChange={(event) =>
                              updateProfile(index, {
                                provider: event.target.value,
                              })
                            }
                          >
                            {PROVIDER_OPTIONS.map((option) => (
                              <option key={option.value} value={option.value}>
                                {option.label}
                              </option>
                            ))}
                            {!PROVIDER_OPTIONS.some(
                              (option) => option.value === profile.provider,
                            ) && (
                              <option value={profile.provider}>
                                {profile.provider}
                              </option>
                            )}
                          </select>
                        </label>
                        <label className="block">
                          <span className={labelClass}>Base URL</span>
                          <input
                            className={inputClass}
                            value={profile.baseUrl}
                            placeholder="https://api.anthropic.com"
                            onChange={(event) =>
                              updateProfile(index, {
                                baseUrl: event.target.value,
                              })
                            }
                          />
                        </label>
                        <div className="block">
                          <span className={labelClass}>模型</span>
                          <div className="mt-1 flex gap-2">
                            <input
                              aria-label="模型"
                              className={`${inputClass} mt-0`}
                              value={profile.model}
                              placeholder="claude-opus-4-8"
                              onChange={(event) =>
                                updateProfile(index, {
                                  model: event.target.value,
                                })
                              }
                            />
                            <button
                              type="button"
                              onClick={() => fetchModels(profile, index)}
                              disabled={modelState?.loading}
                              className={`${secondaryButtonClass} shrink-0`}
                            >
                              {modelState?.loading ? "获取中…" : "获取模型"}
                            </button>
                          </div>
                          {modelState?.models.length ? (
                            <select
                              aria-label="选择模型"
                              className={selectClass}
                              value={profile.model}
                              onChange={(event) =>
                                updateProfile(index, {
                                  model: event.target.value,
                                })
                              }
                            >
                              {!modelState.models.includes(profile.model) && (
                                <option value={profile.model}>
                                  {profile.model || "手动输入模型"}
                                </option>
                              )}
                              {modelState.models.map((model) => (
                                <option key={model} value={model}>
                                  {model}
                                </option>
                              ))}
                            </select>
                          ) : null}
                          {modelState?.message ? (
                            <p
                              className={`mt-1 text-xs ${
                                modelState.status === "error"
                                  ? "text-red-600 dark:text-red-400"
                                  : "text-gray-500 dark:text-gray-400"
                              }`}
                            >
                              {modelState.message}
                            </p>
                          ) : null}
                        </div>
                        <label className="block">
                          <span className={labelClass}>温度</span>
                          <input
                            className={inputClass}
                            type="number"
                            step="0.1"
                            min="0"
                            max="2"
                            value={profile.temperatureText}
                            onChange={(event) =>
                              updateProfile(index, {
                                temperatureText: event.target.value,
                              })
                            }
                          />
                        </label>
                        <label className="block">
                          <span className={labelClass}>最大 Tokens</span>
                          <input
                            className={inputClass}
                            type="number"
                            min="1"
                            value={profile.maxTokensText}
                            onChange={(event) =>
                              updateProfile(index, {
                                maxTokensText: event.target.value,
                              })
                            }
                          />
                        </label>
                        <label className="block sm:col-span-2">
                          <span className={labelClass}>API 密钥</span>
                          <input
                            className={inputClass}
                            type="password"
                            autoComplete="off"
                            value={profile.apiKeyInput}
                            placeholder={
                              profile.hasApiKey
                                ? "已配置，留空保持不变"
                                : "未配置"
                            }
                            onChange={(event) =>
                              updateProfile(index, {
                                apiKeyInput: event.target.value,
                              })
                            }
                          />
                          {profile.keyUnavailable && (
                            <span className="mt-1 block text-red-600 text-xs dark:text-red-400">
                              当前密钥无法解密，请重新填写。
                            </span>
                          )}
                        </label>
                      </div>

                      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-gray-200/70 border-t pt-3 dark:border-gray-800">
                        <p className={helperTextClass}>
                          {profile.active
                            ? "当前默认档案会用于 AI 总结和 Ask。"
                            : "可在上方档案卡片中设为默认。"}
                        </p>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => testConnection(profile, index)}
                            disabled={testingId === key}
                            className={subtleButtonClass}
                          >
                            {testingId === key ? "测试中…" : "测试连接"}
                          </button>
                          <button
                            type="button"
                            onClick={() => removeProfile(index)}
                            disabled={deletingId === profile.id}
                            className={dangerButtonClass}
                          >
                            {deletingId === profile.id
                              ? "删除中…"
                              : confirmDeleteKey === key
                                ? "确认删除"
                                : "删除"}
                          </button>
                        </div>
                      </div>
                      {testState ? (
                        <p
                          className={`mt-2 text-xs ${
                            testState.status === "ok"
                              ? "text-gray-500 dark:text-gray-400"
                              : "text-red-600 dark:text-red-400"
                          }`}
                        >
                          {testState.message}
                        </p>
                      ) : null}
                    </article>
                  );
                })()
              ) : (
                <p className={helperTextClass}>
                  点击一个档案卡片进行详细配置。
                </p>
              )}
            </div>
          )}

          <div className="flex flex-wrap items-center justify-end gap-3">
            {error ? (
              <p className="mr-auto text-red-600 text-sm dark:text-red-400">
                {error}
              </p>
            ) : null}
            {notice ? (
              <p className="mr-auto text-gray-500 text-sm dark:text-gray-400">
                {notice}
              </p>
            ) : null}
            <button
              type="button"
              disabled={saving}
              onClick={save}
              className={primaryButtonClass}
            >
              {saving ? "保存中…" : "保存设置"}
            </button>
          </div>
        </>
      ) : null}
    </div>
  );
}
