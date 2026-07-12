# Product Guidance

This document defines Sillage's product scope, terminology, and AI behavior. See the [Architecture Guide](architecture.md) and the code for implementation details, and the [Web Design Guidelines](design/README.md) for visual rules.

## Positioning

Sillage is a single-user personal record space for capturing daily events, thoughts, and feelings. AI generates summaries only from those records and lets the user ask questions about existing content.

One-line description:

> Sillage is a personal record space for capturing records, revisiting history, and asking questions based on those records.

The product should be private, clear, concrete, easy to understand, and suitable for long-term use. It is not:

- a multi-user collaboration or social publishing platform;
- a knowledge base, task manager, or project management system;
- a complex file drive or a formal long-form writing platform;
- a mood tracker, a diagnostic tool, or a product where AI directs the user's expression.

Tags, public sharing, reactions, relations, public discovery, RSS, and sitemaps are outside the current product scope.

## Core Concepts

| English | Simplified Chinese UI | Meaning |
| --- | --- | --- |
| Record | 记录 | An event, thought, feeling, text, or attachment captured by the user |
| History | 历史 | Records revisited over time |
| Ask | 问答 | Search, questions, and follow-up questions grounded in records |
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

The Android bottom bar uses Records / Calendar / Ask / Settings (`记录 / 日历 / 问答 / 设置`), with list states and search inside the Records screen. The client may connect to an instance online or save locally while offline. Sync is currently triggered manually; background sync and push notifications are not provided.

## Summaries and Ask

A summary must state what it is based on and provide a path back to its source records. It summarizes events, recurring themes, and content worth revisiting; it must not diagnose the user or present uncertain conclusions as facts.

Ask supports multi-turn conversations, branches, regeneration, source citations, conversation search, and archiving. The recommended default context is the most recent 30 days. Full history may be used only when the user explicitly selects it.

Answers should give a short conclusion first, followed by evidence and possible follow-up directions. When the available material is insufficient, the answer must say clearly that the existing records do not provide enough information to determine the answer.

## AI Boundaries

AI may:

- summarize records;
- identify recurring themes or changes in expression;
- answer questions related to records;
- suggest source-grounded follow-up questions;
- save an answer as a new record.

AI must not:

- present speculation as fact;
- use diagnostic psychological language;
- draw conclusions about the user without sources;
- force interruptions while the user is writing;
- replace or obscure original records with AI-generated content.

Recommended phrasing should communicate the equivalent of:

> These records repeatedly mention...

> The evidence currently available is...

> The records do not contain enough information to confirm this.

## Copy Guidelines

- Use plain, direct, concrete Simplified Chinese in the product UI. Do not rely on poetic metaphors to explain functionality.
- Use equally plain, direct English copy in the English UI; prefer short action labels and sentence case.
- Use the localized equivalents of Record / All Records / Ask / Summary / Source (`记录 / 全部记录 / 问答 / 总结 / 来源`) and do not expose `memo` or the English word `Ask` to users in the Simplified Chinese UI.
- Button labels describe actions, such as Save, Continue Asking, View Sources, and Save as Record.
- Empty states explain the current state and the next action instead of advertising features.

## Interface Languages

The Web and Android clients support English and Simplified Chinese. Simplified Chinese remains the default for existing and new installations until the user chooses English. The selected interface language is stored only on the current browser or Android device; it is not an account setting and does not sync through the server.

- `Sillage` is never translated.
- English UI uses Record / All Records / Ask / Summary / Source. Simplified Chinese UI uses `记录 / 全部记录 / 问答 / 总结 / 来源`.
- Language switching covers navigation, controls, status and error feedback, accessibility names, and client-formatted dates and counts.
- Switching the interface language does not translate user records, stored conversation titles, summaries, answers, provider responses, or other existing content.
- Controls must remain readable without horizontal overflow in both languages, including compact mobile layouts.

Before introducing a new concept or primary destination, confirm that it cannot be expressed through the existing core concepts and that it does not compromise the single-user private scope, writing-first design, or source-grounded AI behavior.
