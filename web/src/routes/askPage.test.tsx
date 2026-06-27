import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AskConversation, AskMessage } from "../lib/api";
import { AskProvider } from "../state/AskContext";
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

function renderAsk() {
  return render(
    <MemoryRouter initialEntries={["/ask?conversation=c1"]}>
      <MemosProvider token="t">
        <AskProvider token="t">
          <AskPage />
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

  it("does not render the live user bubble twice after reload", () => {
    const liveUser = message("u9", "user", null, "新问题", "09");
    const entries = [{ message: liveUser, variants: [liveUser], index: 0 }];

    expect(shouldShowLiveUser(entries, liveUser)).toBe(false);
    expect(shouldShowLiveUser([], liveUser)).toBe(true);
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
});
