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
import { useI18n } from "../../i18n/I18nProvider";
import {
  type AskContextScope,
  type AskConversation,
  type AskMessage,
  type AskSourceKind,
  createAskConversation,
  getAskConversation,
  listAskConversations,
  listAskMessages,
  setAskConversationArchived,
  setAskHead,
  streamAskMessage,
} from "../../lib/api";
import { type ActiveEntry, branchLeafId, buildActivePath } from "./askTree";

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
  notification: {
    kind: "success" | "error";
    message: string;
  } | null;
  setScope: (scope: AskContextScope) => void;
  setSourceKind: (kind: AskSourceKind) => void;
  selectConversation: (id: string, conversation?: AskConversation) => void;
  startNew: () => void;
  listConversations: (
    options: { query?: string; archived: boolean },
    signal?: AbortSignal,
  ) => Promise<AskConversation[]>;
  setConversationArchived: (
    id: string,
    archived: boolean,
  ) => Promise<AskConversation>;
  dismissNotification: () => void;
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
  const { locale, t } = useI18n();
  const [conversations, setConversations] = useState<AskConversation[]>([]);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [activeId, setActiveId] = useState("");
  const [activeSnapshot, setActiveSnapshot] = useState<AskConversation | null>(
    null,
  );
  const [messages, setMessages] = useState<AskMessage[]>([]);
  const [headId, setHeadId] = useState<string | null>(null);
  const [scope, setScope] = useState<AskContextScope>("recent_30_days");
  const [sourceKind, setSourceKind] = useState<AskSourceKind>("records");
  const [busy, setBusy] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState("");
  const [notification, setNotification] = useState<{
    kind: "success" | "error";
    message: string;
  } | null>(null);
  const [liveUser, setLiveUser] = useState<AskMessage | null>(null);
  const [liveAnswer, setLiveAnswer] = useState("");
  const [regeneratingId, setRegeneratingId] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const operationRef = useRef(false);
  const activeIdRef = useRef(activeId);
  const navigationGenerationRef = useRef(0);
  const conversationMutationGenerationRef = useRef(0);
  const metadataRequestRef = useRef(0);
  const scopedConversationRef = useRef("");
  activeIdRef.current = activeId;

  useEffect(() => {
    void locale;
    setError("");
    setNotification(null);
  }, [locale]);

  useEffect(() => {
    const controller = new AbortController();
    const mutationGeneration = conversationMutationGenerationRef.current;
    setLoadingConversations(true);
    listAskConversations(token, {}, controller.signal)
      .then((res) => {
        if (
          !controller.signal.aborted &&
          conversationMutationGenerationRef.current === mutationGeneration
        ) {
          setConversations(res.conversations);
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) {
          setError(err instanceof Error ? err.message : t("ask.loadFailed"));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadingConversations(false);
        }
      });
    return () => controller.abort();
  }, [token, t]);

  useEffect(() => {
    if (!activeId || scopedConversationRef.current === activeId) {
      return;
    }
    const found =
      conversations.find((conversation) => conversation.id === activeId) ??
      (activeSnapshot?.id === activeId ? activeSnapshot : undefined);
    if (!found) {
      return;
    }
    scopedConversationRef.current = activeId;
    setScope(found.contextScope);
    setHeadId((current) => current ?? found.headMessageId);
  }, [activeId, activeSnapshot, conversations]);

  useEffect(() => {
    if (
      !activeId ||
      loadingConversations ||
      conversations.some((conversation) => conversation.id === activeId) ||
      activeSnapshot?.id === activeId
    ) {
      return;
    }

    const conversationId = activeId;
    const requestId = metadataRequestRef.current + 1;
    metadataRequestRef.current = requestId;
    const controller = new AbortController();
    void getAskConversation(token, conversationId, controller.signal)
      .then(({ conversation }) => {
        if (
          !controller.signal.aborted &&
          metadataRequestRef.current === requestId &&
          activeIdRef.current === conversationId
        ) {
          setActiveSnapshot(conversation);
          scopedConversationRef.current = conversationId;
          setScope(conversation.contextScope);
          setHeadId((current) => current ?? conversation.headMessageId);
        }
      })
      .catch((cause) => {
        if (
          !controller.signal.aborted &&
          metadataRequestRef.current === requestId &&
          activeIdRef.current === conversationId
        ) {
          setError(
            cause instanceof Error ? cause.message : t("ask.metadataFailed"),
          );
        }
      });

    return () => {
      metadataRequestRef.current += 1;
      controller.abort();
    };
  }, [activeId, activeSnapshot, conversations, loadingConversations, token, t]);

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
          setError(
            err instanceof Error ? err.message : t("ask.messagesFailed"),
          );
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
  }, [token, activeId, t]);

  const selectConversation = useCallback(
    (id: string, conversation?: AskConversation) => {
      if (id === activeIdRef.current) {
        if (conversation) {
          setActiveSnapshot(conversation);
        }
        return;
      }
      navigationGenerationRef.current += 1;
      abortRef.current?.abort();
      activeIdRef.current = id;
      setActiveId(id);
      setActiveSnapshot(
        conversation ?? conversations.find((item) => item.id === id) ?? null,
      );
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
    setActiveSnapshot(null);
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
      const mutationGeneration = conversationMutationGenerationRef.current;
      const [msgs, convs] = await Promise.all([
        listAskMessages(token, conversationId),
        listAskConversations(token),
      ]);
      let conversation = convs.conversations.find(
        (item) => item.id === conversationId,
      );
      if (!conversation && activeIdRef.current === conversationId) {
        const response = await getAskConversation(token, conversationId);
        conversation = response.conversation;
      }
      if (conversationMutationGenerationRef.current === mutationGeneration) {
        setConversations(convs.conversations);
      }
      if (activeIdRef.current === conversationId) {
        setMessages(msgs.messages);
        if (conversation) {
          setActiveSnapshot(conversation);
          setHeadId(conversation.headMessageId);
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
          setError(
            cause instanceof Error ? cause.message : t("ask.generateFailed"),
          );
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
    [token, scope, sourceKind, reload, t],
  );

  const send = useCallback(
    async (question: string) => {
      const trimmed = question.trim();
      if (!trimmed) {
        setError(t("ask.questionRequired"));
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
          conversationMutationGenerationRef.current += 1;
          setConversations((current) => [created.conversation, ...current]);
          setActiveId(conversationId);
          setActiveSnapshot(created.conversation);
        }
        return await runStream(conversationId, { content: trimmed });
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : t("ask.sendFailed"));
        return false;
      } finally {
        operationRef.current = false;
        setBusy(false);
      }
    },
    [token, activeId, scope, runStream, t],
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
        setError(
          cause instanceof Error ? cause.message : t("ask.switchBranchFailed"),
        );
      }
    },
    [token, activeId, messages, t],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  const listConversations = useCallback(
    async (
      options: { query?: string; archived: boolean },
      signal?: AbortSignal,
    ) => {
      const response = await listAskConversations(token, options, signal);
      return response.conversations;
    },
    [token],
  );

  const setConversationArchived = useCallback(
    async (id: string, archived: boolean) => {
      try {
        const response = await setAskConversationArchived(token, id, archived);
        conversationMutationGenerationRef.current += 1;
        setConversations((current) => {
          const withoutUpdated = current.filter(
            (conversation) => conversation.id !== id,
          );
          return archived
            ? withoutUpdated
            : [response.conversation, ...withoutUpdated];
        });
        setNotification({
          kind: "success",
          message: t(archived ? "ask.archived" : "ask.unarchived"),
        });
        return response.conversation;
      } catch (cause) {
        setNotification({
          kind: "error",
          message:
            cause instanceof Error
              ? cause.message
              : archived
                ? t("ask.archiveFailed")
                : t("ask.unarchiveFailed"),
        });
        throw cause;
      }
    },
    [token, t],
  );

  const dismissNotification = useCallback(() => setNotification(null), []);

  const activeConversation = useMemo(
    () =>
      conversations.find((conversation) => conversation.id === activeId) ??
      (activeSnapshot?.id === activeId ? activeSnapshot : undefined),
    [conversations, activeId, activeSnapshot],
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
      notification,
      setScope,
      setSourceKind,
      selectConversation,
      startNew,
      listConversations,
      setConversationArchived,
      dismissNotification,
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
      notification,
      selectConversation,
      startNew,
      listConversations,
      setConversationArchived,
      dismissNotification,
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
