import { describe, expect, it } from "vitest";
import { entryFormFromData, entrySchema } from "../app/lib/validation/entry";

function formOf(fields: Record<string, string>): FormData {
  const form = new FormData();
  for (const [key, value] of Object.entries(fields)) {
    form.set(key, value);
  }
  return form;
}

describe("entry form parsing + validation", () => {
  it("parses tags from comma/space separated input", () => {
    const values = entryFormFromData(
      formOf({
        entryDate: "2026-06-23",
        title: "标题",
        body: "正文",
        mood: "5",
        moodText: "有点松了一口气",
        weather: "晴",
        location: "海边",
        kind: "note",
        noteType: "daily",
        people: "朋友, 家人",
        relationships: "朋友",
        tags: "旅行, 美食  摄影，旅行",
      }),
    );
    expect(values.tags).toEqual(["旅行", "美食", "摄影", "旅行"]);
    expect(values.mood).toBe(5);
    expect(values.moodText).toBe("有点松了一口气");
    expect(values.weather).toBe("晴");
    expect(values.location).toBe("海边");
    expect(values.kind).toBe("note");
    expect(values.noteType).toBe("daily");
    expect(values.people).toEqual(["朋友", "家人"]);
    expect(values.relationships).toEqual(["朋友"]);
    expect(entrySchema.safeParse(values).success).toBe(true);
  });

  it("treats empty optional fields as null/defaults", () => {
    const values = entryFormFromData(
      formOf({ entryDate: "2026-06-23", title: "t", body: "b", mood: "", weather: "  " }),
    );
    expect(values.mood).toBeNull();
    expect(values.weather).toBeNull();
    expect(values.moodText).toBeNull();
    expect(values.location).toBeNull();
    expect(values.kind).toBe("fragment");
    expect(values.noteType).toBeNull();
  });

  it("rejects an entry with neither title nor body", () => {
    const values = entryFormFromData(formOf({ entryDate: "2026-06-23", title: "", body: "" }));
    expect(entrySchema.safeParse(values).success).toBe(false);
  });

  it("rejects a malformed date", () => {
    const values = entryFormFromData(formOf({ entryDate: "2026/06/23", title: "t", body: "" }));
    expect(entrySchema.safeParse(values).success).toBe(false);
  });
});
