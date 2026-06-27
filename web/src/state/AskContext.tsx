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
  setAskHead,
  streamAskMessage,
} from "../lib/api";
import {
  type ActiveEntry,
  branchLeafId,
  buildActivePath,
} from "../lib/askTree";

interface AskContextValue {
  conversations: AskConversation[];
  activeId: string;
  activeConversation: AskConversation | undefined;
  /** The active conversation path (one branch of the message tree). */
  entries: ActiveEntry[];
  /** In-flight question being streamed (normal turn only). */
  liveUser: AskMessage | null;
  /** Accumulated streamed answer text. */
  liveAnswer: string;
  /** The assistant message currently being regenerated, if any. */
  regeneratingId: string | null;
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
  regenerate: (assistantId: string) => Promise<void>;
  selectVariant: (messageId: string) => Promise<void>;
  stop: () => void;
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
  const [headId, setHeadId] = useState<string | null>(null);
  const [scope, setScope] = useState<AskContextScope>("recent_30_days");
  const [sourceKind, setSourceKind] = useState<AskSourceKind>("records");
  const [busy, setBusy] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState("");
  const [liveUser, setLiveUser] = useState<AskMessage | null>(null);
  const [liveAnswer, setLiveAnswer] = useState("");
  const [regeneratingId, setRegeneratingId] = useState<string | null>(null);
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
      return;
    }
    const found = conversations.find((c) => c.id === activeId);
    if (!found) {
      return;
    }
    setScope(found.contextScope);
    setHeadId((current) => current ?? found.headMessageId);
  }, [activeId, conversations]);

  useEffect(() => {
    if (!activeId) {
      setMessages([]);
      setHeadId(null);
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
      setHeadId(null);
      const found = conversations.find((c) => c.id === id);
      if (found) {
        setScope(found.contextScope);
        setHeadId(found.headMessageId);
      }
    },
    [conversations],
  );

  const startNew = useCallback(() => {
    setActiveId("");
    setMessages([]);
    setHeadId(null);
    setError("");
  }, []);

  // Pulls canonical messages + conversation list after a turn, and points the
  // active head at the conversation's server-side head (the new leaf).
  const reload = useCallback(
    async (conversationId: string) => {
      const [msgs, convs] = await Promise.all([
        listAskMessages(token, conversationId),
        listAskConversations(token),
      ]);
      setMessages(msgs.messages);
      setConversations(convs.conversations);
      const conv = convs.conversations.find((c) => c.id === conversationId);
      if (conv) {
        setHeadId(conv.headMessageId);
      }
    },
    [token],
  );

  const runStream = useCallback(
    async (
      conversationId: string,
      input: { content: string; forkOfId?: string },
    ) => {
      const controller = new AbortController();
      abortRef.current = controller;
      try {
        await streamAskMessage(
          token,
          conversationId,
          {
            content: input.content,
            contextScope: scope,
            sourceKind,
            forkOfId: input.forkOfId,
          },
          {
            onStart: ({ userMessage, regenerate }) => {
              setStreaming(true);
              setLiveAnswer("");
              if (!regenerate) {
                setLiveUser(userMessage);
              }
            },
            onDelta: (text) => setLiveAnswer((prev) => prev + text),
            onError: (message) => setError(message),
          },
          controller.signal,
        );
        await reload(conversationId);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "生成回答失败");
      } finally {
        setStreaming(false);
        setLiveUser(null);
        setLiveAnswer("");
        setRegeneratingId(null);
        abortRef.current = null;
      }
    },
    [token, scope, sourceKind, reload],
  );

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
        if (!conversationId) {
          const created = await createAskConversation(token, {
            contextScope: scope,
          });
          conversationId = created.conversation.id;
          setConversations((current) => [created.conversation, ...current]);
          setActiveId(conversationId);
        }
        await runStream(conversationId, { content: trimmed });
      } finally {
        setBusy(false);
      }
    },
    [token, activeId, scope, runStream],
  );

  const regenerate = useCallback(
    async (assistantId: string) => {
      if (!activeId || busy) {
        return;
      }
      setBusy(true);
      setError("");
      setRegeneratingId(assistantId);
      try {
        await runStream(activeId, { content: "", forkOfId: assistantId });
      } finally {
        setBusy(false);
      }
    },
    [activeId, busy, runStream],
  );

  const selectVariant = useCallback(
    async (messageId: string) => {
      if (!activeId) {
        return;
      }
      const leaf = branchLeafId(messages, messageId);
      setHeadId(leaf);
      try {
        await setAskHead(token, activeId, leaf);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "切换分支失败");
      }
    },
    [token, activeId, messages],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  const activeConversation = useMemo(
    () => conversations.find((c) => c.id === activeId),
    [conversations, activeId],
  );
  const entries = useMemo(
    () => buildActivePath(messages, headId),
    [messages, headId],
  );

  const value = useMemo<AskContextValue>(
    () => ({
      conversations,
      activeId,
      activeConversation,
      entries,
      liveUser,
      liveAnswer,
      regeneratingId,
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
      regenerate,
      selectVariant,
      stop,
    }),
    [
      conversations,
      activeId,
      activeConversation,
      entries,
      liveUser,
      liveAnswer,
      regeneratingId,
      scope,
      sourceKind,
      busy,
      streaming,
      error,
      selectConversation,
      startNew,
      send,
      regenerate,
      selectVariant,
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
