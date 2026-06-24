import type { AiProtocol } from "~/lib/settings/ai-settings";
import { endpointCandidates, fetchWithEndpointFallback, responseErrorDetail } from "./endpoints";

export interface ListAiModelsParams {
  protocol: AiProtocol;
  baseUrl: string;
  apiKey: string;
}

export interface ListAiModelsResult {
  ok: boolean;
  models: string[];
  message: string;
  status?: number;
}

interface ModelListResponse {
  data?: Array<{ id?: unknown }>;
}

function parseModelIds(value: unknown): string[] {
  const data = (value as ModelListResponse | null)?.data;
  if (!Array.isArray(data)) {
    return [];
  }
  return Array.from(
    new Set(
      data.map((item) => (typeof item.id === "string" ? item.id.trim() : "")).filter(Boolean),
    ),
  );
}

function modelListRequest(params: ListAiModelsParams): { urls: string[]; headers: HeadersInit } {
  if (params.protocol === "anthropic") {
    return {
      urls: endpointCandidates(params.protocol, params.baseUrl, "models"),
      headers: {
        "x-api-key": params.apiKey,
        "anthropic-version": "2023-06-01",
      },
    };
  }
  return {
    urls: endpointCandidates(params.protocol, params.baseUrl, "models"),
    headers: {
      authorization: `Bearer ${params.apiKey}`,
    },
  };
}

/** Lists provider models for the settings page. Never throws. */
export async function listAiModels(params: ListAiModelsParams): Promise<ListAiModelsResult> {
  if (!params.apiKey) {
    return { ok: false, models: [], message: "缺少 API Key" };
  }

  try {
    const request = modelListRequest(params);
    const response = await fetchWithEndpointFallback(request.urls, {
      method: "GET",
      headers: request.headers,
    });
    if (!response.ok) {
      const fallbackHint =
        response.status === 404 ? "。这个服务可能没有开放模型列表接口，可以手动输入模型名称" : "";
      return {
        ok: false,
        status: response.status,
        models: [],
        message: `获取模型失败（${response.status}）${await responseErrorDetail(response)}${fallbackHint}`,
      };
    }

    const models = parseModelIds(await response.json());
    if (models.length === 0) {
      return { ok: false, status: response.status, models: [], message: "没有读取到可用模型" };
    }
    return { ok: true, status: response.status, models, message: `已获取 ${models.length} 个模型` };
  } catch (error) {
    return {
      ok: false,
      models: [],
      message: error instanceof Error ? error.message : "获取模型失败",
    };
  }
}
