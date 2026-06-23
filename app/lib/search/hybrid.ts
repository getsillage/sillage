import type { SearchResult } from "./fts";

const KEYWORD_WEIGHT = 1;
const SEMANTIC_WEIGHT = 1.5;

function normalizedScore(result: SearchResult): number {
  // FTS bm25 is lower-is-better (often negative/near zero). Semantic scores are
  // higher-is-better. Normalize both into a monotonic higher-is-better value.
  if (result.source === "semantic") {
    return result.score * SEMANTIC_WEIGHT;
  }
  return (1 / (1 + Math.max(0, result.score))) * KEYWORD_WEIGHT;
}

/**
 * Merges keyword and semantic search hits by entry id, preserving the richest
 * entry object and keeping the strongest combined score per id.
 */
export function mergeSearchResults(
  keywordResults: readonly SearchResult[],
  semanticResults: readonly SearchResult[],
  limit = 20,
): SearchResult[] {
  const byId = new Map<string, SearchResult>();

  for (const result of [...keywordResults, ...semanticResults]) {
    const previous = byId.get(result.id);
    if (!previous) {
      byId.set(result.id, { ...result, score: normalizedScore(result) });
      continue;
    }

    const combinedScore = previous.score + normalizedScore(result);
    byId.set(result.id, {
      ...previous,
      source: previous.source === result.source ? previous.source : "semantic",
      score: combinedScore,
    });
  }

  return [...byId.values()]
    .sort((a, b) => b.score - a.score || b.entryDate.localeCompare(a.entryDate))
    .slice(0, limit);
}
