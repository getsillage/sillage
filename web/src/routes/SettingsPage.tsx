import { SettingsWorkspace } from "../components/SettingsWorkspace";
import {
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  wideShellClass,
} from "../components/ui";

export function SettingsPage({ token }: { token: string }) {
  return (
    <main className={wideShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>设置</h1>
          <p className={pageLeadClass}>管理 AI 档案和界面外观。</p>
        </header>
        <SettingsWorkspace token={token} />
      </section>
    </main>
  );
}
