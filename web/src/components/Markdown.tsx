import type { ComponentPropsWithoutRef } from "react";
import ReactMarkdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import remarkGfm from "remark-gfm";

interface MarkdownProps {
  content: string;
  variant?: "reading" | "chat";
}

// Links inside records and answers point at user content or attachments;
// always open them in a new tab and drop the opener reference.
function ExternalLink({ href, children }: ComponentPropsWithoutRef<"a">) {
  return (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  );
}

const proseBase =
  "prose prose-neutral max-w-none dark:prose-invert prose-headings:font-semibold prose-code:rounded prose-code:bg-gray-100 prose-code:px-1 prose-code:py-0.5 prose-code:font-normal prose-code:before:content-none prose-code:after:content-none prose-pre:bg-gray-100 prose-pre:text-gray-900 prose-hr:border-gray-200 dark:prose-code:bg-gray-800 dark:prose-pre:bg-gray-950 dark:prose-pre:text-gray-100 dark:prose-hr:border-gray-700";

const markdownClass = {
  reading: `${proseBase} leading-7`,
  chat: `${proseBase} text-[15px] leading-7 prose-p:my-3`,
};

/**
 * Renders memo content / AI answers as Markdown. react-markdown never emits raw
 * HTML and sanitizes dangerous URL schemes, so stored bodies stay safe.
 * remark-breaks keeps single newlines as line breaks to match diary writing.
 */
export function Markdown({ content, variant = "reading" }: MarkdownProps) {
  return (
    <div className={markdownClass[variant]}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        components={{ a: ExternalLink }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
