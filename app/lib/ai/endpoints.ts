import type { AiProtocol } from "~/lib/settings/ai-settings";

function trimTrailingSlash(value: string): string {
  return value.trim().replace(/\/+$/, "");
}

function unique(values: string[]): string[] {
  return Array.from(new Set(values));
}

function hasV1Suffix(baseUrl: string): boolean {
  return /\/v1$/i.test(baseUrl);
}

export function anthropicEndpointCandidates(baseUrl: string, path: string): string[] {
  const base = trimTrailingSlash(baseUrl);
  const normalizedPath = path.replace(/^\/+/, "");
  return hasV1Suffix(base)
    ? [`${base}/${normalizedPath}`]
    : unique([`${base}/v1/${normalizedPath}`, `${base}/${normalizedPath}`]);
}

export function openAiEndpointCandidates(baseUrl: string, path: string): string[] {
  const base = trimTrailingSlash(baseUrl);
  const normalizedPath = path.replace(/^\/+/, "");
  return hasV1Suffix(base)
    ? [`${base}/${normalizedPath}`]
    : unique([`${base}/${normalizedPath}`, `${base}/v1/${normalizedPath}`]);
}

export function endpointCandidates(protocol: AiProtocol, baseUrl: string, path: string): string[] {
  return protocol === "anthropic"
    ? anthropicEndpointCandidates(baseUrl, path)
    : openAiEndpointCandidates(baseUrl, path);
}

/**
 * Some compatible gateways expect `/v1/...`, while others ask the user to put
 * `/v1` in the Base URL. Retry the alternate form only for a 404, where it is
 * likely we guessed the path shape incorrectly.
 */
export async function fetchWithEndpointFallback(
  urls: string[],
  init: RequestInit,
): Promise<Response> {
  let response = await fetch(urls[0], init);
  for (const url of urls.slice(1)) {
    if (response.status !== 404) {
      return response;
    }
    response = await fetch(url, init);
  }
  return response;
}

export async function responseErrorDetail(response: Response, maxLength = 200): Promise<string> {
  try {
    const text = (await response.text()).trim();
    if (!text) {
      return "";
    }
    if (text.startsWith("<")) {
      return "：服务返回了 HTML 错误页";
    }
    return `：${text.slice(0, maxLength)}`;
  } catch {
    return "";
  }
}
