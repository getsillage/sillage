import { Link } from "react-router";
import type { AiFailureCategory } from "~/lib/ai/generation-result";
import { formatDuration, type ProgressPhase, phaseLabel } from "~/lib/ai/progress";
import type { AiGenerationState } from "./useAiGeneration";

interface GenerationStatusProps {
  state: AiGenerationState;
  phases: readonly ProgressPhase[];
}

/** Failure categories whose fix lives on the settings page. */
const SETTINGS_CATEGORIES: ReadonlySet<AiFailureCategory> = new Set(["disabled", "no-key"]);

/**
 * Renders the live + settled feedback for one AI generation: an anthropomorphic
 * phase line with a ticking "已用 X.X 秒" and a cancel affordance while running,
 * then a calm "用时 · 模型" line on success or a classified hint on failure.
 */
export function GenerationStatus({ state, phases }: GenerationStatusProps) {
  if (state.status === "running") {
    return (
      <div className="mt-2 flex items-center gap-2 text-gray-500 text-xs dark:text-gray-400">
        <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-gray-400 dark:bg-gray-500" />
        <span>{phaseLabel(phases, state.elapsedMs)}</span>
        <span className="text-gray-400 tabular-nums dark:text-gray-500">
          已用 {formatDuration(state.elapsedMs)}
        </span>
        <button
          type="button"
          onClick={state.cancel}
          className="text-gray-400 underline-offset-2 hover:text-gray-700 hover:underline dark:text-gray-500 dark:hover:text-gray-200"
        >
          取消
        </button>
      </div>
    );
  }

  if (state.status === "done" && state.result?.ok) {
    const ms = state.result.durationMs ?? state.elapsedMs;
    return (
      <p className="mt-2 text-green-700 text-xs dark:text-green-300">
        ✓ 用时 {formatDuration(ms)}
        {state.result.model ? ` · ${state.result.model}` : ""}
      </p>
    );
  }

  if (state.status === "error" && state.result) {
    const showSettings = state.result.category
      ? SETTINGS_CATEGORIES.has(state.result.category)
      : false;
    return (
      <p className="mt-2 text-red-600 text-xs dark:text-red-400">
        {state.result.message}
        {state.result.hint ? (
          <>
            {" · "}
            {showSettings ? (
              <Link to="/settings" className="underline underline-offset-2">
                {state.result.hint}
              </Link>
            ) : (
              <span className="text-red-500 dark:text-red-300">{state.result.hint}</span>
            )}
          </>
        ) : null}
      </p>
    );
  }

  if (state.status === "cancelled") {
    return <p className="mt-2 text-gray-400 text-xs dark:text-gray-500">已取消</p>;
  }

  return null;
}
