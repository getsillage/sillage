import { BrainCircuit, LoaderCircle, Palette, Plus, Save } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { LanguageSwitcher } from "../../components/LanguageSwitcher";
import { ThemeToggle } from "../../components/ThemeToggle";
import { Toast, type ToastMessage, useToast } from "../../components/Toast";
import { UnsavedNavigationGuard } from "../../components/UnsavedNavigationGuard";
import {
  dangerButtonClass,
  emptyStateClass,
  helperTextClass,
  inputClass,
  labelClass,
  panelClass,
  primaryButtonClass,
  secondaryButtonClass,
  segmentedControlClass,
  segmentedItemClass,
  selectClass,
  skeletonClass,
  subtleButtonClass,
} from "../../components/ui";
import { useI18n } from "../../i18n/I18nProvider";
import type { TranslationKey } from "../../i18n/messages";
import {
  type AIProfile,
  type AIProfileInput,
  getAISettings,
  listAIModels,
  patchAISettings,
  setAIAutoSummary,
  testAIConnection,
} from "../../lib/api";

const PROVIDER_OPTIONS = [
  {
    value: "anthropic",
    labelKey: "settings.anthropicCompatibleProtocol",
  },
  { value: "openai", labelKey: "settings.openAICompatibleProtocol" },
] as const satisfies readonly {
  value: string;
  labelKey: TranslationKey;
}[];

// Local editing copy: apiKeyInput holds a freshly typed key (empty keeps the
// stored key untouched). temperatureText/maxTokensText keep the raw input as a
// string so clearing or typing "0." never snaps back to a coerced number; they
// are parsed only at save time.
type EditableProfile = AIProfile & {
  apiKeyInput: string;
  temperatureText: string;
  maxTokensText: string;
};

function toEditable(profile: AIProfile): EditableProfile {
  return {
    ...profile,
    apiKeyInput: "",
    temperatureText: String(profile.temperature),
    maxTokensText: String(profile.maxTokens),
  };
}

function blankProfile(): EditableProfile {
  return {
    id: "",
    name: "",
    provider: "anthropic",
    baseUrl: "",
    model: "",
    temperature: 0.3,
    maxTokens: 1000,
    enabled: true,
    active: false,
    hasApiKey: false,
    keyUnavailable: false,
    autoSummary: false,
    createdAt: "",
    updatedAt: "",
    apiKeyInput: "",
    temperatureText: "0.3",
    maxTokensText: "1000",
  };
}

// An empty or invalid field means "let the server default apply"; an explicit 0
// temperature is preserved.
function parseTemperature(text: string): number | undefined {
  const trimmed = text.trim();
  if (trimmed === "") {
    return undefined;
  }
  const value = Number.parseFloat(trimmed);
  return Number.isFinite(value) ? value : undefined;
}

function parseMaxTokens(text: string): number | undefined {
  const trimmed = text.trim();
  if (trimmed === "") {
    return undefined;
  }
  const value = Number.parseInt(trimmed, 10);
  return Number.isFinite(value) && value > 0 ? value : undefined;
}

function normalizeProfilesForSave(
  profiles: EditableProfile[],
): EditableProfile[] {
  if (profiles.length === 0) {
    return profiles;
  }
  const activeIndex = profiles.findIndex((profile) => profile.active);
  const defaultIndex = activeIndex >= 0 ? activeIndex : 0;
  return profiles.map((profile, index) => ({
    ...profile,
    enabled: true,
    active: index === defaultIndex,
  }));
}

function profilesFingerprint(profiles: EditableProfile[]): string {
  return JSON.stringify(profiles);
}

type TestState = { status: "ok" | "error"; message: string };
type ModelState = {
  loading: boolean;
  models: string[];
  status?: "ok" | "error";
  message?: string;
};
type SettingsTab = "ai" | "appearance";
const ACTION_TIMEOUT_MS = 65_000;
const SETTINGS_TABS: {
  value: SettingsTab;
  labelKey: TranslationKey;
  icon: typeof BrainCircuit;
}[] = [
  { value: "ai", labelKey: "settings.aiTab", icon: BrainCircuit },
  {
    value: "appearance",
    labelKey: "settings.appearanceTab",
    icon: Palette,
  },
];

function profileKey(profile: EditableProfile, index: number): string {
  return profile.id || `new-${index}`;
}

function providerLabel(
  provider: string,
  translate: (key: TranslationKey) => string,
): string {
  const option = PROVIDER_OPTIONS.find((option) => option.value === provider);
  return option ? translate(option.labelKey) : provider;
}

async function withTimeout<T>(
  run: (signal: AbortSignal) => Promise<T>,
  controller = new AbortController(),
): Promise<T> {
  const timeout = window.setTimeout(
    () => controller.abort(),
    ACTION_TIMEOUT_MS,
  );
  try {
    return await run(controller.signal);
  } finally {
    window.clearTimeout(timeout);
  }
}

function actionErrorMessage(
  cause: unknown,
  fallback: string,
  timeout: string,
): string {
  if (cause instanceof DOMException && cause.name === "AbortError") {
    return timeout;
  }
  return cause instanceof Error ? cause.message : fallback;
}

export function SettingsWorkspace({ token }: { token: string }) {
  const { locale, t } = useI18n();
  const toast = useToast();
  const [activeTab, setActiveTab] = useState<SettingsTab>("ai");
  const [profiles, setProfiles] = useState<EditableProfile[]>([]);
  const [savedProfilesFingerprint, setSavedProfilesFingerprint] = useState<
    string | null
  >(null);
  const [selectedProfileKey, setSelectedProfileKey] = useState<string | null>(
    null,
  );
  const [autoSummary, setAutoSummary] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const loadRequestIdRef = useRef(0);
  const [saving, setSaving] = useState(false);
  const [autoSummarySaving, setAutoSummarySaving] = useState(false);
  const autoSummaryMutationRef = useRef(false);
  const autoSummaryRequestIdRef = useRef(0);
  const autoSummaryAbortRef = useRef<AbortController | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [confirmDeleteKey, setConfirmDeleteKey] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [invalidProfileKey, setInvalidProfileKey] = useState<string | null>(
    null,
  );
  const [autoSummaryToast, setAutoSummaryToast] = useState<ToastMessage | null>(
    null,
  );
  const [testingId, setTestingId] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, TestState>>({});
  const [modelResults, setModelResults] = useState<Record<string, ModelState>>(
    {},
  );

  useEffect(() => {
    void locale;
    setLoadError((current) => (current ? t("settings.loadFailed") : current));
    setError((current) => (current ? t("errors.requestFailed") : current));
    setNotice("");
    setAutoSummaryToast(null);
    setTestResults({});
    setModelResults({});
  }, [locale, t]);

  function reportError(message: string) {
    setError(message);
    toast.showToast({ kind: "error", message });
  }

  function reportNotice(message: string, kind: "success" | "info") {
    setNotice(message);
    toast.showToast({ kind, message });
  }

  const dirty =
    savedProfilesFingerprint !== null &&
    profilesFingerprint(profiles) !== savedProfilesFingerprint;
  const mutationBusy = saving || autoSummarySaving || deletingId !== null;

  const loadSettings = useCallback(async () => {
    autoSummaryAbortRef.current?.abort();
    autoSummaryAbortRef.current = null;
    autoSummaryRequestIdRef.current += 1;
    autoSummaryMutationRef.current = false;
    setAutoSummarySaving(false);
    const requestId = loadRequestIdRef.current + 1;
    loadRequestIdRef.current = requestId;
    setLoading(true);
    setLoadError("");
    try {
      const res = await getAISettings(token);
      if (loadRequestIdRef.current !== requestId) {
        return;
      }
      const loadedProfiles = res.profiles.map(toEditable);
      const loadedAutoSummary =
        res.autoSummary ?? res.profiles.some((profile) => profile.autoSummary);
      setProfiles(loadedProfiles);
      setAutoSummary(loadedAutoSummary);
      setSavedProfilesFingerprint(profilesFingerprint(loadedProfiles));
      setLoading(false);
    } catch (cause) {
      if (loadRequestIdRef.current !== requestId) {
        return;
      }
      const message =
        cause instanceof Error ? cause.message : t("settings.loadFailed");
      setLoadError(message);
      setLoading(false);
    }
  }, [token, t]);

  useEffect(() => {
    void loadSettings();
    return () => {
      loadRequestIdRef.current += 1;
      autoSummaryAbortRef.current?.abort();
      autoSummaryAbortRef.current = null;
      autoSummaryRequestIdRef.current += 1;
      autoSummaryMutationRef.current = false;
    };
  }, [loadSettings]);

  useEffect(() => {
    if (!dirty) {
      return;
    }
    function warnBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
      event.returnValue = "";
    }
    window.addEventListener("beforeunload", warnBeforeUnload);
    return () => window.removeEventListener("beforeunload", warnBeforeUnload);
  }, [dirty]);

  function updateProfile(index: number, patch: Partial<EditableProfile>) {
    setConfirmDeleteKey(null);
    setNotice("");
    setError("");
    if (patch.name !== undefined && patch.name.trim() !== "") {
      const key = profileKey(profiles[index], index);
      setInvalidProfileKey((current) => (current === key ? null : current));
    }
    setProfiles((current) =>
      current.map((profile, i) =>
        i === index ? { ...profile, ...patch } : profile,
      ),
    );
  }

  function setDefaultProfile(index: number) {
    if (mutationBusy) {
      return;
    }
    if (dirty) {
      reportNotice(t("settings.saveDefaultFirst"), "info");
      setError("");
      return;
    }
    const nextProfiles = profiles.map((profile, i) => ({
      ...profile,
      enabled: true,
      active: i === index,
    }));
    setConfirmDeleteKey(null);
    setProfiles(nextProfiles);
    setSaving(true);
    setNotice("");
    setError("");
    saveProfiles(nextProfiles, t("settings.defaultSaved"))
      .catch((err) => {
        setProfiles(profiles);
        reportError(
          err instanceof Error ? err.message : t("settings.defaultFailed"),
        );
      })
      .finally(() => setSaving(false));
  }

  async function saveProfiles(
    nextProfiles: EditableProfile[],
    successNotice: string,
  ) {
    const normalizedProfiles = normalizeProfilesForSave(nextProfiles);
    const payload: AIProfileInput[] = normalizedProfiles.map((profile) => ({
      id: profile.id || undefined,
      name: profile.name,
      provider: profile.provider,
      baseUrl: profile.baseUrl,
      model: profile.model,
      temperature: parseTemperature(profile.temperatureText),
      maxTokens: parseMaxTokens(profile.maxTokensText),
      enabled: true,
      active: profile.active,
      apiKey: profile.apiKeyInput.trim() ? profile.apiKeyInput : undefined,
    }));
    const res = await patchAISettings(token, {
      profiles: payload,
    });
    const savedProfiles = res.profiles.map(toEditable);
    setProfiles(savedProfiles);
    setSavedProfilesFingerprint(profilesFingerprint(savedProfiles));
    setConfirmDeleteKey(null);
    reportNotice(successNotice, "success");
  }

  async function toggleAutoSummary() {
    if (mutationBusy || autoSummaryMutationRef.current) {
      return;
    }
    const previousValue = autoSummary;
    const nextValue = !previousValue;
    const requestId = autoSummaryRequestIdRef.current + 1;
    const controller = new AbortController();
    autoSummaryRequestIdRef.current = requestId;
    autoSummaryAbortRef.current = controller;
    autoSummaryMutationRef.current = true;
    setAutoSummary(nextValue);
    setAutoSummarySaving(true);
    setAutoSummaryToast(null);
    setNotice("");
    setError("");
    try {
      const res = await withTimeout(
        (signal) => setAIAutoSummary(token, nextValue, signal),
        controller,
      );
      if (autoSummaryRequestIdRef.current !== requestId) {
        return;
      }
      setAutoSummary(res.autoSummary);
      const nextToast: ToastMessage = {
        kind: "success",
        message: t(
          res.autoSummary
            ? "settings.autoSummaryOn"
            : "settings.autoSummaryOff",
        ),
      };
      if (toast.available) {
        toast.showToast(nextToast);
      } else {
        setAutoSummaryToast(nextToast);
      }
    } catch (cause) {
      if (autoSummaryRequestIdRef.current !== requestId) {
        return;
      }
      setAutoSummary(previousValue);
      const nextToast: ToastMessage = {
        kind: "error",
        message: actionErrorMessage(
          cause,
          t("settings.autoSummarySaveFailed"),
          t("settings.timeout"),
        ),
      };
      if (toast.available) {
        toast.showToast(nextToast);
      } else {
        setAutoSummaryToast(nextToast);
      }
    } finally {
      if (autoSummaryRequestIdRef.current === requestId) {
        autoSummaryAbortRef.current = null;
        autoSummaryMutationRef.current = false;
        setAutoSummarySaving(false);
      }
    }
  }

  async function removeProfile(index: number) {
    const profile = profiles[index];
    const key = profile ? profileKey(profile, index) : null;
    if (!profile) {
      return;
    }
    if (mutationBusy) {
      return;
    }
    if (profile.id && dirty) {
      setConfirmDeleteKey(null);
      reportNotice(t("settings.saveBeforeDelete"), "info");
      setError("");
      return;
    }
    if (confirmDeleteKey !== key) {
      setConfirmDeleteKey(key);
      setNotice("");
      setError("");
      return;
    }
    if (!profile.id) {
      setProfiles((current) =>
        normalizeProfilesForSave(current.filter((_, i) => i !== index)),
      );
      setConfirmDeleteKey(null);
      if (selectedProfileKey === key) {
        setSelectedProfileKey(null);
      }
      return;
    }
    setDeletingId(profile.id);
    setNotice("");
    setError("");
    try {
      await saveProfiles(
        profiles.filter((_, i) => i !== index),
        t("settings.profileDeleted"),
      );
    } catch (err) {
      reportError(
        err instanceof Error ? err.message : t("common.deleteFailed"),
      );
    } finally {
      setDeletingId((current) => (current === profile.id ? null : current));
    }
    setConfirmDeleteKey(null);
    if (selectedProfileKey === key) {
      setSelectedProfileKey(null);
    }
  }

  async function save() {
    if (mutationBusy || !dirty) {
      return;
    }
    const invalidProfileIndex = profiles.findIndex(
      (profile) => profile.name.trim() === "",
    );
    if (invalidProfileIndex >= 0) {
      const invalidKey = profileKey(
        profiles[invalidProfileIndex],
        invalidProfileIndex,
      );
      setActiveTab("ai");
      setSelectedProfileKey(invalidKey);
      setInvalidProfileKey(invalidKey);
      setNotice("");
      setError("");
      toast.showToast({
        kind: "error",
        message: t("settings.profileNameRequired"),
      });
      return;
    }
    setInvalidProfileKey(null);
    setSaving(true);
    setNotice("");
    setError("");
    try {
      await saveProfiles(profiles, t("settings.profileSaved"));
      setSelectedProfileKey(null);
    } catch (err) {
      reportError(
        err instanceof Error ? err.message : t("composer.saveFailed"),
      );
    } finally {
      setSaving(false);
    }
  }

  async function testConnection(profile: EditableProfile, index: number) {
    const key = profileKey(profile, index);
    setTestingId(key);
    try {
      const res = await withTimeout((signal) =>
        testAIConnection(
          token,
          {
            id: profile.id || undefined,
            provider: profile.provider,
            baseUrl: profile.baseUrl,
            model: profile.model,
            temperature: parseTemperature(profile.temperatureText),
            maxTokens: parseMaxTokens(profile.maxTokensText),
            apiKey: profile.apiKeyInput.trim() || undefined,
          },
          signal,
        ),
      );
      setTestResults((current) => ({
        ...current,
        [key]: {
          status: "ok",
          message: t("settings.connectionSuccess", { model: res.model }),
        },
      }));
      toast.showToast({
        kind: "success",
        message: t("settings.connectionSuccess", { model: res.model }),
      });
    } catch (cause) {
      const message = actionErrorMessage(
        cause,
        t("settings.connectionFailed"),
        t("settings.timeout"),
      );
      setTestResults((current) => ({
        ...current,
        [key]: {
          status: "error",
          message,
        },
      }));
      toast.showToast({ kind: "error", message });
    } finally {
      setTestingId((current) => (current === key ? null : current));
    }
  }

  async function fetchModels(profile: EditableProfile, index: number) {
    const key = profileKey(profile, index);
    setModelResults((current) => ({
      ...current,
      [key]: { ...(current[key] ?? { models: [] }), loading: true },
    }));
    try {
      const res = await withTimeout((signal) =>
        listAIModels(
          token,
          {
            id: profile.id || undefined,
            provider: profile.provider,
            baseUrl: profile.baseUrl,
            apiKey: profile.apiKeyInput.trim() || undefined,
          },
          signal,
        ),
      );
      setModelResults((current) => ({
        ...current,
        [key]: {
          loading: false,
          models: res.models,
          status: "ok",
          message: t(
            res.models.length > 0
              ? "settings.modelsLoaded"
              : "settings.noModels",
          ),
        },
      }));
      toast.showToast({
        kind: "success",
        message: t(
          res.models.length > 0 ? "settings.modelsLoaded" : "settings.noModels",
        ),
      });
    } catch (cause) {
      const message = actionErrorMessage(
        cause,
        t("settings.modelsFailed"),
        t("settings.timeout"),
      );
      setModelResults((current) => ({
        ...current,
        [key]: {
          ...(current[key] ?? { models: [] }),
          loading: false,
          status: "error",
          message,
        },
      }));
      toast.showToast({ kind: "error", message });
    }
  }

  const selectedProfileIndex = selectedProfileKey
    ? profiles.findIndex(
        (profile, index) => profileKey(profile, index) === selectedProfileKey,
      )
    : -1;
  const selectedProfile =
    selectedProfileIndex >= 0 ? profiles[selectedProfileIndex] : null;

  if (loading) {
    return (
      <div className="space-y-4" role="status">
        <span className="sr-only">{t("settings.loading")}</span>
        <div className={`${skeletonClass} h-10 w-40`} />
        <div className={`${skeletonClass} h-24 w-full`} />
        <div className={`${skeletonClass} h-40 w-full`} />
      </div>
    );
  }

  if (loadError) {
    return (
      <section className={`${panelClass} p-4 sm:p-5`}>
        <h2 className="font-medium text-gray-900 text-sm dark:text-gray-50">
          {t("settings.unavailableTitle")}
        </h2>
        <p role="alert" className="mt-2 text-red-600 text-sm dark:text-red-400">
          {loadError}
        </p>
        <button
          type="button"
          className={`${secondaryButtonClass} mt-4`}
          onClick={() => void loadSettings()}
        >
          {t("common.reload")}
        </button>
      </section>
    );
  }

  return (
    <div className="space-y-5">
      {autoSummaryToast && !toast.available ? (
        <Toast
          toast={autoSummaryToast}
          onClose={() => setAutoSummaryToast(null)}
        />
      ) : null}
      <UnsavedNavigationGuard
        when={dirty}
        title={t("settings.unsavedTitle")}
        description={t("settings.unsavedDescription")}
      />
      <fieldset className={segmentedControlClass}>
        <legend className="sr-only">{t("settings.category")}</legend>
        {SETTINGS_TABS.map((tab) => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.value}
              type="button"
              onClick={() => setActiveTab(tab.value)}
              className={segmentedItemClass(activeTab === tab.value)}
              aria-current={activeTab === tab.value ? "page" : undefined}
            >
              <Icon className="h-4 w-4" aria-hidden="true" />
              {t(tab.labelKey)}
            </button>
          );
        })}
      </fieldset>

      {activeTab === "appearance" ? (
        <section className={`${panelClass} p-4 sm:p-5`}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="font-medium text-gray-900 text-sm dark:text-gray-50">
                {t("theme.sectionTitle")}
              </h2>
              <p className={helperTextClass}>{t("theme.sectionDescription")}</p>
            </div>
            <ThemeToggle />
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-gray-200/70 border-t pt-4 dark:border-gray-800">
            <div>
              <h2 className="font-medium text-gray-900 text-sm dark:text-gray-50">
                {t("settings.languageTitle")}
              </h2>
              <p className={helperTextClass}>
                {t("settings.languageDescription")}
              </p>
            </div>
            <LanguageSwitcher />
          </div>
        </section>
      ) : null}

      {activeTab === "ai" ? (
        <fieldset
          disabled={mutationBusy}
          className="m-0 min-w-0 space-y-5 border-0 p-0"
        >
          <legend className="sr-only">{t("settings.aiSettings")}</legend>
          <div className="flex items-center justify-between gap-3">
            <p className={helperTextClass}>{t("settings.secretDescription")}</p>
            <button
              type="button"
              onClick={() => {
                setNotice("");
                setError("");
                setSelectedProfileKey(`new-${profiles.length}`);
                setProfiles((current) => [
                  ...current,
                  {
                    ...blankProfile(),
                    active: current.length === 0,
                  },
                ]);
              }}
              className={secondaryButtonClass}
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t("settings.addProfile")}
            </button>
          </div>

          <section className={`${panelClass} p-4 sm:p-5`}>
            <div className="flex items-center justify-between gap-4">
              <div>
                <h2 className="font-medium text-gray-800 text-sm dark:text-gray-100">
                  {t("settings.autoSummaryTitle")}
                </h2>
                <p className={helperTextClass}>
                  {t("settings.autoSummaryDescription")}
                </p>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={autoSummary}
                aria-busy={autoSummarySaving}
                aria-label={t("settings.autoSummaryTitle")}
                onClick={() => void toggleAutoSummary()}
                className={`relative h-7 w-12 flex-none rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/40 ${
                  autoSummary
                    ? "bg-gray-900 dark:bg-gray-100"
                    : "bg-gray-300 dark:bg-gray-700"
                }`}
              >
                <span
                  className={`absolute top-1 left-0 h-5 w-5 rounded-full bg-white shadow-sm transition-transform dark:bg-gray-950 ${
                    autoSummary ? "translate-x-6" : "translate-x-1"
                  }`}
                />
              </button>
            </div>
          </section>

          {profiles.length === 0 ? (
            <div className={emptyStateClass}>{t("settings.noProfiles")}</div>
          ) : (
            <div className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {profiles.map((profile, index) => {
                  const key = profileKey(profile, index);
                  const isSelected = key === selectedProfileKey;
                  return (
                    <article
                      key={key}
                      className={`${panelClass} min-h-32 w-full p-4 text-left transition hover:border-gray-300 hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400/35 dark:hover:border-gray-700 dark:hover:bg-gray-900 ${
                        isSelected
                          ? "border-gray-400 bg-gray-50 dark:border-gray-500 dark:bg-gray-900"
                          : ""
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <h3 className="truncate font-medium text-gray-900 text-sm dark:text-gray-50">
                            {profile.name || t("settings.unnamedProfile")}
                          </h3>
                          <p className="mt-1 truncate text-gray-500 text-xs dark:text-gray-400">
                            {providerLabel(profile.provider, t)}
                          </p>
                        </div>
                        {profile.active ? (
                          <span className="shrink-0 rounded-full bg-gray-900 px-2 py-0.5 text-[11px] font-medium text-white dark:bg-gray-100 dark:text-gray-900">
                            {t("settings.defaultBadge")}
                          </span>
                        ) : null}
                      </div>
                      <p className="mt-4 truncate text-gray-700 text-sm dark:text-gray-200">
                        {profile.model || t("settings.modelUnset")}
                      </p>
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        <span className="rounded-full bg-gray-100 px-2 py-0.5 text-[11px] text-gray-500 dark:bg-gray-800 dark:text-gray-400">
                          {profile.hasApiKey || profile.apiKeyInput
                            ? t("settings.hasKey")
                            : t("settings.noKey")}
                        </span>
                        {profile.keyUnavailable ? (
                          <span className="rounded-full bg-red-50 px-2 py-0.5 text-[11px] text-red-600 dark:bg-red-950/35 dark:text-red-300">
                            {t("settings.keyError")}
                          </span>
                        ) : null}
                      </div>
                      <div className="mt-4 flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setSelectedProfileKey(key)}
                          className={subtleButtonClass}
                        >
                          {t("settings.configure")}
                        </button>
                        <button
                          type="button"
                          onClick={() => setDefaultProfile(index)}
                          disabled={profile.active || saving}
                          className={secondaryButtonClass}
                        >
                          {t(
                            profile.active
                              ? "settings.currentDefault"
                              : "settings.makeDefault",
                          )}
                        </button>
                      </div>
                    </article>
                  );
                })}
              </div>

              {selectedProfile ? (
                (() => {
                  const index = selectedProfileIndex;
                  const profile = selectedProfile;
                  const key = profileKey(profile, index);
                  const modelState = modelResults[key];
                  const testState = testResults[key];
                  const nameInvalid = invalidProfileKey === key;
                  const nameInputId = `profile-name-${index}`;
                  const nameErrorId = `profile-name-error-${index}`;
                  return (
                    <article key={key} className={`${panelClass} p-4 sm:p-5`}>
                      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <h3 className="font-medium text-gray-900 text-sm dark:text-gray-50">
                            {t("settings.details")}
                          </h3>
                          <p className={helperTextClass}>
                            {t("settings.detailsDescription")}
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() => setSelectedProfileKey(null)}
                          className={subtleButtonClass}
                        >
                          {t("settings.collapse")}
                        </button>
                      </div>
                      <div className="grid gap-4 sm:grid-cols-2">
                        <div className="block">
                          <label className={labelClass} htmlFor={nameInputId}>
                            {t("settings.name")}
                          </label>
                          <input
                            id={nameInputId}
                            className={`${inputClass} ${
                              nameInvalid
                                ? "border-red-500 focus:border-red-500 focus:ring-red-500/20 dark:border-red-500"
                                : ""
                            }`}
                            value={profile.name}
                            required
                            aria-invalid={nameInvalid ? "true" : undefined}
                            aria-describedby={
                              nameInvalid ? nameErrorId : undefined
                            }
                            onChange={(event) =>
                              updateProfile(index, {
                                name: event.target.value,
                              })
                            }
                          />
                          {nameInvalid ? (
                            <p
                              id={nameErrorId}
                              role="alert"
                              className="mt-1 text-red-600 text-sm dark:text-red-400"
                            >
                              {t("settings.profileNameRequired")}
                            </p>
                          ) : null}
                        </div>
                        <label className="block">
                          <span className={labelClass}>
                            {t("settings.provider")}
                          </span>
                          <select
                            className={selectClass}
                            value={profile.provider}
                            onChange={(event) =>
                              updateProfile(index, {
                                provider: event.target.value,
                              })
                            }
                          >
                            {PROVIDER_OPTIONS.map((option) => (
                              <option key={option.value} value={option.value}>
                                {t(option.labelKey)}
                              </option>
                            ))}
                            {!PROVIDER_OPTIONS.some(
                              (option) => option.value === profile.provider,
                            ) && (
                              <option value={profile.provider}>
                                {profile.provider}
                              </option>
                            )}
                          </select>
                        </label>
                        <label className="block">
                          <span className={labelClass}>
                            {t("settings.baseUrl")}
                          </span>
                          <input
                            className={inputClass}
                            value={profile.baseUrl}
                            placeholder="https://api.anthropic.com"
                            onChange={(event) =>
                              updateProfile(index, {
                                baseUrl: event.target.value,
                              })
                            }
                          />
                        </label>
                        <div className="block">
                          <span className={labelClass}>
                            {t("settings.model")}
                          </span>
                          <div className="mt-1 flex gap-2">
                            <input
                              aria-label={t("settings.model")}
                              className={`${inputClass} mt-0`}
                              value={profile.model}
                              placeholder="claude-opus-4-8"
                              onChange={(event) =>
                                updateProfile(index, {
                                  model: event.target.value,
                                })
                              }
                            />
                            <button
                              type="button"
                              onClick={() => fetchModels(profile, index)}
                              disabled={modelState?.loading}
                              className={`${secondaryButtonClass} shrink-0`}
                            >
                              {t(
                                modelState?.loading
                                  ? "settings.fetchingModels"
                                  : "settings.fetchModels",
                              )}
                            </button>
                          </div>
                          {modelState?.models.length ? (
                            <select
                              aria-label={t("settings.chooseModel")}
                              className={selectClass}
                              value={profile.model}
                              onChange={(event) =>
                                updateProfile(index, {
                                  model: event.target.value,
                                })
                              }
                            >
                              {!modelState.models.includes(profile.model) && (
                                <option value={profile.model}>
                                  {profile.model || t("settings.manualModel")}
                                </option>
                              )}
                              {modelState.models.map((model) => (
                                <option key={model} value={model}>
                                  {model}
                                </option>
                              ))}
                            </select>
                          ) : null}
                          {modelState?.message ? (
                            <p
                              className={`mt-1 text-xs ${
                                modelState.status === "error"
                                  ? "text-red-600 dark:text-red-400"
                                  : "text-gray-500 dark:text-gray-400"
                              }`}
                            >
                              {modelState.message}
                            </p>
                          ) : null}
                        </div>
                        <label className="block">
                          <span className={labelClass}>
                            {t("settings.temperature")}
                          </span>
                          <input
                            className={inputClass}
                            type="number"
                            step="0.1"
                            min="0"
                            max="2"
                            value={profile.temperatureText}
                            onChange={(event) =>
                              updateProfile(index, {
                                temperatureText: event.target.value,
                              })
                            }
                          />
                        </label>
                        <label className="block">
                          <span className={labelClass}>
                            {t("settings.maxTokens")}
                          </span>
                          <input
                            className={inputClass}
                            type="number"
                            min="1"
                            value={profile.maxTokensText}
                            onChange={(event) =>
                              updateProfile(index, {
                                maxTokensText: event.target.value,
                              })
                            }
                          />
                        </label>
                        <label className="block sm:col-span-2">
                          <span className={labelClass}>
                            {t("settings.apiKey")}
                          </span>
                          <input
                            className={inputClass}
                            type="password"
                            autoComplete="off"
                            value={profile.apiKeyInput}
                            placeholder={
                              profile.hasApiKey
                                ? t("settings.keyConfiguredPlaceholder")
                                : t("settings.keyMissingPlaceholder")
                            }
                            onChange={(event) =>
                              updateProfile(index, {
                                apiKeyInput: event.target.value,
                              })
                            }
                          />
                          {profile.keyUnavailable && (
                            <span className="mt-1 block text-red-600 text-xs dark:text-red-400">
                              {t("settings.keyUnavailable")}
                            </span>
                          )}
                        </label>
                      </div>

                      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-gray-200/70 border-t pt-3 dark:border-gray-800">
                        <p className={helperTextClass}>
                          {profile.active
                            ? t("settings.activeProfileDescription")
                            : t("settings.inactiveProfileDescription")}
                        </p>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={() => testConnection(profile, index)}
                            disabled={testingId === key}
                            className={subtleButtonClass}
                          >
                            {t(
                              testingId === key
                                ? "settings.testing"
                                : "settings.testConnection",
                            )}
                          </button>
                          <button
                            type="button"
                            onClick={() => removeProfile(index)}
                            disabled={deletingId === profile.id}
                            className={dangerButtonClass}
                          >
                            {deletingId === profile.id
                              ? t("common.deleting")
                              : confirmDeleteKey === key
                                ? t("common.confirmDelete")
                                : t("common.delete")}
                          </button>
                        </div>
                      </div>
                      {testState ? (
                        <p
                          className={`mt-2 text-xs ${
                            testState.status === "ok"
                              ? "text-gray-500 dark:text-gray-400"
                              : "text-red-600 dark:text-red-400"
                          }`}
                        >
                          {testState.message}
                        </p>
                      ) : null}
                    </article>
                  );
                })()
              ) : (
                <p className={helperTextClass}>{t("settings.selectProfile")}</p>
              )}
            </div>
          )}
        </fieldset>
      ) : null}

      {dirty || saving || (!toast.available && (error || notice)) ? (
        <div className="sticky bottom-3 z-10 mr-14 flex flex-wrap items-center justify-end gap-3 rounded-lg border border-gray-200/80 bg-white/90 p-2 shadow-lg shadow-gray-900/[0.06] backdrop-blur-xl sm:mr-0 dark:border-gray-800 dark:bg-gray-900/90 dark:shadow-black/20">
          <div className="mr-auto min-w-0 space-y-0.5">
            {dirty ? (
              <p
                role="status"
                className="font-medium text-gray-700 text-sm dark:text-gray-200"
              >
                {t("settings.unsaved")}
              </p>
            ) : null}
            {error && !toast.available ? (
              <p
                role="alert"
                className="text-red-600 text-sm dark:text-red-400"
              >
                {error}
              </p>
            ) : null}
            {notice && !toast.available ? (
              <p
                role="status"
                className="text-gray-500 text-sm dark:text-gray-400"
              >
                {notice}
              </p>
            ) : null}
          </div>
          {dirty || saving ? (
            <button
              type="button"
              disabled={mutationBusy}
              onClick={save}
              className={primaryButtonClass}
            >
              {saving ? (
                <LoaderCircle className="h-4 w-4 animate-spin" />
              ) : (
                <Save className="h-4 w-4" />
              )}
              {t(saving ? "common.saving" : "settings.saveSettings")}
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
