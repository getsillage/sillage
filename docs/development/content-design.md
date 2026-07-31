# Content Design

This document defines the stable rules for user-facing content across the Web
client, Android client, website, user documentation, and release-facing
surfaces. Product scope and AI behavior remain defined by
[Product Guidance](product-guidance.md); the organization-wide public voice and
positioning remain defined by the
[Brand and Public Content Guide](https://github.com/getsillage/.github/blob/main/BRAND.md).

## Principles

Sillage content should help a person understand what is happening, what they
can do next, and where their data goes.

1. **Lead with the user's goal.** Name the task or outcome before implementation
   details.
2. **Be direct and concrete.** Prefer specific nouns and verbs over metaphors,
   slogans, or abstract claims.
3. **Make the next action clear.** Empty, error, and confirmation states should
   tell the user what changed and what they can do.
4. **Explain boundaries at the point of choice.** State external data transfer,
   destructive effects, and unavailable behavior before the user acts.
5. **Describe only verified behavior.** Do not imply official hosting,
   background sync, built-in AI, diagnosis, or guarantees the product does not
   provide.
6. **Keep English and Simplified Chinese semantically aligned.** The two
   versions may differ in sentence structure, but not in scope, risk, or
   promised behavior.

## Core Terminology

Use the narrowest term that accurately identifies the object or location.

| Concept | English | Simplified Chinese | Notes |
| --- | --- | --- | --- |
| User content | record | 记录 | `memo` is implementation-only |
| Record history | history | 历史 | Do not use knowledge base or timeline as a new product concept |
| AI conversation | Ask | 问答 | Chinese UI never exposes the English surface name `Ask` |
| AI overview | summary | 总结 | A summary is generated from record content |
| Cited evidence | source | 来源 | A source links back to an original record |
| Running installation | Sillage instance | Sillage 实例 | One instance has one account |
| Device in the user's hand | this device | 当前设备 / 本机 | Use only when the data or action is actually device-local |
| Sillage backend | Sillage server | Sillage 服务端 | It may run on the current machine or a remote machine |
| Host of the instance | the machine running Sillage | 运行 Sillage 的机器 | Use when describing where instance data is stored |
| Persistent storage | data directory | 数据目录 | Includes SQLite, attachments, and runtime secrets |
| External AI destination | configured AI endpoint | 配置的 AI 端点 | It is chosen and operated by the user or deployer |
| Saved AI configuration | AI profile | AI 档案 | Includes protocol, endpoint, model, and secret state |

Do not use **cloud** (`云端`) as a synonym for a user's Sillage server. A
self-hosted server may be local, on a home network, or on a remote host. Use
`Sillage server` / `Sillage 服务端` for sync destinations and `configured AI
endpoint` / `配置的 AI 端点` for external model requests.

## Interface Patterns

### Titles and leads

- A page title names the place or task: `All records`, `Settings`, `开始问答`.
- A lead explains the purpose or scope in one sentence. It should not repeat the
  title or begin with product marketing.
- Put the most common or foundational settings first in summary copy. Optional
  AI configuration should not make the product sound AI-dependent.

### Actions

- Use a verb that describes the immediate result: `Save`, `Retry`, `View
  sources`, `存为记录`.
- Keep one concept per label. Put consequences or prerequisites in supporting
  text, not inside a long button label.
- Use an in-progress form while an action is running: `Saving…`, `正在上传…`.
  Disable conflicting actions until the operation finishes.

### Supporting text

- Explain information the label cannot carry: destination, scope, prerequisite,
  persistence, or consequence.
- Place privacy-relevant explanations beside the action or setting they qualify.
- Do not rely on a separate privacy page to disclose an external AI request.

Recommended:

> Send this record to your configured AI endpoint to create a short summary.

> 将这条记录发送到你配置的 AI 端点，并生成一段简短总结。

Avoid:

> Let AI reflect on this record.

> 让 AI 帮你深入洞察自己。

### Status and progress

- Describe the current operation, not an internal implementation phase or a
  vague promise.
- Use distinct text when phases have meaning for the user.

Recommended Ask sequence:

1. `Checking whether records are needed` / `正在判断是否需要查找记录`
2. `Finding relevant records` / `正在查找相关记录`
3. `Generating` / `生成中`

Avoid using the same `Preparing the answer` message for every phase.

### Empty states

An empty state contains, in order:

1. what is empty;
2. whether that state is expected;
3. the next available action, when one exists.

Recommended: `No archived conversations yet.`

Recommended: `Choose an example question or enter what you want to know.`

Avoid feature advertising, guilt, urgency, or claims that content is missing
when it may simply be outside the selected filter.

### Errors

- State what failed in plain language.
- Preserve user input and loaded content when retry is possible.
- Provide a concrete recovery action when the user can recover.
- Do not expose raw provider responses, stack traces, implementation names, or
  credentials.
- Distinguish authentication, connection, validation, conflict, unsupported
  version, and external-provider failures when the recovery differs.

### Confirmations and destructive actions

- The title asks about the specific action: `Delete this record?`.
- The body states the result, recovery window, and any permanent effect.
- The confirm action names the destructive action; the cancel action preserves
  the user's work.
- Never use a generic `OK` for deletion, discard, sign-out with unsaved work, or
  conflict resolution.

## AI and External Data

AI is optional. Content must make the following behavior understandable without
requiring the user to know how model providers work.

### Before sending data

Supporting text for Summary, automatic summaries, and AI configuration must
state that record content is sent to the configured AI endpoint. For automatic
behavior, also name the trigger and the profile used.

Recommended:

> After a new record is saved, use the default AI profile to send its content to
> your configured endpoint and create a short summary.

Do not say only `Generate a summary automatically`; that hides the external
request and its trigger.

### Personal claims and sources

- General questions may be answered directly without fabricated sources.
- Claims about the user's life, history, behavior, or condition must cite
  relevant records from the selected range.
- If the selected records are insufficient, say so explicitly.
- Separate record-backed observations from general guidance in a mixed answer.
- Use evidence-led language such as `These records mention…` and `The available
  records do not provide enough information…`.

Suggested questions should point to observable record content. Prefer `Which
themes changed across my recent records?` over `How has my state changed?`.

### Health and psychological language

Do not present Sillage as a diagnostic, therapeutic, mood-tracking, or
personality-analysis tool. Avoid unsupported terms such as `mental state`,
`diagnosis`, `personality`, `risk`, or `condition` when describing the user.
AI may summarize changes in topics or expression only when supported by cited
records and described as observations, not conclusions about the person.

## Self-hosting and Data Location

Location copy must remain true for both a laptop installation and a remote
self-hosted server.

Recommended:

> Records stay in the data directory on the machine running Sillage.

> 记录保存在运行 Sillage 的机器和数据目录中。

Avoid:

> Records always stay on this device.

> 数据保存在本机。

Use `this device` only for Android offline data, cached attachments, and other
state that is actually stored on that Android device. Use `Sillage server` for
manual sync. Use `localhost` only when describing the default network binding,
not as a synonym for the deployment host.

## English Style

- Use sentence case for headings, buttons, and status text.
- Prefer contractions only when they improve natural user-facing prose; avoid
  them in error identifiers, technical instructions, and formal warnings.
- Use the serial comma in prose lists.
- Use an ellipsis character (`…`) for visible in-progress or input placeholder
  text. Do not add an ellipsis to an action that opens a page rather than a
  dialog.
- Avoid title-style capitalization for ordinary controls.

## Simplified Chinese Style

- Use concise, natural Simplified Chinese; do not translate English word order
  mechanically.
- Use full-width Chinese punctuation in sentences. Keep product names, URLs,
  commands, protocol names, and code identifiers unchanged.
- Add spaces between Chinese and Latin product or protocol names where it aids
  readability: `AI 端点`, `Android 客户端`, `OpenAI 兼容接口`.
- Do not add a full stop to short labels, buttons, tabs, or navigation items.
- Use `…` for visible in-progress or input placeholder text, not three periods.
- Prefer `无法…` for a failed capability and `请…` only when the user has a
  concrete recovery action.

## Review Checklist

Before merging user-facing content, verify:

- the user's goal and next action are clear;
- English and Simplified Chinese preserve the same meaning and risk;
- core terminology matches this document and Product Guidance;
- `memo`, English `Ask` in Chinese UI, and `云端` as the Sillage server are not
  exposed;
- device, server, host machine, data directory, and AI endpoint are not
  conflated;
- Summary and automatic-summary copy disclose the external AI request;
- personal claims require sources and suggested questions do not imply
  diagnosis or unsupported state inference;
- loading, empty, failure, retry, conflict, and destructive states remain
  actionable;
- text fits narrow layouts and retains accessible names in both languages;
- public claims match current code, user documentation, and release evidence.

Stable terminology and disclosure red lines are enforced by
`scripts/check-terminology.mjs` and `scripts/check-content-copy.mjs`. Automated
checks support review; they do not replace reading the complete user journey in
both languages.
