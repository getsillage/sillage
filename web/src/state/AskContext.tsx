import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  type AskContextScope,
  type AskConversation,
  type AskMessage,
  createAskConversation,
  createAskMessage,
  listAskConversations,
  listAskMessages,
} from "../lib/api";

interface AskContextValue {
  conversations: AskConversation[];
  activeId: string;
  activeConversation: AskConversation | undefined;
  messages: AskMessage[];
  scope: AskContextScope;
  busy: boolean;
  error: string;
  setScope: (scope: AskContextScope) => void;
  selectConversation: (id: string) => void;
  startNew: () => void;
  send: (question: string) => Promise<void>;
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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

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
      try {
        let conversationId = activeId;
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
        const res = await createAskMessage(token, conversationId, {
          content: trimmed,
          contextScope: scope,
        });
        setMessages((current) =>
          createdNew ? res.messages : [...current, ...res.messages],
        );
        const refreshed = await listAskConversations(token);
        setConversations(refreshed.conversations);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "生成回答失败");
      } finally {
        setBusy(false);
      }
    },
    [token, activeId, scope],
  );

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
      busy,
      error,
      setScope,
      selectConversation,
      startNew,
      send,
    }),
    [
      conversations,
      activeId,
      activeConversation,
      messages,
      scope,
      busy,
      error,
      selectConversation,
      startNew,
      send,
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
