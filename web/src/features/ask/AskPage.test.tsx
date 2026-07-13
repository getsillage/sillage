import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { I18nProvider } from "../../i18n/I18nProvider";
import type { AskConversation, AskMessage } from "../../lib/api";
import { MemosProvider } from "../memos/MemosContext";
import { AskProvider, useAsk } from "./AskContext";
import { AskPage, shouldShowLiveUser } from "./AskPage";

vi.mock("../../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../lib/api")>();
  return {
    ...actual,
    listMemos: vi.fn().mockResolvedValue({ memos: [] }),
    createMemo: vi.fn(),
    listAskConversations: vi.fn(),
    getAskConversation: vi.fn(),
    createAskConversation: vi.fn(),
    listAskMessages: vi.fn(),
    setAskConversationArchived: vi.fn(),
    streamAskMessage: vi.fn(),
    setAskHead: vi.fn().mockResolvedValue(undefined),
  };
});

import {
  createAskConversation,
  createMemo,
  getAskConversation,
  listAskConversations,
  listAskMessages,
  setAskConversationArchived,
  setAskHead,
  streamAskMessage,
} from "../../lib/api";

function conversation(): AskConversation {
  return {
    id: "c1",
    title: "对话",
    status: "active",
    contextScope: "recent_30_days",
    headMessageId: "a1",
    pinnedAt: null,
    archivedAt: null,
    createdAt: "1",
    updatedAt: "1",
    deletedAt: null,
  };
}

function message(
  id: string,
  role: "user" | "assistant",
  parentId: string | null,
  content: string,
  createdAt: string,
  forkOfId: string | null = null,
): AskMessage {
  return {
    id,
    conversationId: "c1",
    role,
    content,
    parentId,
    forkOfId,
    status: "complete",
    sourceRefs: [],
    model: "gpt-test",
    promptVersion: role === "assistant" ? "ask-answer-v2" : "",
    createdAt,
    updatedAt: createdAt,
    deletedAt: null,
  };
}

function renderAsk(initialEntry = "/ask?conversation=c1") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <MemosProvider token="t">
        <AskProvider token="t">
          <AskPage />
        </AskProvider>
      </MemosProvider>
    </MemoryRouter>,
  );
}

function renderLocalizedAsk(initialEntry = "/ask?conversation=c1") {
  return render(
    <I18nProvider>
      <LanguageSwitcher compact />
      <MemoryRouter initialEntries={[initialEntry]}>
        <MemosProvider token="t">
          <AskProvider token="t">
            <AskPage />
          </AskProvider>
        </MemosProvider>
      </MemoryRouter>
    </I18nProvider>,
  );
}

function ConversationSwitchHarness() {
  const { selectConversation } = useAsk();
  return (
    <>
      <button type="button" onClick={() => selectConversation("c1")}>
        打开第一个对话
      </button>
      <button type="button" onClick={() => selectConversation("c2")}>
        打开第二个对话
      </button>
      <AskPage />
    </>
  );
}

function renderConversationSwitcher() {
  return render(
    <MemoryRouter initialEntries={["/ask"]}>
      <MemosProvider token="t">
        <AskProvider token="t">
          <ConversationSwitchHarness />
        </AskProvider>
      </MemosProvider>
    </MemoryRouter>,
  );
}

function ConversationArchiveHarness() {
  const { conversations, setConversationArchived } = useAsk();
  return (
    <main>
      <button
        type="button"
        onClick={() => void setConversationArchived("c1", false)}
      >
        恢复问答
      </button>
      {conversations.map((item) => (
        <span key={item.id}>{item.title}</span>
      ))}
    </main>
  );
}

function renderArchiveHarness() {
  return render(
    <MemoryRouter>
      <AskProvider token="t">
        <ConversationArchiveHarness />
      </AskProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listAskConversations).mockResolvedValue({
    conversations: [conversation()],
  });
  vi.mocked(createAskConversation).mockResolvedValue({
    conversation: conversation(),
  });
  vi.mocked(getAskConversation).mockResolvedValue({
    conversation: conversation(),
  });
  vi.mocked(setAskConversationArchived).mockResolvedValue({
    conversation: conversation(),
  });
});

describe("AskPage", () => {
  it("keeps an initial conversation-list error visible and retries it", async () => {
    const user = userEvent.setup();
    vi.mocked(listAskConversations)
      .mockRejectedValueOnce(new Error("问答列表读取失败：网络异常"))
      .mockResolvedValueOnce({ conversations: [] });
    const { container } = renderLocalizedAsk("/ask");

    expect(
      await screen.findAllByText("问答列表读取失败：网络异常"),
    ).toHaveLength(2);
    const errorState = within(container).getByRole("region", {
      name: "问答列表读取失败",
    });
    expect(errorState).toHaveTextContent("问答列表读取失败：网络异常");
    expect(screen.queryByText("开始问答")).not.toBeInTheDocument();

    await user.click(
      within(errorState).getByRole("button", { name: "重试读取问答列表" }),
    );

    await waitFor(() => expect(listAskConversations).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("开始问答")).toBeInTheDocument();
    expect(
      within(container).queryByText("问答列表读取失败：网络异常"),
    ).not.toBeInTheDocument();
  });

  it("keeps a message-load error visible and retries the active conversation", async () => {
    const user = userEvent.setup();
    vi.mocked(listAskMessages)
      .mockRejectedValueOnce(new Error("当前问答读取失败：网络异常"))
      .mockResolvedValueOnce({
        messages: [
          message("u1", "user", null, "重试后的问题", "1"),
          message("a1", "assistant", "u1", "重试后恢复的回答", "2"),
        ],
      });
    const { container } = renderLocalizedAsk();

    expect(
      await screen.findAllByText("当前问答读取失败：网络异常"),
    ).toHaveLength(2);
    const errorState = within(container).getByRole("region", {
      name: "当前问答读取失败",
    });
    expect(errorState).toHaveTextContent("当前问答读取失败：网络异常");
    expect(screen.queryByText("开始问答")).not.toBeInTheDocument();

    await user.click(
      within(errorState).getByRole("button", { name: "重试读取当前问答" }),
    );

    expect(await screen.findByText("重试后恢复的回答")).toBeInTheDocument();
    expect(listAskMessages).toHaveBeenCalledTimes(2);
    expect(
      within(container).queryByText("当前问答读取失败：网络异常"),
    ).not.toBeInTheDocument();
  });

  it("clears a stale save error on language changes without saving again", async () => {
    const user = userEvent.setup();
    const userMessage = message("u1", "user", null, "问题", "1");
    const answer = message("a1", "assistant", "u1", "可保存回答", "2");
    vi.mocked(listAskMessages).mockResolvedValue({
      messages: [userMessage, answer],
    });
    vi.mocked(createMemo).mockRejectedValue(new Error("保存失败"));
    renderLocalizedAsk();

    expect(await screen.findByText("可保存回答")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "存为记录" }));
    expect(await screen.findByText("保存失败")).toBeInTheDocument();
    expect(createMemo).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "English" }));

    expect(screen.queryByText("保存失败")).not.toBeInTheDocument();
    expect(screen.getByText("可保存回答")).toBeInTheDocument();
    expect(createMemo).toHaveBeenCalledTimes(1);
  });

  it("loads archived conversation metadata when a deep link is refreshed", async () => {
    const archived = {
      ...conversation(),
      title: "归档后的睡眠问答",
      contextScope: "all" as const,
      archivedAt: "2026-07-10T09:00:00Z",
    };
    vi.mocked(listAskConversations).mockResolvedValue({ conversations: [] });
    vi.mocked(getAskConversation).mockResolvedValue({ conversation: archived });
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });

    renderAsk();

    expect(
      await screen.findByRole("heading", { name: "归档后的睡眠问答" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(/需要时参考全部记录内的相关记录/),
    ).toBeInTheDocument();
    expect(getAskConversation).toHaveBeenCalledWith(
      "t",
      "c1",
      expect.any(AbortSignal),
    );
  });

  it("refreshes the head after continuing an archived conversation", async () => {
    const user = userEvent.setup();
    const archived = {
      ...conversation(),
      title: "归档后的睡眠问答",
      archivedAt: "2026-07-10T09:00:00Z",
    };
    const continued = { ...archived, headMessageId: "a2" };
    vi.mocked(listAskConversations).mockResolvedValue({ conversations: [] });
    vi.mocked(getAskConversation)
      .mockResolvedValueOnce({ conversation: archived })
      .mockResolvedValueOnce({ conversation: continued });
    vi.mocked(listAskMessages)
      .mockResolvedValueOnce({
        messages: [
          message("u1", "user", null, "之前的问题", "1"),
          message("a1", "assistant", "u1", "之前的回答", "2"),
        ],
      })
      .mockResolvedValueOnce({
        messages: [
          message("u1", "user", null, "之前的问题", "1"),
          message("a1", "assistant", "u1", "之前的回答", "2"),
          message("u2", "user", "a1", "继续追问", "3"),
          message("a2", "assistant", "u2", "新的回答", "4"),
        ],
      });
    vi.mocked(streamAskMessage).mockResolvedValue(undefined);

    renderAsk();
    expect(
      await screen.findByRole("heading", { name: "归档后的睡眠问答" }),
    ).toBeInTheDocument();
    await user.type(screen.getByPlaceholderText(/输入问题/), "继续追问");
    await user.click(screen.getByRole("button", { name: "发送" }));

    expect(await screen.findByText("新的回答")).toBeInTheDocument();
    expect(getAskConversation).toHaveBeenCalledTimes(2);
  });

  it("does not let the initial list overwrite a restored conversation", async () => {
    const user = userEvent.setup();
    let finishInitialList:
      | ((value: { conversations: AskConversation[] }) => void)
      | undefined;
    vi.mocked(listAskConversations).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          finishInitialList = resolve;
        }),
    );
    vi.mocked(setAskConversationArchived).mockResolvedValue({
      conversation: { ...conversation(), title: "刚恢复的问答" },
    });
    renderArchiveHarness();

    await user.click(screen.getByRole("button", { name: "恢复问答" }));
    expect(await screen.findByText("刚恢复的问答")).toBeInTheDocument();

    await act(async () => {
      finishInitialList?.({ conversations: [] });
    });
    expect(screen.getByText("刚恢复的问答")).toBeInTheDocument();
  });

  it("does not let stale conversation metadata override a newer selection", async () => {
    const user = userEvent.setup();
    let resolveFirst:
      | ((value: { conversation: AskConversation }) => void)
      | undefined;
    let resolveSecond:
      | ((value: { conversation: AskConversation }) => void)
      | undefined;
    vi.mocked(listAskConversations).mockResolvedValue({ conversations: [] });
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(getAskConversation).mockImplementation(
      (_token, conversationId) =>
        new Promise((resolve) => {
          if (conversationId === "c1") {
            resolveFirst = resolve;
          } else {
            resolveSecond = resolve;
          }
        }),
    );
    renderConversationSwitcher();

    await user.click(screen.getByRole("button", { name: "打开第一个对话" }));
    await waitFor(() =>
      expect(getAskConversation).toHaveBeenCalledWith(
        "t",
        "c1",
        expect.any(AbortSignal),
      ),
    );
    await user.click(screen.getByRole("button", { name: "打开第二个对话" }));
    await waitFor(() =>
      expect(getAskConversation).toHaveBeenCalledWith(
        "t",
        "c2",
        expect.any(AbortSignal),
      ),
    );
    const firstSignal = vi.mocked(getAskConversation).mock.calls[0][2];
    expect(firstSignal?.aborted).toBe(true);

    await act(async () => {
      resolveSecond?.({
        conversation: {
          ...conversation(),
          id: "c2",
          title: "第二个对话标题",
        },
      });
    });
    expect(await screen.findByText("第二个对话标题")).toBeInTheDocument();

    await act(async () => {
      resolveFirst?.({
        conversation: { ...conversation(), title: "迟到的第一个标题" },
      });
    });
    expect(screen.queryByText("迟到的第一个标题")).not.toBeInTheDocument();
    expect(screen.getByText("第二个对话标题")).toBeInTheDocument();
  });

  it("renders an existing conversation's active path", async () => {
    vi.mocked(listAskMessages).mockResolvedValue({
      messages: [
        message("u1", "user", null, "我最近怎么样？", "1"),
        message("a1", "assistant", "u1", "你睡得更好了。", "2"),
      ],
    });
    renderAsk();
    expect(await screen.findByText("我最近怎么样？")).toBeInTheDocument();
    expect(screen.getByText("你睡得更好了。")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /来源记录/ }),
    ).not.toBeInTheDocument();
  });

  it("collapses answer sources by default and toggles them", async () => {
    const user = userEvent.setup();
    const answer = message("a1", "assistant", "u1", "你睡得更好了。", "2");
    answer.sourceRefs = [
      {
        memoId: "memo-1",
        entryDate: "2026-07-09",
        excerpt: "昨晚睡了八小时",
        rank: 1,
      },
    ];
    vi.mocked(listAskMessages).mockResolvedValue({
      messages: [message("u1", "user", null, "我最近怎么样？", "1"), answer],
    });

    renderAsk();
    const toggle = await screen.findByRole("button", { name: "来源记录 1" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("昨晚睡了八小时")).not.toBeInTheDocument();

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("昨晚睡了八小时")).toBeInTheDocument();

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("昨晚睡了八小时")).not.toBeInTheDocument();
  });

  it("streams a new answer token by token", async () => {
    const user = userEvent.setup();
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(streamAskMessage).mockImplementation(
      async (_token, _conv, _input, handlers) => {
        handlers.onStart?.({
          userMessage: message("u9", "user", null, "新问题", "09"),
          sources: [],
        });
        handlers.onDelta?.("回答");
        handlers.onDelta?.("片段");
      },
    );
    // After the stream, the context reloads canonical messages.
    vi.mocked(listAskMessages)
      .mockResolvedValueOnce({ messages: [] })
      .mockResolvedValue({
        messages: [
          message("u9", "user", null, "新问题", "09"),
          message("a9", "assistant", "u9", "回答片段", "10"),
        ],
      });

    renderAsk();
    await screen.findByPlaceholderText(/输入问题/);
    await user.type(screen.getByPlaceholderText(/输入问题/), "新问题");
    await user.click(screen.getByRole("button", { name: "发送" }));

    expect(await screen.findByText("回答片段")).toBeInTheDocument();
    expect(streamAskMessage).toHaveBeenCalled();
  });

  it("keeps the first answer's pending state after its user message loads", async () => {
    const user = userEvent.setup();
    let finishStream: (() => void) | undefined;
    let finishMessageLoad:
      | ((value: { messages: AskMessage[] }) => void)
      | undefined;
    const firstQuestion = message("u-first", "user", null, "第一条问题", "1");
    vi.mocked(listAskConversations).mockResolvedValue({ conversations: [] });
    vi.mocked(createAskConversation).mockResolvedValue({
      conversation: { ...conversation(), headMessageId: null },
    });
    vi.mocked(listAskMessages)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishMessageLoad = resolve;
          }),
      )
      .mockResolvedValue({
        messages: [
          firstQuestion,
          message("a-first", "assistant", firstQuestion.id, "第一条回答", "2"),
        ],
      });
    vi.mocked(streamAskMessage).mockImplementation(
      async (_token, _conv, _input, handlers) => {
        handlers.onStart?.({ userMessage: firstQuestion, sources: [] });
        await new Promise<void>((resolve) => {
          finishStream = resolve;
        });
      },
    );

    renderAsk("/ask");
    const input = await screen.findByPlaceholderText(/输入问题/);
    await user.type(input, firstQuestion.content);
    await user.click(screen.getByRole("button", { name: "发送" }));

    expect(await screen.findByText("正在整理问答")).toBeInTheDocument();
    await act(async () => {
      finishMessageLoad?.({ messages: [firstQuestion] });
    });
    await waitFor(() =>
      expect(screen.getAllByText(firstQuestion.content)).toHaveLength(1),
    );
    expect(screen.getByText("正在整理问答")).toBeInTheDocument();

    await act(async () => {
      finishStream?.();
    });
    await screen.findByRole("button", { name: "发送" });
  });

  it("shows an error when a new conversation cannot be created", async () => {
    const user = userEvent.setup();
    vi.mocked(listAskConversations).mockResolvedValue({ conversations: [] });
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(createAskConversation).mockRejectedValue(
      new Error("创建问答失败：网络异常"),
    );

    renderAsk("/ask");
    const input = await screen.findByPlaceholderText(/输入问题/);
    await user.type(input, "新的问题");
    await user.click(screen.getByRole("button", { name: "发送" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "创建问答失败：网络异常",
    );
    expect(input).toHaveValue("新的问题");
    expect(streamAskMessage).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "发送" })).toBeEnabled();
  });

  it("clears the composer as soon as a question is sent", async () => {
    const user = userEvent.setup();
    let finishStream: (() => void) | undefined;
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(streamAskMessage).mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishStream = resolve;
        }),
    );

    renderAsk();
    const input = await screen.findByPlaceholderText(/输入问题/);
    await user.type(input, "正在发送的问题");
    await user.click(screen.getByRole("button", { name: "发送" }));

    expect(input).toHaveValue("");
    finishStream?.();
    await waitFor(() => expect(streamAskMessage).toHaveBeenCalled());
  });

  it("does not start a second stream when Enter is pressed while busy", async () => {
    const user = userEvent.setup();
    let finishStream: (() => void) | undefined;
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(streamAskMessage).mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishStream = resolve;
        }),
    );

    renderAsk();
    const input = await screen.findByPlaceholderText(/输入问题/);
    await user.type(input, "第一个问题");
    await user.keyboard("{Enter}");
    await waitFor(() => expect(streamAskMessage).toHaveBeenCalledTimes(1));

    await user.type(input, "准备好的下一个问题");
    await user.keyboard("{Enter}");
    expect(streamAskMessage).toHaveBeenCalledTimes(1);
    expect(input).toHaveValue("准备好的下一个问题");

    finishStream?.();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "发送" })).toBeEnabled(),
    );
  });

  it("sends with Enter and keeps Shift+Enter as a newline", async () => {
    const user = userEvent.setup();
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(streamAskMessage).mockImplementation(
      async (_token, _conv, _input, handlers) => {
        handlers.onStart?.({
          userMessage: message("u9", "user", null, "第一行\n第二行", "09"),
          sources: [],
        });
        handlers.onDelta?.("回答");
      },
    );
    vi.mocked(listAskMessages)
      .mockResolvedValueOnce({ messages: [] })
      .mockResolvedValue({
        messages: [
          message("u9", "user", null, "第一行\n第二行", "09"),
          message("a9", "assistant", "u9", "回答", "10"),
        ],
      });

    renderAsk();
    const input = await screen.findByPlaceholderText(/输入问题/);
    await user.type(input, "第一行{Shift>}{Enter}{/Shift}第二行");
    expect(streamAskMessage).not.toHaveBeenCalled();

    await user.keyboard("{Enter}");

    await waitFor(() => expect(streamAskMessage).toHaveBeenCalled());
    expect(streamAskMessage).toHaveBeenCalledWith(
      "t",
      "c1",
      expect.objectContaining({ content: "第一行\n第二行" }),
      expect.anything(),
      expect.anything(),
    );
  });

  it("does not render the live user bubble twice after reload", () => {
    const liveUser = message("u9", "user", null, "新问题", "09");
    const entries = [{ message: liveUser, variants: [liveUser], index: 0 }];

    expect(shouldShowLiveUser(entries, liveUser)).toBe(false);
    expect(shouldShowLiveUser([], liveUser)).toBe(true);
  });

  it("shows a pending hint while regenerating an answer", async () => {
    const user = userEvent.setup();
    let streamHandlers: Parameters<typeof streamAskMessage>[3] | undefined;
    let finishStream: (() => void) | undefined;
    vi.mocked(listAskMessages).mockResolvedValue({
      messages: [
        message("u1", "user", null, "问题", "1"),
        message("a1", "assistant", "u1", "原回答", "2"),
      ],
    });
    vi.mocked(streamAskMessage).mockImplementation(
      async (_token, _conv, _input, handlers) => {
        streamHandlers = handlers;
        await new Promise<void>((resolve) => {
          finishStream = resolve;
        });
      },
    );

    renderAsk();
    expect(await screen.findByText("原回答")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "重新生成" }));

    expect(await screen.findByRole("status")).toHaveTextContent("正在整理问答");
    expect(screen.queryByText("原回答")).not.toBeInTheDocument();

    act(() => streamHandlers?.onDelta?.("新回答"));
    expect(await screen.findByText("新回答")).toBeInTheDocument();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    await act(async () => finishStream?.());
  });

  it("switches between regenerated answer variants", async () => {
    const user = userEvent.setup();
    // u1 has two assistant answers; head points at the newer (a1b).
    vi.mocked(listAskConversations).mockResolvedValue({
      conversations: [
        {
          ...conversation(),
          headMessageId: "a1b",
        },
      ],
    });
    vi.mocked(listAskMessages).mockResolvedValue({
      messages: [
        message("u1", "user", null, "问题", "1"),
        message("a1", "assistant", "u1", "第一个回答", "2"),
        message("a1b", "assistant", "u1", "第二个回答", "3", "a1"),
      ],
    });
    renderAsk();

    expect(await screen.findByText("第二个回答")).toBeInTheDocument();
    expect(screen.getByText("2/2")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "上一个回答" }));
    await waitFor(() =>
      expect(setAskHead).toHaveBeenCalledWith("t", "c1", "a1"),
    );
    expect(await screen.findByText("第一个回答")).toBeInTheDocument();
  });

  it("uses the loaded conversation head when opened from a URL", async () => {
    vi.mocked(listAskConversations).mockResolvedValue({
      conversations: [
        {
          ...conversation(),
          // The server remembers that the user selected the first variant.
          headMessageId: "a1",
        },
      ],
    });
    vi.mocked(listAskMessages).mockResolvedValue({
      messages: [
        message("u1", "user", null, "问题", "1"),
        message("a1", "assistant", "u1", "第一个回答", "2"),
        message("a1b", "assistant", "u1", "第二个回答", "3", "a1"),
      ],
    });

    renderAsk();

    expect(await screen.findByText("第一个回答")).toBeInTheDocument();
    expect(screen.queryByText("第二个回答")).not.toBeInTheDocument();
  });

  it("clears old messages while another conversation is loading", async () => {
    const user = userEvent.setup();
    let rejectSecond: ((reason: Error) => void) | undefined;
    vi.mocked(listAskConversations).mockResolvedValue({
      conversations: [
        conversation(),
        { ...conversation(), id: "c2", title: "第二个对话" },
      ],
    });
    vi.mocked(listAskMessages).mockImplementation((_token, id) => {
      if (id === "c1") {
        return Promise.resolve({
          messages: [message("u1", "user", null, "第一个对话的问题", "1")],
        });
      }
      return new Promise((_, reject) => {
        rejectSecond = reject;
      });
    });

    renderConversationSwitcher();
    await waitFor(() => expect(listAskConversations).toHaveBeenCalled());
    await user.click(screen.getByRole("button", { name: "打开第一个对话" }));
    expect(await screen.findByText("第一个对话的问题")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "打开第二个对话" }));
    expect(screen.queryByText("第一个对话的问题")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("正在读取对话");

    rejectSecond?.(new Error("第二个对话读取失败"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "第二个对话读取失败",
    );
    expect(screen.queryByText("第一个对话的问题")).not.toBeInTheDocument();
  });

  it("ignores messages that arrive after switching conversations", async () => {
    const user = userEvent.setup();
    let resolveFirst: ((value: { messages: AskMessage[] }) => void) | undefined;
    let resolveSecond:
      | ((value: { messages: AskMessage[] }) => void)
      | undefined;
    vi.mocked(listAskConversations).mockResolvedValue({
      conversations: [
        conversation(),
        {
          ...conversation(),
          id: "c2",
          title: "第二个对话",
          headMessageId: "a2",
        },
      ],
    });
    vi.mocked(listAskMessages).mockImplementation(
      (_token, id) =>
        new Promise((resolve) => {
          if (id === "c1") {
            resolveFirst = resolve;
          } else {
            resolveSecond = resolve;
          }
        }),
    );
    renderConversationSwitcher();
    await waitFor(() => expect(listAskConversations).toHaveBeenCalled());

    await user.click(screen.getByRole("button", { name: "打开第一个对话" }));
    await waitFor(() =>
      expect(listAskMessages).toHaveBeenCalledWith("t", "c1"),
    );
    await user.click(screen.getByRole("button", { name: "打开第二个对话" }));
    await waitFor(() =>
      expect(listAskMessages).toHaveBeenCalledWith("t", "c2"),
    );

    await act(async () => {
      resolveSecond?.({
        messages: [
          message("u2", "user", null, "第二个对话的问题", "3"),
          message("a2", "assistant", "u2", "第二个对话的回答", "4"),
        ],
      });
    });
    expect(await screen.findByText("第二个对话的回答")).toBeInTheDocument();

    await act(async () => {
      resolveFirst?.({
        messages: [message("u1", "user", null, "迟到的第一个问题", "1")],
      });
    });
    expect(screen.queryByText("迟到的第一个问题")).not.toBeInTheDocument();
    expect(screen.getByText("第二个对话的回答")).toBeInTheDocument();
  });

  it("keeps the user-selected scope after conversation reload", async () => {
    const user = userEvent.setup();
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(streamAskMessage).mockImplementation(async () => undefined);

    renderAsk();
    await screen.findByPlaceholderText(/输入问题/);
    await user.selectOptions(screen.getByLabelText("范围"), "all");
    await user.type(screen.getByPlaceholderText(/输入问题/), "总结全部");
    await user.click(screen.getByRole("button", { name: "发送" }));

    await waitFor(() => expect(streamAskMessage).toHaveBeenCalled());
    expect(streamAskMessage).toHaveBeenCalledWith(
      "t",
      "c1",
      expect.objectContaining({ contextScope: "all" }),
      expect.anything(),
      expect.anything(),
    );
    expect(screen.getByLabelText("范围")).toHaveValue("all");
  });

  it("does not let a stale conversation creation override navigation", async () => {
    const user = userEvent.setup();
    let finishCreate:
      | ((value: { conversation: AskConversation }) => void)
      | undefined;
    const second = { ...conversation(), id: "c2", title: "第二个对话" };
    const staleCreated = {
      ...conversation(),
      id: "stale-created",
      title: "过期新对话",
    };
    vi.mocked(listAskConversations).mockResolvedValue({
      conversations: [conversation(), second],
    });
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(createAskConversation).mockImplementation(
      () =>
        new Promise((resolve) => {
          finishCreate = resolve;
        }),
    );

    renderConversationSwitcher();
    const input = await screen.findByPlaceholderText(/输入问题/);
    await user.type(input, "不应进入第二个对话的问题");
    await user.click(screen.getByRole("button", { name: "发送" }));
    await waitFor(() => expect(createAskConversation).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole("button", { name: "打开第二个对话" }));
    expect(
      await screen.findByRole("heading", { name: "第二个对话" }),
    ).toBeInTheDocument();

    await act(async () => {
      finishCreate?.({ conversation: staleCreated });
    });
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "发送" })).toBeDisabled(),
    );
    expect(screen.getByLabelText("范围")).toBeInTheDocument();
    expect(input).toHaveValue("");
    expect(
      screen.getByRole("heading", { name: "第二个对话" }),
    ).toBeInTheDocument();
    expect(streamAskMessage).not.toHaveBeenCalled();
  });

  it("ignores a stale conversation creation error after navigation", async () => {
    const user = userEvent.setup();
    let failCreate: ((reason: Error) => void) | undefined;
    const second = { ...conversation(), id: "c2", title: "第二个对话" };
    vi.mocked(listAskConversations).mockResolvedValue({
      conversations: [conversation(), second],
    });
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(createAskConversation).mockImplementation(
      () =>
        new Promise((_, reject) => {
          failCreate = reject;
        }),
    );

    renderConversationSwitcher();
    const input = await screen.findByPlaceholderText(/输入问题/);
    await user.type(input, "不应恢复到第二个对话的问题");
    await user.click(screen.getByRole("button", { name: "发送" }));
    await waitFor(() => expect(createAskConversation).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole("button", { name: "打开第二个对话" }));
    expect(
      await screen.findByRole("heading", { name: "第二个对话" }),
    ).toBeInTheDocument();

    await act(async () => {
      failCreate?.(new Error("过期创建失败"));
    });
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "发送" })).toBeDisabled(),
    );
    expect(input).toHaveValue("");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "第二个对话" }),
    ).toBeInTheDocument();
    expect(streamAskMessage).not.toHaveBeenCalled();
  });
});
