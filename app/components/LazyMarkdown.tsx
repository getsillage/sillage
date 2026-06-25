import { lazy, type ReactNode, Suspense } from "react";

interface LazyMarkdownProps {
  content: string;
  fallback?: ReactNode;
  variant?: "reading" | "chat";
}

const Markdown = lazy(() => import("./Markdown").then((module) => ({ default: module.Markdown })));

export function LazyMarkdown({
  content,
  fallback = <p className="text-gray-400 text-sm dark:text-gray-500">正在加载内容...</p>,
  variant = "reading",
}: LazyMarkdownProps) {
  return (
    <Suspense fallback={fallback}>
      <Markdown content={content} variant={variant} />
    </Suspense>
  );
}
