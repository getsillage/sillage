import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LocalDateTime } from "./LocalDateTime";

describe("LocalDateTime", () => {
  it("renders a time element, or nothing for an invalid value", () => {
    const { container } = render(
      <LocalDateTime value="2026-06-27T08:30:00Z" />,
    );
    expect(container.querySelector("time")).toBeInTheDocument();

    const invalid = render(<LocalDateTime value="not-a-date" />);
    expect(invalid.container.querySelector("time")).toBeNull();
  });
});
