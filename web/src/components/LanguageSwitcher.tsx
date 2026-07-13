import { Languages } from "lucide-react";
import { useI18n } from "../i18n/I18nProvider";
import type { Locale } from "../i18n/messages";
import { segmentedControlClass, segmentedItemClass } from "./ui";

const options: {
  locale: Locale;
  labelKey: "language.chinese" | "language.english";
  shortLabelKey: "language.chineseShort" | "language.englishShort";
}[] = [
  {
    locale: "zh-CN",
    labelKey: "language.chinese",
    shortLabelKey: "language.chineseShort",
  },
  {
    locale: "en",
    labelKey: "language.english",
    shortLabelKey: "language.englishShort",
  },
];

export function LanguageSwitcher({
  compact = false,
  disabled = false,
}: {
  compact?: boolean;
  disabled?: boolean;
}) {
  const { locale, setLocale, t } = useI18n();

  return (
    <fieldset
      className={`${segmentedControlClass} ${compact ? "min-h-9" : ""}`}
      aria-label={t("language.change")}
      disabled={disabled}
    >
      <legend className="sr-only">{t("language.label")}</legend>
      {compact ? null : (
        <Languages
          className="ml-2 h-4 w-4 text-gray-500 dark:text-gray-400"
          aria-hidden="true"
        />
      )}
      {options.map((option) => (
        <button
          key={option.locale}
          type="button"
          className={`${segmentedItemClass(locale === option.locale)} ${compact ? "h-9 px-2 text-xs" : ""}`}
          aria-pressed={locale === option.locale}
          onClick={() => setLocale(option.locale)}
        >
          <span aria-hidden={compact ? "true" : undefined}>
            {t(compact ? option.shortLabelKey : option.labelKey)}
          </span>
          {compact ? (
            <span className="sr-only">{t(option.labelKey)}</span>
          ) : null}
        </button>
      ))}
    </fieldset>
  );
}
