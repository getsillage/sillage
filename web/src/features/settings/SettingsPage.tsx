import {
  helperTextClass,
  pageLeadClass,
  pageSectionClass,
  pageTitleClass,
  panelClass,
  wideShellClass,
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { BootstrapInfo } from "../../lib/api";
import {
  shortRevision,
  webBuildInfo,
  webServerBuildsMatch,
} from "../../lib/buildInfo";
import { SettingsWorkspace } from "./SettingsWorkspace";

export function SettingsPage({
  token,
  buildInfo,
}: {
  token: string;
  buildInfo: BootstrapInfo | null;
}) {
  const { t } = useI18n();
  const buildsMatch = webServerBuildsMatch(
    buildInfo?.serverVersion,
    buildInfo?.serverRevision,
  );
  return (
    <main className={wideShellClass}>
      <section className={pageSectionClass}>
        <header>
          <h1 className={pageTitleClass}>{t("settings.title")}</h1>
          <p className={pageLeadClass}>{t("settings.lead")}</p>
        </header>
        <SettingsWorkspace token={token} />
        <section
          className={`${panelClass} mt-5 p-4 sm:p-5`}
          aria-labelledby="settings-build-info"
        >
          <h2
            id="settings-build-info"
            className="font-medium text-gray-900 text-sm dark:text-gray-50"
          >
            {t("settings.buildInfoTitle")}
          </h2>
          <p className={helperTextClass}>
            {t("settings.buildInfoDescription")}
          </p>
          <dl className="mt-4 grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-gray-500 dark:text-gray-400">
                {t("settings.serverBuild")}
              </dt>
              <dd className="mt-1 font-mono text-gray-900 dark:text-gray-100">
                {buildInfo?.serverVersion || "dev"} (
                {shortRevision(buildInfo?.serverRevision)})
              </dd>
            </div>
            <div>
              <dt className="text-gray-500 dark:text-gray-400">
                {t("settings.webBuild")}
              </dt>
              <dd className="mt-1 font-mono text-gray-900 dark:text-gray-100">
                {webBuildInfo.version} ({shortRevision(webBuildInfo.revision)})
              </dd>
            </div>
            <div>
              <dt className="text-gray-500 dark:text-gray-400">
                {t("settings.apiVersion")}
              </dt>
              <dd className="mt-1 font-mono text-gray-900 dark:text-gray-100">
                {buildInfo?.apiVersion || "v1"}
              </dd>
            </div>
            <div>
              <dt className="text-gray-500 dark:text-gray-400">
                {t("settings.minimumAndroidVersion")}
              </dt>
              <dd className="mt-1 font-mono text-gray-900 dark:text-gray-100">
                {buildInfo?.minimumAndroidVersionCode ?? 9}
              </dd>
            </div>
          </dl>
          {!buildsMatch ? (
            <p
              role="alert"
              className="mt-4 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-amber-900 text-sm dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"
            >
              {t("settings.buildMismatch")}
            </p>
          ) : null}
        </section>
      </section>
    </main>
  );
}
