import { AskPanel } from "~/components/AskPanel";
import type { AskConversationSummary, AskConversationView } from "~/lib/db/ask-conversations";
import type { EntryWithTags } from "~/lib/db/entries";

interface AskTabProps {
  query: string;
  conversationQuery: string;
  includeArchived: boolean;
  conversations: AskConversationSummary[];
  currentConversation: AskConversationView | null;
  results: EntryWithTags[];
  people: [string, number][];
  relationships: [string, number][];
}

/** The 探寻 page: conversation with your memory, keyword search, people & relationships. */
export function AskTab({
  query,
  conversationQuery,
  includeArchived,
  conversations,
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
      conversations={conversations}
      currentConversation={currentConversation}
      conversationQuery={conversationQuery}
      includeArchived={includeArchived}
    />
  );
}
