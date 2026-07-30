#!/usr/bin/env node

import { createServer } from "node:http";

const port = Number(process.env.E2E_MOCK_AI_PORT);
if (!Number.isInteger(port) || port <= 0 || port > 65535) {
  console.error("E2E_MOCK_AI_PORT must be a valid TCP port");
  process.exit(2);
}

const apiKey = "e2e-mock-key";
const summary = "mock-summary: this record mentions sleep and a calmer evening.";
const answer = "According to the selected records, sleep became more stable. [1]";

const server = createServer(async (request, response) => {
  if (request.method === "GET" && request.url === "/healthz") {
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end('{"ok":true}');
    return;
  }

  if (request.headers.authorization !== `Bearer ${apiKey}`) {
    response.writeHead(401, { "Content-Type": "application/json" });
    response.end('{"error":"missing bearer key"}');
    return;
  }

  if (request.method === "GET" && request.url?.endsWith("/models")) {
    writeJSON(response, { data: [{ id: "e2e-model" }] });
    return;
  }

  if (
    request.method !== "POST" ||
    !request.url?.endsWith("/chat/completions")
  ) {
    response.writeHead(404, { "Content-Type": "application/json" });
    response.end('{"error":"not found"}');
    return;
  }

  let payload;
  try {
    payload = JSON.parse(await readBody(request));
  } catch {
    response.writeHead(400, { "Content-Type": "application/json" });
    response.end('{"error":"invalid json"}');
    return;
  }

  const messages = Array.isArray(payload.messages) ? payload.messages : [];
  const system = messages
    .filter((message) => message?.role === "system")
    .map((message) => String(message.content ?? ""))
    .join("\n");
  const lastUser = [...messages]
    .reverse()
    .find((message) => message?.role === "user");
  const userContent = String(lastUser?.content ?? "");

  let content = answer;
  if (system.includes("Sillage 问答路由器")) {
    content = JSON.stringify({ mode: "records", searchQuery: "睡眠" });
  } else if (userContent.includes("生成简洁总结")) {
    content = summary;
  }

  if (payload.stream) {
    writeStream(response, content);
    return;
  }

  writeJSON(response, {
    choices: [{ message: { content } }],
    usage: {
      input_tokens: 11,
      output_tokens: 7,
      total_tokens: 18,
    },
  });
});

server.listen(port, "127.0.0.1", () => {
  console.log(`E2E mock AI listening on http://127.0.0.1:${port}`);
});

function readBody(request) {
  return new Promise((resolve, reject) => {
    let body = "";
    request.setEncoding("utf8");
    request.on("data", (chunk) => {
      body += chunk;
      if (body.length > 1_048_576) {
        reject(new Error("request body too large"));
        request.destroy();
      }
    });
    request.on("end", () => resolve(body));
    request.on("error", reject);
  });
}

function writeJSON(response, value) {
  response.writeHead(200, { "Content-Type": "application/json" });
  response.end(JSON.stringify(value));
}

function writeStream(response, content) {
  response.writeHead(200, {
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
    "Content-Type": "text/event-stream",
  });
  const midpoint = Math.max(1, Math.floor(content.length / 2));
  for (const chunk of [content.slice(0, midpoint), content.slice(midpoint)]) {
    response.write(
      `data: ${JSON.stringify({ choices: [{ delta: { content: chunk } }] })}\n\n`,
    );
  }
  response.end("data: [DONE]\n\n");
}
