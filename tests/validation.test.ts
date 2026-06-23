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
        weather: "晴",
        tags: "旅行, 美食  摄影，旅行",
      }),
    );
    expect(values.tags).toEqual(["旅行", "美食", "摄影", "旅行"]);
    expect(values.mood).toBe(5);
    expect(values.weather).toBe("晴");
    expect(entrySchema.safeParse(values).success).toBe(true);
  });

  it("treats empty mood/weather as null", () => {
    const values = entryFormFromData(
      formOf({ entryDate: "2026-06-23", title: "t", body: "b", mood: "", weather: "  " }),
    );
    expect(values.mood).toBeNull();
    expect(values.weather).toBeNull();
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
