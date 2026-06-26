import { AskPanel } from "~/components/AskPanel";
import type { LoadedSummary } from "~/components/insights/SummaryCard";
import type { AskConversationView } from "~/lib/db/ask-conversations";
import type { EntryWithTags } from "~/lib/db/entries";

interface AskTabProps {
  query: string;
  currentConversation: AskConversationView | null;
  results: EntryWithTags[];
  summaries: LoadedSummary[];
}

/** The 问答 page: conversation, keyword search, and generated memory reviews. */
export function AskTab({ query, currentConversation, results, summaries }: AskTabProps) {
  return (
    <AskPanel
      query={query}
      results={results}
      summaries={summaries}
      currentConversation={currentConversation}
    />
  );
}
