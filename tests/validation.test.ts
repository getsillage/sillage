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
  it("keeps the entry form focused on date and body", () => {
    const values = entryFormFromData(
      formOf({
        entryDate: "2026-06-23",
        title: "标题",
        body: "正文",
        mood: "5",
        moodText: "有点松了一口气",
        weather: "晴",
        location: "海边",
        people: "朋友, 家人",
        relationships: "朋友",
        tags: "旅行, 美食  摄影，旅行",
      }),
    );
    expect(values.body).toBe("正文");
    const parsed = entrySchema.safeParse(values);
    expect(parsed.success).toBe(true);
    expect(parsed.success ? parsed.data : null).toMatchObject({
      title: "",
      mood: null,
      moodText: null,
      weather: null,
      location: null,
      people: [],
      relationships: [],
      tags: [],
    });
  });

  it("treats empty optional fields as null/defaults", () => {
    const values = entryFormFromData(
      formOf({ entryDate: "2026-06-23", title: "t", body: "b", mood: "", weather: "  " }),
    );
    expect(values).toEqual({ entryDate: "2026-06-23", body: "b" });
  });

  it("rejects an entry with empty body", () => {
    const values = entryFormFromData(formOf({ entryDate: "2026-06-23", title: "", body: "" }));
    expect(entrySchema.safeParse(values).success).toBe(false);
  });

  it("rejects a malformed date", () => {
    const values = entryFormFromData(formOf({ entryDate: "2026/06/23", title: "t", body: "" }));
    expect(entrySchema.safeParse(values).success).toBe(false);
  });
});
