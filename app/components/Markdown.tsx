import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";

interface MarkdownProps {
  content: string;
  variant?: "reading" | "chat";
}

const markdownClass = {
  reading:
    "prose prose-stone max-w-none font-serif leading-8 prose-a:text-celadon-700 prose-blockquote:border-celadon-300 prose-blockquote:text-gray-600 prose-code:rounded prose-code:bg-gray-100 prose-code:px-1 prose-code:py-0.5 prose-code:text-gray-900 prose-headings:font-serif prose-headings:text-gray-900 prose-hr:border-gray-200 prose-pre:bg-gray-950 prose-pre:text-gray-100 prose-strong:text-gray-900 prose-table:text-gray-700 prose-td:border-gray-200 prose-th:border-gray-300 dark:prose-invert dark:prose-a:text-celadon-200 dark:prose-blockquote:border-celadon-800 dark:prose-blockquote:text-gray-300 dark:prose-code:bg-gray-800 dark:prose-code:text-gray-100 dark:prose-headings:text-gray-50 dark:prose-hr:border-gray-800 dark:prose-pre:bg-gray-950 dark:prose-strong:text-gray-50 dark:prose-table:text-gray-200 dark:prose-td:border-gray-700 dark:prose-th:border-gray-600",
  chat: "prose prose-stone max-w-none font-serif text-[15px] leading-8 prose-p:my-3 prose-a:text-celadon-700 prose-blockquote:border-celadon-300 prose-blockquote:text-gray-600 prose-code:rounded prose-code:bg-gray-100 prose-code:px-1 prose-code:py-0.5 prose-code:text-gray-900 prose-headings:font-serif prose-headings:text-gray-900 prose-hr:border-gray-200 prose-pre:bg-gray-950 prose-pre:text-gray-100 prose-strong:text-gray-900 prose-table:text-gray-700 prose-td:border-gray-200 prose-th:border-gray-300 dark:prose-invert dark:prose-a:text-celadon-200 dark:prose-blockquote:border-celadon-800 dark:prose-blockquote:text-gray-300 dark:prose-code:bg-gray-800 dark:prose-code:text-gray-100 dark:prose-headings:text-gray-50 dark:prose-hr:border-gray-800 dark:prose-pre:bg-gray-950 dark:prose-strong:text-gray-50 dark:prose-table:text-gray-200 dark:prose-td:border-gray-700 dark:prose-th:border-gray-600",
};

/**
 * Renders Markdown to sanitized React elements. `rehype-sanitize` strips unsafe
 * HTML, and react-markdown never uses dangerouslySetInnerHTML, so this is safe
 * for rendering stored entry bodies on the server and client.
 */
export function Markdown({ content, variant = "reading" }: MarkdownProps) {
  return (
    <div className={markdownClass[variant]}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
