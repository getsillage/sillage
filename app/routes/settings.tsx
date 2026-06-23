import { env } from "cloudflare:workers";
import { useState } from "react";
import { Form, useNavigation } from "react-router";
import { testAiConnection } from "~/lib/ai/test-connection";
import { requireSession } from "~/lib/auth/session";
import {
  type AiProtocol,
  aiSettingsInputSchema,
  loadAiSettings,
  loadAiSettingsView,
  saveAiSettings,
} from "~/lib/settings/ai-settings";
import type { Route } from "./+types/settings";

const PROTOCOL_DEFAULTS: Record<AiProtocol, { baseUrl: string; model: string }> = {
  anthropic: { baseUrl: "https://api.anthropic.com", model: "claude-opus-4-8" },
  openai: { baseUrl: "https://api.openai.com/v1", model: "gpt-5.1-mini" },
};

export function meta(_: Route.MetaArgs) {
  return [{ title: "设置 · 我的日记" }];
}

export async function loader({ request }: Route.LoaderArgs) {
  await requireSession(request, env);
  return { settings: await loadAiSettingsView(env) };
}

function parseForm(form: FormData) {
  return {
    enabled: form.get("enabled") === "on",
    protocol: String(form.get("protocol") ?? "anthropic"),
    baseUrl: String(form.get("baseUrl") ?? ""),
    model: String(form.get("model") ?? ""),
    apiKey: String(form.get("apiKey") ?? ""),
  };
}

export async function action({ request }: Route.ActionArgs) {
  await requireSession(request, env);
  const form = await request.formData();
  const intent = form.get("intent") === "test" ? "test" : "save";

  const parsed = aiSettingsInputSchema.safeParse(parseForm(form));
  if (!parsed.success) {
    return { intent, ok: false, message: parsed.error.issues[0]?.message ?? "配置无效" };
  }

  if (intent === "test") {
    // Reuse the stored key when the field was left blank.
    let apiKey = parsed.data.apiKey;
    if (!apiKey) {
      apiKey = (await loadAiSettings(env))?.apiKey ?? "";
    }
    const result = await testAiConnection({
      protocol: parsed.data.protocol,
      baseUrl: parsed.data.baseUrl,
      model: parsed.data.model,
      apiKey,
    });
    return { intent, ok: result.ok, message: result.message };
  }

  await saveAiSettings(env, parsed.data);
  return { intent, ok: true, message: "已保存" };
}

const inputClass =
  "mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-gray-900 focus:outline-none";

export default function Settings({ loaderData, actionData }: Route.ComponentProps) {
  const { settings } = loaderData;
  const navigation = useNavigation();
  const busy = navigation.state === "submitting";

  const initialProtocol: AiProtocol = settings?.protocol ?? "anthropic";
  const [protocol, setProtocol] = useState<AiProtocol>(initialProtocol);
  const [baseUrl, setBaseUrl] = useState(
    settings?.baseUrl ?? PROTOCOL_DEFAULTS[initialProtocol].baseUrl,
  );
  const [model, setModel] = useState(settings?.model ?? PROTOCOL_DEFAULTS[initialProtocol].model);

  function onProtocolChange(next: AiProtocol) {
    setProtocol(next);
    // Switching protocol resets the endpoint/model to that protocol's defaults.
    setBaseUrl(PROTOCOL_DEFAULTS[next].baseUrl);
    setModel(PROTOCOL_DEFAULTS[next].model);
  }

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="font-semibold text-xl">设置</h1>
      <p className="mt-1 text-gray-500 text-sm">配置用于摘要 / 情绪分析的 AI 提供商。</p>

      <Form method="post" className="mt-6 space-y-5 rounded-xl border border-gray-200 bg-white p-6">
        <label className="flex items-center gap-2 text-gray-700 text-sm">
          <input
            type="checkbox"
            name="enabled"
            defaultChecked={settings?.enabled ?? false}
            className="h-4 w-4 rounded border-gray-300"
          />
          启用 AI（写日记后自动生成摘要与情绪）
        </label>

        <label className="block text-gray-700 text-sm font-medium">
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

        <label className="block text-gray-700 text-sm font-medium">
          API 地址（Base URL）
          <input
            type="url"
            name="baseUrl"
            value={baseUrl}
            onChange={(event) => setBaseUrl(event.target.value)}
            className={inputClass}
          />
        </label>

        <label className="block text-gray-700 text-sm font-medium">
          模型
          <input
            type="text"
            name="model"
            value={model}
            onChange={(event) => setModel(event.target.value)}
            className={inputClass}
          />
        </label>

        <label className="block text-gray-700 text-sm font-medium">
          API Key
          <input
            type="password"
            name="apiKey"
            autoComplete="off"
            placeholder={settings?.hasApiKey ? "已配置（留空保持不变）" : "sk-..."}
            className={inputClass}
          />
        </label>

        {actionData ? (
          <p className={`text-sm ${actionData.ok ? "text-green-600" : "text-red-600"}`}>
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
            保存
          </button>
          <button
            type="submit"
            name="intent"
            value="test"
            disabled={busy}
            className="rounded-lg border border-gray-300 px-4 py-2 font-medium text-gray-700 text-sm hover:bg-gray-50 disabled:opacity-60"
          >
            测试连接
          </button>
        </div>
      </Form>

      <p className="mt-4 text-gray-400 text-xs">
        语义搜索使用的向量模型仍通过环境变量配置；此页面只管理文本生成（摘要 / 情绪）的提供商。
      </p>
    </main>
  );
}
