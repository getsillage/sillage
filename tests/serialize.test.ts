import { describe, expect, it } from "vitest";
import { toAttachmentDto, toEntryDto } from "../app/lib/api/serialize";
import type { EntryWithAi } from "../app/lib/db/entries";
import type { Attachment } from "../app/lib/db/schema";

function makeEntry(overrides: Partial<EntryWithAi> = {}): EntryWithAi {
  return {
    id: "01890000-0000-7000-8000-000000000000",
    entryDate: "2026-06-24",
    body: "正文",
    version: 3,
    createdAt: new Date("2026-06-24T01:00:00.000Z"),
    updatedAt: new Date("2026-06-24T02:00:00.000Z"),
    deletedAt: null,
    summary: "一句摘要",
    sentiment: "积极",
    aiModel: null,
    aiDurationMs: null,
    aiGeneratedAt: null,
    aiGenerationCount: 0,
    ...overrides,
  };
}

function makeAttachment(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: "att-1",
    entryId: "01890000-0000-7000-8000-000000000000",
    r2Key: "attachments/att-1",
    filename: "photo.png",
    contentType: "image/png",
    size: 1234,
    sha256: "deadbeef",
    width: 800,
    height: 600,
    status: "stored",
    createdAt: new Date("2026-06-24T01:00:00.000Z"),
    updatedAt: new Date("2026-06-24T02:00:00.000Z"),
    deletedAt: null,
    ...overrides,
  };
}

describe("toEntryDto", () => {
  it("emits the clean entry sync shape", () => {
    const dto = toEntryDto(makeEntry());
    expect(dto).toEqual({
      id: "01890000-0000-7000-8000-000000000000",
      entryDate: "2026-06-24",
      body: "正文",
      version: 3,
      ai: { summary: "一句摘要", sentiment: "积极" },
      createdAt: "2026-06-24T01:00:00.000Z",
      updatedAt: "2026-06-24T02:00:00.000Z",
      deletedAt: null,
    });
  });

  it("serializes a soft-delete tombstone as ISO", () => {
    const dto = toEntryDto(makeEntry({ deletedAt: new Date("2026-06-25T00:00:00.000Z") }));
    expect(dto.deletedAt).toBe("2026-06-25T00:00:00.000Z");
  });
});

describe("toAttachmentDto", () => {
  it("exposes a fetch url and hides the internal R2 key", () => {
    const dto = toAttachmentDto(makeAttachment());
    expect(dto.url).toBe("/attachments/att-1");
    expect(dto).not.toHaveProperty("r2Key");
    expect(dto.sha256).toBe("deadbeef");
    expect(dto.width).toBe(800);
    expect(dto.updatedAt).toBe("2026-06-24T02:00:00.000Z");
  });
});
