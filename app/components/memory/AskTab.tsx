import { Form, Link } from "react-router";
import { AskPanel } from "~/components/AskPanel";
import { EntryCard } from "~/components/EntryCard";
import { inputClass, primaryButtonClass, subtlePanelClass } from "~/components/ui";
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

function FacetCloud({
  title,
  empty,
  items,
}: {
  title: string;
  empty: string;
  items: [string, number][];
}) {
  return (
    <div className={`${subtlePanelClass} p-4`}>
      <h2 className="font-medium text-gray-950 text-sm dark:text-gray-50">{title}</h2>
      {items.length === 0 ? (
        <p className="mt-3 text-gray-400 text-sm dark:text-gray-500">{empty}</p>
      ) : (
        <div className="mt-3 flex flex-wrap gap-2">
          {items.map(([value, count]) => (
            <Link
              key={value}
              to={`/ask?q=${encodeURIComponent(value)}`}
              className="rounded-full bg-white px-3 py-1 text-gray-600 text-sm hover:text-gray-950 dark:bg-gray-800 dark:text-gray-300 dark:hover:text-gray-50"
            >
              {value} · {count}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
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
    <>
      <AskPanel
        conversations={conversations}
        currentConversation={currentConversation}
        conversationQuery={conversationQuery}
        includeArchived={includeArchived}
      />

      <details className={`${subtlePanelClass} overflow-hidden`} open={Boolean(query)}>
        <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3">
          <span>
            <span className="block font-medium text-gray-950 text-sm dark:text-gray-50">
              辅助探索
            </span>
            <span className="block text-gray-500 text-xs dark:text-gray-400">
              搜索记录，或从人物和关系继续追问
            </span>
          </span>
          <span className="text-gray-400 text-xs dark:text-gray-500">
            {query ? "已搜索" : "展开"}
          </span>
        </summary>

        <div className="space-y-5 border-gray-200 border-t p-4 dark:border-gray-800">
          <Form method="get" className="flex flex-col gap-2 sm:flex-row">
            <input
              type="search"
              name="q"
              defaultValue={query}
              placeholder="搜索一个词、地点、人物或关系…"
              className={`${inputClass} mt-0 min-w-0 flex-1`}
            />
            <button type="submit" className={`${primaryButtonClass} sm:w-auto`}>
              搜索
            </button>
          </Form>

          {query ? (
            <section>
              <h2 className="mb-3 font-medium text-gray-950 text-sm dark:text-gray-50">搜索结果</h2>
              {results.length === 0 ? (
                <p className="text-gray-400 text-sm dark:text-gray-500">
                  没有找到相关记忆。换一个词，或者看看照见。
                </p>
              ) : (
                <ul className="space-y-3">
                  {results.map((entry) => (
                    <li key={entry.id}>
                      <EntryCard entry={entry} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          ) : null}

          <section className="grid gap-4 sm:grid-cols-2">
            <FacetCloud title="人物" empty="记录人物后，这里会出现关系线索。" items={people} />
            <FacetCloud
              title="关系"
              empty="记录关系后，这里会帮助你回看变化。"
              items={relationships}
            />
          </section>
        </div>
      </details>
    </>
  );
}
