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
  loadingConversations: boolean;
  loadingMessages: boolean;
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
  send: (question: string) => Promise<boolean>;
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
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
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
  const operationRef = useRef(false);
  const activeIdRef = useRef(activeId);
  const navigationGenerationRef = useRef(0);
  const scopedConversationRef = useRef("");
  activeIdRef.current = activeId;

  useEffect(() => {
    listAskConversations(token)
      .then((res) => setConversations(res.conversations))
      .catch((err) =>
        setError(err instanceof Error ? err.message : "读取问答失败"),
      )
      .finally(() => setLoadingConversations(false));
  }, [token]);

  useEffect(() => {
    if (!activeId || scopedConversationRef.current === activeId) {
      return;
    }
    const found = conversations.find((c) => c.id === activeId);
    if (!found) {
      return;
    }
    scopedConversationRef.current = activeId;
    setScope(found.contextScope);
    setHeadId((current) => current ?? found.headMessageId);
  }, [activeId, conversations]);

  useEffect(() => {
    if (!activeId) {
      setMessages([]);
      setHeadId(null);
      setLoadingMessages(false);
      return;
    }
    let cancelled = false;
    setMessages([]);
    setLoadingMessages(true);
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
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingMessages(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token, activeId]);

  const selectConversation = useCallback(
    (id: string) => {
      if (id === activeIdRef.current) {
        return;
      }
      navigationGenerationRef.current += 1;
      abortRef.current?.abort();
      activeIdRef.current = id;
      setActiveId(id);
      setMessages([]);
      setLoadingMessages(true);
      setHeadId(null);
      setLiveUser(null);
      setLiveAnswer("");
      setRegeneratingId(null);
      setError("");
      scopedConversationRef.current = "";
      const found = conversations.find((c) => c.id === id);
      if (found) {
        scopedConversationRef.current = id;
        setScope(found.contextScope);
        setHeadId(found.headMessageId);
      }
    },
    [conversations],
  );

  const startNew = useCallback(() => {
    navigationGenerationRef.current += 1;
    abortRef.current?.abort();
    activeIdRef.current = "";
    scopedConversationRef.current = "";
    setActiveId("");
    setMessages([]);
    setHeadId(null);
    setLoadingMessages(false);
    setLiveUser(null);
    setLiveAnswer("");
    setRegeneratingId(null);
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
      setConversations(convs.conversations);
      if (activeIdRef.current === conversationId) {
        setMessages(msgs.messages);
        const conv = convs.conversations.find((c) => c.id === conversationId);
        if (conv) {
          setHeadId(conv.headMessageId);
        }
      }
    },
    [token],
  );

  const runStream = useCallback(
    async (
      conversationId: string,
      input: { content: string; forkOfId?: string },
    ): Promise<boolean> => {
      const controller = new AbortController();
      let started = false;
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
              started = true;
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
        return true;
      } catch (cause) {
        if (!controller.signal.aborted) {
          setError(cause instanceof Error ? cause.message : "生成回答失败");
        }
        return started;
      } finally {
        setStreaming(false);
        setLiveUser(null);
        setLiveAnswer("");
        setRegeneratingId(null);
        if (abortRef.current === controller) {
          abortRef.current = null;
        }
      }
    },
    [token, scope, sourceKind, reload],
  );

  const send = useCallback(
    async (question: string) => {
      const trimmed = question.trim();
      if (!trimmed) {
        setError("先写下要问的问题");
        return false;
      }
      if (operationRef.current) {
        return false;
      }
      operationRef.current = true;
      setBusy(true);
      setError("");
      try {
        let conversationId = activeId;
        if (!conversationId) {
          const navigationGeneration = navigationGenerationRef.current;
          let created: { conversation: AskConversation };
          try {
            created = await createAskConversation(token, {
              contextScope: scope,
            });
          } catch (cause) {
            if (navigationGeneration !== navigationGenerationRef.current) {
              return true;
            }
            throw cause;
          }
          if (navigationGeneration !== navigationGenerationRef.current) {
            // The user intentionally moved elsewhere while creation was pending.
            // Treat the old question as consumed so AskPage does not restore it
            // into the newly selected conversation's composer.
            return true;
          }
          conversationId = created.conversation.id;
          activeIdRef.current = conversationId;
          setConversations((current) => [created.conversation, ...current]);
          setActiveId(conversationId);
        }
        return await runStream(conversationId, { content: trimmed });
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : "发送问题失败");
        return false;
      } finally {
        operationRef.current = false;
        setBusy(false);
      }
    },
    [token, activeId, scope, runStream],
  );

  const regenerate = useCallback(
    async (assistantId: string) => {
      if (!activeId || busy || operationRef.current) {
        return;
      }
      operationRef.current = true;
      setBusy(true);
      setError("");
      setRegeneratingId(assistantId);
      try {
        await runStream(activeId, { content: "", forkOfId: assistantId });
      } finally {
        operationRef.current = false;
        setBusy(false);
      }
    },
    [activeId, busy, runStream],
  );

  const selectVariant = useCallback(
    async (messageId: string) => {
      if (!activeId || operationRef.current) {
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
      loadingConversations,
      loadingMessages,
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
      loadingConversations,
      loadingMessages,
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
