import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { I18nProvider, useI18n } from "../i18n/I18nProvider";
import { localizeServerMessage } from "../i18n/messages";
import { LanguageSwitcher } from "./LanguageSwitcher";

function AccountLabel() {
  const { t } = useI18n();
  return <p>{t("auth.account")}</p>;
}

describe("LanguageSwitcher", () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.lang = "zh-CN";
  });

  it("defaults to Chinese and persists an English selection", async () => {
    const user = userEvent.setup();
    render(
      <I18nProvider>
        <LanguageSwitcher compact />
        <AccountLabel />
      </I18nProvider>,
    );

    expect(screen.getByText("账号")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("zh-CN");
    expect(window.localStorage.getItem("sillage-language")).toBe("zh-CN");

    await user.click(screen.getByRole("button", { name: "English" }));

    expect(screen.getByText("Username")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
    expect(window.localStorage.getItem("sillage-language")).toBe("en");
    expect(
      localizeServerMessage("账号或密码不正确", "invalid_credentials"),
    ).toBe("Incorrect account or password");
  });

  it("restores the stored language", () => {
    window.localStorage.setItem("sillage-language", "en");
    render(
      <I18nProvider>
        <AccountLabel />
      </I18nProvider>,
    );

    expect(screen.getByText("Username")).toBeInTheDocument();
    expect(document.documentElement.lang).toBe("en");
  });
});
