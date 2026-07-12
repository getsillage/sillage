import {
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  wideShellClass,
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import { SettingsWorkspace } from "./SettingsWorkspace";

export function SettingsPage({ token }: { token: string }) {
  const { t } = useI18n();
  return (
    <main className={wideShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>{t("settings.title")}</h1>
          <p className={pageLeadClass}>{t("settings.lead")}</p>
        </header>
        <SettingsWorkspace token={token} />
      </section>
    </main>
  );
}
