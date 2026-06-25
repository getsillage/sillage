import { useCallback, useEffect, useRef, useState } from "react";
import { useRevalidator } from "react-router";
import type { AiGenerationResult } from "~/lib/ai/generation-result";

export type GenerationStatus = "idle" | "running" | "done" | "error" | "cancelled";

export interface AiGenerationState {
  status: GenerationStatus;
  /** Live elapsed milliseconds while running; final provider time once done. */
  elapsedMs: number;
  result: AiGenerationResult | null;
  /** Fires a POST to the endpoint with the given fields and starts the ticker. */
  run: (fields: Record<string, string>) => void;
  /** Aborts the in-flight request and stops waiting. */
  cancel: () => void;
  /** Clears the last result back to idle. */
  reset: () => void;
}

const TICK_MS = 100;

function toFormData(fields: Record<string, string>): FormData {
  const form = new FormData();
  for (const [key, value] of Object.entries(fields)) {
    form.set(key, value);
  }
  return form;
}

/**
 * Drives a single AI generation against a JSON resource route. Owns a live elapsed
 * ticker (so the UI can show "已用 X.X 秒"), real cancellation via `AbortController`,
 * and a loader revalidation on success so freshly generated content appears without
 * a manual reload. Aborting client-side only stops the *waiting*; the server request
 * it already dispatched may still finish, which is harmless (the result is upserted).
 */
export function useAiGeneration(endpoint: string): AiGenerationState {
  const revalidator = useRevalidator();
  const [status, setStatus] = useState<GenerationStatus>("idle");
  const [elapsedMs, setElapsedMs] = useState(0);
  const [result, setResult] = useState<AiGenerationResult | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const startRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopTimer = useCallback(() => {
    if (timerRef.current !== null) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  useEffect(
    () => () => {
      stopTimer();
      controllerRef.current?.abort();
    },
    [stopTimer],
  );

  const run = useCallback(
    (fields: Record<string, string>) => {
      const controller = new AbortController();
      controllerRef.current = controller;
      startRef.current = Date.now();
      setElapsedMs(0);
      setResult(null);
      setStatus("running");
      stopTimer();
      timerRef.current = setInterval(() => {
        setElapsedMs(Date.now() - startRef.current);
      }, TICK_MS);

      fetch(endpoint, { method: "POST", body: toFormData(fields), signal: controller.signal })
        .then((response) => response.json() as Promise<AiGenerationResult>)
        .then((data) => {
          stopTimer();
          setElapsedMs(Date.now() - startRef.current);
          setResult(data);
          setStatus(data.ok ? "done" : "error");
          if (data.ok) {
            revalidator.revalidate();
          }
        })
        .catch(() => {
          stopTimer();
          if (controller.signal.aborted) {
            setStatus("cancelled");
            return;
          }
          setResult({ ok: false, message: "请求失败，请稍后再试", category: "network" });
          setStatus("error");
        });
    },
    [endpoint, revalidator, stopTimer],
  );

  const cancel = useCallback(() => {
    controllerRef.current?.abort();
    stopTimer();
    setStatus("cancelled");
  }, [stopTimer]);

  const reset = useCallback(() => {
    setStatus("idle");
    setResult(null);
    setElapsedMs(0);
  }, []);

  return { status, elapsedMs, result, run, cancel, reset };
}
