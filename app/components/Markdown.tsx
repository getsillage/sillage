import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import remarkGfm from "remark-gfm";

interface MarkdownProps {
  content: string;
}

/**
 * Renders Markdown to sanitized React elements. `rehype-sanitize` strips unsafe
 * HTML, and react-markdown never uses dangerouslySetInnerHTML, so this is safe
 * for rendering stored entry bodies on the server and client.
 */
export function Markdown({ content }: MarkdownProps) {
  return (
    <div className="prose prose-gray max-w-none dark:prose-invert">
      <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
