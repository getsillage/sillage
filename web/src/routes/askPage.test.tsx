import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AskConversation, AskMessage } from "../lib/api";
import { AskProvider, useAsk } from "../state/AskContext";
import { MemosProvider } from "../state/MemosContext";
import { AskPage, shouldShowLiveUser } from "./AskPage";

vi.mock("../lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../lib/api")>();
  return {
    ...actual,
    listMemos: vi.fn().mockResolvedValue({ memos: [] }),
    listAskConversations: vi.fn(),
    createAskConversation: vi.fn(),
    listAskMessages: vi.fn(),
    streamAskMessage: vi.fn(),
    setAskHead: vi.fn().mockResolvedValue(undefined),
  };
});

import {
  createAskConversation,
  listAskConversations,
  listAskMessages,
  setAskHead,
  streamAskMessage,
} from "../lib/api";

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

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(listAskConversations).mockResolvedValue({
    conversations: [conversation()],
  });
  vi.mocked(createAskConversation).mockResolvedValue({
    conversation: conversation(),
  });
});

describe("AskPage", () => {
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
    await screen.findByPlaceholderText(/根据记录提问/);
    await user.type(screen.getByPlaceholderText(/根据记录提问/), "新问题");
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
    const input = await screen.findByPlaceholderText(/根据记录提问/);
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
    const input = await screen.findByPlaceholderText(/根据记录提问/);
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
    const input = await screen.findByPlaceholderText(/根据记录提问/);
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
    const input = await screen.findByPlaceholderText(/根据记录提问/);
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
    const input = await screen.findByPlaceholderText(/根据记录提问/);
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

  it("keeps the user-selected scope after conversation reload", async () => {
    const user = userEvent.setup();
    vi.mocked(listAskMessages).mockResolvedValue({ messages: [] });
    vi.mocked(streamAskMessage).mockImplementation(async () => undefined);

    renderAsk();
    await screen.findByPlaceholderText(/根据记录提问/);
    await user.selectOptions(screen.getByLabelText("范围"), "all");
    await user.type(screen.getByPlaceholderText(/根据记录提问/), "总结全部");
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
    const input = await screen.findByPlaceholderText(/根据记录提问/);
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
    const input = await screen.findByPlaceholderText(/根据记录提问/);
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
