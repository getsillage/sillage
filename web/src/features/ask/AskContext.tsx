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
import { useToast } from "../../components/Toast";
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
  conversationsLoadError: string;
  loadingMessages: boolean;
  messagesLoadError: string;
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
  savingRecordMessageIds: ReadonlySet<string>;
  busy: boolean;
  streaming: boolean;
  error: string;
  setScope: (scope: AskContextScope) => void;
  setSourceKind: (kind: AskSourceKind) => void;
  tryStartRecordSave: (messageId: string) => boolean;
  finishRecordSave: (messageId: string) => void;
  selectConversation: (id: string, conversation?: AskConversation) => void;
  startNew: () => void;
  listConversations: (
    options: { query?: string; archived: boolean },
    signal?: AbortSignal,
  ) => Promise<AskConversation[]>;
  retryConversations: () => void;
  retryMessages: () => void;
  setConversationArchived: (
    id: string,
    archived: boolean,
  ) => Promise<AskConversation>;
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
  const toast = useToast();
  const [conversations, setConversations] = useState<AskConversation[]>([]);
  const [loadingConversations, setLoadingConversations] = useState(true);
  const [conversationsLoadError, setConversationsLoadError] = useState("");
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [messagesLoadError, setMessagesLoadError] = useState("");
  const [activeId, setActiveId] = useState("");
  const [activeSnapshot, setActiveSnapshot] = useState<AskConversation | null>(
    null,
  );
  const [messages, setMessages] = useState<AskMessage[]>([]);
  const [headId, setHeadId] = useState<string | null>(null);
  const [scope, setScope] = useState<AskContextScope>("recent_30_days");
  const [sourceKind, setSourceKind] = useState<AskSourceKind>("records");
  const [savingRecordMessageIds, setSavingRecordMessageIds] = useState<
    ReadonlySet<string>
  >(() => new Set());
  const [busy, setBusy] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState("");
  const [liveUser, setLiveUser] = useState<AskMessage | null>(null);
  const [liveAnswer, setLiveAnswer] = useState("");
  const [regeneratingId, setRegeneratingId] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const operationRef = useRef(false);
  const savingRecordMessageIdsRef = useRef(new Set<string>());
  const activeIdRef = useRef(activeId);
  const navigationGenerationRef = useRef(0);
  const conversationMutationGenerationRef = useRef(0);
  const conversationListAbortRef = useRef<AbortController | null>(null);
  const conversationListRequestRef = useRef(0);
  const messagesRequestRef = useRef(0);
  const metadataRequestRef = useRef(0);
  const scopedConversationRef = useRef("");
  activeIdRef.current = activeId;

  const tryStartRecordSave = useCallback((messageId: string) => {
    if (savingRecordMessageIdsRef.current.has(messageId)) {
      return false;
    }
    savingRecordMessageIdsRef.current.add(messageId);
    setSavingRecordMessageIds(new Set(savingRecordMessageIdsRef.current));
    return true;
  }, []);

  const finishRecordSave = useCallback((messageId: string) => {
    if (!savingRecordMessageIdsRef.current.delete(messageId)) {
      return;
    }
    setSavingRecordMessageIds(new Set(savingRecordMessageIdsRef.current));
  }, []);

  const reportError = useCallback(
    (message: string) => {
      setError(message);
      toast.showToast({ kind: "error", message });
    },
    [toast],
  );

  useEffect(() => {
    void locale;
    setError("");
    setConversationsLoadError((current) =>
      current ? t("ask.loadFailed") : current,
    );
    setMessagesLoadError((current) =>
      current ? t("ask.messagesFailed") : current,
    );
  }, [locale, t]);

  const retryConversations = useCallback(() => {
    const requestId = conversationListRequestRef.current + 1;
    conversationListRequestRef.current = requestId;
    conversationListAbortRef.current?.abort();
    const controller = new AbortController();
    conversationListAbortRef.current = controller;
    const mutationGeneration = conversationMutationGenerationRef.current;
    setLoadingConversations(true);
    setConversationsLoadError("");
    listAskConversations(token, {}, controller.signal)
      .then((res) => {
        if (
          !controller.signal.aborted &&
          conversationListRequestRef.current === requestId &&
          conversationMutationGenerationRef.current === mutationGeneration
        ) {
          setConversations(res.conversations);
        }
      })
      .catch((err) => {
        if (
          !controller.signal.aborted &&
          conversationListRequestRef.current === requestId
        ) {
          const message =
            err instanceof Error ? err.message : t("ask.loadFailed");
          setConversationsLoadError(message);
          toast.showToast({ kind: "error", message });
        }
      })
      .finally(() => {
        if (
          !controller.signal.aborted &&
          conversationListRequestRef.current === requestId
        ) {
          setLoadingConversations(false);
          if (conversationListAbortRef.current === controller) {
            conversationListAbortRef.current = null;
          }
        }
      });
  }, [token, t, toast]);

  useEffect(() => {
    retryConversations();
    return () => {
      conversationListRequestRef.current += 1;
      conversationListAbortRef.current?.abort();
      conversationListAbortRef.current = null;
    };
  }, [retryConversations]);

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
          reportError(
            cause instanceof Error ? cause.message : t("ask.metadataFailed"),
          );
        }
      });

    return () => {
      metadataRequestRef.current += 1;
      controller.abort();
    };
  }, [
    activeId,
    activeSnapshot,
    conversations,
    loadingConversations,
    token,
    t,
    reportError,
  ]);

  const loadMessages = useCallback(
    (conversationId: string) => {
      const requestId = messagesRequestRef.current + 1;
      messagesRequestRef.current = requestId;
      setMessagesLoadError("");
      setMessages([]);
      setLoadingMessages(true);
      void listAskMessages(token, conversationId)
        .then((res) => {
          if (
            messagesRequestRef.current === requestId &&
            activeIdRef.current === conversationId
          ) {
            setMessages(res.messages);
          }
        })
        .catch((err) => {
          if (
            messagesRequestRef.current === requestId &&
            activeIdRef.current === conversationId
          ) {
            const message =
              err instanceof Error ? err.message : t("ask.messagesFailed");
            setMessagesLoadError(message);
            toast.showToast({ kind: "error", message });
          }
        })
        .finally(() => {
          if (
            messagesRequestRef.current === requestId &&
            activeIdRef.current === conversationId
          ) {
            setLoadingMessages(false);
          }
        });
    },
    [token, t, toast],
  );

  const retryMessages = useCallback(() => {
    const conversationId = activeIdRef.current;
    if (conversationId) {
      loadMessages(conversationId);
    }
  }, [loadMessages]);

  useEffect(() => {
    if (!activeId) {
      messagesRequestRef.current += 1;
      setMessages([]);
      setHeadId(null);
      setMessagesLoadError("");
      setLoadingMessages(false);
      return;
    }
    loadMessages(activeId);
    return () => {
      messagesRequestRef.current += 1;
    };
  }, [activeId, loadMessages]);

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
      messagesRequestRef.current += 1;
      activeIdRef.current = id;
      setActiveId(id);
      setActiveSnapshot(
        conversation ?? conversations.find((item) => item.id === id) ?? null,
      );
      setMessages([]);
      setMessagesLoadError("");
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
    messagesRequestRef.current += 1;
    activeIdRef.current = "";
    scopedConversationRef.current = "";
    setActiveId("");
    setActiveSnapshot(null);
    setMessages([]);
    setMessagesLoadError("");
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
        setConversationsLoadError("");
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
            onError: reportError,
          },
          controller.signal,
        );
        await reload(conversationId);
        return true;
      } catch (cause) {
        if (!controller.signal.aborted) {
          reportError(
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
    [token, scope, sourceKind, reload, t, reportError],
  );

  const send = useCallback(
    async (question: string) => {
      const trimmed = question.trim();
      if (!trimmed) {
        reportError(t("ask.questionRequired"));
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
        reportError(
          cause instanceof Error ? cause.message : t("ask.sendFailed"),
        );
        return false;
      } finally {
        operationRef.current = false;
        setBusy(false);
      }
    },
    [token, activeId, scope, runStream, t, reportError],
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
        reportError(
          cause instanceof Error ? cause.message : t("ask.switchBranchFailed"),
        );
      }
    },
    [token, activeId, messages, t, reportError],
  );

  const stop = useCallback(() => {
    abortRef.current?.abort();
    toast.showToast({ kind: "info", message: t("ask.stopped") });
  }, [t, toast]);

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
        toast.showToast({
          kind: "success",
          message: t(archived ? "ask.archived" : "ask.unarchived"),
        });
        return response.conversation;
      } catch (cause) {
        toast.showToast({
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
    [token, t, toast],
  );

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
      conversationsLoadError,
      loadingMessages,
      messagesLoadError,
      activeId,
      activeConversation,
      entries,
      liveUser,
      liveAnswer,
      regeneratingId,
      scope,
      sourceKind,
      savingRecordMessageIds,
      busy,
      streaming,
      error,
      setScope,
      setSourceKind,
      tryStartRecordSave,
      finishRecordSave,
      selectConversation,
      startNew,
      listConversations,
      retryConversations,
      retryMessages,
      setConversationArchived,
      send,
      regenerate,
      selectVariant,
      stop,
    }),
    [
      conversations,
      loadingConversations,
      conversationsLoadError,
      loadingMessages,
      messagesLoadError,
      activeId,
      activeConversation,
      entries,
      liveUser,
      liveAnswer,
      regeneratingId,
      scope,
      sourceKind,
      savingRecordMessageIds,
      busy,
      streaming,
      error,
      tryStartRecordSave,
      finishRecordSave,
      selectConversation,
      startNew,
      listConversations,
      retryConversations,
      retryMessages,
      setConversationArchived,
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
