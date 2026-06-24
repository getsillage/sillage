import { describe, expect, it } from "vitest";
import { parseMetadata, toAttachmentDto, toEntryDto } from "../app/lib/api/serialize";
import type { EntryWithTags } from "../app/lib/db/entries";
import type { Attachment } from "../app/lib/db/schema";

function makeEntry(overrides: Partial<EntryWithTags> = {}): EntryWithTags {
  return {
    id: "01890000-0000-7000-8000-000000000000",
    entryDate: "2026-06-24",
    title: "标题",
    body: "正文",
    kind: "note",
    noteType: "daily",
    mood: 4,
    moodText: "轻松但想念",
    weather: "晴",
    location: "海边",
    people: JSON.stringify(["朋友"]),
    relationships: JSON.stringify(["朋友"]),
    isPinned: false,
    utcOffsetMinutes: 480,
    metadata: null,
    version: 3,
    createdAt: new Date("2026-06-24T01:00:00.000Z"),
    updatedAt: new Date("2026-06-24T02:00:00.000Z"),
    deletedAt: null,
    summary: "一句摘要",
    sentiment: "积极",
    tags: ["旅行", "生活"],
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

describe("parseMetadata", () => {
  it("returns null for empty/null input", () => {
    expect(parseMetadata(null)).toBeNull();
    expect(parseMetadata("")).toBeNull();
  });

  it("parses a JSON object", () => {
    expect(parseMetadata('{"client":"ios","draft":true}')).toEqual({ client: "ios", draft: true });
  });

  it("rejects non-object JSON (arrays, scalars) and malformed input", () => {
    expect(parseMetadata("[1,2,3]")).toBeNull();
    expect(parseMetadata('"just a string"')).toBeNull();
    expect(parseMetadata("not json")).toBeNull();
  });
});

describe("toEntryDto", () => {
  it("emits ISO timestamps, parsed metadata, and a nested ai object", () => {
    const dto = toEntryDto(makeEntry({ metadata: '{"client":"ios"}' }));
    expect(dto.createdAt).toBe("2026-06-24T01:00:00.000Z");
    expect(dto.updatedAt).toBe("2026-06-24T02:00:00.000Z");
    expect(dto.deletedAt).toBeNull();
    expect(dto.metadata).toEqual({ client: "ios" });
    expect(dto.kind).toBe("note");
    expect(dto.noteType).toBe("daily");
    expect(dto.moodText).toBe("轻松但想念");
    expect(dto.location).toBe("海边");
    expect(dto.people).toEqual(["朋友"]);
    expect(dto.relationships).toEqual(["朋友"]);
    expect(dto.version).toBe(3);
    expect(dto.ai).toEqual({ summary: "一句摘要", sentiment: "积极" });
    expect(dto.tags).toEqual(["旅行", "生活"]);
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
