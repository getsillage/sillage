import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  type AskContextScope,
  type AskConversation,
  type AskMessage,
  type AskSourceKind,
  createAskConversation,
  listAskConversations,
  listAskMessages,
  streamAskMessage,
} from "../lib/api";

interface AskContextValue {
  conversations: AskConversation[];
  activeId: string;
  activeConversation: AskConversation | undefined;
  messages: AskMessage[];
  scope: AskContextScope;
  sourceKind: AskSourceKind;
  busy: boolean;
  streaming: boolean;
  error: string;
  setScope: (scope: AskContextScope) => void;
  setSourceKind: (kind: AskSourceKind) => void;
  selectConversation: (id: string) => void;
  startNew: () => void;
  send: (question: string) => Promise<void>;
  stop: () => void;
}

// A live, not-yet-persisted assistant turn while tokens stream in.
function streamingPlaceholder(
  tempId: string,
  user: AskMessage,
  sources: AskMessage["sourceRefs"],
): AskMessage {
  return {
    id: tempId,
    conversationId: user.conversationId,
    role: "assistant",
    content: "",
    parentId: user.id,
    forkOfId: null,
    status: "streaming",
    sourceRefs: sources,
    model: "",
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
    deletedAt: null,
  };
}

const AskContext = createContext<AskContextValue | null>(null);

export function AskProvider({
  token,
  children,
}: {
  token: string;
  children: ReactNode;
}) {
  const [conversations, setConversations] = useState<AskConversation[]>([]);
  const [activeId, setActiveId] = useState("");
  const [messages, setMessages] = useState<AskMessage[]>([]);
  const [scope, setScope] = useState<AskContextScope>("recent_30_days");
  const [sourceKind, setSourceKind] = useState<AskSourceKind>("records");
  const [busy, setBusy] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState("");
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    listAskConversations(token)
      .then((res) => setConversations(res.conversations))
      .catch((err) =>
        setError(err instanceof Error ? err.message : "读取问答失败"),
      );
  }, [token]);

  useEffect(() => {
    if (!activeId) {
      setMessages([]);
      return;
    }
    let cancelled = false;
    listAskMessages(token, activeId)
      .then((res) => {
        if (!cancelled) {
          setMessages(res.messages);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "读取消息失败");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token, activeId]);

  const selectConversation = useCallback(
    (id: string) => {
      setActiveId(id);
      const found = conversations.find(
        (conversation) => conversation.id === id,
      );
      if (found) {
        setScope(found.contextScope);
      }
    },
    [conversations],
  );

  const startNew = useCallback(() => {
    setActiveId("");
    setMessages([]);
    setError("");
  }, []);

  const send = useCallback(
    async (question: string) => {
      const trimmed = question.trim();
      if (!trimmed) {
        setError("先写下要问的问题");
        return;
      }
      setBusy(true);
      setError("");
      const controller = new AbortController();
      abortRef.current = controller;
      const tempId = `streaming-${Date.now()}`;
      let conversationId = activeId;
      try {
        let createdNew = false;
        if (!conversationId) {
          const created = await createAskConversation(token, {
            contextScope: scope,
          });
          conversationId = created.conversation.id;
          createdNew = true;
          setConversations((current) => [created.conversation, ...current]);
          setActiveId(conversationId);
        }
        await streamAskMessage(
          token,
          conversationId,
          { content: trimmed, contextScope: scope, sourceKind },
          {
            onStart: ({ userMessage, sources }) => {
              setStreaming(true);
              const placeholder = streamingPlaceholder(
                tempId,
                userMessage,
                sources,
              );
              setMessages((current) => [
                ...(createdNew ? [] : current),
                userMessage,
                placeholder,
              ]);
            },
            onDelta: (text) => {
              setMessages((current) =>
                current.map((m) =>
                  m.id === tempId ? { ...m, content: m.content + text } : m,
                ),
              );
            },
            onDone: (message) => {
              setMessages((current) =>
                current.map((m) => (m.id === tempId ? message : m)),
              );
            },
            onError: (message) => {
              setError(message);
              setMessages((current) => current.filter((m) => m.id !== tempId));
            },
          },
          controller.signal,
        );
        // A stop leaves a temp placeholder; reload canonical messages so the
        // persisted partial answer replaces it.
        if (controller.signal.aborted && conversationId) {
          const reloaded = await listAskMessages(token, conversationId);
          setMessages(reloaded.messages);
        }
        const refreshed = await listAskConversations(token);
        setConversations(refreshed.conversations);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "生成回答失败");
        setMessages((current) => current.filter((m) => m.id !== tempId));
      } finally {
        setBusy(false);
        setStreaming(false);
        abortRef.current = null;
      }
    },
    [token, activeId, scope, sourceKind],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  const activeConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === activeId),
    [conversations, activeId],
  );

  const value = useMemo<AskContextValue>(
    () => ({
      conversations,
      activeId,
      activeConversation,
      messages,
      scope,
      sourceKind,
      busy,
      streaming,
      error,
      setScope,
      setSourceKind,
      selectConversation,
      startNew,
      send,
      stop,
    }),
    [
      conversations,
      activeId,
      activeConversation,
      messages,
      scope,
      sourceKind,
      busy,
      streaming,
      error,
      selectConversation,
      startNew,
      send,
      stop,
    ],
  );

  return <AskContext.Provider value={value}>{children}</AskContext.Provider>;
}

export function useAsk(): AskContextValue {
  const value = useContext(AskContext);
  if (!value) {
    throw new Error("useAsk must be used within an AskProvider");
  }
  return value;
}
