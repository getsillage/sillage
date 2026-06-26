import { env } from "cloudflare:workers";
import { useCallback, useEffect, useRef, useState } from "react";
import { Form, useFetcher, useNavigation } from "react-router";
import { BackupSection } from "~/components/BackupSection";
import { SuggestedInput } from "~/components/SuggestedInput";
import { ThemeToggle } from "~/components/ThemeToggle";
import { pageLeadClass, pageTitleClass, primaryButtonClass, wideShellClass } from "~/components/ui";
import {
  DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
  ENTRY_INSIGHT_AUTO_MODES,
  type EntryInsightAutoMode,
} from "~/lib/ai/entry-insights.shared";
import { listAiModels } from "~/lib/ai/models";
import { testAiConnection } from "~/lib/ai/test-connection";
import { requireSession } from "~/lib/auth/session";
import { exportSillageBackup } from "~/lib/backup/export";
import { listBackups } from "~/lib/backup/list";
import {
  type AiProtocol,
  type AiSettingsView,
  activateAiSettingsProfile,
  aiProviderCredentialsSchema,
  aiSettingsInputSchema,
  deleteAiSettingsProfile,
  loadAiSettingsProfile,
  loadAiSettingsView,
  saveAiSettings,
  saveEntryInsightAutoMode,
} from "~/lib/settings/ai-settings";
import type { Route } from "./+types/settings";

const PROTOCOL_DEFAULTS: Record<AiProtocol, { baseUrl: string; model: string }> = {
  anthropic: { baseUrl: "https://api.anthropic.com", model: "" },
  openai: { baseUrl: "https://api.openai.com/v1", model: "" },
};
const LEGACY_DEFAULT_MODELS = new Set(["claude-opus-4-8", "gpt-5.1-mini"]);

const ENTRY_INSIGHT_AUTO_MODE_LABELS: Record<EntryInsightAutoMode, string> = {
  all: "保存后自动生成",
  off: "手动生成",
};

type SettingsActionData = {
  intent: "save" | "test" | "models" | "delete" | "activate" | "export";
  ok: boolean;
  message: string;
  models?: string[];
  profileId?: string;
};

export function meta(_: Route.MetaArgs) {
  return [{ title: "设置 · Sillage" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  const [settings, backups] = await Promise.all([loadAiSettingsView(env), listBackups(env)]);
  return { settings, backups };
}

function parseSettingsForm(form: FormData) {
  const entryInsightAutoMode = String(
    form.get("entryInsightAutoMode") ?? DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
  );
  return {
    id: String(form.get("id") ?? ""),
    name: String(form.get("name") ?? ""),
    enabled: form.get("enabled") === "on",
    protocol: String(form.get("protocol") ?? "anthropic"),
    baseUrl: String(form.get("baseUrl") ?? ""),
    model: String(form.get("model") ?? ""),
    apiKey: String(form.get("apiKey") ?? ""),
    entryInsightAutoMode: ENTRY_INSIGHT_AUTO_MODES.includes(
      entryInsightAutoMode as EntryInsightAutoMode,
    )
      ? (entryInsightAutoMode as EntryInsightAutoMode)
      : DEFAULT_ENTRY_INSIGHT_AUTO_MODE,
  };
}

function parseCredentialsForm(form: FormData) {
  return {
    id: String(form.get("id") ?? ""),
    protocol: String(form.get("protocol") ?? "anthropic"),
    baseUrl: String(form.get("baseUrl") ?? ""),
    apiKey: String(form.get("apiKey") ?? ""),
  };
}

async function resolveApiKey(id: string | undefined, apiKey: string): Promise<string> {
  if (apiKey || !id) {
    return apiKey;
  }
  return (await loadAiSettingsProfile(env, id))?.apiKey ?? "";
}

export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const rawIntent = String(form.get("intent") ?? "save");

  if (rawIntent === "export") {
    try {
      const result = await exportSillageBackup(env);
      return { intent: "export" as const, ok: true, message: `已导出 ${result.entryCount} 条记录` };
    } catch (error) {
      return {
        intent: "export" as const,
        ok: false,
        message: error instanceof Error ? error.message : "导出失败",
      };
    }
  }

  const intent = (
    ["save", "test", "models", "delete", "activate"].includes(rawIntent) ? rawIntent : "save"
  ) as SettingsActionData["intent"];

  if (intent === "delete" || intent === "activate") {
    const id = String(form.get("id") ?? "").trim();
    if (!id) {
      return { intent, ok: false, message: "请选择配置" };
    }
    if (intent === "delete") {
      const deleted = await deleteAiSettingsProfile(env, id);
      return { intent, ok: deleted, message: deleted ? "已删除配置" : "配置不存在" };
    }
    const activated = await activateAiSettingsProfile(env, id);
    return { intent, ok: activated, message: activated ? "已切换当前配置" : "配置不存在" };
  }

  if (intent === "models") {
    const parsed = aiProviderCredentialsSchema.safeParse(parseCredentialsForm(form));
    if (!parsed.success) {
      return { intent, ok: false, message: parsed.error.issues[0]?.message ?? "配置无效" };
    }
    const apiKey = await resolveApiKey(parsed.data.id, parsed.data.apiKey);
    const result = await listAiModels({
      protocol: parsed.data.protocol,
      baseUrl: parsed.data.baseUrl,
      apiKey,
    });
    return { intent, ok: result.ok, message: result.message, models: result.models };
  }

  if (intent === "test") {
    const parsed = aiSettingsInputSchema.safeParse(parseSettingsForm(form));
    if (!parsed.success) {
      return { intent, ok: false, message: parsed.error.issues[0]?.message ?? "配置无效" };
    }
    const apiKey = await resolveApiKey(parsed.data.id, parsed.data.apiKey);
    const result = await testAiConnection({
      protocol: parsed.data.protocol,
      baseUrl: parsed.data.baseUrl,
      model: parsed.data.model,
      apiKey,
    });
    return { intent, ok: result.ok, message: result.message };
  }

  const rawSettings = parseSettingsForm(form);
  const parsed = aiSettingsInputSchema.safeParse(rawSettings);
  if (!parsed.success) {
    return { intent, ok: false, message: parsed.error.issues[0]?.message ?? "配置无效" };
  }

  const profileId = await saveAiSettings(env, parsed.data);
  await saveEntryInsightAutoMode(env, rawSettings.entryInsightAutoMode);
  return { intent, ok: true, message: "已保存并设为当前配置", profileId };
}

const inputClass =
  "mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-gray-900 text-sm placeholder:text-gray-400 focus:border-celadon-600 focus:outline-none focus:ring-2 focus:ring-celadon-600/20 disabled:border-gray-300 disabled:bg-gray-100 disabled:text-gray-600 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-50 dark:placeholder:text-gray-500 dark:focus:border-celadon-400 dark:focus:ring-celadon-400/30 dark:disabled:bg-gray-900 dark:disabled:text-gray-500";
const secondaryButtonClass =
  "inline-flex items-center justify-center rounded-lg border border-gray-300 bg-white px-3 py-2 font-medium text-gray-800 text-sm hover:bg-gray-100 disabled:border-gray-300 disabled:bg-gray-100 disabled:text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:hover:bg-gray-800 dark:disabled:border-gray-800 dark:disabled:bg-gray-900 dark:disabled:text-gray-600";
const subtleTextClass = "text-gray-700 dark:text-gray-400";
const labelClass = "block text-gray-700 text-sm font-medium dark:text-gray-300";
const statusClass = (ok: boolean) =>
  `rounded-lg border px-3 py-2 text-sm ${
    ok
      ? "border-green-300 bg-green-50 text-green-800 dark:border-green-900/70 dark:bg-green-950/50 dark:text-green-200"
      : "border-red-300 bg-red-50 text-red-800 dark:border-red-900/70 dark:bg-red-950/50 dark:text-red-200"
  }`;

function defaultProfile(protocol: AiProtocol = "anthropic") {
  return {
    id: "",
    name: protocol === "anthropic" ? "Claude" : "OpenAI",
    enabled: true,
    protocol,
    baseUrl: PROTOCOL_DEFAULTS[protocol].baseUrl,
    model: PROTOCOL_DEFAULTS[protocol].model,
    hasApiKey: false,
  } satisfies AiSettingsView;
}

export default function Settings({ loaderData, actionData }: Route.ComponentProps) {
  const { settings, backups } = loaderData;
  const navigation = useNavigation();
  const modelFetcher = useFetcher<SettingsActionData>();
  const busy = navigation.state === "submitting";
  const fetchingModels = modelFetcher.state !== "idle";
  const profiles = settings.profiles;
  const activeProfile =
    profiles.find((profile) => profile.id === settings.activeProfileId) ?? profiles[0] ?? null;
  const initialProfile = activeProfile ?? defaultProfile();

  const [selectedProfileId, setSelectedProfileId] = useState(initialProfile.id);
  const [name, setName] = useState(initialProfile.name);
  const [enabled, setEnabled] = useState(initialProfile.enabled);
  const [protocol, setProtocol] = useState<AiProtocol>(initialProfile.protocol);
  const [baseUrl, setBaseUrl] = useState(initialProfile.baseUrl);
  const [apiKey, setApiKey] = useState("");
  const [model, setModel] = useState(initialProfile.model);
  const [modelOptions, setModelOptions] = useState<string[]>([]);
  const [modelOptionsKey, setModelOptionsKey] = useState("");
  const [entryInsightAutoMode, setEntryInsightAutoMode] = useState<EntryInsightAutoMode>(
    settings.entryInsightAutoMode,
  );
  const appliedActiveProfileIdRef = useRef(initialProfile.id);

  const selectedProfile = profiles.find((profile) => profile.id === selectedProfileId) ?? null;
  const hasStoredApiKey = selectedProfile?.hasApiKey ?? false;
  const modelData = modelFetcher.data;
  const currentModelOptionsKey = `${selectedProfileId}\n${protocol}\n${baseUrl.trim()}`;
  const fetchedModelOptions =
    modelOptionsKey === currentModelOptionsKey &&
    modelData?.intent === "models" &&
    modelData.ok &&
    modelData.models
      ? modelData.models
      : [];
  const visibleModelOptions = modelOptions.length > 0 ? modelOptions : fetchedModelOptions;

  const applyProfile = useCallback((profile: AiSettingsView) => {
    setSelectedProfileId(profile.id);
    setName(profile.name);
    setEnabled(profile.enabled);
    setProtocol(profile.protocol);
    setBaseUrl(profile.baseUrl);
    setModel(profile.model);
    setApiKey("");
    setModelOptions([]);
    setModelOptionsKey("");
  }, []);

  useEffect(() => {
    const next = activeProfile ?? defaultProfile();
    const selectedProfileStillExists =
      !selectedProfileId || profiles.some((profile) => profile.id === selectedProfileId);
    if (next.id !== appliedActiveProfileIdRef.current || !selectedProfileStillExists) {
      appliedActiveProfileIdRef.current = next.id;
      applyProfile(next);
    }
  }, [activeProfile, applyProfile, profiles, selectedProfileId]);

  useEffect(() => {
    if (
      modelOptionsKey === currentModelOptionsKey &&
      modelData?.intent === "models" &&
      modelData.ok &&
      modelData.models
    ) {
      setModelOptions(modelData.models);
      if (!model.trim() && modelData.models[0]) {
        setModel(modelData.models[0]);
      }
    }
  }, [currentModelOptionsKey, modelData, model, modelOptionsKey]);

  function onProtocolChange(next: AiProtocol) {
    const previousDefaults = PROTOCOL_DEFAULTS[protocol];
    const nextDefaults = PROTOCOL_DEFAULTS[next];
    setProtocol(next);
    if (!baseUrl.trim() || baseUrl === previousDefaults.baseUrl) {
      setBaseUrl(nextDefaults.baseUrl);
    }
    if (!model.trim() || model === previousDefaults.model || LEGACY_DEFAULT_MODELS.has(model)) {
      setModel(nextDefaults.model);
    }
    setModelOptions([]);
    setModelOptionsKey("");
  }

  function onProfileSelect(id: string) {
    const profile = profiles.find((item) => item.id === id);
    if (profile) {
      applyProfile(profile);
    }
  }

  function onNewProfile() {
    applyProfile(defaultProfile(protocol));
  }

  function onFetchModels() {
    const form = new FormData();
    setModelOptions([]);
    setModelOptionsKey(currentModelOptionsKey);
    form.set("intent", "models");
    form.set("id", selectedProfileId);
    form.set("protocol", protocol);
    form.set("baseUrl", baseUrl);
    form.set("apiKey", apiKey);
    modelFetcher.submit(form, { method: "post" });
  }

  return (
    <main className={wideShellClass}>
      <section className="space-y-8">
        <header>
          <h1 className={pageTitleClass}>设置</h1>
          <p className={pageLeadClass}>管理外观、AI 提供商、数据备份和本地运行偏好。</p>
        </header>

        <div className="grid gap-6 xl:grid-cols-[320px_minmax(0,1fr)] 2xl:grid-cols-[360px_minmax(0,1fr)]">
          <aside className="space-y-4 xl:sticky xl:top-10 xl:self-start">
            <section
              id="appearance"
              className="scroll-mt-6 rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900"
            >
              <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">外观</h2>
              <p className={`mt-1 text-sm ${subtleTextClass}`}>切换浅色 / 深色主题，或跟随系统。</p>
              <div className="mt-3">
                <ThemeToggle />
              </div>
            </section>

            <section className="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
              <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">AI 提供商</h2>
              <p className={`mt-1 text-sm ${subtleTextClass}`}>
                配置用于手动总结与问答的 AI 提供商；所有 AI 配置都在这里管理。
              </p>
            </section>

            <BackupSection backups={backups} />
          </aside>

          <section className="min-w-0">
            <Form method="post" className="space-y-4">
              <input type="hidden" name="id" value={selectedProfileId} />

              <section className="rounded-lg border border-gray-200 bg-white p-4 sm:p-5 dark:border-gray-800 dark:bg-gray-900">
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <div>
                    <h3 className="font-medium text-gray-950 text-sm dark:text-gray-50">AI 功能</h3>
                    <p className={`mt-1 text-sm ${subtleTextClass}`}>
                      保存并开启后，问答功能和手动生成总结会使用当前配置；关闭后保留配置但不调用模型。
                    </p>
                  </div>
                  <label
                    className="inline-flex cursor-pointer items-center gap-3 rounded-full border border-gray-200 bg-gray-100/60 px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-950"
                    aria-label="启用 AI 功能"
                  >
                    <input
                      type="checkbox"
                      name="enabled"
                      checked={enabled}
                      onChange={(event) => setEnabled(event.target.checked)}
                      className="peer sr-only"
                    />
                    <span className="relative h-6 w-11 rounded-full bg-gray-300 transition-colors after:absolute after:top-1 after:left-1 after:h-4 after:w-4 after:rounded-full after:bg-white after:shadow-sm after:transition-transform peer-checked:bg-celadon-600 peer-checked:after:translate-x-5 dark:bg-gray-700 dark:peer-checked:bg-celadon-400 dark:peer-checked:after:bg-gray-950" />
                    <span className="min-w-12 text-gray-900 dark:text-gray-100">
                      {enabled ? "已启用" : "已停用"}
                    </span>
                  </label>
                </div>

                <div className="mt-5 border-gray-200 border-t pt-4 dark:border-gray-800">
                  <label className={labelClass}>
                    单条总结
                    <select
                      name="entryInsightAutoMode"
                      value={entryInsightAutoMode}
                      onChange={(event) =>
                        setEntryInsightAutoMode(event.target.value as EntryInsightAutoMode)
                      }
                      className={inputClass}
                    >
                      {ENTRY_INSIGHT_AUTO_MODES.map((mode) => (
                        <option key={mode} value={mode}>
                          {ENTRY_INSIGHT_AUTO_MODE_LABELS[mode]}
                        </option>
                      ))}
                    </select>
                  </label>
                  <p className={`mt-1 text-xs ${subtleTextClass}`}>
                    关闭自动生成后，仍可在「历史」或记录详情中手动生成。
                  </p>
                </div>
              </section>

              <section className="space-y-5 rounded-lg border border-gray-200 bg-white p-4 sm:p-6 dark:border-gray-800 dark:bg-gray-900">
                <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
                  <label className={labelClass}>
                    已保存配置
                    <select
                      value={selectedProfileId}
                      onChange={(event) => onProfileSelect(event.target.value)}
                      disabled={profiles.length === 0}
                      className={inputClass}
                    >
                      {profiles.length === 0 ? <option value="">暂无配置</option> : null}
                      {profiles.map((profile) => (
                        <option key={profile.id} value={profile.id}>
                          {profile.name}
                          {profile.id === settings.activeProfileId ? "（当前）" : ""}
                        </option>
                      ))}
                    </select>
                  </label>

                  <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
                    <button
                      type="button"
                      onClick={onNewProfile}
                      className={`${secondaryButtonClass} w-full sm:w-auto`}
                    >
                      新建配置
                    </button>
                    <button
                      type="submit"
                      name="intent"
                      value="delete"
                      disabled={busy || !selectedProfileId}
                      onClick={(event) => {
                        if (!confirm("确定删除这个 AI 配置吗？")) {
                          event.preventDefault();
                        }
                      }}
                      className={`${secondaryButtonClass} w-full sm:w-auto`}
                    >
                      删除
                    </button>
                  </div>
                </div>

                <label className={labelClass}>
                  配置名称
                  <input
                    type="text"
                    name="name"
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    className={inputClass}
                  />
                </label>

                <label className={labelClass}>
                  协议
                  <select
                    name="protocol"
                    value={protocol}
                    onChange={(event) => onProtocolChange(event.target.value as AiProtocol)}
                    className={inputClass}
                  >
                    <option value="anthropic">Anthropic（Claude Messages API）</option>
                    <option value="openai">OpenAI（兼容 Chat Completions）</option>
                  </select>
                </label>

                <label className={labelClass}>
                  API 地址（Base URL）
                  <input
                    type="url"
                    name="baseUrl"
                    value={baseUrl}
                    onChange={(event) => setBaseUrl(event.target.value)}
                    className={inputClass}
                  />
                </label>

                <label className={labelClass}>
                  API Key
                  <input
                    type="password"
                    name="apiKey"
                    value={apiKey}
                    onChange={(event) => setApiKey(event.target.value)}
                    autoComplete="off"
                    placeholder={hasStoredApiKey ? "已配置（留空保持不变）" : "sk-..."}
                    className={inputClass}
                  />
                </label>

                <section className="rounded-lg bg-gray-100/60 p-4 dark:bg-gray-950/60">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <label htmlFor="model" className={labelClass}>
                        模型
                      </label>
                      <p className={`mt-1 text-xs ${subtleTextClass}`}>
                        可先获取模型列表下拉选择；如果网关不支持列模型，也可以直接手动输入模型名称。
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={onFetchModels}
                      disabled={fetchingModels || !baseUrl.trim() || (!apiKey && !hasStoredApiKey)}
                      className={`${secondaryButtonClass} w-full sm:w-auto`}
                    >
                      {fetchingModels ? "获取中..." : "获取模型列表"}
                    </button>
                  </div>

                  <SuggestedInput
                    id="model"
                    name="model"
                    optionLabel="选择已获取的模型"
                    options={visibleModelOptions}
                    placeholder="输入模型名称"
                    value={model}
                    onValueChange={setModel}
                    inputClassName={inputClass}
                  />

                  {modelData?.intent === "models" ? (
                    <p className={`mt-3 ${statusClass(modelData.ok)}`}>{modelData.message}</p>
                  ) : null}
                </section>

                {actionData ? (
                  <p className={statusClass(actionData.ok)}>
                    {actionData.intent === "test" ? "测试：" : ""}
                    {actionData.message}
                  </p>
                ) : null}

                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-3">
                  <button
                    type="submit"
                    name="intent"
                    value="save"
                    disabled={busy}
                    className={`${primaryButtonClass} w-full sm:w-auto`}
                  >
                    保存并设为当前
                  </button>
                  <button
                    type="submit"
                    name="intent"
                    value="test"
                    disabled={busy}
                    className={`${secondaryButtonClass} w-full sm:w-auto`}
                  >
                    测试连接
                  </button>
                  <button
                    type="submit"
                    name="intent"
                    value="activate"
                    disabled={
                      busy || !selectedProfileId || selectedProfileId === settings.activeProfileId
                    }
                    className={`${secondaryButtonClass} w-full sm:w-auto`}
                  >
                    设为当前
                  </button>
                </div>
              </section>
            </Form>

            <p className={`mt-4 text-xs ${subtleTextClass}`}>
              API Key 只会加密保存到服务端
              KV；浏览器再次打开页面时只能看到“已配置”的状态，不会拿到明文 key。
            </p>
          </section>
        </div>
      </section>
    </main>
  );
}
