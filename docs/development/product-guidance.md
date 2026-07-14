# Product Guidance

This document defines Sillage's product scope, terminology, and AI behavior. See the [Architecture Guide](architecture.md) and the code for implementation details, and the [Web Design Guidelines](design/README.md) for visual rules.

## Positioning

Sillage is a single-user personal record space for capturing daily events, thoughts, and feelings. AI generates summaries only from those records, grounds claims about the user's personal history in cited records, and may answer general questions without pretending those answers came from the record history.

One-line description:

> Sillage is a personal record space for capturing records, revisiting history, and asking questions with personal claims grounded in those records.

The product should be private, clear, concrete, easy to understand, and suitable for long-term use. It is not:

- a multi-user collaboration or social publishing platform;
- a knowledge base, task manager, or project management system;
- a complex file drive or a formal long-form writing platform;
- a mood tracker, a diagnostic tool, or a product where AI directs the user's expression.

Tags, public sharing, reactions, relations, public discovery, RSS, and sitemaps are outside the current product scope.

Public ingress, TLS termination, DNS, tunneling, CDNs, and other edge-network services are operator-owned infrastructure outside the product and repository. Sillage remains vendor-neutral and does not ship third-party network connectors, credentials, or vendor-specific deployment paths.

AI services associated with edge-network platforms may be reached only through operator-configured compatible endpoints. They do not receive named provider presets, adapters, defaults, or platform-specific behavior in Sillage.

## Core Concepts

| English | Simplified Chinese UI | Meaning |
| --- | --- | --- |
| Record | 记录 | An event, thought, feeling, text, or attachment captured by the user |
| History | 历史 | Records revisited over time |
| Ask | 问答 | Questions and follow-up questions, with claims about personal history grounded in records |
| Summary | 总结 | An AI-generated overview based on records |
| Source | 来源 | An original record cited by a summary or answer |

The backend, database, Proto, and API use `memo`; English user-facing documentation and copy use `record`; the Simplified Chinese UI always displays `记录`. The product name `Sillage` is never translated.

## Records

A record is the only content unit. A day may contain multiple records, and each record requires only a date and Markdown body. Titles, types, tags, weather, location, and predefined moods are not required.

Writing takes priority over organization: entry points should be direct, and short and long content use the same editor. Favorites and archives are lightweight states. Archiving is not deletion, and the favorited, unarchived, and archived views must remain clear and reversible.

Unsaved Web drafts must be recoverable. When a client leaves a dirty editor, detects that a draft's baseline version has changed, or abandons an in-progress attachment upload, it must preserve the content and show a clear warning instead of silently overwriting it.

## Information Architecture

The Web client has only two primary navigation destinations:

- Write a Record (`写记录`): create a record and view today's and recent records;
- All Records (`全部记录`): list, calendar, search, favorites, and archives.

Ask is entered through Start Ask (`开始问答`) and the conversation area instead of taking another primary navigation slot. Settings live in the user menu. A quick-capture entry point is globally available outside the Ask page.

The Android bottom bar uses Records / Calendar / Ask / Settings (`记录 / 日历 / 问答 / 设置`), with list states and search inside the Records screen. System Back returns from Calendar, Ask, or Settings to the starting Records destination before leaving the app. The client may connect to an instance online or save locally while offline. Sync is currently triggered manually; background sync and push notifications are not provided.

## Summaries and Ask

A summary must state what it is based on and provide a path back to its source records. It summarizes events, recurring themes, and content worth revisiting; it must not diagnose the user or present uncertain conclusions as facts.

Ask supports multi-turn conversations, branches, regeneration, source citations, conversation search, and archiving. The recommended default context is the most recent 30 days. Full history may be used only when the user explicitly selects it. Answer-level source links appear only when an answer actually cites records.

Each Ask turn normally uses the configured AI provider twice. The first request receives the system prompt, current question, and current branch history, but no attached record content; it classifies the turn as `general`, `records`, or `mixed` and generates retrieval phrases. A `general` turn proceeds to an answer request with no record lookup or attached sources. For `records` and `mixed`, Sillage uses the generated phrases to select relevant records locally, then attaches only those excerpts or summaries to the answer request. An invalid routing response falls back to `records`, preserving the requirement that personal claims need record evidence.

Answers should give a short conclusion first, followed by evidence and possible follow-up directions. Greetings and general-knowledge questions may be answered naturally from the model's general knowledge without record citations; they must not be reframed as failed searches of the user's records. Claims about the user's life, history, or state must be supported by cited source records. When the available material is insufficient for such a personal claim, the answer must say clearly that the existing records do not provide enough information to determine it. A mixed answer must distinguish record-backed observations from general guidance.

## AI Boundaries

AI may:

- summarize records;
- identify recurring themes or changes in expression;
- answer questions related to records;
- answer general questions without citations when it makes no claim about the user or their records;
- suggest source-grounded follow-up questions;
- save an answer as a new record.

AI must not:

- present speculation as fact;
- use diagnostic psychological language;
- draw conclusions about the user without sources;
- attach unrelated or fabricated record citations to a general answer;
- present general model knowledge as evidence about the user;
- force interruptions while the user is writing;
- replace or obscure original records with AI-generated content.

For personal-record questions, recommended phrasing should communicate the equivalent of:

> These records repeatedly mention...

> The evidence currently available is...

> The records do not contain enough information to confirm this.

## Copy Guidelines

- Use plain, direct, concrete Simplified Chinese in the product UI. Do not rely on poetic metaphors to explain functionality.
- Use equally plain, direct English copy in the English UI; prefer short action labels and sentence case.
- Use the localized equivalents of Record / All Records / Ask / Summary / Source (`记录 / 全部记录 / 问答 / 总结 / 来源`) and do not expose `memo` or the English word `Ask` to users in the Simplified Chinese UI.
- Button labels describe actions, such as Save, Continue Asking, View Sources, and Save as Record.
- Empty states explain the current state and the next action instead of advertising features.

## Accessibility and Status Feedback

Android keeps the Record search and Ask composer field names visible as persistent labels and exposes actual screen and section titles as accessibility headings. Visual emphasis alone does not make a label a heading.

Web and Android search results belong to the exact query that completed successfully; cached results from another query are never shown as current matches. On Android, the localized result count remains visible and readable by assistive technology, but is announced only once for each new successful completion. Editing the query, a failed search, returning to the screen, or an unrelated record update must not replay a stale success announcement.

## Interface Languages

The Web and Android clients support English and Simplified Chinese. Simplified Chinese remains the default for existing and new installations until the user chooses English. The selected interface language is stored only on the current browser or Android device; it is not an account setting and does not sync through the server.

- `Sillage` is never translated.
- English UI uses Record / All Records / Ask / Summary / Source. Simplified Chinese UI uses `记录 / 全部记录 / 问答 / 总结 / 来源`.
- Language switching covers navigation, controls, status and error feedback, accessibility names, and client-formatted dates and counts.
- Switching the interface language does not translate user records, stored conversation titles, summaries, answers, provider responses, or other existing content.
- Controls must remain readable without horizontal overflow in both languages, including compact mobile layouts.

Before introducing a new concept or primary destination, confirm that it cannot be expressed through the existing core concepts and that it does not compromise the single-user private scope, writing-first design, or source-grounded personal-record behavior.
