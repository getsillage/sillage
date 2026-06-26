import { useEffect, useState } from "react";
import {
  type AIProfile,
  type AIProfileInput,
  getAISettings,
  patchAISettings,
} from "../lib/api";

const PROVIDER_OPTIONS = [
  { value: "anthropic", label: "Anthropic Claude" },
  { value: "openai", label: "OpenAI" },
  { value: "workers-ai", label: "Cloudflare Workers AI" },
];

// Local editing copy: apiKeyInput holds a freshly typed key. Empty means keep
// the stored key untouched (the server only returns hasApiKey, never the key).
type EditableProfile = AIProfile & { apiKeyInput: string };

function toEditable(profile: AIProfile): EditableProfile {
  return { ...profile, apiKeyInput: "" };
}

function blankProfile(): EditableProfile {
  return {
    id: "",
    name: "新档案",
    provider: "anthropic",
    baseUrl: "",
    model: "",
    temperature: 0.3,
    maxTokens: 1000,
    enabled: true,
    active: false,
    hasApiKey: false,
    keyUnavailable: false,
    createdAt: "",
    updatedAt: "",
    apiKeyInput: "",
  };
}

export function SettingsWorkspace({ token }: { token: string }) {
  const [profiles, setProfiles] = useState<EditableProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    getAISettings(token)
      .then((res) => {
        if (!cancelled) {
          setProfiles(res.profiles.map(toEditable));
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "读取 AI 设置失败");
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  function updateProfile(index: number, patch: Partial<EditableProfile>) {
    setProfiles((current) =>
      current.map((profile, i) =>
        i === index ? { ...profile, ...patch } : profile,
      ),
    );
  }

  async function save() {
    setSaving(true);
    setNotice("");
    setError("");
    try {
      const payload: AIProfileInput[] = profiles.map((profile) => ({
        id: profile.id || undefined,
        name: profile.name,
        provider: profile.provider,
        baseUrl: profile.baseUrl,
        model: profile.model,
        temperature: profile.temperature,
        maxTokens: profile.maxTokens,
        enabled: profile.enabled,
        active: profile.active,
        apiKey: profile.apiKeyInput.trim() ? profile.apiKeyInput : undefined,
      }));
      const res = await patchAISettings(token, payload);
      setProfiles(res.profiles.map(toEditable));
      setNotice("已保存");
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <section className="settings-workspace">
        <p className="empty-list">正在读取设置…</p>
      </section>
    );
  }

  return (
    <section className="settings-workspace">
      <header className="settings-header">
        <div>
          <h1>AI 设置</h1>
          <p>
            配置用于总结与照见的模型档案。密钥加密保存在本地服务端，不会回显。
          </p>
        </div>
        <button
          className="secondary-button"
          type="button"
          onClick={() => setProfiles((current) => [...current, blankProfile()])}
        >
          新增档案
        </button>
      </header>

      {profiles.length === 0 ? (
        <p className="empty-list">还没有 AI 档案。点击「新增档案」添加一个。</p>
      ) : (
        <div className="settings-profiles">
          {profiles.map((profile, index) => (
            <article
              className="settings-profile"
              key={profile.id || `new-${index}`}
            >
              <label className="settings-field">
                <span>名称</span>
                <input
                  value={profile.name}
                  onChange={(event) =>
                    updateProfile(index, { name: event.target.value })
                  }
                />
              </label>
              <label className="settings-field">
                <span>Provider</span>
                <select
                  value={profile.provider}
                  onChange={(event) =>
                    updateProfile(index, { provider: event.target.value })
                  }
                >
                  {PROVIDER_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                  {!PROVIDER_OPTIONS.some(
                    (option) => option.value === profile.provider,
                  ) && (
                    <option value={profile.provider}>{profile.provider}</option>
                  )}
                </select>
              </label>
              <label className="settings-field">
                <span>Base URL</span>
                <input
                  value={profile.baseUrl}
                  placeholder="https://api.anthropic.com"
                  onChange={(event) =>
                    updateProfile(index, { baseUrl: event.target.value })
                  }
                />
              </label>
              <label className="settings-field">
                <span>模型</span>
                <input
                  value={profile.model}
                  placeholder="claude-opus-4-8"
                  onChange={(event) =>
                    updateProfile(index, { model: event.target.value })
                  }
                />
              </label>
              <label className="settings-field">
                <span>温度</span>
                <input
                  type="number"
                  step="0.1"
                  min="0"
                  max="2"
                  value={profile.temperature}
                  onChange={(event) =>
                    updateProfile(index, {
                      temperature: Number.parseFloat(event.target.value) || 0,
                    })
                  }
                />
              </label>
              <label className="settings-field">
                <span>最大 Tokens</span>
                <input
                  type="number"
                  min="1"
                  value={profile.maxTokens}
                  onChange={(event) =>
                    updateProfile(index, {
                      maxTokens: Number.parseInt(event.target.value, 10) || 0,
                    })
                  }
                />
              </label>
              <label className="settings-field settings-field-wide">
                <span>API 密钥</span>
                <input
                  type="password"
                  autoComplete="off"
                  value={profile.apiKeyInput}
                  placeholder={
                    profile.hasApiKey ? "已配置，留空保持不变" : "未配置"
                  }
                  onChange={(event) =>
                    updateProfile(index, { apiKeyInput: event.target.value })
                  }
                />
                {profile.keyUnavailable && (
                  <small className="form-error">
                    当前密钥无法解密，请重新填写。
                  </small>
                )}
              </label>
              <div className="settings-toggles">
                <label>
                  <input
                    type="checkbox"
                    checked={profile.enabled}
                    onChange={(event) =>
                      updateProfile(index, { enabled: event.target.checked })
                    }
                  />
                  启用
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={profile.active}
                    onChange={(event) =>
                      updateProfile(index, { active: event.target.checked })
                    }
                  />
                  设为默认
                </label>
              </div>
            </article>
          ))}
        </div>
      )}

      <div className="settings-actions">
        {error && <p className="form-error">{error}</p>}
        {notice && <p className="form-note">{notice}</p>}
        <button
          className="secondary-button"
          type="button"
          disabled={saving}
          onClick={save}
        >
          {saving ? "保存中" : "保存设置"}
        </button>
      </div>
    </section>
  );
}
