import { env } from "cloudflare:workers";
import { useCallback, useEffect, useRef, useState } from "react";
import { Form, useFetcher, useNavigation } from "react-router";
import { listAiModels } from "~/lib/ai/models";
import { testAiConnection } from "~/lib/ai/test-connection";
import { requireSession } from "~/lib/auth/session";
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
} from "~/lib/settings/ai-settings";
import type { Route } from "./+types/settings";

const PROTOCOL_DEFAULTS: Record<AiProtocol, { baseUrl: string; model: string }> = {
  anthropic: { baseUrl: "https://api.anthropic.com", model: "" },
  openai: { baseUrl: "https://api.openai.com/v1", model: "" },
};
const LEGACY_DEFAULT_MODELS = new Set(["claude-opus-4-8", "gpt-5.1-mini"]);

type SettingsActionData = {
  intent: "save" | "test" | "models" | "delete" | "activate";
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
  return { settings: await loadAiSettingsView(env) };
}

function parseSettingsForm(form: FormData) {
  return {
    id: String(form.get("id") ?? ""),
    name: String(form.get("name") ?? ""),
    enabled: form.get("enabled") === "on",
    protocol: String(form.get("protocol") ?? "anthropic"),
    baseUrl: String(form.get("baseUrl") ?? ""),
    model: String(form.get("model") ?? ""),
    apiKey: String(form.get("apiKey") ?? ""),
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

  const parsed = aiSettingsInputSchema.safeParse(parseSettingsForm(form));
  if (!parsed.success) {
    return { intent, ok: false, message: parsed.error.issues[0]?.message ?? "配置无效" };
  }

  const profileId = await saveAiSettings(env, parsed.data);
  return { intent, ok: true, message: "已保存并设为当前配置", profileId };
}

const inputClass =
  "mt-1 w-full rounded-lg border border-gray-400 bg-white px-3 py-2 text-gray-950 text-sm placeholder:text-gray-500 focus:border-gray-950 focus:outline-none focus:ring-2 focus:ring-gray-200 disabled:border-gray-300 disabled:bg-gray-100 disabled:text-gray-600";
const secondaryButtonClass =
  "rounded-lg border border-gray-400 bg-white px-3 py-2 font-medium text-gray-900 text-sm hover:bg-gray-100 disabled:border-gray-300 disabled:bg-gray-100 disabled:text-gray-500";
const subtleTextClass = "text-gray-700";
const labelClass = "block text-gray-900 text-sm font-medium";
const statusClass = (ok: boolean) =>
  `rounded-lg border px-3 py-2 text-sm ${
    ok ? "border-green-300 bg-green-50 text-green-800" : "border-red-300 bg-red-50 text-red-800"
  }`;

function modelInputClass(options: string[]): string {
  return options.length > 0 ? `${inputClass} pr-20` : inputClass;
}

function ModelField({
  model,
  options,
  onModelChange,
}: {
  model: string;
  options: string[];
  onModelChange: (model: string) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <input
        id="model"
        type="text"
        name="model"
        value={model}
        onChange={(event) => onModelChange(event.target.value)}
        placeholder="输入模型名称"
        className={modelInputClass(options)}
      />
      {options.length > 0 ? (
        <div className="absolute top-2 right-2 z-20">
          <button
            type="button"
            aria-label="选择已获取的模型"
            aria-expanded={open}
            className="h-7 rounded-md px-2 text-gray-500 text-xs transition hover:bg-gray-100 hover:text-gray-950 focus:outline-none focus:ring-2 focus:ring-gray-900/10"
            onClick={() => setOpen((value) => !value)}
          >
            选择
          </button>
          {open ? (
            <div className="absolute top-9 right-0 z-30 max-h-56 min-w-64 overflow-auto rounded-md border border-gray-200 bg-white py-1 shadow-lg">
              {options.map((option) => (
                <button
                  key={option}
                  type="button"
                  className="block w-full px-3 py-1.5 text-left text-gray-700 text-sm transition hover:bg-gray-50 hover:text-gray-950 focus:bg-gray-50 focus:outline-none"
                  onClick={() => {
                    onModelChange(option);
                    setOpen(false);
                  }}
                >
                  {option}
                </button>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

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
  const { settings } = loaderData;
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
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="font-semibold text-gray-950 text-xl">设置</h1>
      <p className={`mt-1 text-sm ${subtleTextClass}`}>
        配置用于自动摘要的 AI 提供商；所有 AI 配置都在这里管理。
      </p>

      <Form
        method="post"
        className="mt-6 space-y-5 rounded-xl border border-gray-300 bg-white p-6 shadow-sm"
      >
        <input type="hidden" name="id" value={selectedProfileId} />

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

          <div className="flex items-end gap-2">
            <button type="button" onClick={onNewProfile} className={secondaryButtonClass}>
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
              className={secondaryButtonClass}
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

        <label className="flex items-center gap-2 text-gray-900 text-sm">
          <input
            type="checkbox"
            name="enabled"
            checked={enabled}
            onChange={(event) => setEnabled(event.target.checked)}
            className="h-4 w-4 rounded border-gray-300"
          />
          启用当前配置（保存记录后自动生成洞察）
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

        <section className="rounded-lg border border-gray-300 bg-slate-50 p-4">
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
              className={secondaryButtonClass}
            >
              {fetchingModels ? "获取中..." : "获取模型列表"}
            </button>
          </div>

          <ModelField model={model} options={visibleModelOptions} onModelChange={setModel} />

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

        <div className="flex items-center gap-3">
          <button
            type="submit"
            name="intent"
            value="save"
            disabled={busy}
            className="rounded-lg bg-gray-900 px-4 py-2 font-medium text-white text-sm hover:bg-gray-800 disabled:opacity-60"
          >
            保存并设为当前
          </button>
          <button
            type="submit"
            name="intent"
            value="test"
            disabled={busy}
            className={secondaryButtonClass}
          >
            测试连接
          </button>
          <button
            type="submit"
            name="intent"
            value="activate"
            disabled={busy || !selectedProfileId || selectedProfileId === settings.activeProfileId}
            className={secondaryButtonClass}
          >
            设为当前
          </button>
        </div>
      </Form>

      <p className={`mt-4 text-xs ${subtleTextClass}`}>
        API Key 只会加密保存到服务端 KV；浏览器再次打开页面时只能看到“已配置”的状态，不会拿到明文
        key。
      </p>
    </main>
  );
}
