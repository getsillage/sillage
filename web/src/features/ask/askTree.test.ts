import { describe, expect, it } from "vitest";
import type { AskMessage } from "../../lib/api";
import { branchLeafId, buildActivePath } from "./askTree";

function msg(
  id: string,
  role: "user" | "assistant",
  parentId: string | null,
  createdAt: string,
): AskMessage {
  return {
    id,
    conversationId: "c1",
    role,
    content: id,
    parentId,
    forkOfId: null,
    status: "complete",
    sourceRefs: [],
    model: "",
    createdAt,
    updatedAt: createdAt,
    deletedAt: null,
  };
}

describe("buildActivePath", () => {
  it("returns empty for no messages", () => {
    expect(buildActivePath([], null)).toEqual([]);
  });

  it("linearizes a simple conversation root-first", () => {
    const messages = [
      msg("u1", "user", null, "1"),
      msg("a1", "assistant", "u1", "2"),
      msg("u2", "user", "a1", "3"),
      msg("a2", "assistant", "u2", "4"),
    ];
    const path = buildActivePath(messages, "a2");
    expect(path.map((e) => e.message.id)).toEqual(["u1", "a1", "u2", "a2"]);
    // No regeneration yet: every entry is its own only variant.
    expect(path.every((e) => e.variants.length === 1)).toBe(true);
  });

  it("follows head into a regenerated branch and exposes variants", () => {
    // u1 -> a1; a1 regenerated to a1b (sibling under u1). head = a1b.
    const messages = [
      msg("u1", "user", null, "1"),
      msg("a1", "assistant", "u1", "2"),
      msg("a1b", "assistant", "u1", "3"),
    ];
    const path = buildActivePath(messages, "a1b");
    expect(path.map((e) => e.message.id)).toEqual(["u1", "a1b"]);
    const answer = path[1];
    expect(answer.variants.map((v) => v.id)).toEqual(["a1", "a1b"]);
    expect(answer.index).toBe(1);
  });

  it("shows the other variant's branch when head points at it", () => {
    // a1 has a follow-up u2->a2; a1b is the alternative with no follow-up.
    const messages = [
      msg("u1", "user", null, "1"),
      msg("a1", "assistant", "u1", "2"),
      msg("u2", "user", "a1", "3"),
      msg("a2", "assistant", "u2", "4"),
      msg("a1b", "assistant", "u1", "5"),
    ];
    expect(buildActivePath(messages, "a2").map((e) => e.message.id)).toEqual([
      "u1",
      "a1",
      "u2",
      "a2",
    ]);
    expect(buildActivePath(messages, "a1b").map((e) => e.message.id)).toEqual([
      "u1",
      "a1b",
    ]);
  });

  it("falls back to the newest message when head is missing", () => {
    const messages = [
      msg("u1", "user", null, "1"),
      msg("a1", "assistant", "u1", "2"),
    ];
    expect(buildActivePath(messages, null).map((e) => e.message.id)).toEqual([
      "u1",
      "a1",
    ]);
  });
});

describe("branchLeafId", () => {
  it("descends to the newest leaf of a branch", () => {
    const messages = [
      msg("u1", "user", null, "1"),
      msg("a1", "assistant", "u1", "2"),
      msg("u2", "user", "a1", "3"),
      msg("a2", "assistant", "u2", "4"),
    ];
    expect(branchLeafId(messages, "a1")).toBe("a2");
    expect(branchLeafId(messages, "a2")).toBe("a2");
  });

  it("returns the node itself when it has no children", () => {
    const messages = [msg("a1b", "assistant", "u1", "5")];
    expect(branchLeafId(messages, "a1b")).toBe("a1b");
  });
});
