import type { ComponentPropsWithoutRef } from "react";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import remarkGfm from "remark-gfm";

// Links inside records and answers point at user content or attachments;
// always open them in a new tab and drop the opener reference.
function ExternalLink({ href, children }: ComponentPropsWithoutRef<"a">) {
  return (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  );
}

// Renders memo content / AI answers as Markdown. react-markdown does not emit
// raw HTML and sanitizes dangerous URL schemes, so user input stays safe.
// remark-breaks keeps single newlines as line breaks to match diary writing.
export function MarkdownContent({ content }: { content: string }) {
  return (
    <div className="markdown-content">
      <Markdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        components={{ a: ExternalLink }}
      >
        {content}
      </Markdown>
    </div>
  );
}
