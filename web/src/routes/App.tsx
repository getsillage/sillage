import {
  BookOpenText,
  Check,
  FileUp,
  History,
  LogIn,
  MessageSquareText,
  Pin,
  PinOff,
  Settings,
  Trash2,
} from "lucide-react";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import {
  type Account,
  type AskContextScope,
  type AskConversation,
  type AskMessage,
  createAskConversation,
  createAskMessage,
  createMemo,
  deleteMemo,
  getBootstrap,
  getMe,
  initializeAccount,
  listAskConversations,
  listAskMessages,
  listMemos,
  type Memo,
  setMemoArchived,
  setMemoPinned,
  signIn,
  updateMemo,
  uploadAttachment,
} from "../lib/api";
import { getAccessToken, saveAccessToken } from "../lib/auth";

type BootstrapState = "loading" | "needs-init" | "ready";

export function App() {
  const [bootstrap, setBootstrap] = useState<BootstrapState>("loading");
  const [account, setAccount] = useState<Account | null>(null);
  const [token, setToken] = useState(() => getAccessToken());

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const state = await getBootstrap();
      if (cancelled) {
        return;
      }
      setBootstrap(state.initialized ? "ready" : "needs-init");
      if (state.initialized && token) {
        try {
          const me = await getMe(token);
          if (!cancelled) {
            setAccount(me.account);
          }
        } catch {
          if (!cancelled) {
            setAccount(null);
          }
        }
      }
    }
    load().catch(() => setBootstrap("ready"));
    return () => {
      cancelled = true;
    };
  }, [token]);

  if (bootstrap === "loading") {
    return <FullPageState text="正在打开 Sillage" />;
  }

  return (
    <Routes>
      <Route
        path="/initialize"
        element={
          bootstrap === "needs-init" ? (
            <InitializePage
              onDone={(nextToken, nextAccount) => {
                saveAccessToken(nextToken);
                setToken(nextToken);
                setAccount(nextAccount);
                setBootstrap("ready");
              }}
            />
          ) : (
            <Navigate to="/" replace />
          )
        }
      />
      <Route
        path="/login"
        element={
          bootstrap === "needs-init" ? (
            <Navigate to="/initialize" replace />
          ) : (
            <LoginPage
              onDone={(nextToken, nextAccount) => {
                saveAccessToken(nextToken);
                setToken(nextToken);
                setAccount(nextAccount);
              }}
            />
          )
        }
      />
      <Route
        path="*"
        element={
          bootstrap === "needs-init" ? (
            <Navigate to="/initialize" replace />
          ) : account && token ? (
            <Shell account={account} token={token} />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
    </Routes>
  );
}

function InitializePage({
  onDone,
}: {
  onDone: (token: string, account: Account) => void;
}) {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  return (
    <AuthSurface title="创建唯一账号" icon={<LogIn size={20} />}>
      <form
        className="auth-form"
        onSubmit={async (event) => {
          event.preventDefault();
          setError("");
          try {
            const res = await initializeAccount({
              username,
              displayName,
              password,
            });
            onDone(res.accessToken, res.account);
            navigate("/", { replace: true });
          } catch (err) {
            setError(err instanceof Error ? err.message : "初始化失败");
          }
        }}
      >
        <TextInput
          label="账号"
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <TextInput
          label="显示名"
          value={displayName}
          onChange={setDisplayName}
          autoComplete="name"
        />
        <TextInput
          label="密码"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="new-password"
        />
        {error && <p className="form-error">{error}</p>}
        <button className="primary-button" type="submit">
          创建并进入
        </button>
      </form>
    </AuthSurface>
  );
}

function LoginPage({
  onDone,
}: {
  onDone: (token: string, account: Account) => void;
}) {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  return (
    <AuthSurface title="登录 Sillage" icon={<LogIn size={20} />}>
      <form
        className="auth-form"
        onSubmit={async (event) => {
          event.preventDefault();
          setError("");
          try {
            const res = await signIn({ username, password });
            onDone(res.accessToken, res.account);
            navigate("/", { replace: true });
          } catch (err) {
            setError(err instanceof Error ? err.message : "登录失败");
          }
        }}
      >
        <TextInput
          label="账号"
          value={username}
          onChange={setUsername}
          autoComplete="username"
        />
        <TextInput
          label="密码"
          type="password"
          value={password}
          onChange={setPassword}
          autoComplete="current-password"
        />
        {error && <p className="form-error">{error}</p>}
        <button className="primary-button" type="submit">
          登录
        </button>
      </form>
    </AuthSurface>
  );
}

function Shell({ account, token }: { account: Account; token: string }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [memos, setMemos] = useState<Memo[]>([]);
  const [content, setContent] = useState("");
  const [entryDate, setEntryDate] = useState(today());
  const [editing, setEditing] = useState<Memo | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [view, setView] = useState<"records" | "history" | "ask">(
    location.pathname === "/ask" ? "ask" : "records",
  );
  const [askConversations, setAskConversations] = useState<AskConversation[]>(
    [],
  );
  const [activeConversationId, setActiveConversationId] = useState("");
  const [askMessages, setAskMessages] = useState<AskMessage[]>([]);
  const [askQuestion, setAskQuestion] = useState("");
  const [askScope, setAskScope] = useState<AskContextScope>("recent_30_days");
  const [askBusy, setAskBusy] = useState(false);
  const [askMessage, setAskMessage] = useState("");

  useEffect(() => {
    listMemos(token)
      .then((res) => setMemos(sortMemos(res.memos)))
      .catch((err) =>
        setMessage(err instanceof Error ? err.message : "读取记录失败"),
      );
  }, [token]);

  useEffect(() => {
    if (location.pathname === "/ask") {
      setView("ask");
    }
  }, [location.pathname]);

  useEffect(() => {
    listAskConversations(token)
      .then((res) => {
        setAskConversations(res.conversations);
        if (!activeConversationId && res.conversations[0]) {
          setActiveConversationId(res.conversations[0].id);
          setAskScope(res.conversations[0].contextScope);
        }
      })
      .catch((err) =>
        setAskMessage(err instanceof Error ? err.message : "读取问答失败"),
      );
  }, [token, activeConversationId]);

  useEffect(() => {
    if (!activeConversationId) {
      setAskMessages([]);
      return;
    }
    listAskMessages(token, activeConversationId)
      .then((res) => setAskMessages(res.messages))
      .catch((err) =>
        setAskMessage(err instanceof Error ? err.message : "读取消息失败"),
      );
  }, [token, activeConversationId]);

  async function saveMemo() {
    if (!content.trim()) {
      setMessage("先写下要保存的内容");
      return;
    }
    setSaving(true);
    setMessage("");
    try {
      const res = editing
        ? await updateMemo(token, editing, { content, entryDate })
        : await createMemo(token, { content, entryDate });
      setMemos((current) => sortMemos(upsertMemo(current, res.memo)));
      setContent("");
      setEntryDate(today());
      setEditing(null);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function patchMemo(memo: Memo, action: "pin" | "archive" | "delete") {
    try {
      if (action === "delete" && !window.confirm("确定删除这条记录吗？")) {
        return;
      }
      const res =
        action === "pin"
          ? await setMemoPinned(token, memo, !memo.pinnedAt)
          : action === "archive"
            ? await setMemoArchived(token, memo, !memo.archivedAt)
            : await deleteMemo(token, memo);
      setMemos((current) =>
        sortMemos(
          action === "delete"
            ? current.filter((item) => item.id !== memo.id)
            : upsertMemo(current, res.memo),
        ),
      );
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "操作失败");
    }
  }

  async function handleFiles(files: FileList | null) {
    if (!files?.length) {
      return;
    }
    setMessage("");
    try {
      const uploads = await Promise.all(
        Array.from(files).map((file) => uploadAttachment(token, file)),
      );
      const markdown = uploads
        .map(({ attachment }) =>
          attachment.contentType.startsWith("image/")
            ? `![${attachment.filename}](${attachment.url})`
            : `[${attachment.filename}](${attachment.url})`,
        )
        .join("\n");
      setContent((current) => [current, markdown].filter(Boolean).join("\n\n"));
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "上传失败");
    }
  }

  async function startAskConversation(scope = askScope) {
    setAskBusy(true);
    setAskMessage("");
    try {
      const res = await createAskConversation(token, { contextScope: scope });
      setAskConversations((current) => [res.conversation, ...current]);
      setActiveConversationId(res.conversation.id);
      setAskMessages([]);
      setAskScope(res.conversation.contextScope);
      setView("ask");
    } catch (err) {
      setAskMessage(err instanceof Error ? err.message : "创建会话失败");
    } finally {
      setAskBusy(false);
    }
  }

  async function sendAskMessage() {
    const question = askQuestion.trim();
    if (!question) {
      setAskMessage("先写下要问的问题");
      return;
    }
    setAskBusy(true);
    setAskMessage("");
    try {
      let conversationId = activeConversationId;
      let createdNewConversation = false;
      if (!conversationId) {
        const created = await createAskConversation(token, {
          contextScope: askScope,
        });
        conversationId = created.conversation.id;
        createdNewConversation = true;
        setAskConversations((current) => [created.conversation, ...current]);
      }
      const res = await createAskMessage(token, conversationId, {
        content: question,
        contextScope: askScope,
      });
      setAskMessages((current) =>
        createdNewConversation ? res.messages : [...current, ...res.messages],
      );
      setActiveConversationId(conversationId);
      setAskQuestion("");
      const refreshed = await listAskConversations(token);
      setAskConversations(refreshed.conversations);
    } catch (err) {
      setAskMessage(err instanceof Error ? err.message : "生成回答失败");
    } finally {
      setAskBusy(false);
    }
  }

  const visibleMemos = memos.filter((memo) =>
    view === "history" ? true : !memo.archivedAt,
  );
  const activeConversation = askConversations.find(
    (conversation) => conversation.id === activeConversationId,
  );

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">Sillage</div>
        <nav className="nav-list">
          <button
            className={`nav-item ${view === "records" ? "active" : ""}`}
            type="button"
            onClick={() => {
              setView("records");
              navigate("/");
            }}
          >
            <BookOpenText size={18} />
            记录
          </button>
          <button
            className={`nav-item ${view === "history" ? "active" : ""}`}
            type="button"
            onClick={() => {
              setView("history");
              navigate("/");
            }}
          >
            <History size={18} />
            历史
          </button>
          <button
            className={`nav-item ${view === "ask" ? "active" : ""}`}
            type="button"
            onClick={() => {
              setView("ask");
              navigate("/ask");
            }}
          >
            <MessageSquareText size={18} />
            问答
          </button>
        </nav>
        <div className="account-card">
          <span>{account.displayName || account.username}</span>
          <Settings size={16} />
        </div>
      </aside>
      <main className="workspace">
        {view === "ask" ? (
          <AskWorkspace
            conversations={askConversations}
            activeConversation={activeConversation}
            messages={askMessages}
            question={askQuestion}
            scope={askScope}
            busy={askBusy}
            notice={askMessage}
            onQuestionChange={setAskQuestion}
            onScopeChange={setAskScope}
            onSend={sendAskMessage}
            onNew={() => startAskConversation()}
            onSelect={(conversation) => {
              setActiveConversationId(conversation.id);
              setAskScope(conversation.contextScope);
            }}
          />
        ) : (
          <>
            <section className="memo-composer">
              <div className="composer-header">
                <label className="date-field">
                  <span>日期</span>
                  <input
                    type="date"
                    value={entryDate}
                    onChange={(event) => setEntryDate(event.target.value)}
                  />
                </label>
                {editing && (
                  <button
                    className="text-button"
                    type="button"
                    onClick={() => {
                      setEditing(null);
                      setContent("");
                      setEntryDate(today());
                    }}
                  >
                    取消编辑
                  </button>
                )}
              </div>
              <textarea
                value={content}
                placeholder="写下想记录的内容…"
                onChange={(event) => setContent(event.target.value)}
                onDrop={(event) => {
                  event.preventDefault();
                  handleFiles(event.dataTransfer.files);
                }}
                onDragOver={(event) => event.preventDefault()}
              />
              <div className="composer-actions">
                <label className="icon-button" title="上传附件">
                  <FileUp size={18} />
                  <input
                    type="file"
                    multiple
                    onChange={(event) => handleFiles(event.target.files)}
                  />
                </label>
                <button
                  className="secondary-button"
                  type="button"
                  disabled={saving}
                  onClick={saveMemo}
                >
                  <Check size={16} />
                  {saving ? "保存中" : editing ? "保存修改" : "保存"}
                </button>
              </div>
              {message && <p className="form-error">{message}</p>}
            </section>
            {view === "history" && <ActivityStrip memos={memos} />}
            <section className="memo-list">
              {visibleMemos.length === 0 ? (
                <div className="empty-list">还没有记录。可以先写一条记录。</div>
              ) : (
                visibleMemos.map((memo) => (
                  <article
                    className="memo-item"
                    id={`memo-${memo.id}`}
                    key={memo.id}
                  >
                    <div className="memo-meta">
                      <time>{memo.entryDate}</time>
                      {memo.archivedAt && <span>已归档</span>}
                    </div>
                    <p>{memo.content}</p>
                    <div className="memo-actions">
                      <button
                        className="icon-button"
                        type="button"
                        title={memo.pinnedAt ? "取消置顶" : "置顶"}
                        onClick={() => patchMemo(memo, "pin")}
                      >
                        {memo.pinnedAt ? (
                          <PinOff size={16} />
                        ) : (
                          <Pin size={16} />
                        )}
                      </button>
                      <button
                        className="text-button"
                        type="button"
                        onClick={() => patchMemo(memo, "archive")}
                      >
                        {memo.archivedAt ? "取消归档" : "归档"}
                      </button>
                      <button
                        className="text-button"
                        type="button"
                        onClick={() => {
                          setEditing(memo);
                          setContent(memo.content);
                          setEntryDate(memo.entryDate);
                        }}
                      >
                        编辑
                      </button>
                      <button
                        className="danger-button"
                        type="button"
                        onClick={() => patchMemo(memo, "delete")}
                      >
                        <Trash2 size={15} />
                        删除
                      </button>
                    </div>
                  </article>
                ))
              )}
            </section>
          </>
        )}
      </main>
    </div>
  );
}

function AskWorkspace({
  conversations,
  activeConversation,
  messages,
  question,
  scope,
  busy,
  notice,
  onQuestionChange,
  onScopeChange,
  onSend,
  onNew,
  onSelect,
}: {
  conversations: AskConversation[];
  activeConversation?: AskConversation;
  messages: AskMessage[];
  question: string;
  scope: AskContextScope;
  busy: boolean;
  notice: string;
  onQuestionChange: (value: string) => void;
  onScopeChange: (value: AskContextScope) => void;
  onSend: () => void;
  onNew: () => void;
  onSelect: (conversation: AskConversation) => void;
}) {
  return (
    <section className="ask-workspace">
      <aside className="ask-conversations">
        <button
          className="secondary-button"
          type="button"
          disabled={busy}
          onClick={onNew}
        >
          <MessageSquareText size={16} />
          新问答
        </button>
        <div className="ask-conversation-list">
          {conversations.length === 0 ? (
            <p className="empty-list">还没有问答会话。</p>
          ) : (
            conversations.map((conversation) => (
              <button
                className={`ask-conversation-item ${
                  activeConversation?.id === conversation.id ? "active" : ""
                }`}
                type="button"
                key={conversation.id}
                onClick={() => onSelect(conversation)}
              >
                <span>{conversation.title || "新的问答"}</span>
                <small>{scopeLabel(conversation.contextScope)}</small>
              </button>
            ))
          )}
        </div>
      </aside>
      <div className="ask-panel">
        <div className="ask-header">
          <div>
            <h1>{activeConversation?.title || "根据记录提问"}</h1>
            <p>{scopeLabel(scope)}</p>
          </div>
          <label className="scope-field">
            <span>范围</span>
            <select
              value={scope}
              onChange={(event) =>
                onScopeChange(event.target.value as AskContextScope)
              }
            >
              <option value="recent_7_days">最近 7 天</option>
              <option value="recent_30_days">最近 30 天</option>
              <option value="all">全部记录</option>
            </select>
          </label>
        </div>
        <div className="ask-messages">
          {messages.length === 0 ? (
            <div className="ask-empty">
              <p>可以根据记录提问。</p>
            </div>
          ) : (
            messages.map((message) => (
              <article
                className={`ask-message ${message.role}`}
                key={message.id}
              >
                <p>{message.content}</p>
                {message.sourceRefs.length > 0 && (
                  <div className="source-list">
                    {message.sourceRefs.map((source) => (
                      <a
                        href={`/#memo-${source.memoId}`}
                        key={`${message.id}-${source.memoId}-${source.rank}`}
                      >
                        <span>{source.entryDate}</span>
                        <small>{source.excerpt}</small>
                      </a>
                    ))}
                  </div>
                )}
              </article>
            ))
          )}
        </div>
        <div className="ask-input">
          <textarea
            value={question}
            placeholder="根据记录提问…"
            onChange={(event) => onQuestionChange(event.target.value)}
          />
          <button
            className="secondary-button"
            type="button"
            disabled={busy}
            onClick={onSend}
          >
            {busy ? "生成中" : "发送"}
          </button>
        </div>
        {notice && <p className="form-error">{notice}</p>}
      </div>
    </section>
  );
}

function ActivityStrip({ memos }: { memos: Memo[] }) {
  const counts = new Map<string, number>();
  for (const memo of memos) {
    counts.set(memo.entryDate, (counts.get(memo.entryDate) ?? 0) + 1);
  }
  const days = Array.from({ length: 35 }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() - (34 - index));
    const key = date.toISOString().slice(0, 10);
    return { key, count: counts.get(key) ?? 0 };
  });
  return (
    <section className="activity-strip" aria-label="最近记录活动">
      {days.map((day) => (
        <span
          className={`activity-cell level-${Math.min(day.count, 4)}`}
          key={day.key}
          title={`${day.key}: ${day.count} 条`}
        />
      ))}
    </section>
  );
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function upsertMemo(memos: Memo[], memo: Memo) {
  const next = memos.filter((item) => item.id !== memo.id);
  next.push(memo);
  return next;
}

function sortMemos(memos: Memo[]) {
  return [...memos].sort((a, b) => {
    if (a.pinnedAt && !b.pinnedAt) {
      return -1;
    }
    if (!a.pinnedAt && b.pinnedAt) {
      return 1;
    }
    return b.updatedAt.localeCompare(a.updatedAt);
  });
}

function scopeLabel(scope: AskContextScope) {
  if (scope === "recent_7_days") {
    return "最近 7 天";
  }
  if (scope === "all") {
    return "全部记录";
  }
  return "最近 30 天";
}

function AuthSurface({
  title,
  icon,
  children,
}: {
  title: string;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <main className="auth-page">
      <section className="auth-panel">
        <div className="auth-title">
          {icon}
          <h1>{title}</h1>
        </div>
        {children}
      </section>
    </main>
  );
}

function TextInput({
  label,
  value,
  onChange,
  type = "text",
  autoComplete,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  autoComplete?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        autoComplete={autoComplete}
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function FullPageState({ text }: { text: string }) {
  return <main className="full-page-state">{text}</main>;
}
