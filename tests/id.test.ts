import { describe, expect, it } from "vitest";
import { uuidv7 } from "../app/lib/db/id";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("uuidv7", () => {
  it("produces a well-formed v7 UUID (version + variant bits set)", () => {
    expect(uuidv7()).toMatch(UUID_RE);
  });

  it("is unique across many calls", () => {
    const ids = new Set(Array.from({ length: 1000 }, () => uuidv7()));
    expect(ids.size).toBe(1000);
  });

  it("sorts lexicographically by creation time", () => {
    const earlier = uuidv7(1000);
    const later = uuidv7(2000);
    expect(earlier < later).toBe(true);
  });

  it("encodes the timestamp in the high 48 bits", () => {
    const ts = 0x0123456789ab;
    const id = uuidv7(ts);
    const hex = id.replace(/-/g, "").slice(0, 12);
    expect(hex).toBe("0123456789ab");
  });
});
