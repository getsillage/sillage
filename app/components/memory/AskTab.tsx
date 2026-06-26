import { AskPanel } from "~/components/AskPanel";
import type { AskConversationView } from "~/lib/db/ask-conversations";
import type { EntryWithAi } from "~/lib/db/entries";

interface AskTabProps {
  query: string;
  currentConversation: AskConversationView | null;
  results: EntryWithAi[];
}

/** The 问答 page: a focused conversation surface backed by personal records. */
export function AskTab({ query, currentConversation, results }: AskTabProps) {
  return <AskPanel query={query} results={results} currentConversation={currentConversation} />;
}
