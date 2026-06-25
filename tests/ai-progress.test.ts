import { describe, expect, it } from "vitest";
import { ENTRY_INSIGHT_PHASES, formatDuration, phaseLabel } from "../app/lib/ai/progress";

describe("phaseLabel", () => {
  it("returns the first phase at t=0", () => {
    expect(phaseLabel(ENTRY_INSIGHT_PHASES, 0)).toBe("正在读这条记录…");
  });

  it("advances as time passes and saturates at the last phase", () => {
    expect(phaseLabel(ENTRY_INSIGHT_PHASES, 3000)).toBe("正在提炼留下的东西…");
    expect(phaseLabel(ENTRY_INSIGHT_PHASES, 99999)).toBe("比平时慢一些，再等等…");
  });

  it("is safe on an empty phase list", () => {
    expect(phaseLabel([], 1000)).toBe("");
  });
});

describe("formatDuration", () => {
  it("shows tenths of a second under a minute", () => {
    expect(formatDuration(3240)).toBe("3.2 秒");
    expect(formatDuration(0)).toBe("0.0 秒");
  });

  it("switches to minutes past 60 seconds", () => {
    expect(formatDuration(60_000)).toBe("1 分钟");
    expect(formatDuration(95_000)).toBe("1 分 35 秒");
  });
});
