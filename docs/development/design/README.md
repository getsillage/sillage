# Web Design Guidelines

This document covers the stable visual and interaction constraints for the Web client. See [Product Guidance](../product-guidance.md) for product semantics. The code sources of truth for themes and components are `web/src/styles/app.css` and `web/src/components/ui.ts`.

## Direction

The interface should feel quiet, focused, and clear, and should support repeated writing and reading. It must not resemble an administration dashboard or marketing page.

Hard constraints:

1. Brand text, navigation, and routine feedback use neutral grays. The product icon retains its own brand colors, and red is reserved for errors and destructive actions.
2. The entire interface uses sans-serif type, with weight and whitespace establishing hierarchy.
3. Desktop uses a collapsible left sidebar, mobile uses a drawer, and body content maintains a centered reading width.
4. Ask may use a chat layout; records, history, and settings do not use chat bubbles.
5. Action icons use `lucide-react`, except for the product icon and platform icons. Icon buttons must have accessible names.
6. Light and dark themes have equal support. New styles must work in both.

## Layout and Components

- The record home and detail views use a narrow reading column; history and settings use a wide column; Ask uses a dedicated conversation column.
- Prefer whitespace and subtle dividers. Do not nest cards or build hierarchy with heavy stacked shadows.
- Reuse the buttons, inputs, icon buttons, segmented controls, skeletons, and empty-state styles from `ui.ts`.
- Extract a shared token only after the same style appears at least three times, and update this document at the same time. Do not scatter hex values, introduce a second component library, or hand-draw SVG icons.
- Reuse the existing typography styles for rendered Markdown instead of creating another rich-text presentation.
- Keep icon buttons at a stable touch size. Use segmented controls for view selection and switches or checkboxes for binary settings.
- Keep the language selector in Appearance as a compact segmented control. Authentication screens may expose the same choice as a quiet utility control so language can be changed before sign-in.

Page headings and control labels must match the scale of their containers. Narrow screens must not have horizontal overflow, and text must not cover adjacent content.

## Interaction Requirements

- New and edited drafts are isolated by record. In-app navigation, closing the page, and signing out must not bypass unsaved-change protection.
- An edit submission includes the server version associated with the draft. Version conflicts must preserve the draft and ask the user to refresh or retry; they must never overwrite silently.
- Saving, sending, regenerating, and quick capture enforce single-flight behavior at the event entry point so repeated clicks cannot create duplicate writes.
- Conflicting actions are disabled while a save or upload is in progress, with a clear in-progress state.
- After navigation, refresh, or a canonical write, a late response must not overwrite the current page or cache.
- Transient failures preserve loaded content that belongs to the current view or normalized search query, preserve user input, and offer a manual retry. Cached results from another query are never shown as current matches.
- Only one pagination request may run for a given list cursor. The calendar reads every page before presenting results.
- Ask creation, streaming answers, regeneration, and conversation switching must isolate stale requests.
- Transient operation feedback uses the global Toast queue. Successful writes,
  state changes, uploads, and non-recoverable action errors must not add local
  notification blocks inside feature layouts.
- Error Toasts take priority over routine success and informational feedback;
  they may replace the currently visible routine Toast and stay visible longer.
- Loading and search failures with a retry action, field validation, version
  conflicts, unsaved-change protection, and destructive confirmation remain in
  context; they must not rely on an auto-closing Toast alone.

## Accessibility and Responsive Behavior

- Each page has one main content region, and keyboard focus order matches visual order.
- Client-side page changes update the document title, announce the destination politely, and move focus to the main heading after overlays close. Forward and replacement navigation brings that heading into view; browser-history navigation preserves the existing scroll position. The initial render and in-page filters do not steal focus; changing the active Ask conversation counts as page navigation.
- Every interactive element has a visible `focus-visible` state, and critical text meets WCAG AA contrast requirements.
- Primary mobile actions and icon buttons use stable touch dimensions; `ui.ts` is the source of truth for exact sizes.
- Modals and drawers close with Escape and restore focus to their trigger. Escape closes only the topmost layer, and global shortcuts do not open another layer while a modal is active.
- An open mobile drawer locks background scrolling and traps focus. Top and floating actions account for safe areas.
- Hover must not be the only feedback. Loading copy, icons, and dynamic states must not cause layout shifts.

## Acceptance

See the [Contributing Guide](../../../CONTRIBUTING.md) for automated quality gates. Interaction or visual changes must also be checked across:

- light and dark themes on desktop and mobile;
- English and Simplified Chinese on desktop and mobile, including long English labels and client-formatted dates;
- Write a Record, All Records, record detail, Ask, settings, initialization, and sign-in views;
- narrow-screen overflow, keyboard navigation, focus restoration, layered Escape behavior, and dialog semantics;
- loading, empty, failure, retry, unsaved, version-conflict, and in-progress states;
- browser console and page errors, plus accessible names for buttons and icons.

Temporary screenshots and Playwright debugging cases are not committed. Changes to navigation, naming, or product shape must also update [Product Guidance](../product-guidance.md).
