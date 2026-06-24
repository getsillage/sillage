import type { AiProtocol } from "~/lib/settings/ai-settings";
import { endpointCandidates, fetchWithEndpointFallback, responseErrorDetail } from "./endpoints";

export interface ConnectionTestParams {
  protocol: AiProtocol;
  baseUrl: string;
  model: string;
  apiKey: string;
}

export interface ConnectionTestResult {
  ok: boolean;
  status?: number;
  message: string;
}

function ping(params: ConnectionTestParams): Promise<Response> {
  if (params.protocol === "anthropic") {
    return fetchWithEndpointFallback(
      endpointCandidates(params.protocol, params.baseUrl, "messages"),
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          "x-api-key": params.apiKey,
          "anthropic-version": "2023-06-01",
        },
        body: JSON.stringify({
          model: params.model,
          max_tokens: 1,
          messages: [{ role: "user", content: "ping" }],
        }),
      },
    );
  }
  return fetchWithEndpointFallback(
    endpointCandidates(params.protocol, params.baseUrl, "chat/completions"),
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${params.apiKey}`,
      },
      body: JSON.stringify({
        model: params.model,
        max_completion_tokens: 1,
        messages: [{ role: "user", content: "ping" }],
      }),
    },
  );
}

/**
 * Performs a minimal live request against the configured provider so the user
 * can verify protocol, base URL, model, and key before saving. Never throws —
 * failures are reported as a result.
 */
export async function testAiConnection(
  params: ConnectionTestParams,
): Promise<ConnectionTestResult> {
  if (!params.apiKey) {
    return { ok: false, message: "缺少 API Key" };
  }
  if (!params.model.trim()) {
    return { ok: false, message: "缺少模型名称" };
  }

  try {
    const response = await ping(params);
    if (response.ok) {
      return { ok: true, status: response.status, message: "连接成功" };
    }
    return {
      ok: false,
      status: response.status,
      message: `请求失败（${response.status}）${await responseErrorDetail(response)}`,
    };
  } catch (error) {
    return {
      ok: false,
      message: error instanceof Error ? error.message : "连接失败",
    };
  }
}
