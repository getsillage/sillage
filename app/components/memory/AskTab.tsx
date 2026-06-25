import { AskPanel } from "~/components/AskPanel";
import type { AskConversationView } from "~/lib/db/ask-conversations";
import type { EntryWithTags } from "~/lib/db/entries";

interface AskTabProps {
  query: string;
  currentConversation: AskConversationView | null;
  results: EntryWithTags[];
  people: [string, number][];
  relationships: [string, number][];
}

/** The 探寻 page: conversation with your memory, keyword search, people & relationships. */
export function AskTab({
  query,
  currentConversation,
  results,
  people,
  relationships,
}: AskTabProps) {
  return (
    <AskPanel
      query={query}
      results={results}
      people={people}
      relationships={relationships}
      currentConversation={currentConversation}
    />
  );
}
