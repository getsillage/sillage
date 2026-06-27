import { useEffect, useState } from "react";
import {
  type AIProfile,
  type AIProfileInput,
  getAISettings,
  listAIModels,
  patchAISettings,
  testAIConnection,
} from "../lib/api";
import {
  dangerButtonClass,
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

// Local editing copy: apiKeyInput holds a freshly typed key. Empty means keep
// the stored key untouched (the server only returns hasApiKey, never the key).
type EditableProfile = AIProfile & { apiKeyInput: string };

function toEditable(profile: AIProfile): EditableProfile {
  return { ...profile, apiKeyInput: "" };
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
  };
}

type TestState = { status: "ok" | "error"; message: string };
type ModelState = {
  loading: boolean;
  models: string[];
  status?: "ok" | "error";
  message?: string;
};

const ACTION_TIMEOUT_MS = 65_000;

function profileKey(profile: EditableProfile, index: number): string {
  return profile.id || `new-${index}`;
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
  const [profiles, setProfiles] = useState<EditableProfile[]>([]);
  const [autoSummary, setAutoSummary] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
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
    setProfiles((current) =>
      current.map((profile, i) =>
        i === index ? { ...profile, ...patch } : profile,
      ),
    );
  }

  function removeProfile(index: number) {
    setProfiles((current) => current.filter((_, i) => i !== index));
  }

  async function save() {
    setSaving(true);
    setNotice("");
    setError("");
    try {
      const payload: AIProfileInput[] = profiles.map((profile) => ({
        id: profile.id || undefined,
        name: profile.name,
        provider: profile.provider,
        baseUrl: profile.baseUrl,
        model: profile.model,
        temperature: profile.temperature,
        maxTokens: profile.maxTokens,
        enabled: profile.enabled,
        active: profile.active,
        apiKey: profile.apiKeyInput.trim() ? profile.apiKeyInput : undefined,
      }));
      const res = await patchAISettings(token, {
        profiles: payload,
        autoSummary,
      });
      setProfiles(res.profiles.map(toEditable));
      setAutoSummary(res.autoSummary ?? autoSummary);
      setNotice("已保存");
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  // Tests a saved profile. Requires a stored id, so it nudges the user to save
  // first when testing a brand-new, unsaved profile.
  async function testConnection(profile: EditableProfile, index: number) {
    const key = profileKey(profile, index);
    if (!profile.id) {
      setTestResults((current) => ({
        ...current,
        [key]: { status: "error", message: "请先保存后再测试连接。" },
      }));
      return;
    }
    setTestingId(key);
    try {
      const res = await withTimeout((signal) =>
        testAIConnection(token, profile.id, signal),
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

  if (loading) {
    return (
      <p className="text-gray-400 text-sm dark:text-gray-500">正在读取设置…</p>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between gap-3">
        <p className={helperTextClass}>密钥加密保存在本地服务端，不会回显。</p>
        <button
          type="button"
          onClick={() => setProfiles((current) => [...current, blankProfile()])}
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
        <div className="rounded-lg bg-gray-100/60 px-4 py-10 text-center text-gray-500 text-sm dark:bg-gray-900/50 dark:text-gray-400">
          还没有 AI 档案。点击「新增档案」添加一个。
        </div>
      ) : (
        <div className="space-y-4">
          {profiles.map((profile, index) => {
            const key = profileKey(profile, index);
            const modelState = modelResults[key];
            const testState = testResults[key];
            return (
              <article key={key} className={`${panelClass} p-4 sm:p-5`}>
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
                        updateProfile(index, { provider: event.target.value })
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
                        updateProfile(index, { baseUrl: event.target.value })
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
                          updateProfile(index, { model: event.target.value })
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
                          updateProfile(index, { model: event.target.value })
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
                      value={profile.temperature}
                      onChange={(event) =>
                        updateProfile(index, {
                          temperature:
                            Number.parseFloat(event.target.value) || 0,
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
                      value={profile.maxTokens}
                      onChange={(event) =>
                        updateProfile(index, {
                          maxTokens:
                            Number.parseInt(event.target.value, 10) || 0,
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
                        profile.hasApiKey ? "已配置，留空保持不变" : "未配置"
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

                <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-gray-100 border-t pt-3 dark:border-gray-800">
                  <div className="flex flex-wrap items-center gap-4 text-gray-600 text-sm dark:text-gray-300">
                    <label className="inline-flex items-center gap-2">
                      <input
                        type="checkbox"
                        className="accent-gray-900 dark:accent-gray-100"
                        checked={profile.enabled}
                        onChange={(event) =>
                          updateProfile(index, {
                            enabled: event.target.checked,
                          })
                        }
                      />
                      启用
                    </label>
                    <label className="inline-flex items-center gap-2">
                      <input
                        type="checkbox"
                        className="accent-gray-900 dark:accent-gray-100"
                        checked={profile.active}
                        onChange={(event) =>
                          updateProfile(index, { active: event.target.checked })
                        }
                      />
                      设为默认
                    </label>
                  </div>
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
                      className={dangerButtonClass}
                    >
                      删除
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
          })}
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
    </div>
  );
}
